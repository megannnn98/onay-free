"""Adds a dex file to an APK as the next classesN.dex. The dex is stored uncompressed like the others."""
import re
import sys
import zipfile


def main(apk: str, dex: str) -> None:
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
    nums = []
    for n in names:
        m = re.fullmatch(r"classes(\d*)\.dex", n)
        if m:
            nums.append(int(m.group(1) or "1"))
    if not nums:
        sys.exit("no classes*.dex in " + apk)
    nxt = max(nums) + 1
    name = "classes%d.dex" % nxt
    if name in names:
        sys.exit(name + " already present")
    with open(dex, "rb") as f:
        data = f.read()
    with zipfile.ZipFile(apk, "a") as z:
        info = zipfile.ZipInfo(name, date_time=(1981, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        z.writestr(info, data)
    print("added", name, len(data), "bytes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: add_dex.py <apk> <dex>")
    main(sys.argv[1], sys.argv[2])
