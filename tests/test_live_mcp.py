"""Exercise stdio MCP against the disposable, hidden real-game JVM owned by test_attach."""
import asyncio
import json
import pathlib
import sys

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


async def main():
    root = pathlib.Path(__file__).resolve().parents[1]
    observer = pathlib.Path(sys.argv[1])
    server = StdioServerParameters(command=sys.executable, args=[
        str(root / "mcp_server.py"), "--output", str(observer)])

    async with stdio_client(server) as (reader, writer):
        async with ClientSession(reader, writer) as session:
            initialized = await session.initialize()
            assert initialized.serverInfo.version == "0.9.6.5"
            tools = await session.list_tools()
            assert {tool.name for tool in tools.tools} == {
                "observe_state", "read_changes", "health", "project_cell"}

            async def call(name, arguments=None):
                reply = await session.call_tool(name, arguments or {})
                assert not reply.isError, reply
                return json.loads(reply.content[0].text)

            health = await call("health")
            assert health["connected"] and not health["stale"]
            assert health["compatibility"]["compatible"]
            first = await call("observe_state", {"format": "json"})
            assert first["state"]["hero"]["x"] == 2
            assert first["state"]["inventory"][0]["name"] == "靛紫药剂"
            cursor = first["cursor"]
            point = await call("project_cell", {"x": 2, "y": 2, **cursor})
            assert point["screen"] == [40, 40], point
            idle = await call("read_changes", {"cursor": cursor, "format": "json"})
            assert idle["events"] == []
            capture = await call("observe_state", {"format": "json", "include_screenshot": True})
            image = capture["screenshot"]
            assert image["session"] == capture["session"] and image["seq"] == capture["seq"]
            path = pathlib.Path(image["path"])
            assert path.is_absolute() and path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
            assert image["width"] == 320 and image["height"] == 240
            changed = await call("read_changes", {"cursor": cursor, "format": "json"})
            assert changed["cursor"] == capture["cursor"] and changed["events"]
            after_capture = await call("health")
            assert after_capture["lastError"] == "", after_capture
            text = await session.call_tool("observe_state", {})
            assert not text.isError and "完整状态" in text.content[0].text
    print("PASS real hidden game -> Agent HTTP -> official stdio MCP, four tools and bound PNG")


if __name__ == "__main__":
    asyncio.run(main())
