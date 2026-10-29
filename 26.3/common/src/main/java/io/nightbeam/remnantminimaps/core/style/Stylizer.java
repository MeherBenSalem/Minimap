package io.nightbeam.remnantminimaps.core.style;

import io.nightbeam.remnantminimaps.core.map.Columns;
import io.nightbeam.remnantminimaps.core.map.RegionData;

/**
 * Turns raw scanned columns into the hand-drawn parchment look: muted palette, relief shading,
 * contour lines, engraved water, ink coastlines and soft fog edges. Pure function of its inputs,
 * safe to run on any thread.
 */
public final class Stylizer {
    /** World-coordinate access to columns outside the region being styled. */
    public interface Neighbors {
        int color(int wx, int wz);

        int height(int wx, int wz);

        int depth(int wx, int wz);
    }

    public static final Neighbors EMPTY = new Neighbors() {
        @Override
        public int color(int wx, int wz) { return 0; }

        @Override
        public int height(int wx, int wz) { return 0; }

        @Override
        public int depth(int wx, int wz) { return 0; }
    };

    // Ink used for outlines and contours.
    private static final float INK_R = 0.20f, INK_G = 0.155f, INK_B = 0.11f;
    // Warm highlight and cool shadow for relief shading.
    private static final float HI_R = 0.97f, HI_G = 0.93f, HI_B = 0.80f;
    private static final float SH_R = 0.17f, SH_G = 0.15f, SH_B = 0.15f;
    // Water.
    private static final float SHALLOW_R = 0.60f, SHALLOW_G = 0.665f, SHALLOW_B = 0.655f;
    private static final float DEEP_R = 0.335f, DEEP_G = 0.405f, DEEP_B = 0.435f;
    private static final float FOAM_R = 0.79f, FOAM_G = 0.81f, FOAM_B = 0.76f;

    private static final int[] FEATHER_DX = {-2, -1, 0, 1, 2, -1, 0, 1, -1, 1, 0, 0};
    private static final int[] FEATHER_DZ = {0, -1, -2, -1, 0, 1, 2, 1, 0, 0, -1, 1};

    private final MapStyle style;
    private final RegionData region;
    private final Neighbors neighbors;
    private final int minX;
    private final int minZ;

    private float r;
    private float g;
    private float b;

    private Stylizer(MapStyle style, RegionData region, Neighbors neighbors) {
        this.style = style;
        this.region = region;
        this.neighbors = neighbors;
        this.minX = region.minBlockX();
        this.minZ = region.minBlockZ();
    }

    /** Styles the inclusive local rectangle into level 0 of the region's pyramid and updates mips. */
    public static void stylize(MapStyle style, RegionData region, int x0, int z0, int x1, int z1, Neighbors neighbors) {
        Stylizer s = new Stylizer(style, region, neighbors);
        int[] out = region.mips[0];
        for (int z = z0; z <= z1; z++) {
            int row = z << RegionData.SHIFT;
            for (int x = x0; x <= x1; x++) {
                out[row | x] = s.pixel(x, z);
            }
        }
        region.finishStyled(x0, z0, x1, z1);
        region.styleStamp = style.stamp();
    }

    public static void stylizeAll(MapStyle style, RegionData region, Neighbors neighbors) {
        stylize(style, region, 0, 0, RegionData.MASK, RegionData.MASK, neighbors);
    }

    private int col(int lx, int lz) {
        if ((lx | lz) >= 0 && lx < RegionData.SIZE && lz < RegionData.SIZE) {
            return region.colors[(lz << RegionData.SHIFT) | lx];
        }
        return neighbors.color(minX + lx, minZ + lz);
    }

    private int height(int lx, int lz, int fallback) {
        if ((lx | lz) >= 0 && lx < RegionData.SIZE && lz < RegionData.SIZE) {
            int i = (lz << RegionData.SHIFT) | lx;
            return region.colors[i] == 0 ? fallback : region.heights[i];
        }
        int wx = minX + lx;
        int wz = minZ + lz;
        return neighbors.color(wx, wz) == 0 ? fallback : neighbors.height(wx, wz);
    }

    private int depth(int lx, int lz) {
        if ((lx | lz) >= 0 && lx < RegionData.SIZE && lz < RegionData.SIZE) {
            return region.depths[(lz << RegionData.SHIFT) | lx] & 0xFF;
        }
        return neighbors.depth(minX + lx, minZ + lz) & 0xFF;
    }

    private int pixel(int x, int z) {
        int c = region.colors[(z << RegionData.SHIFT) | x];
        if (c == 0) {
            return 0;
        }
        int cat = Columns.category(c);
        int rgb = Columns.rgb(c);
        int wx = minX + x;
        int wz = minZ + z;
        int h = region.heights[(z << RegionData.SHIFT) | x];

        int cl = col(x - 1, z);
        int cr = col(x + 1, z);
        int cu = col(x, z - 1);
        int cd = col(x, z + 1);

        if (cat == Columns.WATER) {
            water(x, z, wx, wz, rgb, cl, cr, cu, cd);
        } else if (cat == Columns.LAVA) {
            lava(wx, wz);
        } else {
            land(cat, rgb);
            relief(x, z, h, cat, cr, cd);
            details(cat, wx, wz, cl, cr, cu, cd);
        }

        if (style.paperGrain()) {
            float grain = (Noise.hash01(wx, wz, 7) - 0.5f) * 0.045f;
            float mottle = (Noise.fbm(wx / 56.0f, wz / 56.0f, 11, 3) - 0.5f) * 0.11f;
            scale(1.0f + grain + mottle);
        }

        // Soft edge where the explored land meets the fog.
        int unexplored = 0;
        for (int i = 0; i < FEATHER_DX.length; i++) {
            if (col(x + FEATHER_DX[i], z + FEATHER_DZ[i]) == 0) {
                unexplored++;
            }
        }
        float alpha = 1.0f;
        if (unexplored > 0) {
            alpha = 1.0f - unexplored / (float) FEATHER_DX.length * 0.9f;
            if (unexplored >= 4) {
                mix(INK_R, INK_G, INK_B, 0.12f);
            }
        }
        return argb(alpha, r, g, b);
    }

    private void land(int cat, int rgb) {
        r = ((rgb >> 16) & 0xFF) / 255.0f;
        g = ((rgb >> 8) & 0xFF) / 255.0f;
        b = (rgb & 0xFF) / 255.0f;
        MapStyle.Palette palette = style.palette();
        if (palette == MapStyle.Palette.CLASSIC) {
            return;
        }
        float keep = switch (cat) {
            case Columns.GRASS -> 0.30f;
            case Columns.FOLIAGE -> 0.40f;
            case Columns.SAND -> 0.36f;
            case Columns.STONE, Columns.SNOW -> 0.30f;
            case Columns.PATH -> 0.30f;
            case Columns.VOID -> 0.20f;
            default -> 0.46f;
        };
        if (palette == MapStyle.Palette.VIVID) {
            keep = Math.min(0.92f, keep * 1.8f);
        }
        float lum = 0.299f * r + 0.587f * g + 0.114f * b;
        r = lum + (r - lum) * keep;
        g = lum + (g - lum) * keep;
        b = lum + (b - lum) * keep;
        // Warm, aged ink-on-paper toning.
        r *= 1.06f;
        g *= 0.985f;
        b *= 0.80f;
        float lift = palette == MapStyle.Palette.VIVID ? 0.06f : 0.11f;
        float span = 1.0f - lift - 0.08f;
        r = lift + r * span;
        g = lift + g * span;
        b = lift * 0.85f + b * span;
        switch (cat) {
            case Columns.FOLIAGE -> scale(0.78f);
            case Columns.SAND -> scale(0.93f);
            case Columns.STONE -> {
                r = r * 0.82f + 0.13f;
                g = g * 0.82f + 0.12f;
                b = b * 0.82f + 0.10f;
            }
            case Columns.SNOW -> mix(0.88f, 0.885f, 0.86f, 0.55f);
            case Columns.PATH -> mix(0.84f, 0.78f, 0.62f, 0.55f);
            case Columns.BUILDING -> mix(0.80f, 0.74f, 0.60f, 0.18f);
            default -> {
            }
        }
    }

    private void relief(int x, int z, int h, int cat, int cr, int cd) {
        float strength = style.hillshade();
        if (strength > 0.0f) {
            int h00 = height(x - 1, z - 1, h);
            int h10 = height(x, z - 1, h);
            int h20 = height(x + 1, z - 1, h);
            int h01 = height(x - 1, z, h);
            int h21 = height(x + 1, z, h);
            int h02 = height(x - 1, z + 1, h);
            int h12 = height(x, z + 1, h);
            int h22 = height(x + 1, z + 1, h);
            float dx = (h20 + 2 * h21 + h22) - (h00 + 2 * h01 + h02);
            float dz = (h02 + 2 * h12 + h22) - (h00 + 2 * h10 + h20);
            float shade = (dx + dz) * (0.045f * strength);
            if (cat == Columns.FOLIAGE) {
                shade *= 0.45f;
            }
            if (shade > 0.0f) {
                mix(HI_R, HI_G, HI_B, Math.min(0.42f, shade));
            } else if (shade < 0.0f) {
                mix(SH_R, SH_G, SH_B, Math.min(0.55f, -shade));
            }
            float steep = Math.abs(dx) + Math.abs(dz);
            if (steep > 14.0f && cat != Columns.FOLIAGE && cat != Columns.BUILDING && Math.floorMod(x - z + minX - minZ, 3) == 0) {
                mix(INK_R, INK_G, INK_B, Math.min(0.24f, (steep - 14.0f) * 0.012f));
            }
            float elevation = Math.max(-0.25f, Math.min(0.4f, (h - 64) / 170.0f));
            scale(1.0f + elevation * 0.22f);
        }

        if (style.contours() && cat != Columns.FOLIAGE && cat != Columns.BUILDING) {
            int interval = style.contourInterval();
            int band = Math.floorDiv(h, interval);
            int best = Integer.MIN_VALUE;
            if (cr != 0 && contourEligible(Columns.category(cr))) {
                int other = Math.floorDiv(height(x + 1, z, h), interval);
                if (other != band) {
                    best = Math.max(band, other);
                }
            }
            if (cd != 0 && contourEligible(Columns.category(cd))) {
                int other = Math.floorDiv(height(x, z + 1, h), interval);
                if (other != band) {
                    best = Math.max(best, Math.max(band, other));
                }
            }
            if (best != Integer.MIN_VALUE) {
                boolean major = Math.floorMod(best, 4) == 0;
                mix(INK_R, INK_G, INK_B, major ? 0.40f : 0.22f);
            }
        }
    }

    private static boolean contourEligible(int cat) {
        return cat != Columns.FOLIAGE && cat != Columns.BUILDING && cat != Columns.WATER && cat != Columns.LAVA;
    }

    private void details(int cat, int wx, int wz, int cl, int cr, int cu, int cd) {
        switch (cat) {
            case Columns.FOLIAGE -> {
                float v = Noise.value(wx * 0.42f, wz * 0.42f, 3);
                scale(0.88f + 0.22f * v);
                if (v < 0.18f) {
                    mix(INK_R, INK_G, INK_B, 0.16f);
                }
            }
            case Columns.FIELD -> {
                if ((wz & 1) == 0) {
                    scale(0.92f);
                }
            }
            case Columns.BUILDING -> {
                if (((wx + wz) & 3) == 0) {
                    scale(0.93f);
                }
            }
            default -> {
            }
        }
        if (!style.inkOutlines()) {
            return;
        }
        if (cat == Columns.BUILDING) {
            if (!isCat(cl, Columns.BUILDING) || !isCat(cr, Columns.BUILDING) || !isCat(cu, Columns.BUILDING) || !isCat(cd, Columns.BUILDING)) {
                mix(INK_R, INK_G, INK_B, 0.58f);
            }
        } else if (cat == Columns.PATH) {
            if (isEdge(cl, Columns.PATH) || isEdge(cr, Columns.PATH) || isEdge(cu, Columns.PATH) || isEdge(cd, Columns.PATH)) {
                mix(INK_R, INK_G, INK_B, 0.18f);
            }
        }
        if (isCat(cl, Columns.WATER) || isCat(cr, Columns.WATER) || isCat(cu, Columns.WATER) || isCat(cd, Columns.WATER)) {
            mix(INK_R, INK_G, INK_B, 0.55f);
        }
    }

    private static boolean isCat(int packed, int cat) {
        return packed != 0 && Columns.category(packed) == cat;
    }

    private static boolean isEdge(int packed, int cat) {
        if (packed == 0) {
            return false;
        }
        int other = Columns.category(packed);
        return other != cat && other != Columns.BUILDING;
    }

    private void water(int x, int z, int wx, int wz, int rgb, int cl, int cr, int cu, int cd) {
        int d = depth(x, z);
        if (style.palette() == MapStyle.Palette.CLASSIC) {
            r = ((rgb >> 16) & 0xFF) / 255.0f;
            g = ((rgb >> 8) & 0xFF) / 255.0f;
            b = (rgb & 0xFF) / 255.0f;
            scale(1.08f - Math.min(0.45f, d * 0.035f));
            return;
        }
        float t = Math.max(0.0f, Math.min(1.0f, (d - 1) / 16.0f));
        t = t * t * (3.0f - 2.0f * t);
        r = SHALLOW_R + (DEEP_R - SHALLOW_R) * t;
        g = SHALLOW_G + (DEEP_G - SHALLOW_G) * t;
        b = SHALLOW_B + (DEEP_B - SHALLOW_B) * t;

        // Keep a hint of the biome's water so swamps and warm seas still read differently.
        float br = ((rgb >> 16) & 0xFF) / 255.0f;
        float bg = ((rgb >> 8) & 0xFF) / 255.0f;
        float bb = (rgb & 0xFF) / 255.0f;
        float lum = 0.299f * br + 0.587f * bg + 0.114f * bb;
        float tint = style.palette() == MapStyle.Palette.VIVID ? 0.35f : 0.16f;
        mix(lum + (br - lum) * 0.5f, lum + (bg - lum) * 0.5f, lum + (bb - lum) * 0.5f, tint);

        if (style.waterEngraving() && d >= 3) {
            float fade = Math.min(1.0f, (d - 2) / 6.0f);
            float wobble = 1.6f * (float) Math.sin(wx * 0.085f) + 0.9f * (float) Math.sin(wx * 0.21f + wz * 0.05f);
            float phase = (wz + wobble) / 5.0f;
            float f = phase - Noise.floor(phase);
            if (f < 0.2f && Noise.value(wx * 0.07f, wz * 0.45f, 5) > 0.36f) {
                mix(0.86f, 0.88f, 0.84f, 0.13f * fade);
            }
        }

        boolean shore = !isWaterLike(cl) || !isWaterLike(cr) || !isWaterLike(cu) || !isWaterLike(cd);
        if (shore) {
            mix(FOAM_R, FOAM_G, FOAM_B, 0.30f);
        } else if (style.inkOutlines()) {
            // Faint offshore depth line, like the double coastlines of an engraved chart.
            boolean deep = d >= 5;
            if (deep != (depth(x + 1, z) >= 5) && isWaterLike(cr) || deep != (depth(x, z + 1) >= 5) && isWaterLike(cd)) {
                mix(INK_R, INK_G, INK_B, 0.16f);
            }
        }
    }

    private static boolean isWaterLike(int packed) {
        return packed == 0 || Columns.category(packed) == Columns.WATER;
    }

    private void lava(int wx, int wz) {
        float v = Noise.fbm(wx * 0.06f, wz * 0.06f, 21, 3);
        r = 0.50f + 0.40f * v;
        g = 0.14f + 0.30f * v;
        b = 0.07f + 0.10f * v;
        float crack = Noise.value(wx * 0.22f, wz * 0.22f, 23);
        if (crack > 0.47f && crack < 0.53f) {
            mix(0.20f, 0.06f, 0.03f, 0.55f);
        }
    }

    private void mix(float tr, float tg, float tb, float t) {
        r += (tr - r) * t;
        g += (tg - g) * t;
        b += (tb - b) * t;
    }

    private void scale(float s) {
        r *= s;
        g *= s;
        b *= s;
    }

    private static int argb(float a, float r, float g, float b) {
        int ai = clamp255(a);
        return (ai << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float v) {
        int i = (int) (v * 255.0f + 0.5f);
        return i < 0 ? 0 : Math.min(255, i);
    }
}
