"""Removes the promotional banner from the cards screen of ONAY! 3.6.5.

The cards screen (ProfileMainCardsFragment) holds a ComposeView "compose_view_banner" with the promo
carousel. Its content is set once in code and its visibility is never changed, so setting
android:visibility="gone" in the layout is enough; the views below move up. The start tag must be found
exactly once per file, otherwise the build stops.
"""
import glob
import os
import re
import sys

VIEW_ID = "compose_view_banner"


def main(decoded_dir: str) -> None:
    files = sorted(glob.glob(os.path.join(decoded_dir, "res", "layout*", "fragment_profile_main_cards.xml")))
    patched = 0
    for path in files:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        if 'android:id="@id/%s"' % VIEW_ID not in text:
            continue
        pattern = re.compile(r'<[A-Za-z.]+\s[^>]*android:id="@id/%s"[^>]*>' % VIEW_ID)
        found = pattern.findall(text)
        if len(found) != 1:
            sys.exit("%s: expected 1 start tag of %s, found %d" % (path, VIEW_ID, len(found)))
        tag = found[0]
        if 'android:visibility="' in tag:
            new_tag = re.sub(r'android:visibility="[^"]*"', 'android:visibility="gone"', tag)
        else:
            new_tag = tag.replace(' android:id="@id/%s"' % VIEW_ID,
                                  ' android:id="@id/%s" android:visibility="gone"' % VIEW_ID, 1)
        if new_tag == tag:
            sys.exit("%s: could not change %s" % (path, VIEW_ID))
        with open(path, "w", encoding="utf-8") as f:
            f.write(text.replace(tag, new_tag, 1))
        patched += 1
    if patched == 0:
        sys.exit("no fragment_profile_main_cards.xml with %s found" % VIEW_ID)
    print("layout patched: banner hidden in %d layout file(s)" % patched)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_layout.py <decoded dir>")
    main(sys.argv[1])
