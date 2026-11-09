import os
from PIL import Image, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', 'app', 'res'))


def make(size, path):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = max(2, size // 24)
    d.rounded_rectangle([m, m, size - m, size - m], radius=size // 5, fill=(0, 102, 204, 255))
    # 齿轮状外圈
    cx = cy = size // 2
    r1 = int(size * 0.30)
    r2 = int(size * 0.14)
    for ang in range(0, 360, 45):
        import math
        a = math.radians(ang)
        x = cx + r1 * math.cos(a)
        y = cy + r1 * math.sin(a)
        d.ellipse([x - r2 // 2, y - r2 // 2, x + r2 // 2, y + r2 // 2], fill=(255, 255, 255, 255))
    d.ellipse([cx - r1, cy - r1, cx + r1, cy + r1], outline=(255, 255, 255, 255), width=max(3, size // 28))
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=(255, 255, 255, 255))
    img.save(path)


sizes = {'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96,
         'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192}
for d_, s in sizes.items():
    p = os.path.join(ROOT, d_)
    os.makedirs(p, exist_ok=True)
    make(s, os.path.join(p, 'ic_launcher.png'))
print('icons done')
