#!/usr/bin/env python3
"""
Procedural texture generator for the Remnants Minimap mod (mod id ``remnantminimaps``).

Every texture is drawn procedurally (signed distance fields, FFT noise, analytic
gradients), rendered at 4x supersampling and reduced with a Lanczos filter so all
edges are anti-aliased.  Output is fully deterministic (fixed RNG seeds).

Usage::

    python tools/generate_assets.py [OUTPUT_DIR] [--preview PATH] [--no-preview]
                                    [--only name1,name2,...]

OUTPUT_DIR defaults to
``26.2/common/src/main/resources/assets/remnantminimaps/textures/gui`` inside the
repository.  The mod logo is written to ``OUTPUT_DIR/../../icon.png``.

Requirements: Python 3.12, Pillow, numpy.  Windows serif fonts are used for the
few letters (compass, question mark) when present, with graceful fallbacks.

All PNGs are RGBA with straight (non-premultiplied) alpha; fully transparent
pixels have RGB 0.
"""
from __future__ import annotations

import argparse
import functools
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

SS = 4                      # supersampling factor
BIG = 1.0e4                 # "far away" distance value
REPO = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO / "26.2" / "common" / "src" / "main" / "resources" / "assets" / "remnantminimaps" / "textures" / "gui"
DEFAULT_PREVIEW = Path(__file__).resolve().parent / "asset_preview.png"
FONT_DIRS = [Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/msttcorefonts"),
             Path("/Library/Fonts"), Path("/System/Library/Fonts/Supplemental")]
SERIF_BOLD = ["georgiab.ttf", "timesbd.ttf", "Georgia Bold.ttf", "Times New Roman Bold.ttf", "DejaVuSerif-Bold.ttf"]
SERIF = ["georgia.ttf", "times.ttf", "Georgia.ttf", "Times New Roman.ttf", "DejaVuSerif.ttf"]


# =============================================================================
# Colour helpers
# =============================================================================
def hexc(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


GOLD = hexc("C9A961")
GOLD_HI = hexc("F2DFA0")
GOLD_DEEP = hexc("8A6B2E")
BRONZE = hexc("3A2E1C")
NEAR_BLACK = hexc("15120D")
PARCH = hexc("CDBE95")
PARCH_D = hexc("B9A77A")
IVORY = hexc("EDE3CC")
INK = hexc("2B2116")
SLATE = hexc("6E7F86")
GRACE = hexc("FFD77A")
CRIMSON = hexc("8E1F1A")
WHITE = np.ones(3, np.float32)
SHADOW = hexc("0B0906")

GOLD_RAMP = [(0.00, "3A2A10"), (0.20, "6E5222"), (0.38, "8A6B2E"), (0.55, "C9A961"),
             (0.72, "E2C985"), (0.86, "F2DFA0"), (1.00, "FFF8E2")]
SILVER_RAMP = [(0.00, "3C3D40"), (0.25, "6F7074"), (0.45, "A3A19B"), (0.65, "CFCBC1"),
               (0.82, "E8E4DA"), (1.00, "FFFFFF")]
STEEL_BLUE_RAMP = [(0.00, "1C2738"), (0.20, "34465E"), (0.38, "5B7592"), (0.55, "8FA9C4"),
                   (0.72, "BCD0E3"), (0.86, "DCE8F3"), (1.00, "F8FCFF")]
BRONZE_RAMP = [(0.00, "0E0B07"), (0.25, "1E1810"), (0.50, "2E2416"), (0.70, "3A2E1C"),
               (0.85, "4E3E26"), (1.00, "6A5532")]
IVORY_RAMP = [(0.00, "3E3A34"), (0.30, "77726A"), (0.55, "ABA59A"), (0.72, "CFC9BD"),
              (0.85, "E8E3D8"), (0.94, "F6F3EC"), (1.00, "FFFEFA")]
CRIMSON_RAMP = [(0.00, "2A0605"), (0.30, "5A110D"), (0.55, "8E1F1A"), (0.75, "B0362B"),
                (0.90, "CF5F4C"), (1.00, "EE9A84")]

_RAMP_CACHE: dict[tuple, tuple[np.ndarray, np.ndarray]] = {}


def ramp(stops, t) -> np.ndarray:
    key = tuple(tuple(s) for s in stops)
    if key not in _RAMP_CACHE:
        _RAMP_CACHE[key] = (np.array([s[0] for s in stops], np.float64),
                            np.stack([hexc(s[1]) for s in stops]))
    pos, cols = _RAMP_CACHE[key]
    t = np.asarray(t, np.float32)
    out = np.empty(t.shape + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(t, pos, cols[:, c])
    return out


def mix(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim:
        t = t[..., None]
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


def smoothstep(e0, e1, x):
    t = np.clip((np.asarray(x, np.float32) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


# =============================================================================
# Canvas (premultiplied float RGBA rendered at SS x resolution)
# =============================================================================
class Canvas:
    """Coordinates (X, Y) are in *design units*.  By default one design unit is one
    output pixel; ``scale`` < 1 shrinks the design uniformly about ``origin``
    (used to give marker icons a safe margin inside their padded cells)."""

    def __init__(self, w: int, h: int, ss: int = SS, scale: float = 1.0, origin=None):
        self.w, self.h, self.ss = w, h, ss
        self.W, self.H = w * ss, h * ss
        self.scale = scale
        self.ox, self.oy = origin if origin is not None else (w / 2.0, h / 2.0)
        self.k = ss * scale                     # supersample pixels per design unit
        self.px = 1.0 / self.k                  # design units per supersample pixel
        xs = (np.arange(self.W, dtype=np.float32) + 0.5) / ss
        ys = (np.arange(self.H, dtype=np.float32) + 0.5) / ss
        xs = self.ox + (xs - self.ox) / scale
        ys = self.oy + (ys - self.oy) / scale
        self.X, self.Y = np.meshgrid(xs.astype(np.float32), ys.astype(np.float32))
        self.rgb = np.zeros((self.H, self.W, 3), np.float32)
        self.a = np.zeros((self.H, self.W), np.float32)

    # -- geometry helpers -----------------------------------------------------
    def to_pix(self, x, y):
        return self.ox + (x - self.ox) * self.scale, self.oy + (y - self.oy) * self.scale

    def win(self, x0, y0, x1, y1):
        x0, y0 = self.to_pix(x0, y0)
        x1, y1 = self.to_pix(x1, y1)
        i0 = max(0, int(math.floor(x0 * self.ss)))
        i1 = min(self.W, int(math.ceil(x1 * self.ss)))
        j0 = max(0, int(math.floor(y0 * self.ss)))
        j1 = min(self.H, int(math.ceil(y1 * self.ss)))
        if i1 <= i0 or j1 <= j0:
            return None
        sl = (slice(j0, j1), slice(i0, i1))
        return sl, self.X[sl], self.Y[sl]

    def cov(self, d, soft: float = 0.0):
        """Coverage from a signed distance (negative inside)."""
        w = self.px + soft
        return np.clip(0.5 - d / w, 0.0, 1.0).astype(np.float32)

    def blur(self, arr, sigma, wrap=False):
        return gauss_blur(arr, sigma * self.k, wrap)

    # -- compositing ----------------------------------------------------------
    def paint(self, color, alpha, sl=None):
        alpha = np.clip(np.asarray(alpha, np.float32), 0.0, 1.0)
        color = np.asarray(color, np.float32)
        if color.ndim == 1:
            color = color.reshape(1, 1, 3)
        rgb = self.rgb if sl is None else self.rgb[sl]
        a = self.a if sl is None else self.a[sl]
        ia = 1.0 - alpha
        rgb *= ia[..., None]
        rgb += color * alpha[..., None]
        a *= ia
        a += alpha

    def paint_under(self, color, alpha, sl=None):
        alpha = np.clip(np.asarray(alpha, np.float32), 0.0, 1.0)
        color = np.asarray(color, np.float32)
        if color.ndim == 1:
            color = color.reshape(1, 1, 3)
        rgb = self.rgb if sl is None else self.rgb[sl]
        a = self.a if sl is None else self.a[sl]
        k = alpha * (1.0 - a)
        rgb += color * k[..., None]
        a += k

    def multiply_alpha(self, m):
        m = np.clip(np.asarray(m, np.float32), 0, 1)
        self.rgb *= m[..., None]
        self.a *= m

    def result(self) -> np.ndarray:
        """Downscale (Lanczos, premultiplied) and return straight-alpha uint8 RGBA."""
        chans = [self.rgb[..., 0], self.rgb[..., 1], self.rgb[..., 2], self.a]
        if self.ss == 1:
            out = np.stack(chans, -1)
        else:
            out = np.stack([lanczos_down(c, self.w, self.h) for c in chans], -1)
        return finalize_premult(out)


def lanczos_down(c: np.ndarray, w: int, h: int) -> np.ndarray:
    im = Image.fromarray(np.ascontiguousarray(c, dtype=np.float32))
    return np.asarray(im.resize((w, h), Image.LANCZOS), np.float32)


def lanczos_down_wrap(c: np.ndarray, w: int, h: int, ss: int) -> np.ndarray:
    """Lanczos downscale of a periodic image (keeps it seamlessly tileable)."""
    pad = 4 * ss
    p = np.pad(c, pad, mode="wrap")
    r = lanczos_down(p, w + 8, h + 8)
    return r[4:4 + h, 4:4 + w]


def finalize_premult(out: np.ndarray) -> np.ndarray:
    a = np.clip(out[..., 3], 0.0, 1.0)
    rgb = np.clip(out[..., :3], 0.0, None)
    safe = np.maximum(a, 1e-6)[..., None]
    col = np.where(a[..., None] > 1e-6, rgb / safe, 0.0)
    col = np.clip(col, 0.0, 1.0)
    a8 = np.round(a * 255.0).astype(np.uint8)
    rgb8 = np.round(col * 255.0).astype(np.uint8)
    rgb8[a8 == 0] = 0
    return np.dstack([rgb8, a8])


def straight_to_png(arr: np.ndarray, path: Path):
    arr = arr.copy()
    arr[arr[..., 3] == 0, :3] = 0
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(path, optimize=True)


# =============================================================================
# Blur and noise
# =============================================================================
def gauss_blur(a: np.ndarray, sigma: float, wrap: bool = False) -> np.ndarray:
    """Gaussian blur via FFT. sigma in array pixels."""
    if sigma <= 0:
        return a.astype(np.float32)
    pad = 0 if wrap else int(math.ceil(3.5 * sigma))
    b = np.pad(a, pad, mode="constant") if pad else a
    H, W = b.shape
    fy = np.fft.fftfreq(H)[:, None]
    fx = np.fft.rfftfreq(W)[None, :]
    G = np.exp(-2.0 * (math.pi ** 2) * (sigma ** 2) * (fx ** 2 + fy ** 2))
    r = np.fft.irfft2(np.fft.rfft2(b) * G, s=(H, W))
    if pad:
        r = r[pad:pad + a.shape[0], pad:pad + a.shape[1]]
    return r.astype(np.float32)


def fft_noise(h: int, w: int, beta: float, seed: int, cutoff: float | None = None,
              lowcut: float | None = None) -> np.ndarray:
    """Periodic (tileable) 1/f^beta noise, zero mean, unit variance.

    cutoff/lowcut are in cycles per *pixel* of the returned array.
    """
    rng = np.random.default_rng(seed)
    white = rng.standard_normal((h, w))
    F = np.fft.rfft2(white)
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.rfftfreq(w)[None, :]
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1.0
    S = 1.0 / f ** beta
    if cutoff is not None:
        S = S * np.exp(-(f / cutoff) ** 2)
    if lowcut is not None:
        S = S * (1.0 - np.exp(-(f / lowcut) ** 2))
    S[0, 0] = 0.0
    n = np.fft.irfft2(F * S, s=(h, w))
    n = (n - n.mean()) / (n.std() + 1e-12)
    return n.astype(np.float32)


def sample_wrap(field: np.ndarray, x: np.ndarray, y: np.ndarray) -> np.ndarray:
    """Bilinear sample of a periodic field at fractional array coordinates."""
    H, W = field.shape
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    fx = (x - x0).astype(np.float32)
    fy = (y - y0).astype(np.float32)
    x0 %= W
    y0 %= H
    x1 = (x0 + 1) % W
    y1 = (y0 + 1) % H
    a = field[y0, x0] * (1 - fx) + field[y0, x1] * fx
    b = field[y1, x0] * (1 - fx) + field[y1, x1] * fx
    return a * (1 - fy) + b * fy


# =============================================================================
# Signed distance functions (all distances in output-pixel units)
# =============================================================================
def sd_circle(X, Y, cx, cy, r):
    return np.hypot(X - cx, Y - cy) - r


def sd_ellipse(X, Y, cx, cy, rx, ry):
    """Approximate SDF of an axis-aligned ellipse (iq's k0*(k0-1)/k1)."""
    px, py = (X - cx), (Y - cy)
    k0 = np.hypot(px / rx, py / ry)
    k1 = np.hypot(px / (rx * rx), py / (ry * ry))
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-6)


def sd_box(X, Y, cx, cy, hw, hh, r=0.0):
    qx = np.abs(X - cx) - hw + r
    qy = np.abs(Y - cy) - hh + r
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def sd_rect(X, Y, x0, y0, x1, y1, r=0.0):
    return sd_box(X, Y, (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2, r)


def sd_segment(X, Y, ax, ay, bx, by, r=0.0):
    pax, pay = X - ax, Y - ay
    bax, bay = bx - ax, by - ay
    h = np.clip((pax * bax + pay * bay) / (bax * bax + bay * bay + 1e-12), 0.0, 1.0)
    return np.hypot(pax - bax * h, pay - bay * h) - r


def sd_polygon(X, Y, pts):
    pts = np.asarray(pts, np.float64)
    n = len(pts)
    d = (X - pts[0, 0]) ** 2 + (Y - pts[0, 1]) ** 2
    s = np.ones_like(X)
    j = n - 1
    for i in range(n):
        vix, viy = pts[i]
        vjx, vjy = pts[j]
        ex, ey = vjx - vix, vjy - viy
        wx, wy = X - vix, Y - viy
        t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey + 1e-12), 0.0, 1.0)
        bx, by = wx - ex * t, wy - ey * t
        d = np.minimum(d, bx * bx + by * by)
        c1 = Y >= viy
        c2 = Y < vjy
        c3 = ex * wy > ey * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
        j = i
    return (s * np.sqrt(d)).astype(np.float32)


def sd_arch(X, Y, x0, x1, y_spring, y_bottom):
    """Round-topped arch (door/window): semicircle on top of a rectangle."""
    cx = (x0 + x1) / 2
    r = (x1 - x0) / 2
    return np.minimum(sd_circle(X, Y, cx, y_spring, r), sd_rect(X, Y, x0, y_spring, x1, y_bottom))


def sd_gothic(X, Y, x0, x1, y_spring, y_bottom, radius=None):
    """Pointed (gothic) arch."""
    span = x1 - x0
    R = span if radius is None else radius
    lens = np.maximum(sd_circle(X, Y, x0 + R if radius else x0, y_spring, R),
                      sd_circle(X, Y, x1 - R if radius else x1, y_spring, R))
    lens = np.maximum(lens, Y - y_spring)
    return np.minimum(lens, sd_rect(X, Y, x0, y_spring - 0.01, x1, y_bottom))


def U(*ds):
    return functools.reduce(np.minimum, ds)


def INT(*ds):
    return functools.reduce(np.maximum, ds)


def SUB(a, b):
    return np.maximum(a, -b)


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b * (1 - h) + a * h - k * h * (1 - h)


def rot(px, py, cx, cy, ang_deg):
    a = math.radians(ang_deg)
    ca, sa = math.cos(a), math.sin(a)
    dx, dy = px - cx, py - cy
    return cx + dx * ca - dy * sa, cy + dx * sa + dy * ca


def rot_pts(pts, cx, cy, ang):
    return [rot(x, y, cx, cy, ang) for x, y in pts]


def teardrop(X, Y, cx, cy, r, tx, ty):
    dx, dy = tx - cx, ty - cy
    D = math.hypot(dx, dy)
    base = math.atan2(dy, dx)
    beta = math.acos(r / D)
    p1 = (cx + r * math.cos(base + beta), cy + r * math.sin(base + beta))
    p2 = (cx + r * math.cos(base - beta), cy + r * math.sin(base - beta))
    return np.minimum(sd_circle(X, Y, cx, cy, r), sd_polygon(X, Y, [(tx, ty), p1, (cx, cy), p2]))


# ---- curves -----------------------------------------------------------------
def qbez(p0, p1, p2, n=24):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2 = (np.asarray(p, np.float64) for p in (p0, p1, p2))
    return (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2


def cbez(p0, p1, p2, p3, n=32):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = (np.asarray(p, np.float64) for p in (p0, p1, p2, p3))
    return ((1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3)


def join(*parts):
    out = [np.asarray(parts[0])]
    for p in parts[1:]:
        out.append(np.asarray(p)[1:])
    return np.concatenate(out, 0)


def arc_pts(cx, cy, r, a0, a1, n=48):
    """Points along a circular arc. Angles in degrees, screen space (0=east, 90=south)."""
    a = np.radians(np.linspace(a0, a1, n))
    return np.stack([cx + r * np.cos(a), cy + r * np.sin(a)], 1)


def spiral_pts(cx, cy, r0, r1, a0, a1, n=80, power=1.0):
    """Spiral from (r0 at angle a0) to (r1 at angle a1), degrees, screen space."""
    t = np.linspace(0, 1, n)
    a = np.radians(a0 + (a1 - a0) * t)
    r = r0 + (r1 - r0) * t ** power
    return np.stack([cx + r * np.cos(a), cy + r * np.sin(a)], 1)


def curl_path(x0, y0, heading_deg, straight, R, hand, turns=1.0, step=0.35):
    """A stem that runs ``straight`` units and then rolls up into a spiral curl.

    hand=+1 curls clockwise on screen, -1 counter-clockwise.  Returns (pts, n_stem)."""
    h = math.radians(heading_deg)
    x, y = x0, y0
    pts = [(x, y)]
    n1 = max(1, int(straight / step))
    for i in range(n1):
        # ease the curvature in over the last third of the stem (clothoid-like)
        s = i / n1
        k = hand * smoothstep(0.65, 1.0, s) / (R * 1.6)
        h += k * step
        x += step * math.cos(h)
        y += step * math.sin(h)
        pts.append((x, y))
    total = turns * 2.0 * math.pi
    th = 0.0
    while th < total:
        r = R * (1.0 - 0.72 * th / total)
        dth = step / r
        h += hand * dth
        th += dth
        x += step * math.cos(h)
        y += step * math.sin(h)
        pts.append((x, y))
    return np.array(pts), n1


def filigree_widths(n, n_stem, w_max, w_end):
    """Half-widths for a curl_path: swell along the stem, taper into the curl."""
    t = np.arange(n, dtype=np.float64)
    w = np.empty(n)
    a = t[:n_stem] / max(n_stem, 1)
    w[:n_stem] = w_max * (0.55 + 0.45 * np.sin(np.pi * np.clip(a * 0.9 + 0.1, 0, 1)) ** 0.6)
    b = (t[n_stem:] - n_stem) / max(n - n_stem, 1)
    w[n_stem:] = w[n_stem - 1] + (w_end - w[n_stem - 1]) * b ** 0.8 if n_stem > 0 else w_end
    return w


def taper(n, w0, w1, wmid=None, ends=None):
    t = np.linspace(0, 1, n)
    if wmid is None:
        w = w0 + (w1 - w0) * t
    else:
        w = np.where(t < 0.5, w0 + (wmid - w0) * smoothstep(0, 0.5, t),
                     wmid + (w1 - wmid) * smoothstep(0.5, 1.0, t))
    return w


def sd_stroke(cv: Canvas, pts, widths, out=None, margin=4.0):
    """Distance field of a polyline with per-vertex radius (half-width).

    Evaluated only in small windows around each segment (fast on big canvases);
    everything else stays at BIG (or the incoming ``out``)."""
    pts = np.asarray(pts, np.float64)
    widths = np.broadcast_to(np.asarray(widths, np.float64), (len(pts),))
    if out is None:
        out = np.full((cv.H, cv.W), BIG, np.float32)
    for k in range(len(pts) - 1):
        ax, ay = pts[k]
        bx, by = pts[k + 1]
        ra, rb = widths[k], widths[k + 1]
        m = max(ra, rb) + margin
        w = cv.win(min(ax, bx) - m, min(ay, by) - m, max(ax, bx) + m, max(ay, by) + m)
        if w is None:
            continue
        sl, Xw, Yw = w
        pax, pay = Xw - ax, Yw - ay
        bax, bay = bx - ax, by - ay
        h = np.clip((pax * bax + pay * bay) / (bax * bax + bay * bay + 1e-12), 0.0, 1.0)
        d = np.hypot(pax - bax * h, pay - bay * h) - (ra + (rb - ra) * h)
        np.minimum(out[sl], d, out=out[sl])
    return out


def field(cv: Canvas, bbox, fn, fill=BIG):
    """Evaluate fn(X, Y) only inside bbox; BIG elsewhere."""
    out = np.full((cv.H, cv.W), fill, np.float32)
    w = cv.win(*bbox)
    if w is not None:
        sl, Xw, Yw = w
        out[sl] = fn(Xw, Yw)
    return out


# =============================================================================
# Lighting / materials
# =============================================================================
LIGHT = np.array([-0.55, -0.835], np.float32)     # direction *towards* the light (top-left)


def bevel_light(d, width, ss, light=LIGHT):
    """Lambert-like term (-1..1) of a rounded bevel of given width along an SDF edge."""
    h = smoothstep(0.0, width, -d)
    gy, gx = np.gradient(h)
    k = ss * width / 1.5
    return np.clip(-(gx * light[0] + gy * light[1]) * k, -1.0, 1.0).astype(np.float32)


def height_light(h, ss, scale=1.0, light=LIGHT):
    gy, gx = np.gradient(h)
    return np.clip(-(gx * light[0] + gy * light[1]) * ss * scale, -1.0, 1.0).astype(np.float32)


def diag_grad(X, Y, x0, y0, x1, y1):
    """0 at the lit (top-left) corner of the bbox, 1 at the bottom-right."""
    u = (X - x0) / max(x1 - x0, 1e-6) * 0.42 + (Y - y0) / max(y1 - y0, 1e-6) * 0.58
    return np.clip(u, 0.0, 1.0).astype(np.float32)


def metal(cv: Canvas, d, ramp_stops=GOLD_RAMP, bevel=1.5, bbox=None, base=0.60, kb=0.30,
          kp=0.30, X=None, Y=None, extra=0.0):
    X = cv.X if X is None else X
    Y = cv.Y if Y is None else Y
    lam = bevel_light(d, bevel, cv.k)
    g = 0.5 if bbox is None else diag_grad(X, Y, *bbox)
    t = base + kb * lam + kp * (0.5 - g) + extra
    return ramp(ramp_stops, t)


def facet_light(nx, ny, light=LIGHT):
    n = np.sqrt(nx * nx + ny * ny) + 1e-6
    return (nx * light[0] + ny * light[1]) / n


def kite(X, Y, cx, cy, ang, a, b, w, m=0.0):
    """Lozenge/kite along direction ``ang`` (deg, 0 = up/north, clockwise).

    Front tip at +a along the axis, back tip at -b, half-width w at axis offset m.
    Returns (sdf, facet_nx, facet_ny) where the facet normal is the in-plane
    direction the facet under that pixel faces (4 facets)."""
    ar = math.radians(ang)
    ux, uy = math.sin(ar), -math.cos(ar)
    vx, vy = math.cos(ar), math.sin(ar)
    pts = [(cx + a * ux, cy + a * uy), (cx + m * ux + w * vx, cy + m * uy + w * vy),
           (cx - b * ux, cy - b * uy), (cx + m * ux - w * vx, cy + m * uy - w * vy)]
    d = sd_polygon(X, Y, pts)
    lu = (X - cx) * ux + (Y - cy) * uy
    lv = (X - cx) * vx + (Y - cy) * vy
    su = np.where(lu >= m, 1.0, -1.0).astype(np.float32)
    sv = np.where(lv >= 0, 1.0, -1.0).astype(np.float32)
    L = np.where(su > 0, a - m, b + m)
    # facet outward normal (in-plane), in (u, v) coords: (su*w, sv*L)
    nu = su * w
    nv = sv * L
    nx = nu * ux + nv * vx
    ny = nu * uy + nv * vy
    return d, nx.astype(np.float32), ny.astype(np.float32)


def spike(X, Y, cx, cy, ang, L, w, r0=0.0, back=0.0):
    """Two-facet star point from the centre outwards. Returns (sdf, side_nx, side_ny)."""
    ar = math.radians(ang)
    ux, uy = math.sin(ar), -math.cos(ar)
    vx, vy = math.cos(ar), math.sin(ar)
    pts = [(cx + L * ux, cy + L * uy), (cx + r0 * ux + w * vx, cy + r0 * uy + w * vy),
           (cx - back * ux, cy - back * uy), (cx + r0 * ux - w * vx, cy + r0 * uy - w * vy)]
    d = sd_polygon(X, Y, pts)
    lv = (X - cx) * vx + (Y - cy) * vy
    sv = np.where(lv >= 0, 1.0, -1.0).astype(np.float32)
    return d, (sv * vx).astype(np.float32), (sv * vy).astype(np.float32)


def union_facets(items):
    """items: iterable of (d, nx, ny). Returns combined (d, nx, ny) choosing the nearest."""
    d = nx = ny = None
    for di, nxi, nyi in items:
        if d is None:
            d, nx, ny = di.copy(), nxi.copy(), nyi.copy()
        else:
            m = di < d
            d = np.where(m, di, d)
            nx = np.where(m, nxi, nx)
            ny = np.where(m, nyi, ny)
    return d, nx, ny


# =============================================================================
# Fonts / text
# =============================================================================
def find_font(names):
    for n in names:
        for d in FONT_DIRS:
            p = d / n
            if p.exists():
                return str(p)
    return None


def load_font(names, size):
    path = find_font(names)
    if path:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def text_mask(cv: Canvas, text, cx, cy, size_px, fonts=SERIF_BOLD, stroke=0.0):
    """Coverage mask of text whose ink bounding box is centred on (cx, cy)."""
    font = load_font(fonts, max(4, int(round(size_px * cv.k))))
    sw = int(round(stroke * cv.k))
    x0, y0, x1, y1 = font.getbbox(text, anchor="ls")
    im = Image.new("L", (cv.W, cv.H), 0)
    dr = ImageDraw.Draw(im)
    pcx, pcy = cv.to_pix(cx, cy)
    px = pcx * cv.ss - (x0 + x1) / 2.0
    py = pcy * cv.ss - (y0 + y1) / 2.0
    dr.text((px, py), text, fill=255, font=font, anchor="ls", stroke_width=sw, stroke_fill=255)
    return np.asarray(im, np.float32) / 255.0


# =============================================================================
# Marker icons (64 x 64 cells)
# =============================================================================
class IconPainter:
    """Paints tintable icons: near-white ivory fill, soft engraved shading, dark rim."""

    def __init__(self, size=64, scale=0.94):
        # icons are designed on a 64-unit grid and shrunk slightly about the centre so
        # outline + filter ringing stay well clear of the 6 px transparent padding
        self.cv = Canvas(size, size, scale=scale, origin=(size / 2.0, size / 2.0))
        self.X, self.Y = self.cv.X, self.cv.Y
        self.size = size
        self.fill = np.zeros_like(self.X)
        self.fd = np.full_like(self.X, BIG)
        self.grad = diag_grad(self.X, self.Y, 8, 8, 56, 56)

    def part(self, d, tone=0.90, ow=1.5, bevel=2.6, kb=0.12):
        cv = self.cv
        cv.paint(NEAR_BLACK, cv.cov(d - ow))
        fd = d + ow
        fc = cv.cov(fd)
        lam = bevel_light(fd, bevel, cv.k)
        t = tone + kb * lam - 0.09 * (self.grad - 0.5)
        cv.paint(ramp(IVORY_RAMP, t), fc)
        self.fill, self.fd = fc, fd
        return fc

    def region(self, d, tone, kb=0.08, bevel=2.0, clip=None, edge=None):
        """Re-tone a region inside the current part (e.g. snow, roof facets)."""
        cv = self.cv
        clip = self.fill if clip is None else clip
        lam = bevel_light(d, bevel, cv.k)
        t = tone + kb * lam - 0.09 * (self.grad - 0.5)
        cv.paint(ramp(IVORY_RAMP, t), cv.cov(d) * clip)
        if edge is not None:
            self.engrave(np.abs(d) - edge[0], tone=edge[1], clip=clip)

    def engrave(self, d, tone=0.50, alpha=1.0, clip=None):
        cv = self.cv
        clip = self.fill if clip is None else clip
        cv.paint(ramp(IVORY_RAMP, np.float32(tone)), cv.cov(d) * alpha * clip)

    def engrave_pts(self, pts, w=0.65, tone=0.50, alpha=1.0, clip=None):
        self.engrave(sd_stroke(self.cv, pts, w), tone, alpha, clip)

    def dark(self, d, clip=None, color=NEAR_BLACK):
        cv = self.cv
        c = cv.cov(d)
        if clip is not None:
            c = c * clip
        cv.paint(color, c)

    def light(self, d, alpha=0.8, clip=None):
        cv = self.cv
        clip = self.fill if clip is None else clip
        cv.paint(hexc("FFFFFF"), cv.cov(d) * alpha * clip)


def house_parts(X, Y, x0, x1, wall_top, bottom, apex_y, eave=3.0):
    cx = (x0 + x1) / 2
    body = sd_rect(X, Y, x0, wall_top, x1, bottom, 0.6)
    roof = sd_polygon(X, Y, [(cx, apex_y), (x1 + eave, wall_top + 2.0), (x0 - eave, wall_top + 2.0)])
    return body, roof


# ---- full-colour icons ------------------------------------------------------
def ic_grace(p: IconPainter):
    cv, X, Y = p.cv, p.X, p.Y
    cx = cy = 32.0
    r = np.hypot(X - cx, Y - cy)
    cut = 1.0 - smoothstep(20.0, 26.0, r)
    glow = (0.62 * np.exp(-(r / 9.0) ** 2) + 0.42 * np.exp(-(r / 16.0) ** 2)) * cut
    cv.paint(GRACE, glow)
    spikes = [(0, 25.0, 3.2), (180, 25.0, 3.2), (90, 21.0, 2.8), (270, 21.0, 2.8),
              (45, 16.0, 2.1), (135, 16.0, 2.1), (225, 16.0, 2.1), (315, 16.0, 2.1)]
    d_sp, nx, ny = union_facets(spike(X, Y, cx, cy, a, L, w, r0=10.5, back=0) for a, L, w in spikes)
    d_sp = np.maximum(d_sp, 9.5 - r)
    ring = np.abs(r - 9.6) - 2.0
    d_all = np.minimum(d_sp, ring)
    cv.paint(NEAR_BLACK, cv.cov(d_all - 1.25) * 0.88)
    # spikes: faceted gold
    t_sp = 0.70 + 0.22 * facet_light(nx, ny) + 0.10 * (1 - smoothstep(6, 24, r))
    cv.paint(ramp(GOLD_RAMP, t_sp), cv.cov(d_sp))
    # ring: bevelled gold
    cv.paint(NEAR_BLACK, cv.cov(ring - 0.9) * 0.55)
    cv.paint(metal(cv, ring, bevel=1.4, bbox=(20, 20, 44, 44), base=0.66, kb=0.32, kp=0.25), cv.cov(ring))
    # glowing core inside the ring
    core = 1 - smoothstep(0.0, 7.6, r)
    cv.paint(mix(hexc("FFF8E4"), GRACE, smoothstep(1.0, 7.6, r)), cv.cov(r - 7.6) * (0.55 + 0.45 * core))
    cv.paint(WHITE, np.exp(-(r / 2.6) ** 2) * 0.95)


def ic_beacon(p: IconPainter):
    cv, X, Y = p.cv, p.X, p.Y
    cx, cy = 32.0, 32.0
    dx, dy = X - cx, Y - cy
    r = np.hypot(dx, dy)
    env = 1.0 - smoothstep(14.0, 26.5, np.abs(dy))
    wid = 1.0 - 0.45 * smoothstep(0.0, 25.0, np.abs(dy))
    cv.paint(GRACE, np.exp(-(dx / (5.0 * wid)) ** 2) * env * 0.80)
    cv.paint(hexc("FFF4D0"), np.exp(-(dx / (1.7 * wid)) ** 2) * env * 0.95)
    # diagonal rays
    for ang in (30, 150, 210, 330, 90, 270):
        a = math.radians(ang)
        ux, uy = math.cos(a), math.sin(a)
        along = dx * ux + dy * uy
        perp = np.abs(-dx * uy + dy * ux)
        L = 22.0 if ang in (90, 270) else 18.0
        ray = np.exp(-(perp / 0.75) ** 2) * smoothstep(8, 12, along) * (1 - smoothstep(L - 7, L, along))
        cv.paint(GRACE, ray * 0.85)
    cv.paint(GRACE, np.exp(-(r / 11.0) ** 2) * 0.55 * (1 - smoothstep(20, 25.5, r)))
    # faceted diamond
    pts = [(cx, 19.0), (cx + 9.5, cy), (cx, 45.0), (cx - 9.5, cy)]
    d = sd_polygon(X, Y, pts)
    cv.paint(NEAR_BLACK, cv.cov(d - 1.5))
    sx = np.where(dx < 0, -1.0, 1.0)
    sy = np.where(dy < 0, -1.0, 1.0)
    t = 0.66 + 0.26 * facet_light(sx * 13.0, sy * 9.5)
    t = t + 0.06 * (1 - smoothstep(0, 13, r))
    cv.paint(ramp(GOLD_RAMP, t), cv.cov(d))
    # inner table
    tpts = [(cx, 26.5), (cx + 4.2, cy), (cx, 37.5), (cx - 4.2, cy)]
    dt = sd_polygon(X, Y, tpts)
    tt = 0.86 + 0.10 * facet_light(sx * 5.5, sy * 4.2)
    cv.paint(ramp(GOLD_RAMP, tt), cv.cov(dt))
    # ridge lines
    ridge = U(sd_segment(X, Y, cx, 19.0, cx, 26.5, 0.35), sd_segment(X, Y, cx, 37.5, cx, 45.0, 0.35),
              sd_segment(X, Y, cx - 9.5, cy, cx - 4.2, cy, 0.35), sd_segment(X, Y, cx + 4.2, cy, cx + 9.5, cy, 0.35))
    cv.paint(GOLD_HI, cv.cov(ridge) * 0.7 * cv.cov(d))
    cv.paint(WHITE, np.exp(-((dx + 1.5) ** 2 + (dy + 3) ** 2) / 3.0) * 0.8)


def ic_death(p: IconPainter):
    cv, X, Y = p.cv, p.X, p.Y
    cx, cy = 32.0, 32.5
    dx, dy = X - cx, Y - cy
    r = np.hypot(dx, dy)
    th = np.arctan2(dy, dx)
    rr = 13.2 + 1.9 * np.sin(3 * th + 0.4) + 1.4 * np.sin(5 * th + 1.9) + 0.8 * np.sin(9 * th + 0.7)
    blob = (r - rr) * 0.8
    drops = [(-58, 19.0, 2.6), (-12, 18.5, 2.1), (28, 19.5, 2.9), (78, 18.5, 2.2), (122, 19.5, 3.0),
             (168, 18.0, 2.0), (205, 19.5, 2.5), (250, 17.5, 1.7)]
    d = blob
    for ang, dist, rad in drops:
        a = math.radians(ang)
        d = smin(d, sd_circle(X, Y, cx + dist * math.cos(a), cy + dist * math.sin(a), rad), 1.8)
    for ang, L, w in [(-35, 19.0, 1.5), (55, 18.5, 1.3), (145, 19.0, 1.5), (230, 18.0, 1.3), (-95, 17.5, 1.2)]:
        a = math.radians(ang)
        pts = np.array([[cx + 9 * math.cos(a), cy + 9 * math.sin(a)], [cx + L * math.cos(a), cy + L * math.sin(a)]])
        d = smin(d, sd_stroke(cv, pts, [w + 1.2, 0.35]), 1.5)
    cv.paint(NEAR_BLACK, cv.cov(d - 1.3))
    lam = bevel_light(d, 3.5, cv.k)
    t = 0.56 + 0.20 * lam + 0.10 * (0.5 - diag_grad(X, Y, 10, 10, 54, 54)) - 0.08 * smoothstep(8, 18, r)
    cv.paint(ramp(CRIMSON_RAMP, t), cv.cov(d))
    # glossy highlight
    hl = sd_ellipse(X, Y, cx - 6.5, cy - 7.0, 3.6, 2.0)
    cv.paint(hexc("F3B3A0"), cv.cov(hl, soft=1.2) * 0.45)
    # small skull
    sk = smin(sd_circle(X, Y, 32, 30.0, 7.4), sd_rect(X, Y, 27.6, 33.5, 36.4, 40.2, 1.6), 1.5)
    cv.paint(NEAR_BLACK, cv.cov(sk - 1.3))
    lam2 = bevel_light(sk, 2.0, cv.k)
    cv.paint(ramp(IVORY_RAMP, 0.86 + 0.1 * lam2), cv.cov(sk))
    eyes = U(sd_ellipse(X, Y, 29.1, 31.0, 2.1, 2.3), sd_ellipse(X, Y, 34.9, 31.0, 2.1, 2.3))
    cv.paint(NEAR_BLACK, cv.cov(eyes))
    nose = sd_polygon(X, Y, [(32, 33.8), (33.2, 36.0), (30.8, 36.0)])
    cv.paint(NEAR_BLACK, cv.cov(nose))
    teeth = U(sd_segment(X, Y, 30.4, 37.6, 30.4, 40.0, 0.45), sd_segment(X, Y, 33.6, 37.6, 33.6, 40.0, 0.45))
    cv.paint(NEAR_BLACK, cv.cov(teeth) * 0.8)


# ---- tintable icons ---------------------------------------------------------
def ic_pin(p: IconPainter):
    X, Y = p.X, p.Y
    d = teardrop(X, Y, 32, 23.5, 14.5, 32, 55.0)
    p.part(d)
    p.dark(sd_circle(X, Y, 32, 23.5, 5.6))
    p.engrave_pts(arc_pts(32, 23.5, 10.2, 195, 265, 20), w=0.9, tone=1.0, alpha=0.9)


def ic_bed(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_rect(X, Y, 9, 18, 16, 52, 1.5), tone=0.80)               # headboard
    p.engrave(sd_segment(X, Y, 12.5, 22, 12.5, 32, 0.55), tone=0.55)
    p.part(sd_rect(X, Y, 48, 30, 55, 52, 1.5), tone=0.80)              # footboard
    p.part(sd_rect(X, Y, 14, 38, 50, 46, 0.8), tone=0.74)              # frame
    p.part(sd_rect(X, Y, 14, 31, 50, 39, 1.5), tone=0.97)              # mattress
    p.part(sd_rect(X, Y, 16.5, 25.5, 27.5, 33.5, 3.2), tone=1.0, ow=1.3)   # pillow
    blanket = sd_rect(X, Y, 29, 26.5, 50, 40.5, 2.0)
    p.part(blanket, tone=0.84, ow=1.3)
    p.engrave_pts([(33.5, 28.5), (33.5, 38.5)], w=0.6, tone=0.55)
    p.engrave_pts(qbez((36, 30), (42, 33), (48, 30), 12), w=0.5, tone=0.6)


def ic_home(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_rect(X, Y, 40.5, 12.5, 47.5, 28, 0.6), tone=0.80)
    body, roof = house_parts(X, Y, 15.5, 48.5, 30.0, 54.0, 9.5, eave=5.0)
    p.part(body, tone=0.95)
    p.dark(sd_arch(X, Y, 27.0, 37.0, 42.0, 54.0))
    p.dark(sd_rect(X, Y, 39.5, 37.5, 45.0, 43.0, 0.6))
    p.part(roof, tone=0.74)
    for y in (20.5, 26.5):
        p.engrave(sd_segment(X, Y, 10, y, 54, y, 0.55), tone=0.5)


def ic_village(p: IconPainter):
    X, Y = p.X, p.Y
    body, roof = house_parts(X, Y, 23.5, 40.5, 22.0, 42.0, 9.0, eave=2.5)
    p.part(body, tone=0.90)
    p.dark(sd_rect(X, Y, 29.5, 26.5, 34.5, 31.5, 0.5))
    p.part(roof, tone=0.70)
    body, roof = house_parts(X, Y, 10.5, 26.5, 36.0, 54.0, 22.5, eave=2.0)
    p.part(body, tone=0.96)
    p.dark(sd_arch(X, Y, 15.0, 22.0, 45.5, 54.0))
    p.part(roof, tone=0.74)
    body, roof = house_parts(X, Y, 37.5, 53.5, 38.0, 54.0, 25.5, eave=2.0)
    p.part(body, tone=0.96)
    p.dark(sd_arch(X, Y, 42.0, 49.0, 47.0, 54.0))
    p.part(roof, tone=0.74)


def ic_castle(p: IconPainter):
    X, Y = p.X, p.Y
    wall = sd_rect(X, Y, 18, 27, 46, 54)
    for nx in (25.0, 32.0, 39.0):
        wall = SUB(wall, sd_rect(X, Y, nx - 2.0, 22, nx + 2.0, 31.0))
    p.part(wall, tone=0.88)
    p.dark(sd_arch(X, Y, 27.0, 37.0, 43.0, 54.0))
    for tx in (14.5, 49.5):
        p.part(sd_rect(X, Y, tx - 5.5, 23, tx + 5.5, 54, 0.5), tone=0.96)
        p.dark(sd_arch(X, Y, tx - 1.3, tx + 1.3, 31.0, 37.5))
        p.part(sd_polygon(X, Y, [(tx, 9.0), (tx + 7.0, 25.5), (tx - 7.0, 25.5)]), tone=0.72)


def ic_church(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_polygon(X, Y, [(9, 40.5), (32, 26), (55, 40.5)]), tone=0.72)
    p.part(sd_rect(X, Y, 12.5, 38.5, 51.5, 54, 0.6), tone=0.92)
    p.dark(sd_arch(X, Y, 16.5, 20.5, 44.5, 50.0))
    p.dark(sd_arch(X, Y, 43.5, 47.5, 44.5, 50.0))
    p.part(U(sd_segment(X, Y, 32, 9.5, 32, 18, 1.15), sd_segment(X, Y, 28.6, 12.6, 35.4, 12.6, 1.15)), tone=0.92, ow=1.4)
    p.part(sd_rect(X, Y, 25.5, 25, 38.5, 54, 0.5), tone=0.98)
    p.dark(sd_circle(X, Y, 32, 32.5, 2.7))
    p.dark(sd_arch(X, Y, 28.5, 35.5, 45.0, 54.0))
    p.part(sd_polygon(X, Y, [(32, 15.0), (39.8, 27.0), (24.2, 27.0)]), tone=0.74)


def ic_ruins(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_rect(X, Y, 8.5, 48, 55.5, 54.5, 0.6), tone=0.82)
    p.part(sd_rect(X, Y, 11.5, 43.5, 52.5, 49.0, 0.6), tone=0.90)
    # left column with capital and broken lintel
    p.part(sd_rect(X, Y, 14.0, 17, 22.0, 44.5), tone=0.96)
    for x in (16.7, 19.3):
        p.engrave(sd_segment(X, Y, x, 19, x, 42.5, 0.5), tone=0.6)
    lint = sd_polygon(X, Y, [(10.5, 10.5), (34.5, 10.5), (32.0, 13.0), (35.0, 15.0), (31.5, 16.5), (10.5, 16.5)])
    p.part(lint, tone=0.84)
    # middle broken stump
    p.part(sd_polygon(X, Y, [(28.5, 44.5), (28.5, 31.5), (31.0, 29.5), (32.5, 32.0), (34.5, 28.5), (36.5, 31.0), (36.5, 44.5)]), tone=0.94)
    p.engrave(sd_segment(X, Y, 32.5, 34.5, 32.5, 42.5, 0.5), tone=0.6)
    # right broken column
    p.part(sd_polygon(X, Y, [(42.0, 44.5), (42.0, 22.0), (44.5, 19.5), (46.0, 23.0), (48.5, 20.5), (50.0, 23.5), (50.0, 44.5)]), tone=0.96)
    for x in (44.7, 47.3):
        p.engrave(sd_segment(X, Y, x, 25.5, x, 42.5, 0.5), tone=0.6)


def ic_tower(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_polygon(X, Y, [(21.5, 54.5), (42.5, 54.5), (39.5, 24.0), (24.5, 24.0)]), tone=0.95)
    p.dark(sd_arch(X, Y, 28.8, 35.2, 46.5, 54.5))
    p.dark(sd_arch(X, Y, 30.6, 33.4, 32.0, 39.0))
    p.engrave(sd_segment(X, Y, 25, 42.5, 39.5, 42.5, 0.5), tone=0.6)
    par = sd_rect(X, Y, 21.0, 19.0, 43.0, 25.5, 0.5)
    p.part(par, tone=0.86)
    p.part(sd_polygon(X, Y, [(32, 7.5), (42.5, 20.0), (21.5, 20.0)]), tone=0.72)


def ic_cave(p: IconPainter):
    X, Y = p.X, p.Y
    rock = smin(smin(sd_circle(X, Y, 22, 38, 15), sd_circle(X, Y, 42, 37, 14), 6.0), sd_circle(X, Y, 31, 27, 15), 6.0)
    rock = np.maximum(rock, Y - 54.0)
    rock = smin(rock, sd_rect(X, Y, 9.5, 46, 54.5, 54, 2.0), 3.0)
    p.part(rock, tone=0.86)
    p.engrave_pts(qbez((15, 30), (19, 27), (23, 28), 10), w=0.55, tone=0.55)
    p.engrave_pts(qbez((38, 21), (43, 23), (45, 28), 10), w=0.55, tone=0.55)
    p.engrave_pts(qbez((47, 37), (50, 41), (50, 45), 10), w=0.55, tone=0.55)
    mouth = sd_arch(X, Y, 22.5, 41.5, 40.5, 54.0)
    p.dark(mouth)
    p.engrave_pts(qbez((22.0, 40), (32, 30), (42.0, 40), 18), w=0.6, tone=0.6)


def ic_catacomb(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_rect(X, Y, 8.5, 49.0, 55.5, 54.5, 0.6), tone=0.82)
    p.part(sd_rect(X, Y, 12.0, 26.0, 52.0, 50.0), tone=0.94)
    for x in (17.0, 47.0):
        p.engrave(sd_segment(X, Y, x, 29, x, 47.5, 0.5), tone=0.6)
    p.dark(sd_arch(X, Y, 23.5, 40.5, 36.0, 50.0))
    p.engrave_pts(arc_pts(32, 36.0, 11.0, 180, 360, 30), w=0.55, tone=0.6)
    p.part(sd_rect(X, Y, 9.5, 23.5, 54.5, 28.5, 0.4), tone=0.80)
    p.part(sd_polygon(X, Y, [(32, 10.0), (54.5, 24.5), (9.5, 24.5)]), tone=0.72)
    p.dark(sd_circle(X, Y, 32, 19.5, 2.4))


def ic_portal(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_rect(X, Y, 10.0, 49.5, 54.0, 55.0, 0.6), tone=0.82)
    outer = sd_gothic(X, Y, 14.0, 50.0, 40.5, 50.5)
    p.part(outer, tone=0.94)
    inner = sd_gothic(X, Y, 20.0, 44.0, 40.5, 50.5, radius=30.0)
    p.dark(inner)
    sp = spiral_pts(32, 38.0, 0.6, 9.0, 90, 90 + 600, n=90)
    d = sd_stroke(p.cv, sp, taper(len(sp), 0.6, 1.9))
    p.cv.paint(ramp(IVORY_RAMP, 0.9), p.cv.cov(d) * p.cv.cov(inner + 1.2))


def ic_boss(p: IconPainter):
    X, Y = p.X, p.Y
    cv = p.cv
    for s in (-1, 1):
        def mx(x):
            return 32 + s * (x - 32)
        pts = cbez((mx(21.0), 30.0), (mx(12.0), 30.0), (mx(8.5), 22.0), (mx(12.5), 9.5), 28)
        horn = sd_stroke(cv, pts, taper(len(pts), 4.0, 0.6))
        p.part(horn, tone=0.84)
    helm = smin(sd_circle(X, Y, 32, 29.0, 13.5),
                sd_polygon(X, Y, [(18.5, 29), (45.5, 29), (45.5, 44), (38.0, 54.0), (26.0, 54.0), (18.5, 44)]), 2.0)
    p.part(helm, tone=0.94)
    p.engrave(sd_segment(X, Y, 32, 17.0, 32, 27.0, 0.6), tone=0.55)
    visor = U(sd_rect(X, Y, 21.5, 31.0, 42.5, 35.0, 1.2), sd_rect(X, Y, 30.2, 31.0, 33.8, 47.0, 1.0))
    p.dark(visor)
    for x in (24.5, 39.5):
        for y in (40.0, 44.5):
            p.dark(sd_circle(X, Y, x, y, 1.1))


def ic_danger(p: IconPainter):
    X, Y = p.X, p.Y
    sk = smin(sd_circle(X, Y, 32, 27.5, 17.0), sd_rect(X, Y, 23.5, 38.0, 40.5, 53.5, 3.0), 3.0)
    p.part(sk, tone=0.94)
    eyes = U(sd_ellipse(X, Y, 25.2, 30.5, 5.0, 5.6), sd_ellipse(X, Y, 38.8, 30.5, 5.0, 5.6))
    p.dark(eyes)
    p.dark(sd_polygon(X, Y, [(32, 36.5), (35.0, 42.0), (29.0, 42.0)]))
    for x in (28.0, 32.0, 36.0):
        p.dark(sd_segment(X, Y, x, 46.0, x, 52.5, 0.8))
    p.engrave_pts(arc_pts(32, 27.5, 12.5, 200, 250, 16), w=0.8, tone=1.0, alpha=0.8)


def ic_treasure(p: IconPainter):
    X, Y = p.X, p.Y
    body = sd_rect(X, Y, 10.0, 31.0, 54.0, 52.5, 1.5)
    p.part(body, tone=0.92)
    for x in (17.5, 46.5):
        p.region(sd_rect(X, Y, x - 2.2, 25.0, x + 2.2, 60.0), 0.70, kb=0.0)
    lid = U(sd_rect(X, Y, 10.0, 17.0, 54.0, 31.0, 9.0), sd_rect(X, Y, 10.0, 24.0, 54.0, 31.5, 0.5))
    p.part(lid, tone=0.86)
    for x in (17.5, 46.5):
        p.region(sd_rect(X, Y, x - 2.2, 5.0, x + 2.2, 40.0), 0.64, kb=0.0)
    p.part(sd_rect(X, Y, 27.5, 27.0, 36.5, 38.5, 1.2), tone=0.98, ow=1.2)
    p.dark(U(sd_circle(X, Y, 32, 31.5, 1.6), sd_polygon(X, Y, [(32, 31.5), (33.4, 35.6), (30.6, 35.6)])))


def ic_mine(p: IconPainter):
    X, Y = p.X, p.Y
    cv = p.cv
    hx0, hy0, hx1, hy1 = 14.0, 52.0, 38.5, 24.5
    p.part(sd_segment(X, Y, hx0, hy0, hx1, hy1, 2.7), tone=0.76)
    ux, uy = (hx1 - hx0), (hy1 - hy0)
    n = math.hypot(ux, uy)
    ux, uy = ux / n, uy / n
    vx, vy = -uy, ux
    c = (hx1 + 1.0 * ux, hy1 + 1.0 * uy)
    L = 19.0
    p0 = (c[0] - L * vx, c[1] - L * vy)
    p2 = (c[0] + L * vx, c[1] + L * vy)
    ctrl = (c[0] + 11.0 * ux, c[1] + 11.0 * uy)
    pts = qbez(p0, ctrl, p2, 40)
    t = np.linspace(0, 1, len(pts))
    w = 0.5 + 3.4 * np.sin(np.pi * t) ** 0.7
    head = sd_stroke(cv, pts, w)
    p.part(head, tone=0.94)
    mid = pts[len(pts) // 2]
    p.engrave_pts(qbez(pts[6], (mid[0] + 1.5 * ux, mid[1] + 1.5 * uy), pts[-7], 20), w=0.5, tone=0.6)
    p.part(sd_circle(X, Y, mid[0] - 1.6 * ux, mid[1] - 1.6 * uy, 3.6), tone=0.84, ow=1.2)


def ic_forge(p: IconPainter):
    X, Y = p.X, p.Y
    top = join(qbez((8.0, 22.0), (14.0, 18.5), (22.0, 18.0), 10), [(55.0, 18.0), (55.0, 27.0), (47.0, 27.0)],
               qbez((47.0, 27.0), (41.0, 29.0), (40.5, 38.0), 10), qbez((40.5, 38.0), (41.0, 44.0), (51.0, 47.0), 10),
               [(53.0, 54.0), (19.0, 54.0)], qbez((21.0, 47.0), (31.0, 44.0), (31.5, 38.0), 10),
               qbez((31.5, 38.0), (31.0, 30.5), (24.0, 28.5), 10), qbez((24.0, 28.5), (14.0, 27.0), (8.0, 22.0), 10))
    anvil = sd_polygon(X, Y, top)
    p.part(anvil, tone=0.92)
    p.region(sd_rect(X, Y, 0, 0, 64, 22.0), 1.0, kb=0.0)
    p.engrave(sd_segment(X, Y, 23.0, 22.0, 54.0, 22.0, 0.5), tone=0.6)
    p.dark(sd_rect(X, Y, 47.5, 19.8, 50.5, 21.4, 0.3))
    p.part(sd_rect(X, Y, 18.0, 49.0, 54.0, 54.5, 0.6), tone=0.80)


def ic_farm(p: IconPainter):
    X, Y = p.X, p.Y
    cv = p.cv
    tx, ty = 32.0, 40.0
    stalks = sd_polygon(X, Y, [(29.5, 38.5), (34.5, 38.5), (44.5, 54.5), (19.5, 54.5)])
    p.part(stalks, tone=0.80)
    for x in (24.5, 28.5, 32.0, 35.5, 39.5):
        p.engrave(sd_segment(X, Y, 32 + (x - 32) * 0.25, 43, x, 53.5, 0.45), tone=0.55)
    ears = []
    for ang in (-32, 0, 32):
        a = math.radians(ang)
        ux, uy = math.sin(a), -math.cos(a)
        vx, vy = math.cos(a), math.sin(a)
        d = sd_segment(X, Y, tx, ty, tx + 22 * ux, ty + 22 * uy, 1.0)
        for k in range(5):
            s_ = 9.5 + k * 3.0
            for sd_ in (-1, 1):
                gx, gy = tx + s_ * ux + sd_ * 2.5 * vx, ty + s_ * uy + sd_ * 2.5 * vy
                Xr, Yr = rot(X, Y, gx, gy, -(ang + sd_ * 22))
                d = np.minimum(d, sd_ellipse(Xr, Yr, gx, gy, 2.1, 3.3))
        gx, gy = tx + 25.0 * ux, ty + 25.0 * uy
        Xr, Yr = rot(X, Y, gx, gy, -ang)
        d = np.minimum(d, sd_ellipse(Xr, Yr, gx, gy, 1.8, 3.0))
        ears.append(d)
    for d in (ears[0], ears[2], ears[1]):
        p.part(d, tone=0.95, ow=1.3)
    p.part(sd_rect(X, Y, 26.0, 37.5, 38.0, 42.5, 1.0), tone=0.72, ow=1.3)


def ic_tree(p: IconPainter):
    X, Y = p.X, p.Y
    trunk = sd_polygon(X, Y, [(28.5, 30.0), (35.5, 30.0), (36.0, 47.0), (44.0, 54.5), (20.0, 54.5), (28.0, 47.0)])
    p.part(trunk, tone=0.78)
    p.engrave_pts(qbez((32.5, 34), (32.0, 44), (28, 51), 12), w=0.5, tone=0.55)
    crown = U(sd_circle(X, Y, 32, 21.0, 12.5), sd_circle(X, Y, 19.5, 27.0, 10.0), sd_circle(X, Y, 44.5, 27.0, 10.0),
              sd_circle(X, Y, 23.5, 17.5, 9.0), sd_circle(X, Y, 40.5, 17.5, 9.0), sd_circle(X, Y, 32, 29.5, 9.0))
    crown = smin(crown, sd_circle(X, Y, 13.0, 31.0, 5.5), 3.0)
    crown = smin(crown, sd_circle(X, Y, 51.0, 31.0, 5.5), 3.0)
    p.part(crown, tone=0.95)
    for cx, cy, r, a0, a1 in ((24, 25, 6.5, 110, 200), (40, 25, 6.5, 340, 70), (32, 17, 6.0, 200, 340),
                              (17, 31, 4.0, 120, 220), (47, 31, 4.0, 320, 60)):
        p.engrave_pts(arc_pts(cx, cy, r, a0, a1, 16), w=0.5, tone=0.6)


def ic_camp(p: IconPainter):
    X, Y = p.X, p.Y
    for s in (-1, 1):
        p.part(sd_segment(X, Y, 32, 16.0, 32 + s * 6.5, 8.5, 1.2), tone=0.80, ow=1.3)
    tent = sd_polygon(X, Y, [(32, 13.0), (55.0, 53.0), (9.0, 53.0)])
    p.part(tent, tone=0.94)
    p.region(sd_polygon(X, Y, [(32, 13.0), (55.0, 53.0), (32.0, 53.0)]), 0.80, kb=0.04)
    p.dark(sd_polygon(X, Y, [(32, 27.0), (40.5, 53.0), (23.5, 53.0)]))
    p.part(sd_polygon(X, Y, [(32, 27.5), (26.0, 53.0), (20.0, 53.0)]), tone=0.98, ow=1.1)
    p.part(sd_rect(X, Y, 8.5, 51.5, 55.5, 55.0, 1.0), tone=0.78, ow=1.2)


def ic_ship(p: IconPainter):
    X, Y = p.X, p.Y
    cv = p.cv
    p.part(sd_segment(X, Y, 32, 9.0, 32, 42, 1.4), tone=0.78, ow=1.3)
    p.part(sd_polygon(X, Y, [(33.0, 8.5), (42.0, 11.0), (33.0, 13.5)]), tone=0.9, ow=1.2)
    sail = join(qbez((19.0, 15.0), (32.0, 13.0), (45.0, 15.0), 16), qbez((45.0, 15.0), (50.5, 25.0), (46.0, 36.0), 16),
                qbez((46.0, 36.0), (32.0, 33.0), (18.0, 36.0), 16), qbez((18.0, 36.0), (13.5, 25.0), (19.0, 15.0), 16))
    sd = sd_polygon(X, Y, sail)
    p.part(sd, tone=0.97)
    p.engrave_pts(qbez((24, 16.5), (21.5, 25), (24.5, 34.0), 14), w=0.5, tone=0.6)
    p.engrave_pts(qbez((40, 16.5), (42.5, 25), (39.5, 34.0), 14), w=0.5, tone=0.6)
    hull = join([(8.5, 37.5), (14.5, 41.5), (49.5, 41.5), (55.5, 36.5)], qbez((55.5, 36.5), (52.0, 47.0), (44.0, 52.5), 12),
                [(20.0, 52.5)], qbez((20.0, 52.5), (13.0, 48.0), (8.5, 37.5), 12))
    p.part(sd_polygon(X, Y, hull), tone=0.84)
    p.engrave(sd_segment(X, Y, 14, 46.0, 51, 46.0, 0.55), tone=0.55)


def ic_star(p: IconPainter):
    X, Y = p.X, p.Y
    cv = p.cv
    cx, cy = 32.0, 33.5
    pts = []
    for k in range(10):
        a = math.radians(-90 + k * 36)
        r = 23.5 if k % 2 == 0 else 10.0
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d = sd_polygon(X, Y, pts)
    cv.paint(NEAR_BLACK, cv.cov(d - 1.5))
    fd = d + 1.5
    # facets: each star point split in two along its axis
    ang = np.degrees(np.arctan2(Y - cy, X - cx)) + 90.0     # 0 = north, clockwise
    sector = np.mod(ang, 72.0)
    sector = np.where(sector > 36, sector - 72, sector)    # -36..36, 0 = on a point's axis
    base_ang = np.radians(ang - sector)
    side = np.where(sector >= 0, 1.0, -1.0)
    vx, vy = np.cos(base_ang) * side, np.sin(base_ang) * side
    fl = facet_light(vx, vy)
    lam = bevel_light(fd, 2.0, cv.k)
    t = 0.86 + 0.10 * fl + 0.05 * lam - 0.06 * (p.grad - 0.5)
    cv.paint(ramp(IVORY_RAMP, t), cv.cov(fd))
    p.fill = cv.cov(fd)
    for k in range(0, 10, 2):
        p.engrave(sd_segment(X, Y, cx, cy, pts[k][0], pts[k][1], 0.45), tone=0.62, alpha=0.9)


def ic_flag(p: IconPainter):
    X, Y = p.X, p.Y
    banner = join(qbez((19.5, 14.0), (30.0, 10.0), (40.0, 14.5), 14), qbez((40.0, 14.5), (46.0, 17.0), (53.0, 15.0), 10),
                  [(46.0, 25.5)], [(53.0, 36.5)], qbez((53.0, 36.5), (46.0, 38.5), (40.0, 36.0), 10),
                  qbez((40.0, 36.0), (30.0, 31.5), (19.5, 35.5), 14))
    p.part(sd_segment(X, Y, 17.5, 13.0, 17.5, 54.5, 1.9), tone=0.80)
    p.part(sd_circle(X, Y, 17.5, 11.5, 3.0), tone=0.92, ow=1.3)
    p.part(sd_polygon(X, Y, banner), tone=0.95)
    p.engrave_pts(qbez((30.0, 13.0), (31.5, 22.0), (30.0, 32.5), 14), w=0.5, tone=0.62)
    p.engrave_pts(qbez((41.0, 16.0), (42.5, 25.0), (41.0, 35.5), 14), w=0.5, tone=0.62)


def ic_merchant(p: IconPainter):
    X, Y = p.X, p.Y
    neck = sd_polygon(X, Y, [(25.0, 21.0), (35.0, 21.0), (39.5, 31.0), (20.5, 31.0)])
    body = smin(sd_ellipse(X, Y, 30.0, 40.5, 16.5, 13.5), neck, 4.0)
    p.part(body, tone=0.92)
    p.engrave_pts(qbez((26.5, 27.0), (21.0, 35.0), (21.5, 46.0), 16), w=0.55, tone=0.6)
    p.engrave_pts(qbez((32.0, 27.0), (31.0, 37.0), (34.0, 46.0), 16), w=0.5, tone=0.62)
    top = join(qbez((21.0, 12.5), (24.5, 15.5), (27.0, 13.5), 8), qbez((27.0, 13.5), (30.0, 11.5), (33.0, 13.5), 8),
               qbez((33.0, 13.5), (35.5, 15.5), (39.0, 12.5), 8), [(36.0, 21.5), (24.0, 21.5)])
    p.part(sd_polygon(X, Y, top), tone=0.86, ow=1.3)
    p.part(sd_rect(X, Y, 23.0, 19.0, 37.0, 24.0, 1.6), tone=0.70, ow=1.2)
    p.part(sd_segment(X, Y, 36.0, 21.5, 41.0, 28.0, 0.9), tone=0.70, ow=1.1)
    # coins in front
    p.part(sd_ellipse(X, Y, 45.0, 50.0, 8.0, 4.0), tone=0.88, ow=1.3)
    p.part(sd_circle(X, Y, 45.5, 42.0, 7.2), tone=0.98, ow=1.3)
    p.engrave(np.abs(sd_circle(X, Y, 45.5, 42.0, 4.4)) - 0.5, tone=0.6)


def ic_gate(p: IconPainter):
    X, Y = p.X, p.Y
    cx, cy = 32.0, 28.5
    ring = np.abs(sd_circle(X, Y, cx, cy, 15.5)) - 4.8
    p.part(ring, tone=0.95)
    for k in range(12):
        ang = k * 30 + 15
        if ang in (255, 285):
            continue
        a = math.radians(ang)
        x0, y0 = cx + 10.8 * math.cos(a), cy + 10.8 * math.sin(a)
        x1, y1 = cx + 19.2 * math.cos(a), cy + 19.2 * math.sin(a)
        p.engrave(sd_segment(X, Y, x0, y0, x1, y1, 0.45), tone=0.58)
    inner = sd_circle(X, Y, cx, cy, 9.0)
    p.dark(inner)
    # the waygate's light: a four-point sparkle
    sp = U(kite(X, Y, cx, cy, 0, 7.5, 7.5, 1.7)[0], kite(X, Y, cx, cy, 90, 7.5, 7.5, 1.7)[0],
           kite(X, Y, cx, cy, 45, 3.6, 3.6, 1.1)[0], kite(X, Y, cx, cy, 135, 3.6, 3.6, 1.1)[0])
    p.cv.paint(ramp(IVORY_RAMP, np.float32(0.97)), p.cv.cov(sp))
    p.part(sd_polygon(X, Y, [(28.0, 8.0), (36.0, 8.0), (34.6, 14.5), (29.4, 14.5)]), tone=0.82, ow=1.2)
    # platform in front of the ring's foot
    p.part(sd_rect(X, Y, 12.5, 42.5, 51.5, 49.0, 0.6), tone=0.88)
    p.part(sd_rect(X, Y, 9.0, 48.0, 55.0, 54.5, 0.6), tone=0.78)


def ic_spring(p: IconPainter):
    X, Y = p.X, p.Y
    d = teardrop(X, Y, 32, 36.5, 14.0, 32, 8.5)
    p.part(d, tone=0.94)
    p.engrave_pts(arc_pts(32, 36.5, 9.5, 120, 205, 20), w=1.0, tone=1.0, alpha=0.95)
    p.light(sd_circle(X, Y, 25.0, 31.5, 1.4), alpha=0.9)
    p.engrave_pts(arc_pts(32, 36.5, 9.5, 10, 70, 16), w=0.6, tone=0.6)


def ic_mountain(p: IconPainter):
    X, Y = p.X, p.Y
    back = sd_polygon(X, Y, [(41.5, 19.5), (56.0, 53.5), (27.0, 53.5)])
    p.part(back, tone=0.86)
    p.region(sd_polygon(X, Y, [(41.5, 19.5), (56.0, 53.5), (41.5, 53.5)]), 0.72, kb=0.0)
    snow_b = sd_polygon(X, Y, [(41.5, 19.5), (47.0, 32.5), (44.0, 30.5), (41.5, 33.5), (39.0, 30.0), (36.0, 32.5)])
    p.region(snow_b, 0.99, kb=0.0)
    front = sd_polygon(X, Y, [(25.0, 10.0), (46.0, 53.5), (8.0, 53.5)])
    p.part(front, tone=0.88)
    p.region(sd_polygon(X, Y, [(25.0, 10.0), (46.0, 53.5), (27.5, 53.5)]), 0.72, kb=0.0)
    snow = sd_polygon(X, Y, [(25.0, 10.0), (33.2, 27.0), (29.5, 24.5), (26.5, 28.5), (23.0, 24.0), (19.0, 27.5)])
    p.region(snow, 0.99, kb=0.0)


def ic_question(p: IconPainter):
    X, Y = p.X, p.Y
    p.part(sd_circle(X, Y, 32, 32, 22.0), tone=0.94)
    p.engrave(np.abs(sd_circle(X, Y, 32, 32, 18.2)) - 0.5, tone=0.62)
    m = text_mask(p.cv, "?", 32.0, 32.5, 30.0, SERIF_BOLD)
    p.cv.paint(NEAR_BLACK, m)


def _sword(p: IconPainter, s):
    X, Y = p.X, p.Y
    cv = p.cv

    def P(x, y):
        return (32 + s * (x - 32), y)

    pom = P(13.5, 50.5)
    tip = P(51.5, 12.5)
    ux, uy = (tip[0] - pom[0]), (tip[1] - pom[1])
    n = math.hypot(ux, uy)
    ux, uy = ux / n, uy / n
    vx, vy = -uy, ux
    g = (pom[0] + 12.0 * ux, pom[1] + 12.0 * uy)
    bl0 = (g[0] + 1.0 * ux, g[1] + 1.0 * uy)
    w = 3.9
    blade = sd_polygon(X, Y, [(bl0[0] + w * vx, bl0[1] + w * vy), (tip[0] - 5.0 * ux + w * 0.75 * vx, tip[1] - 5.0 * uy + w * 0.75 * vy),
                              tip, (tip[0] - 5.0 * ux - w * 0.75 * vx, tip[1] - 5.0 * uy - w * 0.75 * vy),
                              (bl0[0] - w * vx, bl0[1] - w * vy)])
    p.part(sd_segment(X, Y, pom[0], pom[1], g[0], g[1], 2.1), tone=0.72, ow=1.3)
    p.part(sd_circle(X, Y, pom[0], pom[1], 3.0), tone=0.9, ow=1.3)
    p.part(blade, tone=0.97)
    p.engrave(sd_segment(X, Y, bl0[0] + 3 * ux, bl0[1] + 3 * uy, tip[0] - 8 * ux, tip[1] - 8 * uy, 0.5), tone=0.6)
    p.part(sd_segment(X, Y, g[0] - 8.0 * vx, g[1] - 8.0 * vy, g[0] + 8.0 * vx, g[1] + 8.0 * vy, 2.2), tone=0.84, ow=1.3)


def ic_sword(p: IconPainter):
    _sword(p, 1)
    _sword(p, -1)


def ic_eye(p: IconPainter):
    X, Y = p.X, p.Y
    cv = p.cv
    for ang in (-60, -30, 0, 30, 60):
        a = math.radians(ang)
        ux, uy = math.sin(a), -math.cos(a)
        x0, y0 = 32 + 15.0 * ux, 34 + 15.0 * uy
        x1, y1 = 32 + 23.5 * ux, 34 + 23.5 * uy
        p.part(sd_stroke(cv, [(x0, y0), (x1, y1)], [1.6, 0.5]), tone=0.9, ow=1.3)
    a, b = 22.5, 11.0
    R = (a * a + b * b) / (2 * b)
    lens = np.maximum(sd_circle(X, Y, 32, 34 + (R - b), R), sd_circle(X, Y, 32, 34 - (R - b), R))
    p.part(lens, tone=0.97)
    p.part(sd_circle(X, Y, 32, 34, 8.5), tone=0.80, ow=1.3)
    p.dark(sd_circle(X, Y, 32, 34, 4.2))
    p.light(sd_circle(X, Y, 29.8, 31.8, 1.4), alpha=0.95, clip=np.ones_like(X))


ICONS = [ic_grace, ic_beacon, ic_pin, ic_death, ic_bed, ic_home, ic_village, ic_castle,
         ic_church, ic_ruins, ic_tower, ic_cave, ic_catacomb, ic_portal, ic_boss, ic_danger,
         ic_treasure, ic_mine, ic_forge, ic_farm, ic_tree, ic_camp, ic_ship, ic_star,
         ic_flag, ic_merchant, ic_gate, ic_spring, ic_mountain, ic_question, ic_sword, ic_eye]
ICON_NAMES = ["grace", "beacon", "pin", "death", "bed", "home", "village", "castle", "church", "ruins",
              "tower", "cave", "catacomb", "portal", "boss", "danger", "treasure", "mine", "forge", "farm",
              "tree", "camp", "ship", "star", "flag", "merchant", "gate", "spring", "mountain", "question",
              "sword", "eye"]


def make_markers():
    atlas = np.zeros((256, 512, 4), np.uint8)
    for idx, fn in enumerate(ICONS):
        p = IconPainter(64)
        fn(p)
        cell = p.cv.result()
        pad = cell.copy()
        pad[6:58, 6:58] = 0
        if pad[..., 3].max() > 6:
            print(f"  warning: icon {idx} ({ICON_NAMES[idx]}) touches the 6px padding "
                  f"(max alpha {pad[..., 3].max()}); clipping")
        clipped = np.zeros_like(cell)
        clipped[6:58, 6:58] = cell[6:58, 6:58]
        r, c = divmod(idx, 8)
        atlas[r * 64:(r + 1) * 64, c * 64:(c + 1) * 64] = clipped
    return atlas


# =============================================================================
# Player arrows, small widgets
# =============================================================================
ARROW_PTS = [(32.0, 7.0), (50.5, 57.0), (32.0, 46.0), (13.5, 57.0)]


def make_player_arrow(ramp_stops, glow_color, glow_alpha=0.5):
    cv = Canvas(64, 64)
    X, Y = cv.X, cv.Y
    d = sd_polygon(X, Y, ARROW_PTS)
    # round the corners slightly
    d = d + 0.0
    sil = cv.cov(d - 2.2)
    glow = cv.blur(sil, 2.6)
    cv.paint(glow_color, np.clip(glow * glow_alpha * 1.6, 0, glow_alpha))
    cv.paint(NEAR_BLACK, sil)
    side = np.where(X < 32.0, -1.0, 1.0)
    fl = facet_light(side * 1.0, np.full_like(X, -0.35))
    lam = bevel_light(d, 1.6, cv.k)
    inner = d + 5.2
    t_outer = 0.70 + 0.16 * fl + 0.18 * lam + 0.10 * (0.5 - diag_grad(X, Y, 13, 7, 51, 57))
    cv.paint(ramp(ramp_stops, t_outer), cv.cov(d))
    lam_i = bevel_light(inner, 1.2, cv.k)
    t_inner = 0.44 + 0.14 * fl - 0.12 * lam_i + 0.08 * (0.5 - diag_grad(X, Y, 18, 14, 46, 50))
    cv.paint(ramp(ramp_stops, t_inner), cv.cov(inner))
    ridge = sd_segment(X, Y, 32, 14.0, 32, 43.0, 0.45)
    cv.paint(ramp(ramp_stops, np.float32(0.93)), cv.cov(ridge) * cv.cov(inner) * 0.8)
    return cv.result()


def make_entity_dot():
    cv = Canvas(32, 32)
    X, Y = cv.X, cv.Y
    d = sd_circle(X, Y, 16, 16, 10.0)
    cv.paint(NEAR_BLACK, cv.cov(d - 2.8))
    lam = bevel_light(d, 3.0, cv.k)
    t = 0.90 + 0.10 * lam - 0.10 * (diag_grad(X, Y, 6, 6, 26, 26) - 0.5)
    cv.paint(ramp(IVORY_RAMP, t), cv.cov(d))
    cv.paint(WHITE, cv.cov(sd_circle(X, Y, 12.8, 12.6, 2.2), soft=1.0) * 0.8)
    return cv.result()


def make_offscreen_arrow():
    cv = Canvas(32, 32)
    X, Y = cv.X, cv.Y
    d = sd_polygon(X, Y, [(16.0, 6.0), (26.5, 26.0), (5.5, 26.0)])
    cv.paint(NEAR_BLACK, cv.cov(d - 1.9))
    side = np.where(X < 16.0, -1.0, 1.0)
    fl = facet_light(side, np.full_like(X, -0.5))
    t = 0.68 + 0.18 * fl + 0.16 * bevel_light(d, 1.2, cv.k)
    cv.paint(ramp(GOLD_RAMP, t), cv.cov(d))
    return cv.result()


def make_glow():
    cv = Canvas(128, 128)
    r = np.hypot(cv.X - 64, cv.Y - 64)
    R, sig = 64.0, 64.0 / 3.0
    g = np.exp(-(r * r) / (2 * sig * sig))
    e = math.exp(-(R * R) / (2 * sig * sig))
    a = np.clip((g - e) / (1 - e), 0, 1)
    cv.paint(WHITE, a)
    return cv.result()


def make_reticle():
    cv = Canvas(128, 128)
    X, Y = cv.X, cv.Y
    c = 64.0
    r = np.hypot(X - c, Y - c)
    circle = np.abs(r - 40.0) - 1.0
    ticks = []
    for ang in (0, 90, 180, 270):
        a = math.radians(ang)
        ux, uy = math.sin(a), -math.cos(a)
        ticks.append(sd_segment(X, Y, c + 27.0 * ux, c + 27.0 * uy, c + 35.0 * ux, c + 35.0 * uy, 1.0))
        ticks.append(sd_segment(X, Y, c + 45.0 * ux, c + 45.0 * uy, c + 55.0 * ux, c + 55.0 * uy, 1.0))
    dot = sd_circle(X, Y, c, c, 1.6)
    d = U(circle, dot, *ticks)
    cv.paint(NEAR_BLACK, cv.cov(d - 1.1) * 0.75)
    cv.paint(WHITE, cv.cov(d))
    return cv.result()


def make_checkbox():
    cv = Canvas(64, 32)
    X, Y = cv.X, cv.Y
    for cell in (0, 1):
        ox = cell * 32.0
        box = sd_rect(X, Y, ox + 5.0, 5.0, ox + 27.0, 27.0, 2.0)
        cv.paint(SHADOW, cv.cov(box - 1.0, soft=1.0) * 0.35)
        body = mix(hexc("221C14"), hexc("0E0C09"), smoothstep(5, 27, Y))
        cv.paint(body, cv.cov(box) * 0.9)
        border = np.abs(box + 1.4) - 0.6
        t = 0.66 + 0.14 * (0.5 - diag_grad(X, Y, ox + 5, 5, ox + 27, 27))
        cv.paint(ramp(GOLD_RAMP, t), cv.cov(border))
        inner = np.abs(box + 3.6) - 0.35
        cv.paint(GOLD_DEEP, cv.cov(inner) * 0.45)
        if cell == 1:
            cxx, cyy = ox + 16.0, 16.0
            rr = np.hypot(X - cxx, Y - cyy)
            cv.paint(GRACE, np.exp(-(rr / 6.5) ** 2) * 0.45 * cv.cov(box + 2.0))
            dk, nx, ny = kite(X, Y, cxx, cyy, 0, 7.0, 7.0, 7.0, 0.0)
            cv.paint(NEAR_BLACK, cv.cov(dk - 1.0) * 0.8)
            t = 0.72 + 0.22 * facet_light(nx, ny)
            cv.paint(ramp(GOLD_RAMP, t), cv.cov(dk))
            cv.paint(WHITE, np.exp(-(((X - cxx + 1.5) ** 2 + (Y - cyy + 1.8) ** 2) / 2.0)) * 0.55 * cv.cov(dk))
    return cv.result()


def make_divider():
    cv = Canvas(256, 16)
    X, Y = cv.X, cv.Y
    cx, cy = 128.0, 8.0
    u = X / 256.0
    fade = smoothstep(0.0, 0.46, u) * smoothstep(0.0, 0.46, 1.0 - u)
    line = np.abs(Y - cy) - 0.55
    gap = smoothstep(9.0, 12.0, np.abs(X - cx))
    cv.paint(SHADOW, cv.cov(np.abs(Y - cy - 1.2) - 0.6) * fade * gap * 0.35)
    t = 0.62 + 0.25 * (1 - smoothstep(0, 128, np.abs(X - cx)))
    cv.paint(ramp(GOLD_RAMP, t), cv.cov(line) * fade * gap)
    for s in (-1, 1):
        dd = sd_circle(X, Y, cx + s * 14.5, cy, 1.5)
        cv.paint(NEAR_BLACK, cv.cov(dd - 0.8) * 0.6)
        cv.paint(ramp(GOLD_RAMP, np.float32(0.8)), cv.cov(dd))
        dk, nx, ny = kite(X, Y, cx + s * 20.5, cy, 90, 2.6, 2.6, 1.4)
        cv.paint(ramp(GOLD_RAMP, 0.7 + 0.2 * facet_light(nx, ny)), cv.cov(dk) * 0.9)
    dk, nx, ny = kite(X, Y, cx, cy, 0, 5.5, 5.5, 7.0)
    cv.paint(NEAR_BLACK, cv.cov(dk - 1.1) * 0.75)
    cv.paint(ramp(GOLD_RAMP, 0.72 + 0.22 * facet_light(nx, ny)), cv.cov(dk))
    return cv.result()


def make_highlight():
    cv = Canvas(256, 32)
    X, Y = cv.X, cv.Y
    u = X / 256.0
    fade = smoothstep(0.0, 0.42, u) * smoothstep(0.0, 0.42, 1.0 - u)
    vy = np.abs(Y - 16.0) / 16.0
    body = (0.20 + 0.16 * (1 - vy ** 2)) * fade
    body = body * (0.85 + 0.15 * np.exp(-((u - 0.5) / 0.25) ** 2))
    cv.paint(hexc("B38E45"), body)
    cv.paint(GRACE, np.exp(-((u - 0.5) / 0.22) ** 2) * np.exp(-((Y - 16) / 9.0) ** 2) * 0.10)
    lfade = smoothstep(0.0, 0.48, u) * smoothstep(0.0, 0.48, 1.0 - u)
    for yy in (1.5, 30.5):
        glow = np.exp(-((Y - yy) / 1.8) ** 2) * 0.35
        cv.paint(GOLD, glow * lfade)
        cv.paint(GOLD_HI, cv.cov(np.abs(Y - yy) - 0.5) * lfade)
    return cv.result()


def make_panel():
    cv = Canvas(96, 96)
    X, Y = cv.X, cv.Y
    body = sd_rect(X, Y, 0.0, 0.0, 96.0, 96.0, 3.0)
    cv.paint(hexc("0E0C09"), cv.cov(body) * 0.85)
    # gold border (uniform along every edge so the 9-slice middle stretches cleanly)
    border = np.abs(sd_rect(X, Y, 4.0, 4.0, 92.0, 92.0, 1.5)) - 0.5
    cv.paint(GOLD, cv.cov(border) * 0.95)
    # corner flourishes (all within the 24px corners)
    flour = np.full_like(X, BIG)
    for sx in (0, 1):
        for sy in (0, 1):
            def P(x, y):
                return (96 - x if sx else x, 96 - y if sy else y)
            pts = [P(7.5, 18.0), P(7.5, 7.5), P(18.0, 7.5)]
            flour = sd_stroke(cv, pts, [0.35, 0.5, 0.35], out=flour)
            sc = np.array([P(*q) for q in qbez((9.5, 15.5), (11.0, 11.0), (15.5, 9.5), 12)])
            flour = sd_stroke(cv, sc, 0.38, out=flour)
            cx, cy = P(4.5, 4.5)
            dk, _, _ = kite(X, Y, cx, cy, 45, 3.2, 3.2, 1.9)
            flour = np.minimum(flour, dk)
            for q in ((20.5, 7.5), (7.5, 20.5)):
                qx, qy = P(*q)
                flour = np.minimum(flour, sd_circle(X, Y, qx, qy, 0.9))
    t = 0.66 + 0.18 * (0.5 - diag_grad(X, Y, 0, 0, 96, 96))
    cv.paint(NEAR_BLACK, cv.cov(flour - 0.7) * 0.6)
    cv.paint(ramp(GOLD_RAMP, t), cv.cov(flour))
    return cv.result()


# =============================================================================
# Minimap frames
# =============================================================================
def polar(cv, c=256.0):
    dx, dy = cv.X - c, cv.Y - c
    r = np.hypot(dx, dy)
    th = np.arctan2(dx, -dy)          # 0 = north, clockwise
    return r, th


def ring_ornament(cv, c, ang, kind, out_d, out_t):
    """Adds a tick/diamond to (out_d, out_t) arrays at polar angle ang (deg)."""
    a = math.radians(ang)
    ux, uy = math.sin(a), -math.cos(a)
    if kind == "minor":
        rm, ha, w = 234.0, 6.0, 1.5
    elif kind == "mid":
        rm, ha, w = 234.0, 8.5, 2.3
    elif kind == "cardinal":
        rm, ha, w = 234.0, 11.5, 5.2
    else:  # north
        rm, ha, w = 236.0, 16.5, 8.0
    cx, cy = c + rm * ux, c + rm * uy
    m = ha + w + 6
    wn = cv.win(cx - m - 8, cy - m - 8, cx + m + 8, cy + m + 8)
    sl, Xw, Yw = wn
    d, nx, ny = kite(Xw, Yw, cx, cy, ang, ha, ha, w)
    t = 0.66 + 0.24 * facet_light(nx, ny)
    if kind in ("cardinal", "north"):
        # side dots
        for s in (-1, 1):
            vx, vy = math.cos(a) * s, math.sin(a) * s
            off = w + 4.2
            dd = sd_circle(Xw, Yw, cx + off * vx, cy + off * vy, 1.7 if kind == "cardinal" else 2.1)
            lam = bevel_light(dd, 1.0, cv.k)
            tt = 0.68 + 0.25 * lam
            sel = dd < d
            d = np.where(sel, dd, d)
            t = np.where(sel, tt, t)
        if kind == "north":
            di, nxi, nyi = kite(Xw, Yw, cx, cy, ang, ha * 0.45, ha * 0.45, w * 0.45)
            ti = 0.86 + 0.12 * facet_light(nxi, nyi)
            t = np.where(di < 0, ti, t)
    od = out_d[sl]
    sel = d < od
    out_d[sl] = np.where(sel, d, od)
    out_t[sl] = np.where(sel, t, out_t[sl])


def make_minimap_ring():
    cv = Canvas(512, 512)
    X, Y = cv.X, cv.Y
    c = 256.0
    r, th = polar(cv, c)
    R0, R1 = 220.0, 248.0
    light_dir = math.radians(-35.0)
    angl = np.cos(th - light_dir).astype(np.float32)          # 1 at top-left
    # soft outer shadow
    sh = 0.70 * (1 - smoothstep(R1 - 2.0, 256.0, r)) ** 1.6
    cv.paint(SHADOW, sh * (r > R0 + 4))
    # bronze band with convex profile + brushed texture
    band = np.maximum(R0 - r, r - R1)
    u = np.clip((r - R0) / (R1 - R0), 0, 1)
    prof = np.clip(np.sin(np.pi * u), 0, 1) ** 0.8
    rng = np.random.default_rng(11)
    brush_r = gauss_blur(rng.standard_normal((1, 4096)).astype(np.float32), 1.2, wrap=True)[0]
    brush_r = brush_r / (np.abs(brush_r).max() + 1e-6)
    ridx = np.clip((r * 8).astype(np.int64), 0, 4095)
    tex = fft_noise(cv.H, cv.W, 1.5, 12)
    t = 0.40 + 0.30 * prof + 0.14 * angl * (0.4 + 0.6 * prof) + 0.03 * brush_r[ridx] + 0.025 * tex
    cv.paint(ramp(BRONZE_RAMP, t), cv.cov(band))
    # engraved channel: two hairlines and an interlaced running pattern
    eng = np.full_like(X, BIG)
    for rr in (226.2, 241.8):
        eng = np.minimum(eng, np.abs(r - rr) - 0.55)
    n_wave = 48
    amp = 3.4
    for ph in (0.0, math.pi):
        rw = 234.0 + amp * np.sin(n_wave * th + ph)
        slope = amp * n_wave / 234.0
        dw = np.abs(r - rw) / np.sqrt(1 + (slope * np.cos(n_wave * th + ph)) ** 2) - 0.55
        eng = np.minimum(eng, dw)
    # tiny bosses in the eyes of the braid
    eyes = np.mod(th * n_wave / (2 * math.pi) * 2.0, 1.0) - 0.5          # crossings every half wave
    ang_d = np.abs(eyes) * (2 * math.pi * 234.0 / (n_wave * 2.0))         # arc distance to nearest eye centre
    boss = np.hypot(ang_d - (math.pi * 234.0 / (n_wave * 2.0)), r - 234.0) - 0.9
    eng = np.minimum(eng, boss)
    # incised look: dark groove offset outward, deep-gold line on top
    cv.paint(hexc("0E0B07"), cv.cov(eng - 0.35) * 0.55)
    cv.paint(hexc("8A6B2E"), cv.cov(eng) * 0.55)
    # gold bevel lines
    inner = np.maximum(R0 - r, r - (R0 + 3.0))
    outer = np.maximum((R1 - 3.0) - r, r - R1)
    for dline in (inner, outer):
        lam = bevel_light(dline, 1.3, cv.k)
        tt = 0.62 + 0.24 * lam + 0.18 * angl
        cv.paint(ramp(GOLD_RAMP, tt), cv.cov(dline))
    cv.paint(hexc("0E0B07"), cv.cov(np.abs(r - (R0 + 3.6)) - 0.5) * 0.8)
    cv.paint(hexc("0E0B07"), cv.cov(np.abs(r - (R1 - 3.6)) - 0.5) * 0.8)
    # ticks
    od = np.full_like(X, BIG)
    ot = np.zeros_like(X)
    for k in range(16):
        ang = k * 22.5
        if k == 0:
            kind = "north"
        elif k % 4 == 0:
            kind = "cardinal"
        elif k % 2 == 0:
            kind = "mid"
        else:
            kind = "minor"
        ring_ornament(cv, c, ang, kind, od, ot)
    cv.paint(NEAR_BLACK, cv.cov(od - 1.3) * 0.9)
    cv.paint(ramp(GOLD_RAMP, ot + 0.10 * angl), cv.cov(od))
    arr = cv.result()
    # contract: hole of radius 220 is exactly transparent
    yy, xx = np.mgrid[0:512, 0:512]
    hole = np.hypot(xx + 0.5 - 256, yy + 0.5 - 256) < 220.0
    arr[hole] = 0
    return arr


def make_minimap_square():
    cv = Canvas(512, 512)
    X, Y = cv.X, cv.Y
    hole = sd_rect(X, Y, 36, 36, 476, 476)
    outer = sd_rect(X, Y, 8, 8, 504, 504, 5.0)
    # shadow
    cv.paint(SHADOW, (1 - smoothstep(-2.0, 8.0, outer)) ** 1.5 * 0.7 * (outer > -4))
    band = np.maximum(outer, -hole)
    s = np.clip(hole / 28.0, 0, 1)   # 0 at hole edge .. 1 at outer edge
    prof = np.clip(np.sin(np.pi * s), 0, 1) ** 0.8
    h = prof * 6.0
    lam = height_light(h, cv.k, 1.0)
    tex = fft_noise(cv.H, cv.W, 1.5, 21)
    t = 0.42 + 0.26 * prof + 0.16 * lam + 0.025 * tex
    cv.paint(ramp(BRONZE_RAMP, t), cv.cov(band))
    # engraved hairlines inside the band
    eng = U(np.abs(hole - 6.5) - 0.45, np.abs(hole - 21.5) - 0.45)
    cv.paint(hexc("120E08"), cv.cov(eng) * 0.75)
    cv.paint(GOLD_DEEP, cv.cov(eng + 0.15) * 0.55)
    # gold double border (inner bevel at the hole edge and outer bevel)
    gin = np.maximum(-hole, hole - 3.0)
    gout = np.maximum(-outer - 3.0, outer)
    for dline in (gin, gout):
        lam = bevel_light(dline, 1.3, cv.k)
        tt = 0.64 + 0.26 * lam + 0.10 * (0.5 - diag_grad(X, Y, 0, 0, 512, 512))
        cv.paint(ramp(GOLD_RAMP, tt), cv.cov(dline))
    cv.paint(hexc("0E0B07"), cv.cov(np.abs(hole - 3.6) - 0.5) * 0.8)
    cv.paint(hexc("0E0B07"), cv.cov(np.abs(outer + 3.6) - 0.5) * 0.8)
    # ornaments
    od = np.full_like(X, BIG)
    ot = np.zeros_like(X)
    om = np.zeros_like(X)        # material: 0 = gold, 1 = dark bronze inlay

    def add(d, t, mat=0.0):
        nonlocal od, ot, om
        sel = d < od
        od = np.where(sel, d, od)
        ot = np.where(sel, t, ot)
        om = np.where(sel, mat, om)

    def inlay(d_in, t, mat=0.0, edge_t=0.30):
        """Re-material a region that lies inside an ornament already added."""
        nonlocal ot, om
        sel = d_in < 0
        ot = np.where(sel, t, ot)
        om = np.where(sel, mat, om)
        edge = np.abs(d_in) < 0.55
        ot = np.where(edge, edge_t, ot)
        om = np.where(edge, 0.0, om)

    def band_mapper(cx, cy, sx, sy, axis):
        def P(a, b):
            # a: along the edge away from (cx, cy); b: across the band from its outer side
            if axis == 0:
                return (cx + sx * a, 8.0 + b if sy > 0 else 504.0 - b)
            return (8.0 + b if sx > 0 else 504.0 - b, cy + sy * a)
        return P

    def filigree(P, a0, length, curl_r, hand, branch=True):
        pts, n1 = curl_path(a0, 14.0, 0.0, length, curl_r, hand, turns=1.05)
        dd = sd_stroke(cv, np.array([P(a, b) for a, b in pts]), filigree_widths(len(pts), n1, 1.55, 0.55))
        if branch:
            bp, m1 = curl_path(a0 + length * 0.55, 14.0, 38.0 * -hand, 5.0, curl_r * 0.72, -hand, turns=1.0)
            dd = sd_stroke(cv, np.array([P(a, b) for a, b in bp]), filigree_widths(len(bp), m1, 1.15, 0.45), out=dd)
            leaf = qbez((a0 + 4.0, 14.0), (a0 + length * 0.30, 14.0 + 7.0 * hand), (a0 + length * 0.48, 14.0 + 5.5 * hand), 18)
            lw = 0.35 + 1.25 * np.sin(np.linspace(0, math.pi, len(leaf))) ** 0.8
            dd = sd_stroke(cv, np.array([P(a, b) for a, b in leaf]), lw, out=dd)
        return dd

    corners = [(22.0, 22.0, 1, 1), (490.0, 22.0, -1, 1), (22.0, 490.0, 1, -1), (490.0, 490.0, -1, -1)]
    for cx, cy, sx, sy in corners:
        dk, nx, ny = kite(X, Y, cx, cy, 0, 14.5, 14.5, 14.5)
        add(dk, 0.66 + 0.24 * facet_light(nx, ny))
        di, nx, ny = kite(X, Y, cx, cy, 0, 7.5, 7.5, 7.5)
        inlay(di, 0.66 + 0.16 * facet_light(nx, ny), 1.0)
        dg = sd_circle(X, Y, cx, cy, 2.8)
        inlay(dg, 0.86 + 0.12 * bevel_light(dg, 1.4, cv.k))
        for axis in (0, 1):
            P = band_mapper(cx, cy, sx, sy, axis)
            dd = filigree(P, 16.0, 36.0, 5.2, -1)
            for a, r_ in ((68.0, 1.6), (73.5, 1.0)):
                dd = np.minimum(dd, sd_circle(X, Y, *P(a, 14.0), r_))
            add(dd, 0.70 + 0.18 * bevel_light(dd, 0.9, cv.k))
    # edge-centre diamonds with small flanking scrolls
    for (cx, cy, axis, big) in ((256.0, 22.0, 0, True), (256.0, 490.0, 0, False), (22.0, 256.0, 1, False), (490.0, 256.0, 1, False)):
        ang = {(0, True): 0, (0, False): 180, (1, False): 270 if cx < 256 else 90}[(axis, big)]
        a, w = (13.5, 8.5) if big else (11.0, 6.5)
        dk, nx, ny = kite(X, Y, cx, cy, ang, a, a, w)
        add(dk, 0.66 + 0.24 * facet_light(nx, ny))
        if big:
            di, nx, ny = kite(X, Y, cx, cy, ang, a * 0.45, a * 0.45, w * 0.45)
            inlay(di, 0.88 + 0.1 * facet_light(nx, ny), 0.0, edge_t=0.40)
        sy = 1 if cy < 256 else -1
        sx = 1 if cx < 256 else -1
        for s in (-1, 1):
            if axis == 0:
                P = band_mapper(cx, cy, s, sy, 0)
            else:
                P = band_mapper(cx, cy, sx, s, 1)
            dd = filigree(P, w + 3.0, 9.0, 4.2, -1, branch=False)
            dd = np.minimum(dd, sd_circle(X, Y, *P(w + 24.0, 14.0), 1.4))
            add(dd, 0.70 + 0.18 * bevel_light(dd, 0.9, cv.k))
    cv.paint(NEAR_BLACK, cv.cov(od - 1.3) * 0.9)
    tt = ot + 0.10 * (0.5 - diag_grad(X, Y, 0, 0, 512, 512))
    col = np.where((om > 0.5)[..., None], ramp(BRONZE_RAMP, ot), ramp(GOLD_RAMP, tt))
    cv.paint(col, cv.cov(od))
    arr = cv.result()
    arr[36:476, 36:476] = 0
    return arr


def make_vignette_disc():
    cv = Canvas(256, 256)
    r = np.hypot(cv.X - 128, cv.Y - 128) / 128.0
    t = np.clip((r - 0.62) / 0.38, 0, 1)
    a = 0.6 * t ** 1.7 * (3 - 2 * t) / (1 + 0.0 * t)
    a = 0.6 * (t * t * (3 - 2 * t)) ** 0.85
    cv.paint(hexc("1A1208"), a * cv.cov((r - 1.0) * 128.0))
    arr = cv.result()
    yy, xx = np.mgrid[0:256, 0:256]
    arr[np.hypot(xx + 0.5 - 128, yy + 0.5 - 128) > 128.0] = 0
    return arr


def make_vignette_square():
    cv = Canvas(256, 256)
    X, Y = cv.X, cv.Y
    dx = np.minimum(X, 256 - X) / 128.0
    dy = np.minimum(Y, 256 - Y) / 128.0
    fx = (1 - smoothstep(0.0, 0.40, dx)) ** 1.3 * 0.58
    fy = (1 - smoothstep(0.0, 0.40, dy)) ** 1.3 * 0.58
    a = 1 - (1 - fx) * (1 - fy)
    cv.paint(hexc("1A1208"), a)
    return cv.result()


# =============================================================================
# Compass rose
# =============================================================================
def make_compass_rose():
    cv = Canvas(256, 256)
    X, Y = cv.X, cv.Y
    c = 128.0
    r = np.hypot(X - c, Y - c)
    items = []
    for ang in (0, 90, 180, 270):
        items.append(spike(X, Y, c, c, ang, 86.0, 7.5, r0=7.0, back=0.0))
    for ang in (45, 135, 225, 315):
        items.append(spike(X, Y, c, c, ang, 48.0, 5.0, r0=5.0, back=0.0))
    dmain, nx, ny = union_facets(items[:4])
    ddiag, nx2, ny2 = union_facets(items[4:])
    ring_line = np.abs(r - 60.0) - 0.9
    center = np.abs(r - 8.0) - 2.0
    d_all = U(dmain, ddiag, ring_line, center)
    # thin outline + subtle shadow so it reads on any background
    cv.paint(NEAR_BLACK, cv.blur(cv.cov(d_all - 1.0), 1.4) * 0.45)
    cv.paint(NEAR_BLACK, cv.cov(d_all - 1.25) * 0.9)
    cv.paint(ramp(SILVER_RAMP, np.float32(0.70)), cv.cov(ring_line))
    for ang in range(0, 360, 15):
        if ang % 45 == 0:
            continue
        a = math.radians(ang)
        dd = sd_circle(X, Y, c + 60 * math.sin(a), c - 60 * math.cos(a), 1.6)
        cv.paint(NEAR_BLACK, cv.cov(dd - 0.9) * 0.9)
        cv.paint(ramp(SILVER_RAMP, np.float32(0.80)), cv.cov(dd))

    def classic(nx_, ny_, dd, base):
        # classic compass shading: the clockwise half of every point is lit, the other
        # half shaded, plus a little directional light from the top-left
        side = np.sign((X - c) * ny_ - (Y - c) * nx_)
        fl = facet_light(nx_, ny_)
        tt = base + 0.15 * side + 0.08 * fl - 0.05 * (r / 90.0)
        return ramp(SILVER_RAMP, tt)
    cv.paint(NEAR_BLACK, cv.cov(ddiag - 1.0) * 0.9)
    cv.paint(classic(nx2, ny2, ddiag, 0.66), cv.cov(ddiag))
    cv.paint(NEAR_BLACK, cv.cov(dmain - 1.0) * 0.9)
    cv.paint(classic(nx, ny, dmain, 0.70), cv.cov(dmain))
    cv.paint(NEAR_BLACK, cv.cov(center - 1.0))
    lam = bevel_light(center, 1.2, cv.k)
    cv.paint(ramp(SILVER_RAMP, 0.78 + 0.2 * lam), cv.cov(center))
    cv.paint(ramp(SILVER_RAMP, np.float32(0.9)), cv.cov(sd_circle(X, Y, c, c, 2.2)))
    # letters
    for letter, ang, size in (("N", 0, 27.0), ("E", 90, 22.0), ("S", 180, 22.0), ("W", 270, 22.0)):
        a = math.radians(ang)
        rr = 107.0 if letter == "N" else 106.0
        lx, ly = c + rr * math.sin(a), c - rr * math.cos(a)
        m_out = text_mask(cv, letter, lx, ly, size, SERIF_BOLD, stroke=1.6)
        m_in = text_mask(cv, letter, lx, ly, size, SERIF_BOLD)
        cv.paint(NEAR_BLACK, cv.blur(m_out, 1.2) * 0.4)
        cv.paint(NEAR_BLACK, m_out)
        tt = 0.88 - 0.22 * smoothstep(ly - size / 2, ly + size / 2, Y)
        cv.paint(ramp(SILVER_RAMP, tt), m_in)
    return cv.result()


# =============================================================================
# Time dial
# =============================================================================
def make_time_dial_face():
    cv = Canvas(256, 256)
    X, Y = cv.X, cv.Y
    c = 128.0
    dx, dy = X - c, Y - c
    r = np.hypot(dx, dy)
    day = ramp([(0.0, "E3A955"), (0.18, "EBC57A"), (0.45, "F1DDA6"), (1.0, "F4ECD6")], np.clip(-dy / 118.0, 0, 1))
    night = ramp([(0.0, "3A3550"), (0.12, "222642"), (0.45, "131830"), (1.0, "080A16")], np.clip(dy / 118.0, 0, 1))
    k = smoothstep(-9.0, 9.0, dy)
    col = mix(day, night, k)
    # horizon glow (dawn / dusk)
    hz = np.exp(-(dy / 7.0) ** 2)
    col = mix(col, hexc("F09A55"), hz * 0.45)
    # wispy clouds in the day half
    clouds = fft_noise(cv.H, cv.W, 2.2, 31)
    cl = smoothstep(0.6, 1.6, clouds) * (1 - k) * smoothstep(12, 40, -dy)
    col = mix(col, hexc("FBF6E8"), cl * 0.35)
    cv.paint(col, cv.cov(r - 120.0))
    # sun
    sx, sy = c, c - 78.0
    rs = np.hypot(X - sx, Y - sy)
    cv.paint(hexc("FFE6A0"), np.exp(-(rs / 20.0) ** 2) * 0.55)
    rays = np.full_like(X, BIG)
    for kk in range(12):
        a = kk * 30.0
        L = 23.5 if kk % 2 == 0 else 19.5
        dd, _, _ = spike(X, Y, sx, sy, a, L, 3.2 if kk % 2 == 0 else 2.6, r0=12.0)
        rays = np.minimum(rays, dd)
    sun = sd_circle(X, Y, sx, sy, 12.5)
    cv.paint(hexc("8A5A1E"), cv.cov(np.minimum(rays, sun) - 1.0) * 0.55)
    cv.paint(ramp(GOLD_RAMP, np.float32(0.80)), cv.cov(rays))
    lam = bevel_light(sun, 4.0, cv.k)
    cv.paint(ramp([(0, "E09A30"), (0.5, "F5C451"), (1.0, "FFF4C8")], 0.55 + 0.35 * lam + 0.25 * (1 - smoothstep(0, 12, rs))), cv.cov(sun))
    # moon
    mx, my = c, c + 80.0
    rm = np.hypot(X - mx, Y - my)
    cv.paint(hexc("C9D3EA"), np.exp(-(rm / 22.0) ** 2) * 0.28)
    moon = SUB(sd_circle(X, Y, mx, my, 13.0), sd_circle(X, Y, mx + 6.0, my - 4.5, 11.0))
    cv.paint(hexc("05060C"), cv.cov(moon - 0.8) * 0.5)
    lam = bevel_light(moon, 3.0, cv.k)
    cv.paint(ramp(SILVER_RAMP, 0.80 + 0.16 * lam), cv.cov(moon))
    # stars
    rng = np.random.default_rng(41)
    placed = 0
    tries = 0
    while placed < 30 and tries < 2000:
        tries += 1
        a = rng.uniform(0, math.pi)
        rr = rng.uniform(22, 112)
        px, py = c + rr * math.cos(a), c + rr * math.sin(a)
        if py < c + 10 or math.hypot(px - mx, py - my) < 22:
            continue
        size = rng.choice([0.55, 0.7, 0.85, 1.0, 1.3])
        alpha = rng.uniform(0.55, 1.0)
        rs2 = np.hypot(X - px, Y - py)
        if size >= 1.3:
            star = U(sd_segment(X, Y, px - 3.5, py, px + 3.5, py, 0.35), sd_segment(X, Y, px, py - 3.5, px, py + 3.5, 0.35))
            cv.paint(hexc("F4F1FF"), cv.cov(star, soft=0.3) * alpha)
        cv.paint(hexc("FFFFFF"), cv.cov(rs2 - size, soft=0.2) * alpha)
        cv.paint(hexc("BFD0FF"), np.exp(-(rs2 / (size * 2.5)) ** 2) * alpha * 0.25)
        placed += 1
    # inner rim shading
    cv.paint(hexc("0A0805"), smoothstep(108.0, 120.0, r) * 0.35 * cv.cov(r - 120.0))
    arr = cv.result()
    yy, xx = np.mgrid[0:256, 0:256]
    arr[np.hypot(xx + 0.5 - 128, yy + 0.5 - 128) > 120.75] = 0
    return arr


def make_time_dial_frame():
    cv = Canvas(256, 256)
    X, Y = cv.X, cv.Y
    c = 128.0
    dx, dy = X - c, Y - c
    r = np.hypot(dx, dy)
    th = np.arctan2(dx, -dy)
    angl = np.cos(th - math.radians(-35.0))
    RW, RO = 104.0, 122.0
    window = np.maximum(r - RW, dy)            # < 0 inside the opening
    disc = r - RO
    shape = np.maximum(disc, -window)          # bezel + lower plate
    # outer shadow
    cv.paint(SHADOW, (1 - smoothstep(RO - 2, 128.0, r)) ** 1.5 * 0.65 * (r > RO - 3))
    # soft inner shadow cast into the window (cosmetic depth)
    ins = np.clip(-window, 0, None)
    cv.paint(SHADOW, (1 - smoothstep(0.0, 7.0, ins)) * 0.40 * cv.cov(window))
    # bronze body
    u = np.clip((r - RW) / (RO - RW), 0, 1)
    prof = np.where(dy < 0, np.clip(np.sin(np.pi * u), 0, 1) ** 0.8, 0.55 + 0.25 * np.clip(np.sin(np.pi * u), 0, 1) ** 0.8)
    tex = fft_noise(cv.H, cv.W, 1.5, 51)
    plate_t = 0.40 - 0.10 * smoothstep(0, 100, dy) + 0.03 * tex
    band_t = 0.40 + 0.30 * prof + 0.14 * angl * prof + 0.025 * tex
    t = np.where((dy > 2.5) & (r < RW + 0.5), plate_t, band_t)
    cv.paint(ramp(BRONZE_RAMP, t), cv.cov(shape))
    # plate engravings
    eng = np.full_like(X, BIG)
    eng = np.minimum(eng, np.maximum(np.abs(r - 92.0) - 0.45, -(dy - 8.0)))
    eng = np.minimum(eng, np.maximum(np.abs(r - 52.0) - 0.45, -(dy - 8.0)))
    for ang in range(105, 256, 15):
        a = math.radians(ang)
        ux, uy = math.sin(a), -math.cos(a)
        eng = np.minimum(eng, sd_segment(X, Y, c + 56 * ux, c + 56 * uy, c + 88 * ux, c + 88 * uy, 0.45))
    cv.paint(hexc("0E0B07"), cv.cov(eng) * 0.8)
    cv.paint(GOLD_DEEP, cv.cov(eng + 0.12) * 0.55)
    # gold lines: outer bevel, inner bevel around the window, horizon trim
    gout = np.maximum(r - RO, (RO - 3.0) - r)
    gwin = np.maximum(np.maximum(r - (RW + 3.0), RW - r), dy - 1.5)
    trim = np.maximum(np.abs(dy - 1.6) - 1.6, r - (RW + 1.0))
    for dline in (gout, np.minimum(gwin, trim)):
        lam = bevel_light(dline, 1.2, cv.k)
        tt = 0.64 + 0.24 * lam + 0.14 * angl
        cv.paint(ramp(GOLD_RAMP, tt), cv.cov(dline))
    cv.paint(hexc("0E0B07"), cv.cov(np.abs(r - (RO - 3.6)) - 0.45) * 0.8)
    cv.paint(hexc("0E0B07"), cv.cov(np.maximum(np.abs(r - (RW + 3.6)) - 0.45, dy)) * 0.8)
    # ornaments: ticks around the upper arc, horizon diamonds, centre medallion
    od = np.full_like(X, BIG)
    ot = np.zeros_like(X)

    def add(d, tt):
        nonlocal od, ot
        sel = d < od
        od = np.where(sel, d, od)
        ot = np.where(sel, tt, ot)
    for ang in range(-75, 76, 15):
        if ang == 0:
            continue
        a = math.radians(ang)
        ux, uy = math.sin(a), -math.cos(a)
        big = ang % 45 == 0
        dk, nx, ny = kite(X, Y, c + 113.0 * ux, c + 113.0 * uy, ang, 4.5 if big else 3.2, 4.5 if big else 3.2, 2.2 if big else 1.4)
        add(dk, 0.66 + 0.22 * facet_light(nx, ny))
    for s in (-1, 1):
        dk, nx, ny = kite(X, Y, c + s * 113.0, c + 1.6, 90 * s, 7.5, 7.5, 5.0)
        add(dk, 0.66 + 0.24 * facet_light(nx, ny))
    # centre medallion on the plate
    dk, nx, ny = kite(X, Y, c, c + 28.0, 0, 13.0, 13.0, 9.0)
    add(dk, 0.66 + 0.24 * facet_light(nx, ny))
    di, nx, ny = kite(X, Y, c, c + 28.0, 0, 6.0, 6.0, 4.2)
    add(np.where(di < 0, di - 0.01, BIG).astype(np.float32), 0.88 + 0.1 * facet_light(nx, ny))
    for s in (-1, 1):
        main = cbez((c + s * 12, c + 28), (c + s * 30, c + 16), (c + s * 46, c + 40), (c + s * 64, c + 26), 32)
        dd = sd_stroke(cv, main, taper(len(main), 1.6, 0.7, wmid=1.4))
        cx2, cy2 = c + s * 64, c + 26
        curl = spiral_pts(cx2 + s * 0.0, cy2 + 5.0, 5.0, 0.8, -90, -90 + s * 320, 30)
        dd = sd_stroke(cv, curl, taper(len(curl), 0.8, 0.5), out=dd)
        leaf = cbez((c + s * 34, c + 25), (c + s * 38, c + 36), (c + s * 46, c + 50), (c + s * 40, c + 56), 20)
        dd = sd_stroke(cv, leaf, taper(len(leaf), 1.0, 0.3), out=dd)
        dd = np.minimum(dd, sd_circle(X, Y, c + s * 80.0, c + 18.0, 1.8))
        add(dd, 0.70 + 0.18 * bevel_light(dd, 0.9, cv.k))
    # pointer (top centre, pointing down into the window)
    ptr = sd_polygon(X, Y, [(c - 8.0, 9.0), (c + 8.0, 9.0), (c, 36.0)])
    ptr = smin(ptr, sd_circle(X, Y, c, 12.5, 5.0), 2.0)
    pside = np.where(X < c, -1.0, 1.0)
    add(ptr, 0.70 + 0.18 * facet_light(pside, np.full_like(X, -0.3)))
    cv.paint(NEAR_BLACK, cv.cov(od - 1.2) * 0.9)
    cv.paint(ramp(GOLD_RAMP, ot + 0.06 * angl), cv.cov(od))
    cv.paint(WHITE, np.exp(-(((X - c + 1.5) ** 2 + (Y - 10.0) ** 2) / 4.0)) * 0.5)
    return cv.result()


# =============================================================================
# Tileable textures
# =============================================================================
def tile_canvas(size=256):
    return Canvas(size, size)


def make_paper(size=256, seed=101):
    ss = SS
    N = size * ss
    cv = tile_canvas(size)
    cyc = 1.0 / N                                   # one cycle per tile, in cycles/pixel
    # band-limited mottling: no 1-2 cycle-per-tile modes, so tiling is not obvious
    n1 = fft_noise(N, N, 2.0, seed, cutoff=9 * cyc, lowcut=3.2 * cyc)
    n2 = fft_noise(N, N, 1.6, seed + 1, cutoff=28 * cyc, lowcut=8 * cyc)
    n3 = fft_noise(N, N, 1.0, seed + 2, cutoff=0.25)
    stains = fft_noise(N, N, 2.0, seed + 3, cutoff=10 * cyc, lowcut=4 * cyc)
    col = np.broadcast_to(PARCH, (N, N, 3)).copy()
    col = mix(col, PARCH_D, np.clip(0.26 + 0.17 * n1, 0, 1))
    col = mix(col, hexc("D9CCA6"), np.clip(0.18 * n2, 0, 0.35))
    col = col * (1.0 + 0.014 * n3)[..., None]
    st = smoothstep(1.4, 2.4, stains)
    col = mix(col, hexc("AE9866"), st * 0.18)
    rim = np.exp(-((stains - 1.55) / 0.10) ** 2) * 0.06
    col = mix(col, hexc("8E7748"), rim)
    # fibres (drawn with wrap-around so the tile stays seamless)
    rng = np.random.default_rng(seed + 9)
    light = np.full((N, N), BIG, np.float32)
    dark = np.full((N, N), BIG, np.float32)
    for i in range(520):
        x, y = rng.uniform(0, size, 2)
        L = rng.uniform(4, 16)
        a = rng.uniform(0, math.pi)
        bend = rng.uniform(-2.5, 2.5)
        ux, uy = math.cos(a), math.sin(a)
        p0 = (x - ux * L / 2, y - uy * L / 2)
        p2 = (x + ux * L / 2, y + uy * L / 2)
        p1 = (x - uy * bend, y + ux * bend)
        pts = qbez(p0, p1, p2, 8)
        w = rng.uniform(0.18, 0.40)
        tgt = light if i % 3 else dark
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                q = pts + np.array([ox, oy])
                if q[:, 0].max() < -2 or q[:, 0].min() > size + 2 or q[:, 1].max() < -2 or q[:, 1].min() > size + 2:
                    continue
                sd_stroke(cv, q, taper(len(q), w * 0.3, w * 0.3, wmid=w), out=tgt, margin=2.0)
    col = mix(col, hexc("E6DBBE"), cv.cov(light) * 0.22)
    col = mix(col, hexc("8F7A4E"), cv.cov(dark) * 0.16)
    # specks
    specks = np.zeros((N, N), np.float32)
    for i in range(70):
        x, y = rng.uniform(0, size, 2)
        rr = rng.uniform(0.25, 0.7)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                if -3 < x + ox < size + 3 and -3 < y + oy < size + 3:
                    d = field(cv, (x + ox - 3, y + oy - 3, x + ox + 3, y + oy + 3), lambda X, Y: sd_circle(X, Y, x + ox, y + oy, rr))
                    specks = np.maximum(specks, cv.cov(d) * rng.uniform(0.15, 0.4))
    col = mix(col, hexc("5E4A2A"), specks)
    out = np.stack([lanczos_down_wrap(col[..., k], size, size, ss) for k in range(3)] + [np.ones((size, size), np.float32)], -1)
    return finalize_premult(out)


def swirl_path(cx, cy, R, hand, a0, turns, tail, bend):
    """Stylised cloud curl: a spiral unwinding outwards that leaves in a long tail."""
    n = 70
    t = np.linspace(0.0, 1.0, n)
    ang = a0 + hand * turns * 2.0 * math.pi * t
    rr = R * (0.10 + 0.90 * t ** 0.85)
    pts = [np.stack([cx + rr * np.cos(ang), cy + rr * np.sin(ang)], 1)]
    p = pts[0][-1].copy()
    d = pts[0][-1] - pts[0][-2]
    heading = math.atan2(d[1], d[0])
    k0 = hand / R
    steps = max(4, int(tail / 1.0))
    tail_pts = []
    for i in range(steps):
        s = i / steps
        k = k0 * (1.0 - s) * 0.9 - hand * bend / R * s
        heading += k * 1.0
        p = p + np.array([math.cos(heading), math.sin(heading)])
        tail_pts.append(p.copy())
    return np.concatenate([pts[0], np.array(tail_pts)], 0), n


def draw_wrapped(cv, size, pts, widths, out):
    for ox in (-size, 0, size):
        for oy in (-size, 0, size):
            q = pts + np.array([ox, oy])
            if q[:, 0].max() < -3 or q[:, 0].min() > size + 3 or q[:, 1].max() < -3 or q[:, 1].min() > size + 3:
                continue
            sd_stroke(cv, q, widths, out=out, margin=2.0)
    return out


def make_fog(size=256, seed=202):
    ss = SS
    N = size * ss
    cv = tile_canvas(size)
    cyc = 1.0 / N
    n1 = fft_noise(N, N, 2.0, seed, cutoff=8 * cyc, lowcut=3.0 * cyc)
    n2 = fft_noise(N, N, 1.6, seed + 1, cutoff=24 * cyc, lowcut=7 * cyc)
    n3 = fft_noise(N, N, 1.0, seed + 2, cutoff=0.25)
    cool = fft_noise(N, N, 2.0, seed + 3, cutoff=8 * cyc, lowcut=3.0 * cyc)
    t = np.clip(0.52 + 0.21 * n1 + 0.08 * n2, 0, 1)
    col = mix(hexc("8E9394"), hexc("A8A9A1"), t)
    col = mix(col, hexc("8A959C"), np.clip(0.22 * cool, 0, 0.28))
    col = col * (1.0 + 0.010 * n3)[..., None]
    # very faint swirling cloud line-work (wrap-around strokes keep it seamless)
    rng = np.random.default_rng(seed + 9)
    lines = np.full((N, N), BIG, np.float32)
    # jittered grid of curl centres so the motif is evenly spread over the tile
    g = 2
    cells = [(i, j) for i in range(g) for j in range(g)]
    for (i, j) in cells:
        cx = (i + rng.uniform(0.15, 0.85)) * size / g
        cy = (j + rng.uniform(0.15, 0.85)) * size / g
        R = rng.uniform(17.0, 25.0)
        hand = 1 if rng.random() < 0.5 else -1
        a0 = rng.uniform(0, 2 * math.pi)
        pts, n_sp = swirl_path(cx, cy, R, hand, a0, rng.uniform(1.15, 1.5), R * rng.uniform(3.5, 5.0), rng.uniform(0.25, 0.6))
        m = len(pts)
        w = np.concatenate([np.linspace(0.25, 0.70, n_sp), np.linspace(0.70, 0.12, m - n_sp)])
        draw_wrapped(cv, size, pts, w, lines)
        # companion inner curl for the stylised double line
        pts2, n2_ = swirl_path(cx, cy, R * 0.62, hand, a0 + hand * 0.3, rng.uniform(0.8, 1.0), R * 1.2, 0.2)
        w2 = np.concatenate([np.linspace(0.18, 0.45, n2_), np.linspace(0.45, 0.10, len(pts2) - n2_)])
        draw_wrapped(cv, size, pts2, w2, lines)
    vis = smoothstep(-0.8, 0.6, fft_noise(N, N, 2.0, seed + 7, cutoff=8 * cyc, lowcut=2.5 * cyc))
    col = mix(col, hexc("6B7273"), cv.cov(lines) * (0.12 + 0.14 * vis))
    out = np.stack([lanczos_down_wrap(col[..., k], size, size, ss) for k in range(3)] + [np.ones((size, size), np.float32)], -1)
    return finalize_premult(out)


# =============================================================================
# Decorative: map icon, cloud corner, mod logo
# =============================================================================
def make_map_icon():
    cv = Canvas(64, 64)
    X, Y = cv.X, cv.Y
    panels = [[(9.0, 15.0), (24.0, 10.0), (24.0, 50.0), (9.0, 55.0)],
              [(24.0, 10.0), (40.0, 15.0), (40.0, 55.0), (24.0, 50.0)],
              [(40.0, 15.0), (55.0, 10.0), (55.0, 50.0), (40.0, 55.0)]]
    ds = [sd_polygon(X, Y, P) for P in panels]
    d = U(*ds)
    cv.paint(SHADOW, cv.blur(cv.cov(d - 2.0), 1.5) * 0.5)
    cv.paint(NEAR_BLACK, cv.cov(d - 2.2))
    # gold rim
    lam = bevel_light(d, 1.2, cv.k)
    cv.paint(ramp(GOLD_RAMP, 0.66 + 0.25 * lam + 0.12 * (0.5 - diag_grad(X, Y, 9, 10, 55, 55))), cv.cov(d))
    paper = [hexc("DCCDA2"), hexc("B9A77A"), hexc("D3C398")]
    for k, dk in enumerate(ds):
        inner = dk + 2.2
        tint = paper[k] * (1.0 - 0.10 * diag_grad(X, Y, 9, 10, 55, 55))[..., None]
        cv.paint(tint, cv.cov(inner))
    # ink details
    clip = cv.cov(d + 2.4)
    coast = join(qbez((12, 22), (20, 20), (22, 28), 12), qbez((22, 28), (25, 36), (33, 33), 12), qbez((33, 33), (40, 30), (44, 38), 12),
                 qbez((44, 38), (47, 44), (52, 44), 10))
    dc = sd_stroke(cv, coast, 0.55)
    cv.paint(INK, cv.cov(dc) * clip * 0.85)
    route = join(qbez((14, 46), (22, 40), (28, 44), 14), qbez((28, 44), (36, 49), (40, 24), 16), qbez((40, 24), (44, 18), (48, 22), 10))
    seg_len = np.cumsum(np.r_[0, np.hypot(*np.diff(route, axis=0).T)])
    dr = np.full_like(X, BIG)
    for k in range(len(route) - 1):
        if int(seg_len[k] / 2.6) % 2 == 0:
            dr = np.minimum(dr, sd_segment(X, Y, route[k][0], route[k][1], route[k + 1][0], route[k + 1][1], 0.75))
    cv.paint(CRIMSON, cv.cov(dr) * clip)
    xm = U(sd_segment(X, Y, 46, 17, 51, 22, 1.0), sd_segment(X, Y, 51, 17, 46, 22, 1.0))
    cv.paint(CRIMSON, cv.cov(xm) * clip)
    # fold shading
    for x in (24.0, 40.0):
        cv.paint(hexc("6E5A33"), cv.cov(np.abs(X - x) - 0.45) * cv.cov(d) * 0.6)
    return cv.result()


def cloud_chain(curve, rmax, seed, jitter=0.16, r_lo=0.30, push=(0.15, 0.40)):
    """Places cloud lobes along a curve; lobe size swells in the middle of the curve."""
    rng = np.random.default_rng(seed)
    seg = np.hypot(*np.diff(curve, axis=0).T)
    cum = np.r_[0.0, np.cumsum(seg)]
    total = cum[-1]
    lobes = []
    s = rmax * 0.12
    while s < total - rmax * 0.05:
        u = s / total
        i = min(int(np.searchsorted(cum, s)), len(curve) - 2)
        p = curve[i]
        tg = curve[i + 1] - curve[max(i - 1, 0)]
        nrm = np.array([tg[1], -tg[0]]) / (np.hypot(*tg) + 1e-9)
        if nrm @ (p - np.array([-40.0, -40.0])) < 0:
            nrm = -nrm
        r = rmax * (r_lo + (1 - r_lo) * math.sin(math.pi * u) ** 0.75) * (1.0 + jitter * rng.uniform(-1, 1))
        c = p + nrm * r * rng.uniform(*push)
        lobes.append((float(c[0]), float(c[1]), float(r), nrm))
        s += r * rng.uniform(0.78, 0.98)
    return lobes


def outer_cusp(l1, l2):
    (x1, y1, r1, n1), (x2, y2, r2, n2) = l1, l2
    c1, c2 = np.array([x1, y1]), np.array([x2, y2])
    d = float(np.hypot(*(c2 - c1)))
    if d >= r1 + r2 - 0.5 or d <= abs(r1 - r2) + 0.5:
        return None
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(r1 * r1 - a * a, 0.0))
    e = (c2 - c1) / d
    p = c1 + a * e
    perp = np.array([-e[1], e[0]])
    outward = n1 + n2
    q1, q2 = p + h * perp, p - h * perp
    return q1 if (q1 - p) @ outward > 0 else q2


def cusp_curl(cusp, into, other, turns=0.95):
    """Spiral that leaves the notch between two lobes and curls into lobe ``into``."""
    cx, cy, r, _ = into
    ox, oy = other[0], other[1]
    phi0 = math.atan2(cusp[1] - cy, cusp[0] - cx)
    phio = math.atan2(oy - cy, ox - cx)
    dphi = (phi0 - phio + math.pi) % (2 * math.pi) - math.pi
    s = 1.0 if dphi >= 0 else -1.0
    n = 110
    t = np.linspace(0, 1, n)
    ang = phi0 + s * turns * 2 * math.pi * t
    rad = r * (0.96 - 0.70 * t ** 0.62)
    return np.stack([cx + rad * np.cos(ang), cy + rad * np.sin(ang)], 1)


def make_cloud_corner():
    cv = Canvas(512, 512)
    X, Y = cv.X, cv.Y
    rc = np.hypot(X, Y)
    tex = fft_noise(cv.H, cv.W, 2.0, 84, cutoff=0.01)
    cloud_ramp = [(0.0, "625F57"), (0.35, "8C887C"), (0.6, "ACA796"), (0.85, "CBC5B0"), (1.0, "DDD7C2")]
    # banks: (Ax, Ay, off, rmax, seed, tone)   back -> front
    banks = [
        (480.0, 400.0, 76.0, 56.0, 91, 0.42),
        (370.0, 470.0, 50.0, 50.0, 92, 0.58),
        (270.0, 250.0, 22.0, 42.0, 93, 0.76),
    ]
    # soft mist wash so the corner reads densest
    mist = (1 - smoothstep(60.0, 330.0, rc)) * 0.55
    cv.paint(mix(hexc("A9A595"), hexc("C9C3AE"), 1 - smoothstep(0, 300, rc)), mist)
    for bi, (Ax, Ay, off, rmax, seed, tone) in enumerate(banks):
        off_end = 0.30 * off
        curve = cbez((Ax, off_end), (Ax * 0.45, off), (off, Ay * 0.45), (off_end, Ay), 500)
        lobes = cloud_chain(curve, rmax, seed)
        poly = [(Ax, -30.0)] + [tuple(q) for q in curve[::14]] + [tuple(curve[-1]), (-30.0, Ay), (-30.0, -30.0)]
        bbox = (-2, -2, max(Ax, Ay) + rmax, max(Ax, Ay) + rmax)
        d = field(cv, bbox, lambda Xw, Yw: sd_polygon(Xw, Yw, poly))
        for (lx, ly, lr, _) in lobes:
            m = lr + 12
            d = np.minimum(d, field(cv, (lx - m, ly - m, lx + m, ly + m),
                                    lambda Xw, Yw, lx=lx, ly=ly, lr=lr: sd_circle(Xw, Yw, lx, ly, lr)))
        if bi > 0:
            cv.paint(INK, cv.blur(cv.cov(d - 2.0), 5.0) * 0.28)
        cv.paint(INK, cv.cov(d - 2.0) * 0.92)
        lam = bevel_light(d, 10.0, cv.k)
        rim = smoothstep(-22.0, 0.0, d)
        tt = tone + 0.07 * lam - 0.08 * rim + 0.035 * tex
        cv.paint(ramp(cloud_ramp, tt), cv.cov(d))
        # engraved inner line following the silhouette
        cv.paint(INK, cv.cov(np.abs(d + 7.5) - 0.55) * cv.cov(d + 2) * 0.32)
        # curls springing from the notches between lobes
        curl_cov = np.zeros_like(X)
        for k in range(len(lobes) - 1):
            a, b = lobes[k], lobes[k + 1]
            if (k + bi) % 2:
                continue
            ii = k if a[2] >= b[2] else k + 1
            into, other = lobes[ii], (b if ii == k else a)
            if into[2] < rmax * 0.50:
                continue
            cusp = outer_cusp(a, b)
            if cusp is None:
                continue
            sp = cusp_curl(cusp, into, other)
            cx_, cy_, r_, _ = into
            w = cv.win(cx_ - r_ - 4, cy_ - r_ - 4, cx_ + r_ + 4, cy_ + r_ + 4)
            if w is None:
                continue
            sl, Xw, Yw = w
            cc = cv.cov(sd_stroke(cv, sp, taper(len(sp), 1.5, 0.45, wmid=1.3))[sl])
            own = np.hypot(Xw - cx_, Yw - cy_) - r_
            for jj in (ii - 1, ii + 1):            # keep the curl inside its own lobe's cell
                if 0 <= jj < len(lobes):
                    nx_, ny_, nr_, _ = lobes[jj]
                    cc *= smoothstep(-2.0, 2.0, (np.hypot(Xw - nx_, Yw - ny_) - nr_) - own)
            curl_cov[sl] = np.maximum(curl_cov[sl], cc)
        cv.paint(INK, curl_cov * cv.cov(d + 1.0) * 0.85)
    fade = 1.0 - smoothstep(170.0, 480.0, rc)
    fade *= (1 - smoothstep(440.0, 505.0, X)) * (1 - smoothstep(440.0, 505.0, Y))
    cv.multiply_alpha(fade)
    return cv.result()


def make_icon():
    cv = Canvas(256, 256)
    X, Y = cv.X, cv.Y
    c = 128.0
    dx, dy = X - c, Y - c
    r = np.hypot(dx, dy)
    cv.paint(GRACE, (0.45 * np.exp(-(r / 70.0) ** 2)) * (1 - smoothstep(110, 127, r)))
    R = 88.0
    disc = r - R
    # parchment map
    n1 = fft_noise(cv.H, cv.W, 2.4, 301, cutoff=0.01)
    n2 = fft_noise(cv.H, cv.W, 1.6, 302, cutoff=0.06)
    paper = mix(hexc("E2D6B3"), PARCH, np.clip(0.45 + 0.30 * n1 + 0.10 * n2, 0, 1))
    paper = mix(paper, hexc("7A6238"), smoothstep(55, R, r) ** 2.2 * 0.65)
    cv.paint(NEAR_BLACK, cv.cov(disc - 6.5))
    cv.paint(paper, cv.cov(disc))
    # a few hills drawn as clean topographic contours (gently warped)
    wx = fft_noise(cv.H, cv.W, 2.6, 304, cutoff=0.004)
    wy = fft_noise(cv.H, cv.W, 2.6, 305, cutoff=0.004)
    Xw, Yw = X + 8.0 * wx, Y + 8.0 * wy
    topo = np.zeros_like(X)
    for hx, hy, sa, sb, ang, hh in ((152, 72, 22, 13, 30, 1.0), (182, 150, 24, 14, -60, 0.95),
                                    (104, 188, 22, 13, 15, 0.9), (74, 106, 20, 12, 70, 0.85)):
        ca, sn = math.cos(math.radians(ang)), math.sin(math.radians(ang))
        u = (Xw - hx) * ca + (Yw - hy) * sn
        v = -(Xw - hx) * sn + (Yw - hy) * ca
        topo += hh * np.exp(-(u * u / (2 * sa * sa) + v * v / (2 * sb * sb)))
    gy, gx = np.gradient(topo)
    gm = np.sqrt(gx * gx + gy * gy) * cv.k + 1e-5
    spacing = 0.19
    q = topo / spacing
    dist = np.abs(q - np.round(q)) * spacing / gm
    lines = (1 - smoothstep(0.35, 0.95, dist)) * smoothstep(0.12, 0.22, topo)
    cv.paint(INK, lines * cv.cov(disc + 2) * 0.55)
    lake = sd_ellipse(X, Y, 96, 72, 11, 7)
    cv.paint(SLATE, cv.cov(lake) * 0.75)
    cv.paint(INK, cv.cov(np.abs(lake) - 0.6) * 0.6)
    river = cbez((60, 200), (95, 170), (90, 150), (150, 130), 40)
    river = join(river, cbez((150, 130), (185, 118), (196, 90), (214, 70), 30))
    dr = sd_stroke(cv, river, taper(len(river), 2.4, 1.0))
    cv.paint(SLATE, cv.cov(dr) * cv.cov(disc + 2) * 0.9)
    # gold rim around the map
    rim = np.abs(r - (R + 3.0)) - 3.0
    lam = bevel_light(rim, 1.6, cv.k)
    angl = np.cos(np.arctan2(dx, -dy) - math.radians(-35))
    cv.paint(ramp(GOLD_RAMP, 0.62 + 0.25 * lam + 0.14 * angl), cv.cov(rim))
    cv.paint(NEAR_BLACK, cv.cov(np.abs(r - (R + 3.0)) - 0.5) * 0.6)
    # grace symbol
    spikes = [(0, 123.0, 8.5), (180, 123.0, 8.5), (90, 102.0, 7.0), (270, 102.0, 7.0),
              (45, 66.0, 5.0), (135, 66.0, 5.0), (225, 66.0, 5.0), (315, 66.0, 5.0)]
    d_sp, nx, ny = union_facets(spike(X, Y, c, c, a, L, w, r0=39.0) for a, L, w in spikes)
    d_sp = np.maximum(d_sp, 35.0 - r)
    ring = np.abs(r - 34.0) - 4.4
    d_all = np.minimum(d_sp, ring)
    cv.paint(SHADOW, cv.blur(cv.cov(d_all - 2.0), 3.0) * 0.55)
    cv.paint(NEAR_BLACK, cv.cov(d_all - 2.0))
    t_sp = 0.70 + 0.22 * facet_light(nx, ny) + 0.10 * (1 - smoothstep(30, 120, r))
    cv.paint(ramp(GOLD_RAMP, t_sp), cv.cov(d_sp))
    cv.paint(NEAR_BLACK, cv.cov(ring - 1.6) * 0.6)
    cv.paint(metal(cv, ring, bevel=2.8, bbox=(90, 90, 166, 166), base=0.64, kb=0.34, kp=0.26), cv.cov(ring))
    # the grace light inside the ring (the map stays faintly visible through it)
    inside = cv.cov(r - 29.6)
    cv.paint(GRACE, (0.30 + 0.55 * np.exp(-(r / 17.0) ** 2)) * inside)
    cv.paint(hexc("FFF6D6"), np.exp(-(r / 10.0) ** 2) * 0.95)
    cv.paint(WHITE, np.exp(-(r / 4.5) ** 2) * 0.95)
    return cv.result()


# =============================================================================
# Preview contact sheet
# =============================================================================
def checker_bg(w, h, color):
    return Image.new("RGBA", (w, h), tuple(int(c * 255) for c in color) + (255,))


def tile_bg(tile: Image.Image, w, h):
    bg = Image.new("RGBA", (w, h))
    for y in range(0, h, tile.height):
        for x in range(0, w, tile.width):
            bg.paste(tile, (x, y))
    return bg


def make_preview(results: dict[str, np.ndarray], path: Path, paper_arr: np.ndarray | None = None):
    font = load_font(["georgia.ttf", "times.ttf", "DejaVuSerif.ttf"], 14)
    font_b = load_font(SERIF_BOLD, 20)
    if paper_arr is not None:
        paper = Image.fromarray(paper_arr, "RGBA")
    else:
        paper = checker_bg(256, 256, PARCH)
    sections = []

    def show(name, im: Image.Image, maxdim=256, zoom_small=True):
        w, h = im.size
        scale = 1.0
        if max(w, h) > maxdim:
            scale = maxdim / max(w, h)
        elif zoom_small and max(w, h) <= 64:
            scale = 2.0
        if scale != 1.0:
            res = Image.NEAREST if scale > 1 else Image.LANCZOS
            im = im.resize((max(1, int(w * scale)), max(1, int(h * scale))), res)
        sections.append((name + ("" if scale == 1 else f"  (x{scale:g})"), im, False))

    def mock(name, im):
        sections.append((name, im, True))

    order = ["markers.png", "player_arrow.png", "player_other.png", "entity_dot.png", "offscreen_arrow.png",
             "map_icon.png", "minimap_ring.png", "minimap_square.png", "vignette_disc.png", "vignette_square.png",
             "compass_rose.png", "time_dial_face.png", "time_dial_frame.png", "reticle.png", "glow.png",
             "panel.png", "checkbox.png", "highlight.png", "divider.png", "cloud_corner.png", "icon.png", "fog.png"]
    for name in order:
        if name not in results:
            continue
        im = Image.fromarray(results[name], "RGBA")
        if name == "markers.png":
            show(name, im, maxdim=512, zoom_small=False)
        elif name == "fog.png":
            tiled = tile_bg(im, 512, 512)
            show(name + "  2x2 tiled (seam check)", tiled, maxdim=512)
        elif name == "icon.png":
            show("../../icon.png (mod logo)", im)
        else:
            show(name, im)
    have = lambda *ks: all(k in results for k in ks)  # noqa: E731
    if have("markers.png"):
        mock("markers at 16 / 24 px with sample tints (rows: white, red, blue / green, gold, white)", marker_strip(results))
    if have("minimap_ring.png", "fog.png", "vignette_disc.png", "player_arrow.png", "markers.png"):
        mock("mock: round minimap (ring + vignette_disc + markers + player_arrow)", mock_minimap(results, paper))
    if have("minimap_square.png", "fog.png", "vignette_square.png", "player_arrow.png", "markers.png"):
        mock("mock: square minimap", mock_square(results, paper))
    if have("time_dial_face.png", "time_dial_frame.png"):
        mock("mock: time dial (face rotated 0 / 60 / 120 / 180 deg under the frame)", mock_dial(results))
    if have("panel.png", "highlight.png", "divider.png", "checkbox.png", "map_icon.png"):
        mock("mock: settings panel (9-slice panel, divider, highlight, checkboxes)", mock_menu(results))
    if have("cloud_corner.png", "fog.png", "compass_rose.png", "reticle.png", "markers.png"):
        mock("mock: fullscreen map (cloud_corner mirrored to all corners, compass, reticle)", mock_fullscreen(results, paper))

    pad = 12
    grey = (128, 128, 128, 255)
    blocks = []
    for name, im, single in sections:
        w, h = im.size
        cellw = max(w, 96) + 16
        panels = [(None, cellw)] if single else [("grey", cellw), ("paper", cellw)]
        bw = sum(p[1] for p in panels) + pad * (len(panels) - 1)
        label_w = int(font.getlength(name)) + 4
        blk = Image.new("RGBA", (max(bw, label_w, 120), h + 16 + 24), (40, 36, 30, 255))
        bd = ImageDraw.Draw(blk)
        bd.text((0, 2), name, fill=(237, 227, 204, 255), font=font)
        x = 0
        for kind, pw in panels:
            if kind == "grey":
                panel = Image.new("RGBA", (pw, h + 16), grey)
            elif kind == "paper":
                panel = tile_bg(paper, pw, h + 16)
            else:
                panel = Image.new("RGBA", (pw, h + 16), (58, 52, 44, 255))
            panel.alpha_composite(im, ((pw - w) // 2, 8))
            blk.paste(panel, (x, 24))
            x += pw + pad
        blocks.append(blk)
    sheet_w = 2300
    x, y, row_h = pad, 44, 0
    placed = []
    for blk in blocks:
        if x + blk.width > sheet_w - pad and x > pad:
            x, y = pad, y + row_h + 2 * pad
            row_h = 0
        placed.append((blk, x, y))
        x += blk.width + 3 * pad
        row_h = max(row_h, blk.height)
    sheet = Image.new("RGBA", (sheet_w, y + row_h + 2 * pad), (40, 36, 30, 255))
    dr = ImageDraw.Draw(sheet)
    dr.text((pad, 10), "Remnants Minimap - generated GUI textures   (each asset on mid-grey, left, and on parchment, right; "
            "x2 = nearest-neighbour zoom, x0.5 = downscaled)", fill=(242, 223, 160, 255), font=font_b)
    for blk, bx, by in placed:
        sheet.paste(blk, (bx, by))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path)


def _img(results, name):
    return Image.fromarray(results[name], "RGBA")


def _marker(results, i, size, tint=None):
    atlas = _img(results, "markers.png")
    cell = atlas.crop(((i % 8) * 64, (i // 8) * 64, (i % 8) * 64 + 64, (i // 8) * 64 + 64)).resize((size, size), Image.LANCZOS)
    if tint is not None:
        arr = np.asarray(cell).astype(np.float32)
        arr[..., :3] *= np.array(tint, np.float32) / 255.0
        cell = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA")
    return cell


def _map_surface(results, paper, size, fog_mask):
    base = np.asarray(tile_bg(paper, size[0], size[1])).copy()
    fogl = np.asarray(tile_bg(_img(results, "fog.png"), size[0], size[1]))
    base[fog_mask] = fogl[fog_mask]
    return Image.fromarray(base, "RGBA")


def _place_markers(results, out, items, size=40):
    for i, x, y, tint in items:
        out.alpha_composite(_marker(results, i, size, tint), (x - size // 2, y - size // 2))


def mock_minimap(results, paper):
    size = 512
    yy, xx = np.mgrid[0:size, 0:size]
    disc = _map_surface(results, paper, (size, size), (np.hypot(xx - 330, yy - 170) < 150) | (xx + yy > 720))
    _place_markers(results, disc, [(0, 150, 200, None), (5, 330, 300, (120, 200, 255)), (7, 200, 360, (255, 120, 90)),
                                   (3, 300, 140, None), (20, 120, 300, (140, 220, 120)), (13, 380, 220, (230, 200, 255))])
    disc.alpha_composite(_img(results, "vignette_disc.png").resize((440, 440), Image.LANCZOS), (36, 36))
    arrow = _img(results, "player_arrow.png").resize((44, 44), Image.LANCZOS).rotate(-20, Image.BICUBIC)
    disc.alpha_composite(arrow, (256 - 22, 256 - 22))
    mask = Image.fromarray(((np.hypot(xx + 0.5 - 256, yy + 0.5 - 256) < 220.5) * 255).astype(np.uint8))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(disc, (0, 0), mask)
    out.alpha_composite(_img(results, "minimap_ring.png"))
    return out


def mock_square(results, paper):
    size = 512
    yy, xx = np.mgrid[0:size, 0:size]
    surf = _map_surface(results, paper, (size, size), (np.hypot(xx - 140, yy - 380) < 170) | (yy < 120))
    _place_markers(results, surf, [(0, 330, 250, None), (6, 180, 200, (255, 210, 120)), (16, 380, 380, (120, 220, 255)),
                                   (28, 120, 160, (200, 200, 210)), (2, 260, 420, (255, 90, 80))])
    surf.alpha_composite(_img(results, "vignette_square.png").resize((440, 440), Image.LANCZOS), (36, 36))
    other = _img(results, "player_other.png").resize((40, 40), Image.LANCZOS).rotate(35, Image.BICUBIC)
    surf.alpha_composite(other, (300, 300))
    surf.alpha_composite(_img(results, "player_arrow.png").resize((44, 44), Image.LANCZOS), (256 - 22, 256 - 22))
    edge = _img(results, "offscreen_arrow.png").resize((24, 24), Image.LANCZOS)
    surf.alpha_composite(edge, (420, 52))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(surf.crop((36, 36, 476, 476)), (36, 36))
    out.alpha_composite(_img(results, "minimap_square.png"))
    return out


def mock_dial(results):
    face = _img(results, "time_dial_face.png")
    frame = _img(results, "time_dial_frame.png")
    out = Image.new("RGBA", (4 * 256 + 3 * 16, 256), (0, 0, 0, 0))
    for k, ang in enumerate((0, 60, 120, 180)):
        f = face.rotate(-ang, Image.BICUBIC)
        f.alpha_composite(frame)
        out.alpha_composite(f, (k * 272, 0))
    return out


def nine_slice(img: Image.Image, w, h, c=24):
    W, H = img.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    xs = [(0, c, 0, c), (c, W - c, c, w - c), (W - c, W, w - c, w)]
    ys = [(0, c, 0, c), (c, H - c, c, h - c), (H - c, H, h - c, h)]
    for sx0, sx1, dx0, dx1 in xs:
        for sy0, sy1, dy0, dy1 in ys:
            piece = img.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST)
            out.paste(piece, (dx0, dy0))
    return out


def mock_menu(results):
    W, H = 560, 330
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bg = np.zeros((H, W, 4), np.uint8)
    yy, xx = np.mgrid[0:H, 0:W]
    v = (40 + 25 * np.exp(-(((xx - W / 2) / 300.0) ** 2 + ((yy - H / 2) / 200.0) ** 2))).astype(np.uint8)
    bg[..., 0], bg[..., 1], bg[..., 2], bg[..., 3] = v, (v * 0.9).astype(np.uint8), (v * 0.75).astype(np.uint8), 255
    out = Image.fromarray(bg, "RGBA")
    out.alpha_composite(nine_slice(_img(results, "panel.png"), 520, 290), (20, 20))
    out.alpha_composite(_img(results, "map_icon.png").resize((40, 40), Image.LANCZOS), (48, 38))
    dr = ImageDraw.Draw(out)
    title = load_font(SERIF_BOLD, 26)
    body = load_font(SERIF, 17)
    dr.text((98, 44), "Map", fill=(242, 223, 160, 255), font=title)
    out.alpha_composite(_img(results, "divider.png").resize((440, 16), Image.LANCZOS), (60, 88))
    cb = _img(results, "checkbox.png")
    rows = [("Show minimap", True), ("Rotate with player", False), ("Show waypoints", True), ("Cave mode", False)]
    for k, (label, checked) in enumerate(rows):
        y = 116 + k * 44
        if k == 1:
            out.alpha_composite(_img(results, "highlight.png").resize((470, 36), Image.LANCZOS), (45, y - 4))
        cell = cb.crop((32, 0, 64, 32)) if checked else cb.crop((0, 0, 32, 32))
        out.alpha_composite(cell, (70, y))
        dr.text((114, y + 6), label, fill=(237, 227, 204, 255) if k == 1 else (200, 190, 168, 255), font=body)
    return out


def mock_fullscreen(results, paper):
    W, H = 1000, 600
    yy, xx = np.mgrid[0:H, 0:W]
    surf = _map_surface(results, paper, (W, H), (np.hypot(xx - 760, yy - 180) < 210) | (np.hypot(xx - 150, yy - 520) < 200))
    _place_markers(results, surf, [(0, 420, 300, None), (8, 300, 200, (255, 220, 150)), (12, 600, 380, (200, 160, 255)),
                                   (22, 820, 420, (120, 200, 255)), (14, 520, 160, (255, 110, 90))], size=36)
    cc = _img(results, "cloud_corner.png").resize((300, 300), Image.LANCZOS)
    surf.alpha_composite(cc, (0, 0))
    surf.alpha_composite(cc.transpose(Image.FLIP_LEFT_RIGHT), (W - 300, 0))
    surf.alpha_composite(cc.transpose(Image.FLIP_TOP_BOTTOM), (0, H - 300))
    surf.alpha_composite(cc.transpose(Image.ROTATE_180), (W - 300, H - 300))
    surf.alpha_composite(_img(results, "compass_rose.png").resize((150, 150), Image.LANCZOS), (W - 190, H - 200))
    surf.alpha_composite(_img(results, "reticle.png"), (W // 2 - 64 + 60, H // 2 - 64))
    surf.alpha_composite(_img(results, "glow.png").resize((90, 90), Image.LANCZOS).point(lambda v: v), (375, 255))
    return surf


def marker_strip(results):
    tints = [(255, 255, 255), (230, 70, 60), (90, 170, 255), (110, 210, 110), (240, 200, 80), (255, 255, 255)]
    w = 32 * 34 + 8
    out = Image.new("RGBA", (w, 3 * 20 + 3 * 28 + 8), (0, 0, 0, 0))
    y = 4
    for row, tint in enumerate(tints):
        size = 16 if row < 3 else 24
        x = 4
        for i in range(32):
            out.alpha_composite(_marker(results, i, size, None if i in (0, 1, 3) else tint), (x + (24 - size) // 2, y))
            x += 34
        y += size + 4
    return out.crop((0, 0, w, y))


# =============================================================================
# Validation
# =============================================================================
def validate(results: dict[str, np.ndarray]):
    problems = []
    expect = {"markers.png": (512, 256), "player_arrow.png": (64, 64), "player_other.png": (64, 64),
              "minimap_ring.png": (512, 512), "minimap_square.png": (512, 512), "vignette_disc.png": (256, 256),
              "vignette_square.png": (256, 256), "compass_rose.png": (256, 256), "time_dial_face.png": (256, 256),
              "time_dial_frame.png": (256, 256), "fog.png": (256, 256), "panel.png": (96, 96),
              "highlight.png": (256, 32), "divider.png": (256, 16), "checkbox.png": (64, 32), "reticle.png": (128, 128),
              "glow.png": (128, 128), "map_icon.png": (64, 64), "entity_dot.png": (32, 32),
              "offscreen_arrow.png": (32, 32), "cloud_corner.png": (512, 512), "icon.png": (256, 256)}
    for name, arr in results.items():
        h, w = arr.shape[:2]
        if (w, h) != expect.get(name, (w, h)):
            problems.append(f"{name}: size {w}x{h}, expected {expect[name]}")
        if arr.shape[2] != 4:
            problems.append(f"{name}: not RGBA")
        if np.any(arr[arr[..., 3] == 0, :3] != 0):
            problems.append(f"{name}: transparent pixels with non-zero RGB")
    yy, xx = np.mgrid[0:512, 0:512]
    if "minimap_ring.png" in results:
        a = results["minimap_ring.png"][..., 3]
        if a[np.hypot(xx + 0.5 - 256, yy + 0.5 - 256) < 220].max() != 0:
            problems.append("minimap_ring.png: hole not transparent")
    if "minimap_square.png" in results:
        if results["minimap_square.png"][36:476, 36:476, 3].max() != 0:
            problems.append("minimap_square.png: hole not transparent")
    for name in ("fog.png",):
        if name in results and results[name][..., 3].min() != 255:
            problems.append(f"{name}: not fully opaque")
    if "vignette_disc.png" in results:
        y2, x2 = np.mgrid[0:256, 0:256]
        rr = np.hypot(x2 + 0.5 - 128, y2 + 0.5 - 128)
        if results["vignette_disc.png"][rr > 128.75, 3].max() != 0:
            problems.append("vignette_disc.png: not transparent outside r=128")
    if "time_dial_frame.png" in results:
        y2, x2 = np.mgrid[0:256, 0:256]
        rr = np.hypot(x2 + 0.5 - 128, y2 + 0.5 - 128)
        win = (rr < 96) & (y2 + 0.5 < 120) & (np.abs(x2 + 0.5 - 128) > 12)
        if results["time_dial_frame.png"][win, 3].max() > 110:
            problems.append("time_dial_frame.png: window not open")
    return problems


# =============================================================================
# Main
# =============================================================================
GENERATORS = {
    "markers.png": make_markers,
    "player_arrow.png": lambda: make_player_arrow(GOLD_RAMP, GRACE, 0.45),
    "player_other.png": lambda: make_player_arrow(STEEL_BLUE_RAMP, hexc("BFD8F2"), 0.40),
    "minimap_ring.png": make_minimap_ring,
    "minimap_square.png": make_minimap_square,
    "vignette_disc.png": make_vignette_disc,
    "vignette_square.png": make_vignette_square,
    "compass_rose.png": make_compass_rose,
    "time_dial_face.png": make_time_dial_face,
    "time_dial_frame.png": make_time_dial_frame,
    "fog.png": make_fog,
    "panel.png": make_panel,
    "highlight.png": make_highlight,
    "divider.png": make_divider,
    "checkbox.png": make_checkbox,
    "reticle.png": make_reticle,
    "glow.png": make_glow,
    "map_icon.png": make_map_icon,
    "entity_dot.png": make_entity_dot,
    "offscreen_arrow.png": make_offscreen_arrow,
    "cloud_corner.png": make_cloud_corner,
    "icon.png": make_icon,
}


def output_path(out_dir: Path, name: str) -> Path:
    if name == "icon.png":
        return (out_dir / ".." / ".." / "icon.png").resolve()
    return out_dir / name


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out_dir", nargs="?", default=str(DEFAULT_OUT))
    ap.add_argument("--preview", default=str(DEFAULT_PREVIEW), help="contact sheet path")
    ap.add_argument("--no-preview", action="store_true")
    ap.add_argument("--only", default="", help="comma separated asset names (e.g. markers,fog)")
    args = ap.parse_args(argv)
    out_dir = Path(args.out_dir).resolve()
    only = {s.strip() if s.strip().endswith(".png") else s.strip() + ".png" for s in args.only.split(",") if s.strip()}
    results = {}
    for name, fn in GENERATORS.items():
        if only and name not in only:
            continue
        print(f"rendering {name} ...", flush=True)
        arr = fn()
        results[name] = arr
        straight_to_png(arr, output_path(out_dir, name))
    if only:
        # load the remaining assets from disk for the preview
        for name in GENERATORS:
            p = output_path(out_dir, name)
            if name not in results and p.exists():
                results[name] = np.asarray(Image.open(p).convert("RGBA"))
    problems = validate(results)
    for pr in problems:
        print("  PROBLEM:", pr)
    if not args.no_preview:
        # paper.png is not shipped; a parchment tile is generated in memory only
        # as the backdrop for the contact sheet and the mock-ups
        make_preview(results, Path(args.preview), make_paper())
        print(f"preview written to {args.preview}")
    print(f"done: {len(results)} assets in {out_dir}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
