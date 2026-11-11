"""Generate launcher icons (adaptive vector + PNG mipmaps) and Play Store graphics."""
import os, sys
from PIL import Image, ImageDraw

res = sys.argv[1]; store = sys.argv[2]
BG1 = (21, 101, 192); BG2 = (0, 172, 193)
WHITE = (255, 255, 255); KEY = (21, 101, 192); ACC = (0, 191, 165)

# shapes in a 108x108 viewport: (kind, x0, y0, x1, y1, r, color)
def shapes():
    s = [("rr", 22, 36, 86, 78, 8, WHITE)]
    rows = [(42, 5), (52, 5), (62, 0)]
    x0, x1 = 28, 80
    for y, n in rows[:2]:
        w = (x1 - x0 - (n - 1) * 3) / n
        for i in range(n):
            a = x0 + i * (w + 3)
            s.append(("rr", a, y, a + w, y + 7, 2, KEY))
    s.append(("rr", 36, 63, 72, 70, 2, ACC))   # space bar
    s.append(("rr", 28, 63, 33, 70, 2, KEY))
    s.append(("rr", 75, 63, 80, 70, 2, KEY))
    # badge with stripes of the Uzbek flag (blue / white / green) on top
    s.append(("rr", 40, 24, 68, 32, 4, WHITE))
    s.append(("rr", 43, 26.5, 65, 29.5, 1.5, ACC))
    return s

def vector_path(kind, x0, y0, x1, y1, r):
    return (f"M{x0+r},{y0} H{x1-r} A{r},{r} 0 0 1 {x1},{y0+r} V{y1-r} A{r},{r} 0 0 1 {x1-r},{y1} "
            f"H{x0+r} A{r},{r} 0 0 1 {x0},{y1-r} V{y0+r} A{r},{r} 0 0 1 {x0+r},{y0} Z")

def hexc(c): return "#FF%02X%02X%02X" % c

paths = "\n".join(
    f'    <path android:fillColor="{hexc(c)}" android:pathData="{vector_path(k,a,b,cx,d,r)}" />'
    for k, a, b, cx, d, r, c in shapes())
fg = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{paths}
</vector>
'''
bg = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:startColor="%s" android:endColor="%s" />
        </aapt:attr>
    </path>
</vector>
''' % (hexc(BG1), hexc(BG2))
mono = fg
open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(fg)
open(f"{res}/drawable/ic_launcher_background.xml", "w").write(bg)
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
for n in ("ic_launcher", "ic_launcher_round"):
    open(f"{res}/mipmap-anydpi-v26/{n}.xml", "w").write(adaptive)

def gradient(size):
    im = Image.new("RGB", (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(int(BG1[i] + (BG2[i] - BG1[i]) * t) for i in range(3))
    return im

def render(size, crop=True, round_=False):
    S = 4  # supersample
    big = size * S
    # full 108 viewport, PNG legacy icons show the middle ~ 72/108 enlarged a bit
    scale = big / (76 if crop else 108)
    off = 16 if crop else 0
    im = gradient(big).convert("RGBA")
    d = ImageDraw.Draw(im)
    for k, a, b, c2, e, r, col in shapes():
        d.rounded_rectangle([(a - off) * scale, (b - off) * scale, (c2 - off) * scale, (e - off) * scale],
                            radius=r * scale, fill=col)
    mask = Image.new("L", (big, big), 0)
    md = ImageDraw.Draw(mask)
    if round_:
        md.ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.22, fill=255)
    out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)

for dens, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    os.makedirs(f"{res}/mipmap-{dens}", exist_ok=True)
    render(px).save(f"{res}/mipmap-{dens}/ic_launcher.png", optimize=True)
    render(px, round_=True).save(f"{res}/mipmap-{dens}/ic_launcher_round.png", optimize=True)

os.makedirs(store, exist_ok=True)
# Play Store icon: 512x512, full square (Play applies its own mask)
S = 4; big = 512 * S; scale = big / 76
im = gradient(big).convert("RGBA"); d = ImageDraw.Draw(im)
for k, a, b, c2, e, r, col in shapes():
    d.rounded_rectangle([(a - 16) * scale, (b - 16) * scale, (c2 - 16) * scale, (e - 16) * scale], radius=r * scale, fill=col)
im.resize((512, 512), Image.LANCZOS).convert("RGB").save(f"{store}/play_icon_512.png")

# Feature graphic 1024x500: gradient + big keyboard illustration
W, H = 1024 * 2, 500 * 2
fg_im = Image.new("RGB", (W, H)); px = fg_im.load()
for y in range(H):
    for x in range(W):
        t = x / (W - 1) * 0.7 + y / (H - 1) * 0.3
        px[x, y] = tuple(int(BG1[i] + (BG2[i] - BG1[i]) * t) for i in range(3))
d = ImageDraw.Draw(fg_im)
# draw a keyboard with 3 rows of 11 keys
kx0, ky0, kx1, ky1 = 260, 230, 1790, 860
d.rounded_rectangle([kx0, ky0, kx1, ky1], radius=60, fill=(255, 255, 255))
rows = [11, 11, 9]
for ri, n in enumerate(rows):
    y = ky0 + 50 + ri * 150
    pad = 50 + (ri == 2) * 120
    w = (kx1 - kx0 - 2 * pad - (n - 1) * 20) / n
    for i in range(n):
        x = kx0 + pad + i * (w + 20)
        hl = (ri, i) in {(0, 10), (1, 9), (2, 3), (2, 8)}  # positions of ō ḡ ç ş
        d.rounded_rectangle([x, y, x + w, y + 120], radius=22, fill=ACC if hl else KEY)
d.rounded_rectangle([kx0 + 400, ky0 + 500, kx1 - 400, ky0 + 590], radius=22, fill=ACC)
d.rounded_rectangle([760, 90, 1290, 170], radius=40, fill=(255, 255, 255))
d.rounded_rectangle([800, 115, 1250, 145], radius=15, fill=ACC)
fg_im.resize((1024, 500), Image.LANCZOS).save(f"{store}/feature_graphic_1024x500.png")
print("icons done")
