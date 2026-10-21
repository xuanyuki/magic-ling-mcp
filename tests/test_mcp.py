"""Real MCP stdio transports against a controllable isolated loopback fixture."""
import asyncio
from contextlib import asynccontextmanager
import copy
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import pathlib
import sys
import tempfile
import threading
import unittest
import urllib.parse

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from bridge_client import BridgeClient, ObservationError


class Fixture:
    def __init__(self):
        self.lock = threading.RLock()
        self.restart()

    def restart(self):
        self.session = "test-session-" + str(getattr(self, "generation", 0) + 1)
        self.generation = getattr(self, "generation", 0) + 1
        self.seq = 1
        self.stale = False
        self.compatible = True
        self.error = ""
        self.events = []
        self.state = {"scene": "GameScene", "map": {"origin": [0, 0], "rows": [".."],
                      "screenProjection": {"worldZero": [0, 0], "cellPixels": 16}, "viewport": [320, 240]},
                      "ui": {"text": [], "buttons": [], "modal": False}}

    def snapshot(self):
        return {"schema": 1, "session": self.session, "seq": self.seq,
                "observedAt": "2026-10-21T00:00:00Z", "state": copy.deepcopy(self.state)}

    def health(self):
        return {"installed": True, "connected": True, "session": self.session, "seq": self.seq,
                "stale": self.stale, "observationAgeMs": 11000 if self.stale else 1,
                "compatibility": {"compatible": self.compatible}, "lastError": self.error, "droppedLog": 0}

    def change(self, patch, logs=()):
        base = self.seq
        self.seq += 1
        for key, value in patch.items():
            if value is None:
                self.state.pop(key, None)
            else:
                self.state[key] = value
        self.events.append({"session": self.session, "base": base, "seq": self.seq,
                            "set": patch, "newLog": list(logs)})


@asynccontextmanager
async def session(output):
    params = StdioServerParameters(command=sys.executable,
                                  args=[str(ROOT / "mcp_server.py"), "--output", str(output)],
                                  env={"PYTHONUTF8": "1"}, cwd=str(ROOT))
    async with stdio_client(params) as (reader, writer):
        async with ClientSession(reader, writer) as client:
            initialized = await client.initialize()
            assert initialized.serverInfo.name == "Magic Ling Text Bridge"
            assert initialized.serverInfo.version == "0.9.6.5"
            yield client


async def call(client, tool, args=None, error=False):
    result = await client.call_tool(tool, args or {})
    assert bool(result.isError) == error, result
    assert len(result.content) == 1, result
    return json.loads(result.content[0].text)


class McpTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ml-mcp-")
        self.output = pathlib.Path(self.temp.name)
        self.fixture = fixture = Fixture()

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *unused):
                pass

            def do_GET(self):
                parsed = urllib.parse.urlsplit(self.path)
                with fixture.lock:
                    if parsed.path == "/health":
                        data = fixture.health()
                    elif parsed.path == "/snapshot":
                        data = fixture.snapshot()
                    elif parsed.path == "/changes":
                        query = urllib.parse.parse_qs(parsed.query)
                        seq = int(query["since"][0])
                        data = {"session": fixture.session, "seq": fixture.seq,
                                "resync": seq < 0 or seq > fixture.seq,
                                "events": [event for event in fixture.events if event["seq"] > seq]}
                    else:
                        self.send_error(404)
                        return
                    self.respond(data)

            def do_POST(self):
                with fixture.lock:
                    if not self.path.startswith("/capture?"):
                        self.send_error(405)
                        return
                    # File creation is tested against the real Java framebuffer separately.
                    fixture.change({})
                    self.respond({"snapshot": fixture.snapshot(), "screenshot": {
                        "path": str(self_test.output / "fixture.png"), "session": fixture.session, "seq": fixture.seq}})

            def respond(self, data):
                payload = json.dumps(data).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

        self_test = self
        self.http = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.thread.start()
        self.connection()

    def connection(self):
        (self.output / "connection.json").write_text(json.dumps({
            "url": f"http://127.0.0.1:{self.http.server_port}", "session": self.fixture.session}), encoding="utf-8")

    async def asyncTearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.thread.join(timeout=2)
        self.temp.cleanup()

    async def test_real_stdio_four_tools_independent_cursors_and_resync(self):
        async with session(self.output) as a, session(self.output) as b:
            tools = await a.list_tools()
            self.assertEqual({tool.name for tool in tools.tools},
                             {"observe_state", "read_changes", "health", "project_cell"})
            first = await call(a, "observe_state", {"format": "json"})
            other = await call(b, "read_changes", {"format": "json"})
            self.assertEqual(first["cursor"], other["cursor"])
            self.assertTrue((await call(a, "health"))["connected"])
            point = await call(a, "project_cell", {"x": 0, "y": 0, **first["cursor"]})
            self.assertEqual(point["screen"], [8, 8])
            with self.fixture.lock:
                self.fixture.change({"scene": "GameScene", "screenLog": None}, ["重复", "重复"])
            args = {"cursor": first["cursor"], "format": "json"}
            delta = await call(a, "read_changes", args)
            self.assertEqual(delta["events"][0]["newLog"], ["重复", "重复"])
            self.assertIsNone(delta["events"][0]["set"]["screenLog"])
            self.assertEqual((await call(b, "read_changes", args))["events"], delta["events"])
            self.assertEqual((await call(a, "read_changes", {"cursor": delta["cursor"], "format": "json"}))["events"], [])
            stale = await call(a, "project_cell", {"x": 0, "y": 0, **first["cursor"]}, error=True)
            self.assertEqual(stale["error"], "stale_coordinates")
            image = await call(a, "observe_state", {"format": "json", "include_screenshot": True})
            self.assertEqual(image["screenshot"]["seq"], image["seq"])
            self.assertTrue(pathlib.Path(image["screenshot"]["path"]).is_absolute())
            reset = await call(a, "read_changes", {"cursor": {"session": first["session"], "seq": 999}, "format": "json"})
            self.assertEqual(reset["type"], "snapshot")
            with self.fixture.lock:
                self.fixture.restart()
                self.connection()
            restart = await call(b, "read_changes", args)
            self.assertNotEqual(restart["session"], first["session"])
            text = await a.call_tool("observe_state")
            self.assertIn("完整状态", text.content[0].text)
        # A new stdio server has no cursor memory to lose or steal from clients.
        async with session(self.output) as c:
            again = await call(c, "read_changes", {"cursor": restart["cursor"], "format": "json"})
            self.assertEqual(again["events"], [])

    async def test_errors_stale_incompatible_occluded_and_disconnected(self):
        async with session(self.output) as client:
            first = await call(client, "observe_state", {"format": "json"})
            with self.fixture.lock:
                self.fixture.state["ui"]["modal"] = True
            blocked = await call(client, "project_cell", {"x": 0, "y": 0, **first["cursor"]}, error=True)
            self.assertEqual(blocked["error"], "invalid_target")
            with self.fixture.lock:
                self.fixture.stale = True
            stale = await call(client, "observe_state", error=True)
            self.assertEqual(stale["error"], "observation_unavailable")
            with self.fixture.lock:
                self.fixture.stale = False
                self.fixture.compatible = False
            unsupported = await call(client, "observe_state", error=True)
            self.assertEqual(unsupported["error"], "unsupported_game_version")
            version_health = await call(client, "health", error=True)
            self.assertTrue(version_health["connected"])
            self.assertFalse(version_health["compatibility"]["compatible"])
            (self.output / "connection.json").unlink()
            disconnected = await call(client, "health", error=True)
            self.assertFalse(disconnected["connected"])

    async def test_invalid_responses_are_explicit_and_cli_refuses_old_data(self):
        import subprocess
        reader = BridgeClient(self.output)
        for payload in ({"state": {}}, {"seq": True}, {"session": "wrong", "seq": 1}):
            with self.fixture.lock:
                self.fixture.snapshot = lambda: payload
            with self.assertRaises(ObservationError):
                reader.observe()
        for compatible, stale, error_code in ((False, False, "unsupported_game_version"),
                                               (True, True, "observation_unavailable")):
            self.fixture.compatible, self.fixture.stale = compatible, stale
            cli = subprocess.run([sys.executable, "-X", "utf8", str(ROOT / "ai_client.py"),
                                  "--output", str(self.output), "--mode", "snapshot"],
                                 capture_output=True, text=True, encoding="utf-8", timeout=5)
            self.assertEqual(cli.returncode, 2)
            self.assertEqual(json.loads(cli.stdout)["error"], error_code)

    async def test_local_url_and_cursor_validation(self):
        reader = BridgeClient(self.output)
        for address in ("https://example.com", "http://localhost:12", "http://127.0.0.1:12/redirect",
                        "http://user@127.0.0.1:12", "http://127.0.0.1:12?host=remote"):
            (self.output / "connection.json").write_text(json.dumps({"url": address, "session": "x"}))
            with self.assertRaises(ObservationError):
                reader.observe()
        with self.assertRaises(ObservationError):
            reader.changes({"session": "x", "seq": True})


if __name__ == "__main__":
    unittest.main()
