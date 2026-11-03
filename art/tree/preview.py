"""
Lays a skill tree page out the way the game draws it — the branch's painting, the frames and lines over it, the icons
in their slots — for a made-up guild, three times the size. Needs the client's textures in /tmp/mcassets.

    python3 preview.py [out.png]
"""
import math
import os
import sys

import yaml
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
GUILDS = os.path.join(HERE, "..", "..", "guilds-module", "src", "main", "resources", "de", "raindancer", "modules",
                       "guilds")
FONT = os.path.join(GUILDS, "pack", "assets", "rainsguilds", "textures", "font")
MC = "/tmp/mcassets/assets/minecraft/textures"
SCENES = ["hall", "craft", "lore", "road", "banner"]

RANKS = {"recruiter": 3, "vault": 2, "quartermaster": 1, "town_crier": 1, "charter": 1, "great_table": 1,
         "haste": 2, "prospectors": 2, "deep_delvers": 1, "scholar": 3, "prosperous": 1, "soul_ward": 1,
         "pathfinder": 2, "sailors": 1, "sea_legs": 1, "shoulder_to_shoulder": 3, "rally": 1, "vanguard": 1}


def state(node, nid):
    rank = RANKS.get(nid, 0)
    if rank >= node.get("ranks", 1):
        return "maxed"
    if rank > 0:
        return "owned"
    if any(RANKS.get(o, 0) > 0 for o in node.get("excludes", [])):
        return "barred"
    req = node.get("requires", [])
    return "open" if not req or any(RANKS.get(o, 0) > 0 for o in req) else "locked"


def icon(name):
    for folder in ("item", "block"):
        path = os.path.join(MC, folder, name + ".png")
        if os.path.exists(path):
            img = Image.open(path).convert("RGBA")
            return img.crop((0, 0, 16, 16)) if img.height > 16 else img
    return None


def page(branch, tree):
    window = Image.open(os.path.join(FONT, "tree_scene", branch + ".png")).convert("RGBA")
    nodes = {k: v for k, v in tree["nodes"].items() if v["branch"] == branch}
    sprites = []
    for nid, node in nodes.items():
        for need in node.get("requires", []):
            other = nodes[need]
            dc, dr = node["at"][0] - other["at"][0], node["at"][1] - other["at"][1]
            shape = "across" if dr == 0 else "down" if dc == 0 else "falling" if dc == dr else "rising"
            lit = state(node, nid) in ("owned", "maxed") and state(other, need) in ("owned", "maxed")
            sprites.append((shape + ("_lit" if lit else "_dim"), other["at"][1] * 2 + dr, other["at"][0] * 2 + dc))
    for nid, node in nodes.items():
        sprites.append(("frame_" + state(node, nid), node["at"][1] * 2, node["at"][0] * 2))
    for name, row, col in sprites:
        sprite = Image.open(os.path.join(FONT, "tree", name + ".png")).convert("RGBA")
        window.alpha_composite(sprite, (16 + 18 * col - 13, 13 + 18 * row))
    for nid, node in nodes.items():
        img = icon(node["icon"])
        if img:
            window.alpha_composite(img, (8 + 36 * node["at"][0], 18 + 36 * node["at"][1]))
    for col, name in ((0, "arrow"), (3, "spectral_arrow"), (4, tree["branches"][branch]["icon"]), (5, "spectral_arrow"),
                      (6, "writable_book"), (8, "barrier")):
        img = icon(name)
        if img:
            window.alpha_composite(img, (8 + 18 * col, 18 + 18 * 5))
    return window


def main():
    tree = yaml.safe_load(open(os.path.join(GUILDS, "skilltree.yml")))
    pages = [page(b, tree) for b in SCENES]
    out = Image.new("RGBA", ((176 + 6) * 3, (222 + 6) * 2), (30, 30, 34, 255))
    for i, p in enumerate(pages):
        out.alpha_composite(p, (3 + (i % 3) * 182, 3 + (i // 3) * 228))
    target = sys.argv[1] if len(sys.argv) > 1 else "/tmp/guild-tree-preview.png"
    out.resize((out.width * 3, out.height * 3), Image.NEAREST).save(target)
    print(target)


if __name__ == "__main__":
    main()
