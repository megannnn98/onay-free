#!/usr/bin/env python3
"""Install only a rebuilt binary AndroidManifest.xml into the original APK.

apktool recompiles resources to generate the compiled manifest. We do NOT ship
its recompiled resources/dex: using the original merged APK preserves them.
"""
from pathlib import Path
import sys
import zipfile


def replace_manifest(original: Path, rebuilt: Path, output: Path) -> None:
    if original == output or rebuilt == output:
        raise ValueError("output must be a new file")
    with zipfile.ZipFile(rebuilt) as other:
        new_manifest = other.read("AndroidManifest.xml")
    if not new_manifest or new_manifest.startswith(b"<?xml"):
        raise ValueError("expected compiled Android manifest, got empty/text XML")
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(output, "w") as result:
        names = source.namelist()
        if names.count("AndroidManifest.xml") != 1:
            raise ValueError("source must contain exactly one AndroidManifest.xml")
        for info in source.infolist():
            # Remove old APK v1 signature metadata. A new signing step follows.
            name = info.filename
            upper = name.upper()
            if upper.startswith("META-INF/") and (
                    upper.endswith(".RSA") or upper.endswith(".DSA")
                    or upper.endswith(".EC") or upper.endswith(".SF")
                    or upper == "META-INF/MANIFEST.MF"):
                continue
            data = new_manifest if name == "AndroidManifest.xml" else source.read(info)
            result.writestr(info, data)
    print("binary AndroidManifest.xml replaced; all other source entries retained")


if __name__ == "__main__":
    try:
        replace_manifest(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
    except (ValueError, OSError, zipfile.BadZipFile, KeyError, IndexError) as exc:
        sys.exit(f"manifest-only APK repack failed: {exc}")
