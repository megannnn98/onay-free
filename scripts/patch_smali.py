"""Patches the decoded smali of ONAY! 3.6.5. Every smali change is listed here and in docs/PATCHES.md.

MainActivity.Q0(int), tab 2 (Life, the coupons and partner section) returns NearbyStopFragment.
The transport tab (0) keeps the original RoutesFragment, so the original transport map stays one tab away.

Each replacement must match exactly once, otherwise the build stops: a changed app version must not
be patched silently.
"""
import glob
import os
import sys

PATCHES = (
    (
        "tab 2 (Life) -> NearbyStopFragment",
        "    invoke-static {}, Lkz/onay/ui/main/life/LifeFragment;->newInstance()"
        "Lkz/onay/ui/main/life/LifeFragment;\n",
        "    const/4 v0, 0x0\n"
        "\n"
        "    invoke-static {v0}, Lkz/onay/nearby/ui/NearbyStopFragment;->newInstance(Landroid/os/Bundle;)"
        "Lkz/onay/nearby/ui/NearbyStopFragment;\n",
    ),
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
    marker = ".method public final Q0(I)Landroidx/fragment/app/Fragment;"
    start = text.index(marker)
    end = text.index(".end method", start)
    body = text[start:end]
    for name, old, new in PATCHES:
        if text.count(old) != 1:
            sys.exit("%s: expected 1 occurrence, found %d" % (name, text.count(old)))
        if old not in body:
            sys.exit("%s: the call is not inside Q0(I)" % name)
        body = body.replace(old, new)
        print("smali patched: MainActivity.Q0 " + name)
    text = text[:start] + body + text[end:]
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_smali.py <decoded dir>")
    main(sys.argv[1])
