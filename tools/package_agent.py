import argparse
import pathlib
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--classes", required=True)
    parser.add_argument("--dependency", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    classes = pathlib.Path(args.classes)
    dependency = pathlib.Path(args.dependency)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)

    manifest = (
        b"Manifest-Version: 1.0\r\n"
        b"Premain-Class: com.magicling.myriad.agent.MyriadAgent\r\n"
        b"Agent-Class: com.magicling.myriad.agent.MyriadAgent\r\n"
        b"Can-Redefine-Classes: false\r\n"
        b"Can-Retransform-Classes: true\r\n\r\n"
    )

    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        target.writestr("META-INF/MANIFEST.MF", manifest)
        for path in classes.rglob("*"):
            if path.is_file():
                target.write(path, path.relative_to(classes).as_posix())

        with zipfile.ZipFile(dependency) as source:
            for entry in source.infolist():
                name = entry.filename
                upper_name = name.upper()
                if entry.is_dir() or name in ("META-INF/MANIFEST.MF", "META-INF/INDEX.LIST"):
                    continue
                if upper_name.endswith((".SF", ".RSA", ".DSA", ".EC")):
                    continue
                if name == "module-info.class" or name.endswith("/module-info.class"):
                    continue
                if name in target.namelist():
                    continue
                target.writestr(entry, source.read(name))

    print(f"Packaged {output.resolve()} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
