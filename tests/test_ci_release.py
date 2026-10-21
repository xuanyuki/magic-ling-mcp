import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import ci_release


class VersionGateTest(unittest.TestCase):
    def test_only_new_numeric_stable_versions_build(self):
        cases = (
            ("0.9.1", "0.9.2", True),
            ("0.9.1", "0.9.1", False),
            ("0.9.1", "0.9.1-beta1", False),
            ("0.9.1", "0.9.1-bate1", False),
            (None, "0.9.2", True),
            ("0.9.2-beta1", "0.9.2-beta2", False),
            ("0.9.10", "0.9.9", True),
            ("0.9.1.2", "0.9.1.3", True),
        )
        for previous, current, expected in cases:
            with self.subTest(previous=previous, current=current):
                self.assertEqual(ci_release.should_build(previous, current), expected)

    def test_first_commit_builds_and_prerelease_is_skipped(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            git = lambda *args: subprocess.run(
                ["git", "-C", str(root), *args], check=True, capture_output=True, text=True
            )
            git("init", "-q")
            git("config", "user.email", "test@example.invalid")
            git("config", "user.name", "Release test")
            project = root / "pyproject.toml"
            project.write_text('[project]\nversion = "0.9.1"\n', encoding="utf-8")
            git("add", "pyproject.toml")
            git("commit", "-qm", "initial")
            before = git("rev-parse", "HEAD").stdout.strip()
            project.write_text('[project]\nversion = "0.9.1-beta1"\n', encoding="utf-8")
            git("commit", "-qam", "prerelease")
            self.assertFalse(ci_release.detect(root, before))
            before = git("rev-parse", "HEAD").stdout.strip()
            project.write_text('[project]\nversion = "0.9.2"\n', encoding="utf-8")
            git("commit", "-qam", "stable")
            self.assertTrue(ci_release.detect(root, before))

    def test_runtime_and_source_archives_include_required_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for relative in ("tools", "dist", "out", "src"):
                (root / relative).mkdir(parents=True, exist_ok=True)
            for filename, content in (
                ("pyproject.toml", '[project]\nname="magic-ling-text-mcp"\nversion="0.9.6.5"\n'),
                ("mcp_server.py", 'SERVER_VERSION = "0.9.6.5"\n'),
                ("uv.lock", 'version = 1\n[[package]]\nname="magic-ling-text-mcp"\nversion="0.9.6.5"\n'),
            ):
                (root / filename).write_text(content, encoding="utf-8")
            for filename in ("README.md", "LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md",
                             "mcp_server.py", "bridge_client.py", "ai_client.py", "start-mcp.ps1",
                             "attach.ps1", "pyproject.toml", "uv.lock"):
                (root / filename).write_text(filename, encoding="utf-8")
            (root / "examples").mkdir()
            (root / "examples/mcp-config.json").write_text("{}", encoding="utf-8")
            (root / "docs").mkdir()
            for filename in ("COVERAGE.md", "READ-AUDIT.md"):
                (root / "docs" / filename).write_text(filename, encoding="utf-8")
            agent = root / "dist/game-text-bridge.jar"
            with zipfile.ZipFile(agent, "w") as archive:
                for filename in ("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"):
                    archive.writestr(f"META-INF/game-text-bridge/{filename}",
                                     (root / filename).read_bytes())
            (root / "out/Attach.class").write_bytes(b"attach")
            (root / "src/Placeholder.java").write_text("class Placeholder {}", encoding="utf-8")
            run_git = lambda *args: subprocess.run(
                ["git", "-C", str(root), *args], check=True, capture_output=True, text=True
            )
            run_git("init", "-q")
            run_git("config", "user.email", "test@example.invalid")
            run_git("config", "user.name", "Release test")
            (root / "pyproject.toml").write_text(
                '[project]\nname="magic-ling-text-mcp"\nversion="0.9.6.5"\n', encoding="utf-8"
            )
            (root / "mcp_server.py").write_text(
                'SERVER_VERSION = "0.9.6.5"\n', encoding="utf-8"
            )
            (root / "uv.lock").write_text(
                'version = 1\n[[package]]\nname="magic-ling-text-mcp"\nversion="0.9.6.5"\n',
                encoding="utf-8",
            )
            run_git("add", "-A")
            run_git("commit", "-qm", "release")
            ci_release.package(root)
            with zipfile.ZipFile(root / "dist/LingLens-0.9.6.5-windows.zip") as archive:
                self.assertIn("LingLens-0.9.6.5/mcp_server.py", archive.namelist())
                self.assertIn("LingLens-0.9.6.5/dist/game-text-bridge.jar", archive.namelist())
                self.assertIn("LingLens-0.9.6.5/out/Attach.class", archive.namelist())
            with zipfile.ZipFile(root / "dist/LingLens-0.9.6.5-source.zip") as archive:
                self.assertIn("LingLens-0.9.6.5-source/src/Placeholder.java", archive.namelist())
            checksums = (root / "dist/SHA256SUMS.txt").read_text(encoding="ascii")
            self.assertIn("LingLens-0.9.6.5-windows.zip", checksums)
            self.assertIn("LingLens-0.9.6.5-source.zip", checksums)


if __name__ == "__main__":
    unittest.main()
