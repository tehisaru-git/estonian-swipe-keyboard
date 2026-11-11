#!/usr/bin/env python3
"""Generate original Estonian Swipe legacy launcher PNGs from the vector's geometry.

Requires Pillow only when regenerating artwork; Android builds use checked-in PNGs
and the adaptive vector under res/drawable. No upstream mascot assets are used.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BASE = 108
SCALE = 8
NAVY = '#24354F'
MINT = '#8DE2D1'
SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def draw_mark(size: int) -> Image.Image:
    scale = SCALE
    image = Image.new('RGBA', (BASE * scale, BASE * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    def xy(box):
        return tuple(round(v * scale) for v in box)
    draw.rounded_rectangle(xy((3, 3, 105, 105)), radius=23 * scale, fill=NAVY)
    for left in (20, 44, 68):
        draw.rounded_rectangle(xy((left, 33, left + 20, 58)), radius=6 * scale, fill='white')
    points = []
    for i in range(101):
        t = i / 100
        x = (1-t)**3 * 29 + 3*(1-t)**2*t*40 + 3*(1-t)*t*t*52 + t**3*65
        y = (1-t)**3 * 75 + 3*(1-t)**2*t*59 + 3*(1-t)*t*t*78 + t**3*70
        points.append((round(x * scale), round(y * scale)))
    for i in range(101):
        t = i / 100
        x = (1-t)**3 * 65 + 3*(1-t)**2*t*72 + 3*(1-t)*t*t*76 + t**3*80
        y = (1-t)**3 * 70 + 3*(1-t)**2*t*66 + 3*(1-t)*t*t*65 + t**3*64
        points.append((round(x * scale), round(y * scale)))
    draw.line(points, fill=MINT, width=round(5.5 * scale), joint='curve')
    for x, y, r in ((29,75,2.75), (79,64,5)):
        draw.ellipse(xy((x-r,y-r,x+r,y+r)), fill=MINT)
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    for density, pixels in SIZES.items():
        path = ROOT / 'res' / ('mipmap-' + density) / 'ic_launcher.png'
        draw_mark(pixels).save(path, optimize=True)
        print(path.relative_to(ROOT), pixels)
    preview = ROOT / 'build' / 'tooling' / 'estonian-swipe-icon-preview.png'
    preview.parent.mkdir(parents=True, exist_ok=True)
    draw_mark(432).save(preview, optimize=True)
    print(preview)
