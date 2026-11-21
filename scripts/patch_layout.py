"""Hides the containers of promotional banners in ONAY! 3.6.5 (layout patches).

The ad requests themselves are stopped by patch_ads.py; these containers are hidden so that no empty
space is left behind. Each start tag must be found exactly once per layout file, otherwise the build stops.

  fragment_profile_main_cards.xml  compose_view_banner  promo carousel on the cards screen
  bottom_sheet_branded_stops.xml   yandexBannerView     Yandex banner in the branded stops sheet
  bottom_sheet_poi.xml             composeViewBanner    partner banner in the partner point sheet
"""
import glob
import os
import re
import sys

TARGETS = (
    ("fragment_profile_main_cards.xml", "compose_view_banner"),
    ("bottom_sheet_branded_stops.xml", "yandexBannerView"),
    ("bottom_sheet_poi.xml", "composeViewBanner"),
)


def hide(path: str, view_id: str) -> None:
    with open(path, encoding="utf-8") as f:
        text = f.read()
    pattern = re.compile(r'<[A-Za-z.]+\s[^>]*android:id="@id/%s"[^>]*>' % view_id)
    found = pattern.findall(text)
    if len(found) != 1:
        sys.exit("%s: expected 1 start tag of %s, found %d" % (path, view_id, len(found)))
    tag = found[0]
    if 'android:visibility="' in tag:
        new_tag = re.sub(r'android:visibility="[^"]*"', 'android:visibility="gone"', tag)
    else:
        new_tag = tag.replace(' android:id="@id/%s"' % view_id,
                              ' android:id="@id/%s" android:visibility="gone"' % view_id, 1)
    if new_tag == tag:
        sys.exit("%s: could not change %s" % (path, view_id))
    with open(path, "w", encoding="utf-8") as f:
        f.write(text.replace(tag, new_tag, 1))


def main(decoded_dir: str) -> None:
    for name, view_id in TARGETS:
        files = [p for p in sorted(glob.glob(os.path.join(decoded_dir, "res", "layout*", name)))
                 if 'android:id="@id/%s"' % view_id in open(p, encoding="utf-8").read()]
        if not files:
            sys.exit("no %s with %s found" % (name, view_id))
        for path in files:
            hide(path, view_id)
        print("layout patched: %s hidden in %d file(s) (%s)" % (view_id, len(files), name))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_layout.py <decoded dir>")
    main(sys.argv[1])
