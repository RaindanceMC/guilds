"""
The guild skill tree's looks: heraldic shields for the nodes, gilded chain between them (Core's tree.py writes the
font rainsguilds:tree from them), and a painting per branch behind the page (font rainsguilds:tree_scene, U+E000 + the
branch's place in GuildsPack.SCENES). Writes the pack's files.txt and pack.mcmeta too. preview.py lays the pages out.

    python3 skilltree_art.py
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CORE_ART = os.path.join(HERE, "..", "..", "..", "..", "RainsCore", "art", "menus")
sys.path.insert(0, CORE_ART)

from canvas import (Canvas, W, chest_well, inventory_well, hotbar_well, label_y, height, mix, wood, well,  # noqa: E402
                    vignette, shade)
import tree as core_tree  # noqa: E402

PACK = os.path.join(HERE, "..", "..", "guilds-module", "src", "main", "resources", "de", "raindancer", "modules",
                    "guilds", "pack")
NS = "rainsguilds"
ASSETS = os.path.join(PACK, "assets", NS)
ROWS = 6
SCENES = ["hall", "craft", "lore", "road", "banner"]

GOLD = (222, 176, 64)
GOLD_DARK = (122, 82, 22)
GOLD_LIGHT = (255, 236, 160)


# ------------------------------------------------------------------------------------------------ frames and lines

def shield_width(y):
    """Half the shield's width at y (centre 0): straight sides, then curving to a point."""
    if y < -11.5 or y > 12.2:
        return -1.0
    if y <= 1.0:
        return 11.0
    t = (y - 1.0) / 11.2
    return 11.0 * math.sqrt(max(0.0, 1 - t * t))


def inside(x, y, inset):
    return abs(x) <= shield_width(y) - inset and y >= -11.5 + inset and shield_width(y + inset * 0.8) - inset >= 0


class GuildLook(core_tree.Style):
    """Heraldic shields and gilded chain."""

    def line(self, along, across, lit):
        link = int(along // 5)
        u = (along % 5) - 2.5
        d = abs(across)
        metal = (GOLD, GOLD_DARK, GOLD_LIGHT) if lit else ((96, 92, 88), (40, 38, 36), (150, 146, 140))
        face, dark, light = metal
        if link % 2 == 0:
            e = (u / 2.9) ** 2 + (across / 2.3) ** 2
            if 0.42 < e <= 1.0:
                return mix(dark, light, 0.5 - across / 4.6), 255
        else:
            if d <= 0.8 and abs(u) <= 3.0:
                return mix(face, light, 0.4 - across / 3), 255
        if lit and d <= 4.0:
            return (255, 214, 110), int(70 * (1 - d / 4.0))
        return None

    def frame(self, state, x, y):
        fields = {
            "locked": ((96, 94, 92), (44, 42, 42), (140, 138, 134), (36, 36, 40)),
            "open": (GOLD, GOLD_DARK, GOLD_LIGHT, (26, 30, 38)),
            "owned": (GOLD, GOLD_DARK, GOLD_LIGHT, (34, 62, 132)),
            "maxed": ((250, 206, 92), (150, 96, 26), (255, 248, 210), (128, 24, 30)),
            "barred": ((122, 70, 52), (60, 30, 22), (168, 104, 80), (34, 14, 14)),
        }
        face, dark, light, field = fields[state]
        if not inside(x, y, 0):
            r = math.hypot(x, y)
            if state == "open" and inside(x, y, -1.6):
                return (130, 250, 150), 210
            if state == "maxed" and r <= 12.9:
                ray = max(0.0, math.cos(math.atan2(y, x) * 6)) ** 6
                if ray > 0.3:
                    return (255, 236, 150), int(255 * ray)
            return None
        if not inside(x, y, 2.2):
            # the rim, lit from the top left
            lit = 0.5 - 0.5 * (x + y) / max(1.0, math.hypot(x, y)) / 1.2
            return mix(face, mix(dark, light, lit), 0.7), 255
        # the field: quartered, the top-left and bottom-right quarters a touch lighter
        lighter = (x < 0) == (y < 0)
        colour = shade(field, 1.15) if lighter else field
        colour = mix(colour, shade(colour, 0.6), max(0.0, (y + 4) / 18))
        if state == "barred" and (abs(x - y * 0.9) <= 1.0 or abs(x + y * 0.9) <= 1.0):
            return (200, 60, 48), 255
        return colour, 210 if state == "locked" else 235


# ------------------------------------------------------------------------------------------------ the paintings

def noise(rng, x, y, s):
    return (math.sin(x * 0.29 + y * 0.13) * 0.5 + math.sin(x * 0.071 - y * 0.19 + 1.3) * 0.5) * s \
        + rng.uniform(-1, 1) * s * 0.6


def chrome_and_inventory(c, rng, plank, plank_dark, plank_light, well_inner):
    """The bottom: a plank bar for the chrome row, the player's inventory on planks, and its label's patch."""
    h = height(ROWS)
    y0 = 17 + 18 * 5 - 2
    wood(c, 0, y0, W, h, plank, plank_dark, plank_light, seed=rng.randint(0, 999), plank=10)
    vignette(c, 0, y0, W, h, (10, 8, 6), strength=0.35, reach=8)
    for col in range(9):
        x, y = chest_well(5, col)
        well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    ly = label_y(ROWS)
    for y in range(ly - 2, ly + 9):
        for x in range(5, 70):
            c.put(x, y, mix((222, 206, 168), (194, 174, 132), (y - ly + 2) / 11 + rng.uniform(-0.04, 0.04)))
    c.hline(5, 70, ly - 3, plank_dark)
    c.hline(5, 70, ly + 9, plank_dark)
    for x in (5, 69):
        c.vline(x, ly - 2, ly + 9, plank_dark)
    for row in range(3):
        for col in range(9):
            x, y = inventory_well(ROWS, row, col)
            well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    for col in range(9):
        x, y = hotbar_well(ROWS, col)
        well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    c.round_corners(3)


def title_band(c, top, bottom, edge):
    for y in range(0, 16):
        for x in range(W):
            c.put(x, y, mix(top, bottom, y / 15))
    c.hline(0, W, 15, edge)
    c.hline(0, W, 16, shade(edge, 0.6), 160)


T0, T1 = 17, 17 + 18 * 5 - 2


def glow(c, gx, gy, colour, reach, strength):
    for y in range(T0, T1):
        for x in range(W):
            d = math.hypot(x - gx, y - gy)
            if d < reach:
                c.tint(x, y, colour, strength * (1 - d / reach) ** 2)


def banner(c, x0, top, width, length, cloth, charge):
    """A hanging banner with a swallow-tail and a simple charge (a diamond) in the middle."""
    for y in range(top, top + length):
        for x in range(x0, x0 + width):
            tail = y > top + length - 6 and abs(x - (x0 + width / 2)) < (y - (top + length - 6)) * 0.9
            if tail:
                continue
            fold = 0.85 + 0.15 * math.sin((x - x0) * 1.3)
            colour = shade(cloth, fold)
            mx, my = x0 + width / 2, top + length * 0.42
            if abs(x - mx) / (width * 0.28) + abs(y - my) / (length * 0.18) <= 1:
                colour = shade(charge, fold)
            c.put(x, y, colour)
    c.hline(x0 - 1, x0 + width + 1, top - 1, (90, 60, 30))


def scene_hall(seed=1):
    """The great hall: a stone wall of big blocks, two banners, torchlight."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        row = (y - T0) // 9
        for x in range(W):
            off = 11 if row % 2 else 0
            mortar = (y - T0) % 9 == 0 or (x + off) % 22 == 0
            base = (92, 88, 84) if not mortar else (50, 48, 46)
            stone = (row * 7 + (x + off) // 22 * 13) % 5
            colour = shade(base, 0.9 + stone * 0.03)
            c.put(x, y, mix(colour, (0, 0, 0), noise(rng, x, y, 0.03) + 0.03))
    for bx, cloth in ((22, (40, 64, 140)), (140, (140, 30, 36))):
        banner(c, bx, T0 + 4, 14, 38, cloth, GOLD)
    for tx in (60, 116):
        glow(c, tx, T0 + 14, (255, 190, 100), 46, 0.35)
        for y in range(T0 + 12, T0 + 20):
            c.put(tx, y, (90, 60, 30))
        c.put(tx, T0 + 11, (255, 220, 120))
        c.put(tx, T0 + 10, (255, 170, 60))
    vignette(c, 0, T0, W, T1, (10, 8, 8), strength=0.5, reach=18)
    title_band(c, (36, 32, 30), (54, 48, 44), (18, 16, 14))
    chrome_and_inventory(c, rng, (104, 78, 52), (54, 38, 24), (150, 116, 80), (34, 28, 24))
    return c


def scene_craft(seed=2):
    """The workshop: a dark plank wall with tools hung on it, the forge glowing at one side."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    wood(c, 0, T0, W, T1, (92, 64, 40), (46, 30, 18), (132, 96, 62), seed=seed, plank=12, vertical=True)
    tools = [(30, "pick"), (58, "hammer"), (118, "saw"), (146, "tongs")]
    for tx, kind in tools:
        top = T0 + 8
        for y in range(top, top + 26):
            c.put(tx, y, (70, 46, 24))
        iron = (150, 150, 158)
        if kind == "pick":
            for i in range(-8, 9):
                c.put(tx + i, top + 2 + abs(i) // 3, iron)
        elif kind == "hammer":
            for y in range(top, top + 5):
                for x in range(tx - 5, tx + 6):
                    c.put(x, y, iron)
        elif kind == "saw":
            for y in range(top, top + 20):
                for x in range(tx + 1, tx + 8):
                    if x - tx < 7 - (y - top) // 4:
                        c.put(x, y, iron if (x + y) % 3 else shade(iron, 0.7))
        else:
            for i in range(10):
                c.put(tx - i // 3, top + 26 + i, iron)
                c.put(tx + i // 3, top + 26 + i, iron)
    glow(c, 0, T1, (255, 120, 40), 110, 0.55)
    glow(c, W, T1, (255, 150, 60), 60, 0.25)
    vignette(c, 0, T0, W, T1, (12, 6, 2), strength=0.5, reach=18)
    title_band(c, (44, 26, 14), (66, 40, 22), (22, 12, 6))
    chrome_and_inventory(c, rng, (96, 70, 46), (48, 34, 20), (140, 106, 70), (34, 26, 18))
    return c


def scene_lore(seed=3):
    """The library: shelves of books in many colours, candlelight between them."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    shelf_wood, shelf_dark = (96, 64, 36), (52, 34, 18)
    spines = [(120, 34, 30), (40, 70, 120), (44, 96, 52), (150, 120, 60), (90, 40, 90), (60, 50, 40), (150, 140, 120)]
    for y in range(T0, T1):
        for x in range(W):
            c.put(x, y, shelf_dark)
    shelf = 0
    y = T0
    while y < T1:
        top, bottom = y + 2, min(T1, y + 20)
        x = 2
        while x < W - 2:
            w = rng.randint(2, 4)
            h = rng.randint(12, 17)
            colour = rng.choice(spines)
            for yy in range(bottom - h, bottom):
                for xx in range(x, min(W - 2, x + w)):
                    band = (yy - (bottom - h)) in (2, h - 3)
                    c.put(xx, yy, shade(colour, 0.75 if xx == x else 1.0) if not band else GOLD_DARK)
            x += w + (1 if rng.random() < 0.2 else 0)
        for xx in range(W):
            c.put(xx, bottom, shelf_wood)
            c.put(xx, min(T1 - 1, bottom + 1), shade(shelf_wood, 0.7))
        y = bottom + 2
        shelf += 1
    for cx, cy in ((44, T0 + 30), (132, T0 + 52)):
        glow(c, cx, cy, (255, 200, 120), 50, 0.4)
    vignette(c, 0, T0, W, T1, (8, 4, 2), strength=0.6, reach=20)
    title_band(c, (40, 24, 18), (60, 38, 26), (20, 12, 8))
    chrome_and_inventory(c, rng, (100, 70, 44), (52, 36, 22), (146, 110, 74), (36, 26, 20))
    return c


def scene_road(seed=4):
    """The road at dusk: a fading sky, rolling hills, a road winding to a far castle."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            c.put(x, y, mix((60, 70, 120), (220, 140, 90), min(1, v * 1.8)))
    hills = [(T0 + 40, (64, 76, 70), 0.05, 6), (T0 + 52, (52, 74, 50), 0.07, 5), (T0 + 64, (40, 62, 38), 0.04, 7)]
    for base, colour, freq, amp in hills:
        phase = rng.uniform(0, 6)
        for x in range(W):
            top = int(base + math.sin(x * freq + phase) * amp)
            for y in range(top, T1):
                c.put(x, y, shade(colour, 0.95 + 0.05 * math.sin(x * 0.7 + y)))
    for x in range(118, 130):
        for y in range(T0 + 26, T0 + 40):
            if not (x in (120, 124, 128) and y < T0 + 29):
                c.put(x, y, (46, 44, 60))
    for y in range(T0 + 40, T1):
        t = (y - (T0 + 40)) / (T1 - T0 - 40)
        centre = 124 - t * 60 + math.sin(t * 5) * 14
        half = 1 + t * 9
        for x in range(int(centre - half), int(centre + half) + 1):
            c.put(x, y, mix((200, 170, 120), (150, 120, 80), abs(x - centre) / max(1, half)))
    vignette(c, 0, T0, W, T1, (20, 14, 20), strength=0.45, reach=18)
    title_band(c, (34, 36, 62), (50, 52, 84), (16, 16, 30))
    chrome_and_inventory(c, rng, (104, 82, 58), (54, 40, 28), (150, 122, 88), (36, 30, 26))
    return c


def scene_banner(seed=5):
    """The war camp at night: stars, tents, banners on poles, a campfire."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    stars = {(rng.randint(0, W - 1), rng.randint(T0, T0 + 40)) for _ in range(50)}
    for y in range(T0, T1):
        v = (y - T0) / (T1 - T0)
        for x in range(W):
            colour = mix((10, 14, 34), (30, 30, 52), v)
            if (x, y) in stars:
                colour = (220, 220, 240)
            c.put(x, y, colour)
    ground = T0 + 66
    for y in range(ground, T1):
        for x in range(W):
            c.put(x, y, mix((40, 34, 26), (24, 20, 16), (y - ground) / max(1, T1 - ground)))
    for tx, tw in ((18, 30), (126, 34)):
        th = 24
        for y in range(ground - th, ground):
            half = (y - (ground - th)) * tw / 2 / th
            for x in range(int(tx + tw / 2 - half), int(tx + tw / 2 + half) + 1):
                c.put(x, y, shade((150, 130, 100), 0.8 if x < tx + tw / 2 else 0.95))
    for px, cloth in ((64, (150, 28, 34)), (104, (40, 60, 140))):
        for y in range(T0 + 10, ground):
            c.put(px, y, (80, 56, 30))
        banner(c, px + 1, T0 + 12, 11, 24, cloth, GOLD)
    glow(c, 88, ground - 2, (255, 140, 50), 70, 0.6)
    for i in range(7):
        c.put(85 + i, ground - 1, (255, 200 - i * 10, 80))
        c.put(86 + i % 5, ground - 2 - i % 3, (255, 160, 60))
    vignette(c, 0, T0, W, T1, (4, 4, 10), strength=0.5, reach=18)
    title_band(c, (34, 16, 18), (56, 24, 28), (16, 8, 8))
    chrome_and_inventory(c, rng, (92, 66, 42), (48, 34, 20), (140, 106, 70), (34, 26, 18))
    return c


PAINTERS = [scene_hall, scene_craft, scene_lore, scene_road, scene_banner]


def write_scenes():
    out = os.path.join(ASSETS, "textures", "font", "tree_scene")
    os.makedirs(out, exist_ok=True)
    providers = []
    files = []
    for index, (name, paint) in enumerate(zip(SCENES, PAINTERS)):
        c = paint()
        c.save(os.path.join(out, name + ".png"))
        files.append("assets/%s/textures/font/tree_scene/%s.png" % (NS, name))
        providers.append({"type": "bitmap", "file": "%s:font/tree_scene/%s.png" % (NS, name),
                          "ascent": 13, "height": height(ROWS), "chars": [chr(0xE000 + index)]})
    providers.append({"type": "space", "advances": {"": -8, "": -169}})
    with open(os.path.join(ASSETS, "font", "tree_scene.json"), "w") as f:
        json.dump({"providers": providers}, f, indent=1)
    files.append("assets/%s/font/tree_scene.json" % NS)
    return files


def main():
    os.makedirs(os.path.join(ASSETS, "font"), exist_ok=True)
    files, images = core_tree.write(ASSETS, NS, "tree", GuildLook())
    core_tree.sheet(images, "/tmp/tree-guilds.png")
    files += write_scenes()
    with open(os.path.join(PACK, "files.txt"), "w") as out:
        out.write("\n".join(files) + "\n")
    with open(os.path.join(PACK, "pack.mcmeta"), "w") as out:
        json.dump({"pack": {"description": "Rain's Guilds — the guild skill tree", "min_format": 88,
                            "max_format": 97}}, out, indent=1)
    print(len(files), "files")


if __name__ == "__main__":
    main()
