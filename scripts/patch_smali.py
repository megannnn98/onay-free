"""Patches the decoded smali of ONAY! 3.6.5. Every smali change is listed here and in docs/PATCHES.md.

MainActivity.Q0(int), tab 2 (Life, the coupons and partner section) returns NearbyStopFragment.
The transport tab (0) keeps the original RoutesFragment, so the original transport map stays one tab away.

MainActivity.onCreate(Bundle): on a cold start (savedInstanceState == null) the original selects tab 1 (Cards);
it now selects tab 2 (Life, the "Рядом" screen). Rotation and process restore keep the saved tab.

Each replacement must match exactly once, otherwise the build stops: a changed app version must not
be patched silently.
"""
import glob
import os
import re
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


START_TAB_RE = re.compile(
    r"(\n    if-nez p1, :(?P<label>cond_\w+)\n(?:\s*\.line \d+\n|\s*\n)*)"
    r"    invoke-virtual \{p0, (?P<reg>v\d+)\}, Lkz/onay/ui/main/MainActivity;->x1\(I\)V\n"
)


def patch_start_tab(text: str) -> str:
    """Cold start opens tab 2 (Life) instead of tab 1 (Cards). The register that held 1 is restored afterwards."""
    marker = ".method public onCreate(Landroid/os/Bundle;)V"
    if marker not in text:
        sys.exit("start tab: MainActivity.onCreate(Bundle) not found")
    start = text.index(marker)
    end = text.index(".end method", start)
    body = text[start:end]
    found = list(START_TAB_RE.finditer(body))
    if len(found) != 1:
        sys.exit("start tab: expected 1 'if-nez p1 -> x1(I)' in onCreate, found %d" % len(found))
    m = found[0]
    reg = m.group("reg")
    new = (m.group(1) + "    const/4 %s, 0x2\n\n" % reg
           + "    invoke-virtual {p0, %s}, Lkz/onay/ui/main/MainActivity;->x1(I)V\n\n" % reg
           + "    const/4 %s, 0x1\n" % reg)
    return text[:start] + body[:m.start()] + new + body[m.end():] + text[end:]


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
    text = patch_start_tab(text)
    print("smali patched: MainActivity.onCreate cold start -> tab 2 (Life)")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_smali.py <decoded dir>")
    main(sys.argv[1])
