"""Draws the mimic jump-scare: a chest-textured maw lunging at the screen, as a font glyph image.

Writes ../assets/tfmc_spiders/textures/font/mimic_scare.png and ../assets/tfmc_spiders/font/mimic_scare.json.
Needs Pillow and numpy. Run: python mimic_scare.py [--preview out.png]
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets", "tfmc_spiders")

W, H = 192, 108
rng = np.random.default_rng(13)

# the vanilla chest's palette: oak planks, dark seams, black iron bands, a grey latch
PLANK = [(160, 112, 58), (146, 100, 50), (125, 85, 42), (171, 122, 66)]
SEAM = (74, 48, 22)
GRAIN = (110, 74, 36)
IRON = (38, 36, 34)
IRON_HI = (70, 66, 62)
LATCH = (190, 190, 190)
LATCH_SHADE = (110, 110, 112)
BONE = (232, 222, 196)
BONE_SHADE = (178, 165, 132)
BONE_DARK = (96, 84, 62)
GUM = (120, 18, 28)
TONGUE = (150, 26, 40)
TONGUE_HI = (196, 58, 70)
EYE = (255, 40, 24)


def planks(draw, box, board=7, slant=0.0):
    """Horizontal boards with seams and grain, like the chest's sides."""
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        row = (y - y0) // board
        colour = PLANK[row % len(PLANK)]
        for x in range(x0, x1):
            c = colour
            if (y - y0) % board == board - 1:
                c = SEAM
            elif rng.random() < 0.08:
                c = GRAIN
            elif (x * 7 + row * 13) % 23 == 0:
                c = GRAIN
            draw.point((x, y), fill=c)


def tooth(draw, base_x, base_y, width, length, down, lean=0.0):
    """A jagged fang: shaded bone triangle with a dark outline."""
    tip = (base_x + lean * length, base_y + (length if down else -length))
    left, right = (base_x - width / 2, base_y), (base_x + width / 2, base_y)
    draw.polygon([left, right, tip], fill=BONE_DARK)
    inset = 1
    draw.polygon([(left[0] + inset, base_y), (right[0] - inset, base_y), (tip[0], tip[1] - (inset if down else -inset))], fill=BONE)
    # shade the far side of every fang
    draw.polygon([(base_x + lean, base_y), (right[0] - inset, base_y), (tip[0], tip[1] - (inset if down else -inset))], fill=BONE_SHADE)


def glow_eye(img, cx, cy, r, slit=True):
    """A red eye that glows, with a black slit pupil."""
    halo = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(halo)
    d.ellipse((cx - r * 3, cy - r * 3, cx + r * 3, cy + r * 3), fill=(255, 20, 10, 110))
    halo = halo.filter(ImageFilter.GaussianBlur(r * 1.2))
    img.alpha_composite(halo)
    d = ImageDraw.Draw(img)
    d.ellipse((cx - r, cy - r * 0.8, cx + r, cy + r * 0.8), fill=EYE + (255,))
    d.ellipse((cx - r * 0.55, cy - r * 0.5, cx + r * 0.25, cy), fill=(255, 190, 120, 255))
    if slit:
        d.rectangle((cx - max(0.5, r * 0.18), cy - r * 0.75, cx + max(0.5, r * 0.18), cy + r * 0.75), fill=(0, 0, 0, 255))


def draw_scare():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)

    # the throat: red-black depth, darkest in the middle
    yy, xx = np.mgrid[0:H, 0:W]
    depth = np.clip(1 - np.hypot((xx - W / 2) / (W * 0.42), (yy - H * 0.5) / (H * 0.36)), 0, 1)
    throat = np.zeros((H, W, 4), np.uint8)
    throat[..., 0] = (52 * (1 - depth) ** 1.6 + 4).astype(np.uint8)
    throat[..., 1] = (6 * (1 - depth)).astype(np.uint8)
    throat[..., 2] = (10 * (1 - depth)).astype(np.uint8)
    throat[..., 3] = 255
    img.alpha_composite(Image.fromarray(throat))
    d = ImageDraw.Draw(img)

    # gums along both jaws
    d.rectangle((0, 22, W, 30), fill=GUM)
    d.rectangle((0, 80, W, 87), fill=GUM)

    # eyes deep in the throat: two great slits and a spider's cluster around them
    for cx, cy, r in [(70, 50, 7), (122, 50, 7)]:
        glow_eye(img, cx, cy, r)
    for cx, cy, r in [(84, 40, 3), (108, 40, 3), (96, 34, 2.5), (56, 41, 2), (136, 41, 2), (90, 60, 2), (102, 60, 2)]:
        glow_eye(img, cx, cy, r, slit=r > 2.2)
    d = ImageDraw.Draw(img)

    # the lid, flung open above, and the base below: the chest's own planks and iron
    planks(d, (0, 0, W, 18))
    planks(d, (0, 92, W, H))
    for y0, y1 in [(17, 22), (87, 92)]:
        d.rectangle((0, y0, W, y1), fill=IRON)
        d.line([(0, y0), (W, y0)], fill=IRON_HI)
    for x in range(6, W, 22):
        d.rectangle((x, 19, x + 1, 20), fill=IRON_HI)
        d.rectangle((x + 8, 89, x + 9, 90), fill=IRON_HI)
    # the corner bands
    for x0 in (0, W - 6):
        d.rectangle((x0, 0, x0 + 5, 22), fill=IRON)
        d.rectangle((x0, 87, x0 + 5, H), fill=IRON)

    # tongue: a swollen tongue rolls out of the throat and hangs over the lower jaw, fangs sunk in it
    def edge(k, side):
        half = 6 + 13 * math.sin(math.pi * min(k, 0.999)) ** 0.6
        return (96 + 10 * k * k + side * half, 56 + k * 46)

    steps = [i / 40 for i in range(41)]
    outline = [edge(k, 1) for k in steps] + [edge(k, -1) for k in reversed(steps)]
    d.polygon(outline, fill=(70, 8, 16))
    d.polygon([edge(k, 0.86) for k in steps[:-1]] + [edge(k, -0.86) for k in reversed(steps[:-1])], fill=TONGUE)
    d.line([(96 + 10 * k * k, 58 + k * 40) for k in np.linspace(0, 0.9, 12)], fill=(90, 10, 20), width=2)
    for x, y in [(89, 70), (106, 76), (93, 86), (110, 90), (101, 64)]:
        d.ellipse((x - 2, y - 1, x + 2, y + 1), fill=TONGUE_HI)

    # upper fangs hang from the lid, lower fangs rise from the base; long canines at the corners
    def row(y, down, count, long_at):
        xs = np.linspace(10, W - 10, count) + rng.uniform(-3, 3, count)
        for i, x in enumerate(xs):
            length = rng.uniform(8, 15)
            width = rng.uniform(5, 8)
            if i in long_at:
                length, width = rng.uniform(24, 30), rng.uniform(9, 11)
            tooth(d, x, y, width, length, down, lean=rng.uniform(-0.15, 0.15))

    row(22, True, 15, {0, 3, 11, 14})
    row(87, False, 14, {1, 12})
    # a ragged second row behind the first
    for x in np.linspace(20, W - 20, 10):
        tooth(d, x + rng.uniform(-4, 4), 24, rng.uniform(3, 5), rng.uniform(5, 9), True)

    # the latch, hanging from the lid's rim like a nose ring
    d.rectangle((88, 14, 104, 28), fill=LATCH_SHADE)
    d.rectangle((90, 14, 102, 26), fill=LATCH)
    d.rectangle((94, 18, 98, 23), fill=(20, 20, 20))

    # strings of drool between the jaws
    drool = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(drool)
    for x in [30, 47, 64, 131, 150, 167]:
        sag = rng.uniform(-3, 3)
        dd.line([(x, 34), (x + sag, 60), (x + sag * 0.5, 78)], fill=(230, 200, 200, 110), width=1)
        dd.ellipse((x + sag - 1, 60, x + sag + 1, 63), fill=(240, 210, 210, 150))
    img.alpha_composite(drool)

    # blood-red vignette that fades out at the edges, so the flash blends into the screen
    edge = np.minimum.reduce([xx, W - 1 - xx, yy, H - 1 - yy]).astype(float)
    alpha = np.clip(edge / 9.0, 0, 1)
    pixels = np.array(img).astype(float)
    centre = np.hypot((xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2))
    tint = np.clip(centre - 0.55, 0, 1)[..., None]
    pixels[..., :3] = pixels[..., :3] * (1 - tint * 0.6) + np.array([110, 0, 0]) * tint * 0.6
    pixels[..., 3] = pixels[..., 3] * alpha
    return Image.fromarray(pixels.clip(0, 255).astype(np.uint8))


# Titles draw text at 4x scale, starting 10 units above the screen centre. A glyph is placed at
# (7 - ascent) below the text's top, so ascent = height / 2 - 3 centres it on the screen.
FRAMES = [("", 54), ("", 76), ("", 92)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="also save an upscaled preview here")
    args = parser.parse_args()
    img = draw_scare()
    textures = os.path.join(ASSETS, "textures", "font")
    os.makedirs(textures, exist_ok=True)
    img.save(os.path.join(textures, "mimic_scare.png"))
    if args.preview:
        img.resize((W * 5, H * 5), Image.NEAREST).save(args.preview)

    providers = [{"type": "bitmap", "file": "tfmc_spiders:font/mimic_scare.png", "height": h, "ascent": h // 2 - 3, "chars": [c]}
                 for c, h in FRAMES]
    fonts = os.path.join(ASSETS, "font")
    os.makedirs(fonts, exist_ok=True)
    with open(os.path.join(fonts, "mimic_scare.json"), "w", newline="\n") as f:
        json.dump({"providers": providers}, f, indent=2)
        f.write("\n")
    print("wrote mimic_scare.png and mimic_scare.json")


if __name__ == "__main__":
    main()
