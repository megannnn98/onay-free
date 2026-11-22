"""Replace just the ONAY Free extension DEX in a released single APK.

This retains the existing compiled resources, original application code, manifest,
native libraries and icon. It must NOT be used when those components also change.
Output is unsigned: zipalign and apksigner must run afterwards.
"""
import re
import sys
import zipfile
from pathlib import Path

NEARBY_CLASS = b"Lkz/onay/nearby/ui/NearbyStopFragment;"
NEW_CORRIDOR_CLASS = b"Lkz/onay/nearby/logic/RouteCorridors;"


def replace(source: Path, compiled_dex: Path, output: Path) -> str:
    if not source.is_file() or not compiled_dex.is_file():
        raise ValueError("source APK or replacement DEX is missing")
    fresh = compiled_dex.read_bytes()
    if not fresh.startswith(b"dex\\n"):
        raise ValueError("replacement file is not a DEX")
    if NEARBY_CLASS not in fresh or NEW_CORRIDOR_CLASS not in fresh:
        raise ValueError("compiled DEX does not contain the new Nearby UI and corridor logic")

    with zipfile.ZipFile(source, "r") as zin:
        dex_names = [n for n in zin.namelist() if re.fullmatch(r"classes(?:\\d+)?\\.dex", n)]
        if not dex_names:
            raise ValueError("source APK has no DEX files")
        extension = [n for n in dex_names if NEARBY_CLASS in zin.read(n)]
        if len(extension) != 1:
            raise ValueError(f"expected exactly one Nearby extension DEX, found {extension}")
        target = extension[0]
        if NEW_CORRIDOR_CLASS in zin.read(target):
            raise ValueError("source APK already has corridor logic; refusing to double-patch")
        with zipfile.ZipFile(output, "w") as zout:
            zout.comment = zin.comment
            for info in zin.infolist():
                data = fresh if info.filename == target else zin.read(info.filename)
                # Copy original metadata, entry attributes and compression methods.
                zout.writestr(info, data)
    if target not in zipfile.ZipFile(output).namelist():
        raise ValueError("output lost the replaced DEX entry")
    return target


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: python3 replace_nearby_dex.py <source.apk> <new.dex> <unsigned.apk>")
    selected = replace(*(Path(a) for a in sys.argv[1:]))
    print("replaced:", selected)
