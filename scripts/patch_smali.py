"""Patches the decoded smali of ONAY! 3.6.5. Every smali change is listed here and in docs/PATCHES.md.

Patch 1: MainActivity.Q0(int), tab 0 (transport) returns NearbyStopFragment instead of RoutesFragment.
The original screen stays reachable through the "Обычная карта" button of the new screen.

Each replacement must match exactly once, otherwise the build stops: a changed app version must not
be patched silently.
"""
import glob
import os
import sys

OLD = (
    "    invoke-static {}, Lkz/onay/ui/routes2/frontscreen/RoutesFragment;->newInstance()"
    "Lkz/onay/ui/routes2/frontscreen/RoutesFragment;\n"
)
NEW = (
    "    const/4 v0, 0x0\n"
    "\n"
    "    invoke-static {v0}, Lkz/onay/nearby/ui/NearbyStopFragment;->newInstance(Landroid/os/Bundle;)"
    "Lkz/onay/nearby/ui/NearbyStopFragment;\n"
)


def main(decoded_dir: str) -> None:
    matches = glob.glob(os.path.join(decoded_dir, "smali*", "kz", "onay", "ui", "main", "MainActivity.smali"))
    if len(matches) != 1:
        sys.exit("expected exactly one MainActivity.smali, found %d" % len(matches))
    path = matches[0]
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if "NearbyStopFragment" in text:
        sys.exit("MainActivity.smali is already patched")
    if text.count(OLD) != 1:
        sys.exit("RoutesFragment.newInstance call: expected 1 occurrence, found %d" % text.count(OLD))
    marker = ".method public final Q0(I)Landroidx/fragment/app/Fragment;"
    start = text.index(marker)
    end = text.index(".end method", start)
    if OLD not in text[start:end]:
        sys.exit("the call is not inside Q0(I)")
    text = text.replace(OLD, NEW)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("smali patched: MainActivity.Q0 tab 0 -> NearbyStopFragment")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_smali.py <decoded dir>")
    main(sys.argv[1])
