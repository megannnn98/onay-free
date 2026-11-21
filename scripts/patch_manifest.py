"""Adds the debug activity to a decoded AndroidManifest.xml (debug builds only, see build_apk.sh).

Every manifest change is listed here and in docs/PATCHES.md.
"""
import sys

ACTIVITY = (
    '        <activity android:exported="true" '
    'android:name="kz.onay.nearby.ui.NearbyStopDebugActivity" '
    'android:theme="@android:style/Theme.Material.Light.NoActionBar"/>\n'
)


def main(path: str) -> None:
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if "kz.onay.nearby.ui.NearbyStopDebugActivity" in text:
        sys.exit("manifest already patched: " + path)
    if text.count("</application>") != 1:
        sys.exit("expected exactly one </application> in " + path)
    text = text.replace("</application>", ACTIVITY + "    </application>")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("manifest patched: +1 activity")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_manifest.py <AndroidManifest.xml>")
    main(sys.argv[1])
