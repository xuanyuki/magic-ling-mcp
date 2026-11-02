"""Read test artifacts from a FAT12 image without mounting or changing it."""
from __future__ import annotations

import argparse
import io
from pathlib import Path
import struct
import zipfile


class Fat12:
    def __init__(self, image: bytes) -> None:
        self.image = image
        self.sector_size = struct.unpack_from("<H", image, 11)[0]
        self.cluster_size = self.sector_size * image[13]
        reserved = struct.unpack_from("<H", image, 14)[0]
        fat_count = image[16]
        root_entries = struct.unpack_from("<H", image, 17)[0]
        fat_sectors = struct.unpack_from("<H", image, 22)[0]
        if self.sector_size != 512 or self.cluster_size == 0:
            raise ValueError("Unexpected FAT12 geometry")
        fat_start = reserved * self.sector_size
        self.fat = image[fat_start:fat_start + fat_sectors * self.sector_size]
        root_start = (reserved + fat_count * fat_sectors) * self.sector_size
        self.root = image[root_start:root_start + root_entries * 32]
        self.data_start = root_start + (
            (root_entries * 32 + self.sector_size - 1) // self.sector_size
        ) * self.sector_size
        self.entries: dict[str, tuple[int, int]] = {}
        for offset in range(0, len(self.root), 32):
            entry = self.root[offset:offset + 32]
            if not entry[0]:
                break
            if entry[0] == 0xE5 or entry[11] & 0x18 or entry[11] == 0x0F:
                continue
            stem = entry[:8].decode("ascii").rstrip()
            extension = entry[8:11].decode("ascii").rstrip()
            name = stem + ("." + extension if extension else "")
            self.entries[name] = (
                struct.unpack_from("<H", entry, 26)[0],
                struct.unpack_from("<I", entry, 28)[0],
            )

    def read(self, name: str) -> bytes:
        cluster, length = self.entries[name]
        remaining = length
        chunks = []
        seen = set()
        while remaining:
            if cluster < 2 or cluster >= 0xFF0 or cluster in seen:
                raise ValueError("Invalid or incomplete FAT12 cluster chain")
            seen.add(cluster)
            start = self.data_start + (cluster - 2) * self.cluster_size
            count = min(remaining, self.cluster_size)
            chunk = self.image[start:start + count]
            if len(chunk) != count:
                raise ValueError("Cluster falls outside image")
            chunks.append(chunk)
            remaining -= count
            offset = cluster + cluster // 2
            pair = struct.unpack_from("<H", self.fat, offset)[0]
            cluster = (pair >> 4 if cluster & 1 else pair) & 0xFFF
        return b"".join(chunks)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    disk = Fat12(args.image.read_bytes())
    if not disk.entries:
        print("No test artifacts are present.")
    for name, (_, size) in disk.entries.items():
        print(f"{name}: {size} bytes")
        if name.endswith((".TXT", ".LOG")):
            print(disk.read(name).decode("utf-8-sig", errors="replace"))
    if args.output and "RESULT.ZIP" in disk.entries:
        archive_bytes = disk.read("RESULT.ZIP")
        output = args.output.resolve()
        output.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
            for entry in archive.infolist():
                target = (output / entry.filename).resolve()
                if not target.is_relative_to(output):
                    raise ValueError("ZIP path escapes output")
                if entry.file_size > 20_000_000:
                    raise ValueError("Unexpectedly large ZIP member")
            archive.extractall(output)
        (output / "RESULT.ZIP").write_bytes(archive_bytes)
        print(f"Extracted guest artifacts to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
