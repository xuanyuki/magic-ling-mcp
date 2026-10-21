import pathlib
import sys
import zipfile

classes, dependency, output = map(pathlib.Path, sys.argv[1:])
project = pathlib.Path(__file__).resolve().parents[1]
notices = {
    name: (project / name).read_bytes()
    for name in ("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md")
}
manifest = (
    "Manifest-Version: 1.0\r\n"
    "Premain-Class: ml.bridge.Agent\r\n"
    "Agent-Class: ml.bridge.Agent\r\n"
    "Can-Retransform-Classes: true\r\n\r\n"
)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as target:
    target.writestr("META-INF/MANIFEST.MF", manifest)
    for path in classes.rglob("*.class"):
        target.write(path, path.relative_to(classes).as_posix())
    with zipfile.ZipFile(dependency) as source:
        for name in source.namelist():
            if name.endswith("/") or name in ("META-INF/MANIFEST.MF", "META-INF/INDEX.LIST"):
                continue
            if name.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) or name.endswith("module-info.class"):
                continue
            target.writestr(name, source.read(name))
    for name, content in notices.items():
        target.writestr(f"META-INF/game-text-bridge/{name}", content)
print(output)
