#!/usr/bin/env python3
"""Ensure apktool's manifest-only repack has not silently dropped app payloads."""
import sys
import zipfile


def verify(before: str, after: str) -> None:
    with zipfile.ZipFile(before) as source, zipfile.ZipFile(after) as result:
        old = {n: source.read(n) for n in source.namelist()
               if not n.endswith("/") and not n.startswith("META-INF/")}
        new = set(result.namelist())
        missing = set(old) - new - {"AndroidManifest.xml"}
        if missing:
            raise ValueError("repack dropped entries: " + ", ".join(sorted(missing)[:15]))
        important = [n for n in old if n.endswith(".dex") or n.startswith("lib/")
                     or n.startswith("assets/") or n.startswith("res/")]
        for name in important:
            if source.read(name) != result.read(name):
                raise ValueError("repack unexpectedly changed payload: " + name)
        if not any(n.endswith(".dex") for n in important):
            raise ValueError("no DEX files in repacked APK")
        if not any(n.startswith("lib/arm64-v8a/") and n.endswith(".so") for n in important):
            raise ValueError("no native ARM64 libraries in repacked APK")
        print(f"clone payload preserved: {len(important)} checked entries")


if __name__ == "__main__":
    try:
        verify(sys.argv[1], sys.argv[2])
    except (ValueError, OSError, zipfile.BadZipFile, IndexError) as exc:
        sys.exit(f"cloned APK payload check failed: {exc}")
