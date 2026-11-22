"""Replaces the ONAY! launcher icon in a decoded APK with the ONAY Free icon (assets/icon/res).

Usage: patch_app_icon.py <decoded apktool dir>

ONAY! 3.6.5 declares <application android:icon="@mipmap/ic_launcher_autumn"
android:roundIcon="@mipmap/ic_launcher_autumn_round"> and has no activity-alias with other icons. The adaptive
icon (mipmap-anydpi-v26) is background + foreground (+ monochrome) and the legacy webp files serve Android < 8.
The script stops the build when the manifest points at other resources, when an expected original file is
missing or when the XML has a different shape, so an ONAY! update cannot silently keep the old icon.
Standard library only (it runs under `python3 -I` in build_apk.sh).
"""
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_SOURCE = os.path.join(ROOT, "assets", "icon", "res")

ICON = "ic_launcher_autumn"
DENSITIES = ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
RASTER_NAMES = (ICON, ICON + "_round", ICON + "_foreground")   # exist in the original, replaced in place
NEW_RASTER_NAMES = (ICON + "_monochrome",)                      # added by this patch
BACKGROUND_COLOR = "#FF141A23"

BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector android:height="108.0dp" android:width="108.0dp" android:viewportWidth="108.0" android:viewportHeight="108.0"
  xmlns:android="http://schemas.android.com/apk/res/android">
    <path android:fillColor="%s" android:pathData="M0,0h108v108h-108z" />
</vector>
""" % BACKGROUND_COLOR

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon
  xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/%(icon)s_background" />
    <foreground android:drawable="@mipmap/%(icon)s_foreground" />
    <monochrome android:drawable="@mipmap/%(icon)s_monochrome" />
</adaptive-icon>
""" % {"icon": ICON}

ORIGINAL_ADAPTIVE = re.compile(
    r'(?:<\?xml[^>]*\?>\s*)?<adaptive-icon\s+xmlns:android="[^"]+">\s*'
    r'<background android:drawable="@drawable/%(i)s_background"\s*/>\s*'
    r'<foreground android:drawable="@mipmap/%(i)s_foreground"\s*/>\s*'
    r'<monochrome android:drawable="@mipmap/%(i)s_foreground"\s*/>\s*</adaptive-icon>' % {"i": ICON})


def fail(message: str) -> None:
    sys.exit("patch_app_icon: " + message)


def read(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def write(path: str, text: str) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def check_manifest(decoded: str) -> None:
    manifest = read(os.path.join(decoded, "AndroidManifest.xml"))
    application = re.search(r"<application\b[^>]*>", manifest)
    if not application:
        fail("no <application> element in AndroidManifest.xml")
    tag = application.group(0)
    for attribute, expected in (("icon", "@mipmap/" + ICON), ("roundIcon", "@mipmap/" + ICON + "_round")):
        found = re.findall(r'android:%s="([^"]*)"' % attribute, tag)
        if found != [expected]:
            fail("android:%s is %s, expected %s (ONAY! changed its launcher icon?)" % (attribute, found, expected))
    if "<activity-alias" in manifest:
        fail("the manifest has activity-alias elements: their icons would stay unpatched")


def patch(decoded: str) -> None:
    if not os.path.isdir(os.path.join(decoded, "res")):
        fail("%s has no res directory" % decoded)
    check_manifest(decoded)

    plan = []   # (source, target); everything is verified before the first file is written
    for density in DENSITIES:
        for name in RASTER_NAMES + NEW_RASTER_NAMES:
            relative = os.path.join("mipmap-" + density, name + ".webp")
            source = os.path.join(RES_SOURCE, relative)
            target = os.path.join(decoded, "res", relative)
            if not os.path.isfile(source):
                fail("generated resource missing: %s (run scripts/generate_icon_resources.py)" % source)
            if name in RASTER_NAMES and not os.path.isfile(target):
                fail("expected original resource not found: res/%s" % relative)
            plan.append((source, target))

    background = os.path.join(decoded, "res", "drawable", ICON + "_background.xml")
    if not os.path.isfile(background):
        fail("expected original resource not found: res/drawable/%s_background.xml" % ICON)
    adaptive = [os.path.join(decoded, "res", "mipmap-anydpi-v26", ICON + suffix + ".xml") for suffix in ("", "_round")]
    for path in adaptive:
        if not os.path.isfile(path):
            fail("expected adaptive icon not found: " + os.path.relpath(path, decoded))
        text = read(path)
        if text != ADAPTIVE_XML and not ORIGINAL_ADAPTIVE.fullmatch(text.strip()):
            fail("unexpected adaptive icon format in %s" % os.path.relpath(path, decoded))

    for source, target in plan:
        shutil.copyfile(source, target)
    write(background, BACKGROUND_XML)
    for path in adaptive:
        write(path, ADAPTIVE_XML)
    print("app icon patched: %d raster files, background and 2 adaptive icons" % len(plan))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_app_icon.py <decoded apktool dir>")
    patch(sys.argv[1])
