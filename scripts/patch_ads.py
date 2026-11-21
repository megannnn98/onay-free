"""Removes advertising from ONAY! 3.6.5. Every smali change is listed here and in docs/PATCHES.md.

1. FeatureCheckerImpl.isFeatureEnabled(String) answers false for the advertising feature flags
   (Firebase Remote Config), so screens that check them never create ad containers.
2. The Yandex Ads SDK entry points that start loading an ad become no-ops, so no ad is ever requested or
   shown, wherever the app calls them: NativeAdLoader.loadAd (native blocks, pop-up ad, payment status),
   BannerAdView.loadAd (banners) and SliderAdLoader.loadAd (the carousel on the cards screen).

Functional flags (taxi, metro, biometrics and so on) and the discounts section are not touched.
Each replacement must match exactly once, otherwise the build stops.
"""
import glob
import os
import sys

BLOCKED_FLAGS = (
    "isYandexAdsEnabled",
    "isRoutesNativeInterstitialAdsEnabled",
    "partnerTilesMainScreenEnabled",
    "partnerTilesMyTicketsEnabled",
)

NOOP_METHODS = (
    ("com/yandex/mobile/ads/nativeads/NativeAdLoader.smali", (
        ".method public final loadAd(Lcom/yandex/mobile/ads/common/AdRequest;"
        "Lcom/yandex/mobile/ads/nativeads/NativeAdLoadListener;)V",
        ".method public final loadAd(Lcom/yandex/mobile/ads/common/AdRequest;"
        "Lcom/yandex/mobile/ads/nativeads/NativeAdOptions;Lcom/yandex/mobile/ads/nativeads/NativeAdLoadListener;)V",
    )),
    ("com/yandex/mobile/ads/banner/BannerAdView.smali", (
        ".method public final loadAd(Lcom/yandex/mobile/ads/common/AdRequest;)V",
    )),
    ("com/yandex/mobile/ads/nativeads/SliderAdLoader.smali", (
        ".method public final loadAd(Lcom/yandex/mobile/ads/common/AdRequest;"
        "Lcom/yandex/mobile/ads/nativeads/SliderAdLoadListener;)V",
        ".method public final loadAd(Lcom/yandex/mobile/ads/common/AdRequest;"
        "Lcom/yandex/mobile/ads/nativeads/NativeAdOptions;Lcom/yandex/mobile/ads/nativeads/SliderAdLoadListener;)V",
    )),
)

FEATURE_METHOD = ".method public isFeatureEnabled(Ljava/lang/String;)Z"
FEATURE_ANCHOR = (
    "    iget-object v0, p0, Lkz/onay/firebase/feature_checker/FeatureCheckerImpl;->firebaseRemoteConfig:"
    "Lcom/google/firebase/remoteconfig/FirebaseRemoteConfig;\n"
)
FEATURE_TAIL = "    return p1\n.end method"


def find_one(decoded_dir: str, relative: str) -> str:
    matches = glob.glob(os.path.join(decoded_dir, "smali*", relative))
    if len(matches) != 1:
        sys.exit("expected exactly one %s, found %d" % (relative, len(matches)))
    return matches[0]


def read(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def write(path: str, text: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def patch_feature_checker(decoded_dir: str) -> None:
    path = find_one(decoded_dir, "kz/onay/firebase/feature_checker/FeatureCheckerImpl.smali")
    text = read(path)
    if "onay_ad_flag_off" in text:
        sys.exit("FeatureCheckerImpl is already patched")
    start = text.index(FEATURE_METHOD)
    end = text.index(".end method", start) + len(".end method")
    body = text[start:end]
    if body.count(FEATURE_ANCHOR) != 1 or not body.endswith(FEATURE_TAIL):
        sys.exit("FeatureCheckerImpl.isFeatureEnabled does not look as expected")
    checks = ""
    for flag in BLOCKED_FLAGS:
        checks += (
            '    const-string v0, "%s"\n\n'
            "    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n\n"
            "    move-result v0\n\n"
            "    if-nez v0, :onay_ad_flag_off\n\n" % flag
        )
    new_body = body.replace(FEATURE_ANCHOR, checks + FEATURE_ANCHOR)
    new_body = new_body[: -len(FEATURE_TAIL)] + (
        "    return p1\n\n    :onay_ad_flag_off\n    const/4 p1, 0x0\n\n    return p1\n.end method"
    )
    write(path, text[:start] + new_body + text[end:])
    print("smali patched: FeatureCheckerImpl.isFeatureEnabled blocks %d ad flags" % len(BLOCKED_FLAGS))


def patch_noops(decoded_dir: str) -> None:
    for relative, headers in NOOP_METHODS:
        path = find_one(decoded_dir, relative)
        text = read(path)
        for header in headers:
            if text.count(header + "\n") != 1:
                sys.exit("%s: expected 1 method %s, found %d" % (relative, header, text.count(header + "\n")))
            start = text.index(header + "\n")
            end = text.index(".end method", start) + len(".end method")
            text = text[:start] + header + "\n    .locals 0\n\n    return-void\n.end method" + text[end:]
            print("smali patched: %s no-op %s" % (os.path.basename(relative), header.split("loadAd")[1][:60]))
        write(path, text)


def main(decoded_dir: str) -> None:
    patch_feature_checker(decoded_dir)
    patch_noops(decoded_dir)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: patch_ads.py <decoded dir>")
    main(sys.argv[1])
