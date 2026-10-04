# -*- coding: utf-8 -*-
"""Лого Mana Essence Bridge: квадрат для CurseForge и баннер для списка модов."""
import math
import random
from PIL import Image

T = 'C:/custom mod/1.20.1/src/main/resources/assets/manaessencebridge/textures/'
OUT = 'C:/custom mod/branding/'


def tex(rel):
    return Image.open(T + rel).convert('RGBA')


CATALYSTS = [tex('item/%s_mana_catalyst.png' % n) for n in
             ('inferium', 'prudentium', 'tertium', 'imperium', 'supremium')]
TIER_COLORS = [(0x8F, 0xAF, 0x00), (0x00, 0xBA, 0x2E), (0xE2, 0x56, 0x00), (0x00, 0x94, 0xFF), (0xE2, 0x00, 0x00)]
SIDE, TOP = tex('block/essence_condenser_side.png'), tex('block/essence_condenser_top.png')
MYST, REAP = tex('block/mysticarnation.png'), tex('block/reaperbloom.png')

# --- пиксельный шрифт 5x7 -----------------------------------------------------
GLYPHS = {
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'B': ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'],
    'C': ['.####', '#....', '#....', '#....', '#....', '#....', '.####'],
    'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'G': ['.####', '#....', '#....', '#..##', '#...#', '#...#', '.####'],
    'I': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
    'M': ['#...#', '##.##', '#.#.#', '#.#.#', '#...#', '#...#', '#...#'],
    'N': ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
}
BLUE = [(0xE8, 0xF4, 0xFF), (0xBF, 0xE3, 0xFF), (0x96, 0xCD, 0xFF), (0x7D, 0xB8, 0xF0),
        (0x4A, 0x8F, 0xD6), (0x3A, 0x78, 0xC4), (0x2C, 0x5C, 0xA0)]
LIME = [(0xF1, 0xF6, 0xAF), (0xE5, 0xF1, 0x8B), (0xCA, 0xDD, 0x36), (0xBA, 0xC9, 0x22),
        (0x95, 0xA6, 0x0A), (0x7B, 0x8B, 0x00), (0x59, 0x61, 0x02)]
LILAC = [(0xF6, 0xEC, 0xFF), (0xEA, 0xD6, 0xFF), (0xD9, 0xB3, 0xFF), (0xC9, 0x9C, 0xF5),
         (0xB0, 0x80, 0xE6), (0x96, 0x68, 0xD0), (0x7A, 0x52, 0xB4)]
OUTLINE = (0x10, 0x12, 0x28, 255)


def text_width(word, scale):
    return sum((5 if c != ' ' else 3) * scale + scale for c in word) - scale


def draw_text(img, x, y, word, palette, scale=1):
    """Буквы с вертикальным градиентом и тёмной обводкой в пиксель."""
    px = img.load()
    cells = []
    cx = x
    for c in word:
        if c == ' ':
            cx += 4 * scale
            continue
        for gy, row in enumerate(GLYPHS[c]):
            for gx, ch in enumerate(row):
                if ch == '#':
                    for sy in range(scale):
                        for sx in range(scale):
                            cells.append((cx + gx * scale + sx, y + gy * scale + sy, gy))
        cx += 6 * scale
    filled = {(a, b) for a, b, _ in cells}
    for a, b, _ in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (a + dx, b + dy)
                if q not in filled and 0 <= q[0] < img.width and 0 <= q[1] < img.height:
                    px[q] = OUTLINE
    for a, b, gy in cells:
        px[a, b] = palette[gy] + (255,)


# --- изометрический куб конденсатора -------------------------------------------
def shade(im, f):
    o = im.copy()
    d = o.load()
    for y in range(o.height):
        for x in range(o.width):
            r, g, b, a = d[x, y]
            d[x, y] = (int(r * f), int(g * f), int(b * f), a)
    return o


def iso(side, top, s):
    W = 32 * s
    out = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    o = out.load()
    L, R, Tp = shade(side, .82).load(), shade(side, .62).load(), top.load()
    for py in range(W):
        for px_ in range(W):
            x, y = (px_ + .5) / s, (py + .5) / s
            a, b = (x - 16) / 16.0, (y - 8) / 8.0
            U, V = (b + a) / 2 * 16 + 8, (b - a) / 2 * 16 + 8
            if 0 <= U < 16 and 0 <= V < 16 and y < 16:
                o[px_, py] = Tp[int(U), int(V)]
                continue
            if x < 16:
                ty = y - 8 - x / 2
                if 0 <= ty < 16:
                    o[px_, py] = L[min(15, int(x)), min(15, int(ty))]
            else:
                ty = y - 16 + (x - 16) / 2
                if 0 <= ty < 16:
                    o[px_, py] = R[min(15, int(x - 16)), min(15, int(ty))]
    return out


# --- фон -----------------------------------------------------------------------
def background(w, h, glow_center, glow_radius, seed):
    img = Image.new('RGBA', (w, h))
    px = img.load()
    top, bottom = (0x0B, 0x10, 0x30), (0x26, 0x10, 0x3C)
    glow = (0x2C, 0x5C, 0xA0)
    for y in range(h):
        t = round(y / (h - 1) * 7) / 7          # 8 полос, как в пиксель-арте
        base = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            d = math.hypot(x - glow_center[0], (y - glow_center[1]) * 1.15)
            k = max(0.0, 1 - d / glow_radius)
            k = round(k * 5) / 5 * 0.55          # ступени свечения
            px[x, y] = tuple(int(base[i] + (glow[i] - base[i]) * k) for i in range(3)) + (255,)
    r = random.Random(seed)
    for _ in range(int(w * h / 90)):
        x, y = r.randrange(w), r.randrange(h)
        c = r.choice([(0xCA, 0xDD, 0x36), (0x96, 0xCD, 0xFF), (0xFF, 0xFF, 0xFF), (0xD9, 0xB3, 0xFF)])
        px[x, y] = c + (255,)
        if r.random() < 0.18 and 1 <= x < w - 1 and 1 <= y < h - 1:
            dim = tuple(v // 2 + 20 for v in c) + (255,)
            for q in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                px[q] = dim
    return img


def dotted(img, a, b, color, step=3):
    px = img.load()
    n = int(math.hypot(b[0] - a[0], b[1] - a[1]))
    for i in range(0, n, step):
        x = int(a[0] + (b[0] - a[0]) * i / n)
        y = int(a[1] + (b[1] - a[1]) * i / n)
        if 0 <= x < img.width and 0 <= y < img.height:
            px[x, y] = color + (255,)


def glow_under(img, sprite, x, y, color):
    """Мягкий ореол цвета под спрайтом - в две ступени."""
    px = img.load()
    a = sprite.getchannel('A')
    for ring, alpha in ((2, 0.18), (1, 0.32)):
        for sy in range(sprite.height):
            for sx in range(sprite.width):
                if a.getpixel((sx, sy)) == 0:
                    continue
                for dx in range(-ring, ring + 1):
                    for dy in range(-ring, ring + 1):
                        X, Y = x + sx + dx, y + sy + dy
                        if 0 <= X < img.width and 0 <= Y < img.height:
                            r, g, b, _ = px[X, Y]
                            px[X, Y] = (int(r + (color[0] - r) * alpha), int(g + (color[1] - g) * alpha),
                                        int(b + (color[2] - b) * alpha), 255)


# ================================ квадрат ========================================
S = 128
sq = background(S, S, (64, 62), 58, seed=7)
cube = iso(SIDE, TOP, 2)                         # 64x64
cube_xy = (32, 30)
cube_top = (cube_xy[0] + 32, cube_xy[1] + 16)    # центр верхней грани
arc = []
for i, cat in enumerate(CATALYSTS):
    ang = math.radians(200 + i * 35)             # дуга над кубом
    cx = 64 + int(round(math.cos(ang) * 50))
    cy = 38 + int(round(math.sin(ang) * 30))
    arc.append((cx - 8, cy - 8))
for (x, y), col in zip(arc, TIER_COLORS):
    dotted(sq, (x + 8, y + 14), cube_top, col, step=3)
sq.alpha_composite(cube, cube_xy)
for (x, y), cat, col in zip(arc, CATALYSTS, TIER_COLORS):
    glow_under(sq, cat, x, y, col)
    sq.alpha_composite(cat, (x, y))
big_myst = MYST.resize((32, 32), Image.NEAREST)
big_reap = REAP.resize((32, 32), Image.NEAREST)
sq.alpha_composite(big_myst, (4, 62))
sq.alpha_composite(big_reap, (92, 62))
w1 = text_width('MANA', 1) + 4 + text_width('ESSENCE', 1)
x1 = (S - w1) // 2
draw_text(sq, x1, 98, 'MANA', BLUE, 1)
draw_text(sq, x1 + text_width('MANA', 1) + 4, 98, 'ESSENCE', LIME, 1)
w2 = text_width('BRIDGE', 2)
draw_text(sq, (S - w2) // 2, 109, 'BRIDGE', LILAC, 2)
sq_big = sq.resize((S * 4, S * 4), Image.NEAREST)
sq_big.save(OUT + 'logo_curseforge_512.png')

# ================================ баннер =========================================
BW, BH = 192, 48
bn = background(BW, BH, (26, 24), 30, seed=11)
cube_s = iso(SIDE, TOP, 1)                       # 32x32
bn.alpha_composite(cube_s, (10, 8))
draw_text(bn, 52, 6, 'MANA', BLUE, 2)
draw_text(bn, 52 + text_width('MANA', 2) + 8, 6, 'ESSENCE', LIME, 2)
draw_text(bn, 52, 27, 'BRIDGE', LILAC, 2)
bx = 52 + text_width('BRIDGE', 2) + 10
for i, (cat, col) in enumerate(zip(CATALYSTS, TIER_COLORS)):
    small = cat.resize((10, 10), Image.NEAREST)
    x = bx + i * 12
    glow_under(bn, small, x, 29, col)
    bn.alpha_composite(small, (x, 29))
bn_big = bn.resize((BW * 4, BH * 4), Image.NEAREST)
bn_big.save(OUT + 'logo_banner_768x192.png')
print('square', sq_big.size, 'banner', bn_big.size, 'catalysts end at', bx + 4 * 12 + 10)
