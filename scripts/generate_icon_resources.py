"""Generates the ONAY Free launcher icon resources from assets/icon/onay-free-icon-source.png.

Developer tool (needs Pillow and numpy); the build itself only copies the committed result
(scripts/patch_app_icon.py), so run this after changing the source image and commit assets/icon/res.

Android layers produced for every density (mdpi..xxxhdpi), names follow the ONAY! 3.6.5 manifest
(@mipmap/ic_launcher_autumn and @mipmap/ic_launcher_autumn_round):

  ic_launcher_autumn.webp            legacy icon: the rounded tile of the source, transparent corners
  ic_launcher_autumn_round.webp      legacy round icon: tile on the background colour, circle crop
  ic_launcher_autumn_foreground.webp adaptive foreground (108dp canvas, art inside the 66dp safe zone)
  ic_launcher_autumn_monochrome.webp themed icon of Android 13+: white silhouette of the bright shapes

The artwork edge is feathered into the background colour, so no hard cut of the grey streets is visible
with any launcher mask. Nothing is stretched: every size is resampled from the full-size source.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "assets", "icon", "onay-free-icon-source.png")
OUT = os.path.join(ROOT, "assets", "icon", "res")

BACKGROUND = (0x14, 0x1A, 0x23)
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ART_DP = 62.0        # width of the tile inside the 108dp adaptive canvas: its far corners stay in the 66dp circle
ROUND_SCALE = 0.88   # tile width relative to the round icon: bright shapes stay inside the circle
FEATHER_PX = 36      # distance from the tile edge where the artwork has faded into the background


def outside_mask(rgb: Image.Image) -> np.ndarray:
    """True for the white canvas around the rounded tile (flood fill from the corners)."""
    binary = rgb.convert("L").point(lambda v: 255 if v > 200 else 0)
    for seed in ((0, 0), (binary.width - 1, 0), (0, binary.height - 1), (binary.width - 1, binary.height - 1)):
        if binary.getpixel(seed) == 255:
            ImageDraw.floodfill(binary, seed, 128)
    return np.asarray(binary) == 128


def load_tile():
    """Returns (art RGB over the background colour with feathered alpha, hard tile alpha, square size)."""
    rgb = Image.open(SOURCE).convert("RGB")
    outside = outside_mask(rgb)
    if not outside[0, 0] or outside.mean() < 0.01 or outside.mean() > 0.3:
        sys.exit("unexpected source image: the white surrounding of the tile was not found")
    ys, xs = np.where(~outside)
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    side = max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    box = (cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)
    rgb = rgb.crop(box)
    inside = Image.fromarray(((~outside) * 255).astype(np.uint8)).crop(box)
    hard = inside.filter(ImageFilter.GaussianBlur(1.0))
    feather = inside.filter(ImageFilter.MinFilter(2 * FEATHER_PX + 1)).filter(ImageFilter.GaussianBlur(FEATHER_PX / 2))
    base = Image.new("RGB", rgb.size, BACKGROUND)
    art = Image.composite(rgb, base, hard)
    return art, hard, feather, side


def rgba(art: Image.Image, alpha: Image.Image) -> Image.Image:
    out = art.convert("RGBA")
    out.putalpha(alpha)
    return out


def resized(image: Image.Image, size: int) -> Image.Image:
    # premultiplied resampling would be needed only for semi-transparent colours; every colour here is opaque
    return image.resize((size, size), Image.LANCZOS)


def legacy(art, hard, size):
    return resized(rgba(art, hard), size)


def round_icon(art, feather, size):
    scale = 4  # supersampled circle edge
    big = size * scale
    canvas = Image.new("RGBA", (big, big), BACKGROUND + (255,))
    tile = resized(rgba(art, feather), round(big * ROUND_SCALE))
    canvas.alpha_composite(tile, ((big - tile.width) // 2, (big - tile.height) // 2))
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    canvas.putalpha(mask)
    return canvas.resize((size, size), Image.LANCZOS)


def foreground(art, feather, density):
    canvas = Image.new("RGBA", (round(108 * density),) * 2, (0, 0, 0, 0))
    tile = resized(rgba(art, feather), round(ART_DP * density))
    canvas.alpha_composite(tile, ((canvas.width - tile.width) // 2, (canvas.height - tile.height) // 2))
    return canvas


def monochrome(art, feather, density):
    luminance = np.asarray(art.convert("L"), dtype=np.float32)
    shapes = np.clip((luminance - 110.0) / 60.0, 0.0, 1.0)   # white and lime pass, grey streets and background do not
    alpha = (shapes * np.asarray(feather, dtype=np.float32)).astype(np.uint8)
    white = Image.new("RGBA", art.size, (255, 255, 255, 255))
    white.putalpha(Image.fromarray(alpha))
    canvas = Image.new("RGBA", (round(108 * density),) * 2, (0, 0, 0, 0))
    tile = resized(white, round(ART_DP * density))
    canvas.alpha_composite(tile, ((canvas.width - tile.width) // 2, (canvas.height - tile.height) // 2))
    return canvas


def save(image: Image.Image, relative: str) -> None:
    path = os.path.join(OUT, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "WEBP", lossless=True, quality=100, method=6)


def main() -> None:
    art, hard, feather, _ = load_tile()
    for name, density in DENSITIES.items():
        size = round(48 * density)
        save(legacy(art, hard, size), "mipmap-%s/ic_launcher_autumn.webp" % name)
        save(round_icon(art, feather, size), "mipmap-%s/ic_launcher_autumn_round.webp" % name)
        save(foreground(art, feather, density), "mipmap-%s/ic_launcher_autumn_foreground.webp" % name)
        save(monochrome(art, feather, density), "mipmap-%s/ic_launcher_autumn_monochrome.webp" % name)
    print("icon resources written to", OUT)


if __name__ == "__main__":
    main()
