#!/usr/bin/env python3
"""Generate Viipio keyboard legacy launcher PNGs from the adaptive vector geometry.

Requires Pillow only when regenerating artwork; Android builds use checked-in PNGs
and the adaptive vector under res/drawable. No upstream mascot assets are used.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BASE = 108
SCALE = 8
NAVY = '#22364C'
MINT = '#A5E4D9'
KEY = '#EDF3F7'
SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def draw_mark(size: int) -> Image.Image:
    scale = SCALE
    image = Image.new('RGBA', (BASE * scale, BASE * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    def xy(box):
        return tuple(round(v * scale) for v in box)
    draw.rounded_rectangle(xy((3, 3, 105, 105)), radius=23 * scale, fill=NAVY)
    for left in (22, 44, 66):
        draw.rounded_rectangle(xy((left, 29, left + 20, 59)), radius=6 * scale, fill=KEY)
    points = []
    for i in range(101):
        t = i / 100
        x = (1-t)**3 * 28 + 3*(1-t)**2*t*39 + 3*(1-t)*t*t*42 + t**3*54
        y = (1-t)**3 * 67 + 3*(1-t)**2*t*67 + 3*(1-t)*t*t*81 + t**3*81
        points.append((round(x * scale), round(y * scale)))
    for i in range(101):
        t = i / 100
        x = (1-t)**3 * 54 + 3*(1-t)**2*t*66 + 3*(1-t)*t*t*69 + t**3*81
        y = (1-t)**3 * 81 + 3*(1-t)**2*t*81 + 3*(1-t)*t*t*67 + t**3*67
        points.append((round(x * scale), round(y * scale)))
    draw.line(points, fill=MINT, width=round(6 * scale), joint='curve')
    for x, y, r in ((28,67,3), (81,67,4)):
        draw.ellipse(xy((x-r,y-r,x+r,y+r)), fill=MINT)
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    for density, pixels in SIZES.items():
        path = ROOT / 'res' / ('mipmap-' + density) / 'ic_launcher.png'
        draw_mark(pixels).save(path, optimize=True)
        print(path.relative_to(ROOT), pixels)
    preview = ROOT / 'build' / 'tooling' / 'viipio-icon-preview.png'
    preview.parent.mkdir(parents=True, exist_ok=True)
    draw_mark(432).save(preview, optimize=True)
    print(preview)
