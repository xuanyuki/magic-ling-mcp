"""Run the real host classes in an isolated, hidden JVM and dynamically attach the built agent."""
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time

java, root, game = sys.argv[1], pathlib.Path(sys.argv[2]), sys.argv[3]
fixture = pathlib.Path(tempfile.mkdtemp(prefix="attach-test-", dir=root / "out"))
observer = fixture / "observer"
observer.mkdir()
(observer / "bridge.properties").write_text("port=0\nintervalMs=250\n", encoding="utf-8")
agent = fixture / "agent.jar"
shutil.copyfile(root / "dist/game-text-bridge.jar", agent)
flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
command = [java, "-Xmx512m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8", "-cp", f'{root / "out/test-classes"};{game}',
           "ml.bridge.BoundaryTest", str(fixture), str(observer), "wait-attach"]
with (fixture / "stdout.log").open("w", encoding="utf-8") as out, (fixture / "stderr.log").open("w", encoding="utf-8") as err:
    process = subprocess.Popen(command, stdout=out, stderr=err, creationflags=flags)
    try:
        end = time.monotonic() + 15
        while not (fixture / "ready").exists():
            if process.poll() is not None or time.monotonic() > end:
                raise RuntimeError(f"Fixture did not become ready: {fixture}")
            time.sleep(0.05)
        subprocess.run([java, "-Xmx128m", "-XX:+UseSerialGC", "--add-modules", "jdk.attach", "-cp", str(root / "out"),
                        "Attach", str(process.pid), str(agent), str(observer)],
                       check=True, timeout=15, creationflags=flags)
        (fixture / "proceed").touch()
        end = time.monotonic() + 30
        while not (fixture / "mcp-ready").exists():
            if process.poll() is not None or time.monotonic() > end:
                raise RuntimeError(f"Fixture did not reach MCP observation point: {fixture}")
            time.sleep(0.05)
        mcp_test = subprocess.run(["uv", "run", "--project", str(root), "--frozen", "python",
                                   str(root / "tests/test_live_mcp.py"), str(observer)],
                                  check=True, timeout=25, creationflags=flags,
                                  capture_output=True, text=True, encoding="utf-8")
        print(mcp_test.stdout.strip())
        (fixture / "mcp-done").touch()
        result = process.wait(timeout=20)
        if result:
            raise RuntimeError(f"Fixture failed with code {result}: {fixture}")
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=5)
print((fixture / "stdout.log").read_text(encoding="utf-8", errors="replace"))
print("PASS dynamic attach and retransformation, using packaged agent classes only")
