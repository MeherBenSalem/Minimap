"""Generate the Remnants Minimap logo.

A gilded minimap medallion: a small parchment chart (coast, forest, contour lines,
engraved water) inside a gold ring, with the gold player arrow over a Site of Grace glow.

Usage: python tools/generate_icon.py
Writes:
  docs/icon.png                                                   512x512 (Modrinth / CurseForge logo)
  26.2/common/src/main/resources/assets/remnantminimaps/icon.png    256x256 (in-mod logo)
  26.1.2/common/src/main/resources/assets/remnantminimaps/icon.png  256x256
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SS = 4                 # supersampling factor
SIZE = 512
N = SIZE * SS
C = N / 2


def hexrgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


# ---------------------------------------------------------------- noise
def value_noise(shape, scale, seed):
    rng = np.random.default_rng(seed)
    gh, gw = int(shape[0] / scale) + 3, int(shape[1] / scale) + 3
    grid = rng.random((gh, gw)).astype(np.float32)
    y = np.arange(shape[0], dtype=np.float32) / scale
    x = np.arange(shape[1], dtype=np.float32) / scale
    yi, xi = y.astype(int), x.astype(int)
    yf, xf = y - yi, x - xi
    yf = yf * yf * (3 - 2 * yf)
    xf = xf * xf * (3 - 2 * xf)
    a = grid[yi][:, xi]
    b = grid[yi][:, xi + 1]
    c = grid[yi + 1][:, xi]
    d = grid[yi + 1][:, xi + 1]
    top = a + (b - a) * xf[None, :]
    bot = c + (d - c) * xf[None, :]
    return top + (bot - top) * yf[:, None]


def fbm(shape, scale, seed, octaves=5):
    out = np.zeros(shape, np.float32)
    amp, norm = 0.5, 0.0
    for i in range(octaves):
        out += value_noise(shape, scale / (2 ** i), seed + i * 97) * amp
        norm += amp
        amp *= 0.5
    return out / norm


# ---------------------------------------------------------------- the little chart
def make_chart(res: int) -> np.ndarray:
    """Parchment map, RGB float array (res x res)."""
    shape = (res, res)
    h = fbm(shape, res / 1.8, 11, 4)
    # bias land toward the lower-left so a sea and coast cross the medallion
    yy, xx = np.mgrid[0:res, 0:res].astype(np.float32) / res
    h = h + (0.55 - xx) * 0.35 + (yy - 0.45) * 0.25
    forest = fbm(shape, res / 4.0, 23, 4)
    sea = 0.47

    land = hexrgb("#b4ad84") * np.ones(shape + (3,), np.float32)
    olive = hexrgb("#727548")
    fmask = np.clip((forest - 0.50) * 9, 0, 1)[..., None]
    land = land * (1 - fmask) + olive * fmask
    high = np.clip((h - 0.86) * 8, 0, 1)[..., None]
    land = land * (1 - high) + hexrgb("#d8d2bd") * high

    # relief shading from the north-west
    gy, gx = np.gradient(h * res * 0.5)
    shade = np.clip((gx + gy) * 0.9, -0.5, 0.45)[..., None]
    land = np.where(shade > 0, land + (hexrgb("#f4ecd0") - land) * shade, land * (1 + shade * 0.8))

    # contour lines
    bands = np.floor(h * 16)
    edge = (np.abs(np.diff(bands, axis=0, prepend=bands[:1])) + np.abs(np.diff(bands, axis=1, prepend=bands[:, :1]))) > 0
    ink = hexrgb("#33271b")
    land = np.where(edge[..., None], land * 0.72 + ink * 0.28, land)

    depth = np.clip((sea - h) / 0.18, 0, 1)[..., None]
    water = hexrgb("#93a6a6") * (1 - depth) + hexrgb("#53646b") * depth
    waves = (np.sin((yy * res) * 0.55 + np.sin(xx * 9) * 3) > 0.86) & (depth[..., 0] > 0.25)
    water = np.where(waves[..., None], water + 0.07, water)

    is_land = (h >= sea)[..., None]
    img = np.where(is_land, land, water)

    # ink coastline + pale shallows
    lm = is_land[..., 0]
    coast = lm & ~(np.roll(lm, 1, 0) & np.roll(lm, -1, 0) & np.roll(lm, 1, 1) & np.roll(lm, -1, 1))
    img = np.where(coast[..., None], img * 0.4 + ink * 0.6, img)

    grain = (np.random.default_rng(5).random(shape).astype(np.float32) - 0.5) * 0.05
    mottle = (fbm(shape, res / 5, 41, 3) - 0.5) * 0.12
    img = img * (1 + grain + mottle)[..., None]
    return np.clip(img, 0, 1)


# ---------------------------------------------------------------- drawing helpers
def radial(n=N):
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    return np.hypot(xx - n / 2 + 0.5, yy - n / 2 + 0.5), np.arctan2(yy - n / 2, xx - n / 2)


def to_img(rgb, alpha):
    arr = np.dstack([np.clip(rgb, 0, 1), np.clip(alpha, 0, 1)])
    return Image.fromarray((arr * 255).astype(np.uint8), "RGBA")


def gold(t):
    """Metallic gold ramp for t in [0, 1] (0 = shadow, 1 = highlight)."""
    stops = [hexrgb("#5a4219"), hexrgb("#8a6b2e"), hexrgb("#c9a961"), hexrgb("#f2dfa0"), hexrgb("#fff6d8")]
    t = np.clip(t, 0, 1) * (len(stops) - 1)
    i = np.clip(t.astype(int), 0, len(stops) - 2)
    f = (t - i)[..., None]
    s = np.stack(stops)
    return s[i] * (1 - f) + s[i + 1] * f


def build() -> Image.Image:
    r, ang = radial()
    R = N * 0.47              # outer radius of the medallion
    ring_in = R * 0.80        # map disc radius
    canvas = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    # drop shadow
    shadow = to_img(np.zeros((N, N, 3)), np.clip((R * 1.03 - r) / (R * 0.06), 0, 1) * 0.55)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(N * 0.012)), (int(N * 0.004), int(N * 0.012)))

    # chart
    res = int(ring_in * 2)
    chart = make_chart(res)
    chart_img = Image.fromarray((chart * 255).astype(np.uint8), "RGB").resize((res, res), Image.LANCZOS)
    disc = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    disc.paste(chart_img, (int(C - ring_in), int(C - ring_in)))
    mask = np.clip(ring_in + 1 - r, 0, 1)
    da = np.array(disc).astype(np.float32) / 255
    # vignette inside the disc
    vig = np.clip((r - ring_in * 0.55) / (ring_in * 0.45), 0, 1) ** 1.6 * 0.55
    da[..., :3] *= (1 - vig)[..., None]
    da[..., 3] = mask
    canvas.alpha_composite(to_img(da[..., :3], da[..., 3]))

    # grace glow and light rays over the chart
    glow_r = ring_in * 0.62
    g = np.exp(-(r / glow_r) ** 2 * 3.2) * 0.85
    rays = (0.5 + 0.5 * np.cos(ang * 12)) ** 6 * np.exp(-(r / (ring_in * 0.9)) ** 2 * 2.5) * 0.35
    light = np.clip(g + rays, 0, 1) * mask
    canvas.alpha_composite(to_img(np.ones((N, N, 3)) * hexrgb("#ffdf8a"), light))

    # gold ring with bevels
    band = (r >= ring_in) & (r <= R)
    t = (r - ring_in) / (R - ring_in)
    bevel = 0.42 + 0.22 * np.cos(t * math.pi * 2) - 0.22 * np.sin(ang + math.pi / 4)
    ring_rgb = gold(bevel)
    ring_a = np.clip(np.minimum(r - ring_in + 1, R + 1 - r), 0, 1)
    # inner dark groove and engraved inner band
    groove = (np.abs(r - (ring_in + (R - ring_in) * 0.32)) < N * 0.0035)
    ring_rgb = np.where(groove[..., None], ring_rgb * 0.45, ring_rgb)
    lip_in = np.abs(r - ring_in) < N * 0.004
    lip_out = np.abs(r - R) < N * 0.004
    ring_rgb = np.where(lip_in[..., None] | lip_out[..., None], gold(np.full_like(r, 0.95)), ring_rgb)
    canvas.alpha_composite(to_img(ring_rgb, ring_a * band))

    d = ImageDraw.Draw(canvas)
    mid = (ring_in + R) / 2 + (R - ring_in) * 0.12
    # tick marks
    for i in range(16):
        a = i * math.pi / 8 - math.pi / 2
        major = i % 4 == 0
        l0 = mid - (R - ring_in) * (0.22 if major else 0.12)
        l1 = mid + (R - ring_in) * (0.22 if major else 0.12)
        w = int(N * (0.010 if major else 0.006))
        p0 = (C + math.cos(a) * l0, C + math.sin(a) * l0)
        p1 = (C + math.cos(a) * l1, C + math.sin(a) * l1)
        d.line([p0, p1], fill=(48, 34, 14, 255), width=w + int(N * 0.004))
        d.line([p0, p1], fill=(255, 238, 190, 255), width=w)
    # north diamond
    top = C - R * 1.005
    dw, dh = N * 0.045, N * 0.075
    diamond = [(C, top - dh * 0.45), (C + dw, top + dh * 0.25), (C, top + dh * 0.9), (C - dw, top + dh * 0.25)]
    d.polygon(diamond, fill=(40, 28, 10, 255))
    inner = [(C, top - dh * 0.3), (C + dw * 0.72, top + dh * 0.25), (C, top + dh * 0.75), (C - dw * 0.72, top + dh * 0.25)]
    d.polygon(inner, fill=(245, 222, 150, 255))
    d.polygon([(C, top - dh * 0.3), (C + dw * 0.72, top + dh * 0.25), (C, top + dh * 0.25)], fill=(255, 248, 220, 255))

    # player arrow (chevron) pointing north
    s = ring_in * 0.62
    cx, cy = C, C + s * 0.08
    outer = [(cx, cy - s * 0.62), (cx + s * 0.40, cy + s * 0.48), (cx, cy + s * 0.24), (cx - s * 0.40, cy + s * 0.48)]
    d.polygon(outer, fill=(30, 22, 10, 255))
    o2 = [(cx, cy - s * 0.52), (cx + s * 0.32, cy + s * 0.38), (cx, cy + s * 0.17), (cx - s * 0.32, cy + s * 0.38)]
    d.polygon(o2, fill=(201, 169, 97, 255))
    d.polygon([(cx, cy - s * 0.52), (cx - s * 0.32, cy + s * 0.38), (cx, cy + s * 0.17)], fill=(250, 232, 170, 255))
    d.line([(cx, cy - s * 0.50), (cx, cy + s * 0.15)], fill=(120, 88, 34, 255), width=int(N * 0.004))

    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    icon = build()
    out = ROOT / "docs" / "icon.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(out)
    small = icon.resize((256, 256), Image.LANCZOS)
    for version in ("26.2", "26.1.2"):
        p = ROOT / version / "common/src/main/resources/assets/remnantminimaps/icon.png"
        if p.parent.exists():
            small.save(p)
            print("wrote", p.relative_to(ROOT))
    print("wrote", out.relative_to(ROOT))


if __name__ == "__main__":
    main()
