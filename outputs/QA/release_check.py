import struct
import sys
import zipfile


def alignments(blob: bytes) -> list[int]:
    if blob[:4] != b"\x7fELF" or blob[5] != 1:
        raise ValueError("Expected little-endian ELF")
    if blob[4] == 2:
        phoff = struct.unpack_from("<Q", blob, 32)[0]
        entsize, count = struct.unpack_from("<HH", blob, 54)
        alignoff = 48
    elif blob[4] == 1:
        phoff = struct.unpack_from("<I", blob, 28)[0]
        entsize, count = struct.unpack_from("<HH", blob, 42)
        alignoff = 28
    else:
        raise ValueError("Unknown ELF class")
    return [
        struct.unpack_from("<Q" if blob[4] == 2 else "<I", blob, phoff + i * entsize + alignoff)[0]
        for i in range(count)
        if struct.unpack_from("<I", blob, phoff + i * entsize)[0] == 1
    ]


with zipfile.ZipFile(sys.argv[1]) as archive:
    failed = False
    native_count = 0
    for entry in archive.infolist():
        if not entry.filename.endswith(".so"):
            continue
        native_count += 1
        values = alignments(archive.read(entry))
        if not values:
            raise ValueError(f"No PT_LOAD segments: {entry.filename}")
        is64 = "arm64-v8a" in entry.filename or "x86_64" in entry.filename
        okay = min(values) >= (16384 if is64 else 4096)
        print(f"{entry.filename}: PT_LOAD p_align={','.join(hex(v) for v in values)} {'OK' if okay else 'FAIL'}")
        failed = failed or not okay
    if not native_count:
        raise ValueError("Archive contains no native libraries; alignment check would be vacuous")
    if failed:
        raise SystemExit(1)
    print(f"Checked {native_count} native libraries; 64-bit PT_LOAD alignment is at least 16 KiB.")
