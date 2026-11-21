#!/usr/bin/env bash
# Builds the compile-only classpath of the app from base.apk:
#   1. enjarify converts dex to a jar (slow, ~6 min, single threaded);
#   2. class file version is raised 49 -> 52, because enjarify writes version 49 and javac refuses
#      default methods in interfaces of that version.
# The result is used ONLY by javac and d8 as a classpath; it is never packaged.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${APK:-$ROOT/work/apks/base.apk}"
OUT="$ROOT/work/classpath"
mkdir -p "$OUT"
[ -f "$APK" ] || { echo "missing $APK" >&2; exit 1; }
if [ ! -s "$OUT/app-classes.jar" ]; then
  enjarify -f -o "$OUT/app-classes.jar" "$APK"
fi
python3 -I - "$OUT/app-classes.jar" "$OUT/app-classes-v52.jar" <<'PY'
import sys, zipfile
src, dst = sys.argv[1:3]
n = 0
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_STORED) as zo:
    for info in zi.infolist():
        data = zi.read(info.filename)
        if info.filename.endswith(".class") and len(data) > 8 and data[:4] == b"\xca\xfe\xba\xbe":
            if data[6:8] < b"\x00\x34":
                data = data[:6] + b"\x00\x34" + data[8:]
                n += 1
        zo.writestr(info.filename, data)
print("patched class files:", n)
PY
ls -l "$OUT/app-classes-v52.jar"
