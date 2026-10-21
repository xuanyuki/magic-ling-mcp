"""Version-gated CI builds and clean runtime bundles. Requires Python 3.11+."""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tomllib
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NUMERIC = re.compile(r"[0-9]+(?:\.[0-9]+){2,}")
PREFIX = re.compile(r"([0-9]+(?:\.[0-9]+){2,})(?=$|[^0-9.])")


def version_from(content):
    return tomllib.loads(content)["project"]["version"]


def should_build(previous, current):
    # A prerelease at the new revision never produces a stable build.
    if not isinstance(current, str) or not NUMERIC.fullmatch(current):
        return False
    if previous is None:
        return True
    match = PREFIX.match(previous)
    if not match:
        return True
    old = tuple(map(int, match[1].split(".")))
    new = tuple(map(int, current.split(".")))
    return old != new


def git(root, *arguments):
    return subprocess.check_output(
        ["git", "-C", str(root), *arguments], encoding="utf-8", errors="strict"
    ).strip()


def previous_version(root, before):
    if not re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", before):
        raise ValueError("PUSH_BEFORE must contain a Git commit hash")
    if set(before) == {"0"}:
        parents = git(root, "rev-list", "--parents", "-n", "1", "HEAD").split()
        if len(parents) == 1:
            return None
        before = parents[1]
    # A missing/unreachable old commit must fail, never masquerade as a first push.
    git(root, "cat-file", "-e", before + "^{commit}")
    commits = git(root, "log", "--format=%H", before, "--", "pyproject.toml").splitlines()
    for commit in commits:
        content = git(root, "show", f"{commit}:pyproject.toml")
        version = version_from(content)
        if NUMERIC.fullmatch(version):
            return version
    return None


def detect(root, before, output=None):
    current = version_from((root / "pyproject.toml").read_text(encoding="utf-8"))
    previous = previous_version(root, before)
    build = should_build(previous, current)
    print(f"Version: {previous!r} -> {current!r}; build={build}")
    if output:
        with Path(output).open("a", encoding="utf-8") as stream:
            stream.write(f"build={str(build).lower()}\n")
            if build:
                stream.write(f"version={current}\n")
    return build


def validate_versions(root):
    version = version_from((root / "pyproject.toml").read_text(encoding="utf-8"))
    if not NUMERIC.fullmatch(version):
        raise ValueError("A runtime bundle requires a numeric stable version")
    module = ast.parse((root / "mcp_server.py").read_text(encoding="utf-8"))
    server = next(ast.literal_eval(node.value) for node in module.body
                  if isinstance(node, ast.Assign)
                  and any(isinstance(target, ast.Name) and target.id == "SERVER_VERSION"
                          for target in node.targets))
    lock = tomllib.loads((root / "uv.lock").read_text(encoding="utf-8"))
    locked = next(package["version"] for package in lock["package"]
                  if package["name"] == "magic-ling-text-mcp")
    if not version == server == locked:
        raise ValueError("Synchronize pyproject.toml, mcp_server.py SERVER_VERSION and uv.lock")
    return version


def download(root):
    project_version = validate_versions(root)
    inputs = json.loads((root / "ci/build-inputs.json").read_text(encoding="utf-8"))
    game_version = inputs["game"]["version"]
    game_build = inputs["game"]["build"]
    if project_version != game_version:
        raise ValueError("Update the pinned game build inputs before building a new game-matched version")
    compatibility = (root / "src/ml/bridge/Compatibility.java").read_text(encoding="utf-8")
    version_match = re.search(r'VERSION\s*=\s*"([^"]+)"\s*,\s*BUILD\s*=\s*"([^"]+)"', compatibility)
    if not version_match or version_match.groups() != (game_version, game_build):
        raise ValueError("Update Compatibility.java to match the pinned game version and build")
    dependencies = root / "deps"
    dependencies.mkdir(exist_ok=True)
    for key, filename in (("game", "game.jar"), ("byte_buddy", "byte-buddy-1.17.7.jar")):
        entry = inputs[key]
        if not entry["url"].startswith("https://") or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
            raise ValueError(f"Invalid pinned input: {key}")
        target = dependencies / filename
        print(f"Downloading {key}", flush=True)
        request = urllib.request.Request(entry["url"], headers={"User-Agent": "LingLens-CI"})
        digest = hashlib.sha256()
        with urllib.request.urlopen(request, timeout=120) as response, target.open("wb") as stream:
            for chunk in iter(lambda: response.read(1024 * 1024), b""):
                digest.update(chunk)
                stream.write(chunk)
        if digest.hexdigest() != entry["sha256"]:
            target.unlink()
            raise ValueError(f"SHA256 mismatch: {key}")
    with zipfile.ZipFile(dependencies / "game.jar") as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    for name, expected in (("Specification-Version", inputs["game"]["version"]),
                           ("Implementation-Version", inputs["game"]["build"])):
        if f"{name}: {expected}" not in manifest.splitlines():
            raise ValueError(f"Unexpected game manifest: {name}")


def package(root):
    version = validate_versions(root)
    dist = root / "dist"
    agent = dist / "game-text-bridge.jar"
    attach = root / "out/Attach.class"
    if not agent.is_file() or not attach.is_file():
        raise ValueError("Run build.ps1 before packaging")
    with zipfile.ZipFile(agent) as archive:
        for notice in ("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md"):
            if archive.read(f"META-INF/game-text-bridge/{notice}") != (root / notice).read_bytes():
                raise ValueError(f"Agent notice is outdated: {notice}")
    commit = git(root, "rev-parse", "HEAD")
    prefix = f"LingLens-{version}"
    source = dist / f"{prefix}-source.zip"
    subprocess.run(["git", "-C", str(root), "archive", "--format=zip",
                    f"--prefix={prefix}-source/", f"--output={source}", "HEAD"], check=True)
    runtime = dist / f"{prefix}-windows.zip"
    files = ["README.md", "LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "pyproject.toml",
             "uv.lock", "mcp_server.py", "bridge_client.py", "ai_client.py", "start-mcp.ps1",
             "attach.ps1", "examples/mcp-config.json", "docs/COVERAGE.md", "docs/READ-AUDIT.md",
             "dist/game-text-bridge.jar", "out/Attach.class"]
    with zipfile.ZipFile(runtime, "w", zipfile.ZIP_DEFLATED) as archive:
        for filename in files:
            archive.write(root / filename, f"{prefix}/{filename}")
        archive.writestr(f"{prefix}/SOURCE.txt", f"Commit: {commit}\nSource archive: {source.name}\n")
    checksums = "".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                        for path in (runtime, source))
    (dist / "SHA256SUMS.txt").write_text(checksums, encoding="ascii")
    print(f"Packaged {runtime.name} and {source.name} from {commit}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("detect", "download", "package"))
    args = parser.parse_args()
    if args.command == "detect":
        detect(ROOT, os.environ["PUSH_BEFORE"], os.environ.get("GITHUB_OUTPUT"))
    elif args.command == "download":
        download(ROOT)
    else:
        package(ROOT)
