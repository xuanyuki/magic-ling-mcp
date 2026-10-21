import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile


PROJECT = pathlib.Path(__file__).resolve().parents[1]


class PackageLicenseTest(unittest.TestCase):
    def test_project_notices_and_dependency_licenses_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            classes = root / "classes"
            agent = classes / "ml" / "bridge" / "Agent.class"
            agent.parent.mkdir(parents=True)
            agent.write_bytes(b"test-class")
            dependency = root / "dependency.jar"
            originals = {
                "META-INF/LICENSE": b"dependency license",
                "META-INF/NOTICE": b"dependency attribution",
                "META-INF/licenses/ASM": b"ASM license",
                "net/bytebuddy/Example.class": b"dependency-class",
            }
            with zipfile.ZipFile(dependency, "w") as archive:
                for name, content in originals.items():
                    archive.writestr(name, content)
                archive.writestr("META-INF/MANIFEST.MF", b"old-manifest")
                archive.writestr("META-INF/SIGNATURE.SF", b"signature")
                archive.writestr("module-info.class", b"old-module")

            output = root / "observer.jar"
            result = subprocess.run(
                [sys.executable, str(PROJECT / "tools" / "package.py"),
                 str(classes), str(dependency), str(output)],
                capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            with zipfile.ZipFile(output) as archive:
                for name, content in originals.items():
                    self.assertEqual(archive.read(name), content)
                for name in ("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"):
                    self.assertEqual(
                        archive.read(f"META-INF/game-text-bridge/{name}"),
                        (PROJECT / name).read_bytes(),
                    )
                self.assertEqual(archive.read("ml/bridge/Agent.class"), b"test-class")
                self.assertIn(b"Premain-Class: ml.bridge.Agent",
                              archive.read("META-INF/MANIFEST.MF"))
                self.assertNotIn("META-INF/SIGNATURE.SF", archive.namelist())
                self.assertNotIn("module-info.class", archive.namelist())
                self.assertEqual(len(archive.namelist()), len(set(archive.namelist())))


if __name__ == "__main__":
    unittest.main()
