package io.nightbeam.remnantminimaps.client.render;

import io.nightbeam.remnantminimaps.client.render.gfx.Samplers.Sampler;
import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.GuiTexture;
import io.nightbeam.remnantminimaps.client.render.gfx.Mesh;
import io.nightbeam.remnantminimaps.client.render.gfx.Samplers;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.client.session.OverviewTile;
import io.nightbeam.remnantminimaps.core.map.RegionData;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the fog background and the region textures of a map view, clipped to an arbitrary convex
 * screen polygon (a circle or a rectangle). Each region becomes one textured polygon whose UVs come
 * from the inverse projection, so rotation and sub-pixel scrolling are exact.
 */
public final class MapPainter {
    /** Screen pixels covered by one repeat of the fog texture. */
    private static final float FOG_TILE = 200.0f;

    private final float[] clipX = new float[128];
    private final float[] clipY = new float[128];
    private int clipN;
    private float clipSign = 1.0f;

    private float[] ax = new float[160];
    private float[] ay = new float[160];
    private float[] bx = new float[160];
    private float[] by = new float[160];
    private final float[] us = new float[160];
    private final float[] vs = new float[160];

    public void setCircleClip(float cx, float cy, float radius, int segments) {
        clipN = Math.min(segments, clipX.length);
        for (int i = 0; i < clipN; i++) {
            double a = i * Math.PI * 2.0 / clipN;
            clipX[i] = cx + (float) Math.cos(a) * radius;
            clipY[i] = cy + (float) Math.sin(a) * radius;
        }
        computeWinding();
    }

    public void setRectClip(float x0, float y0, float x1, float y1) {
        clipN = 4;
        clipX[0] = x0;
        clipY[0] = y0;
        clipX[1] = x1;
        clipY[1] = y0;
        clipX[2] = x1;
        clipY[2] = y1;
        clipX[3] = x0;
        clipY[3] = y1;
        computeWinding();
    }

    private void computeWinding() {
        float area = 0.0f;
        for (int i = 0; i < clipN; i++) {
            int j = (i + 1) % clipN;
            area += clipX[i] * clipY[j] - clipX[j] * clipY[i];
        }
        clipSign = area >= 0 ? 1.0f : -1.0f;
    }

    /** Fog / unexplored parchment covering the whole clip area, anchored to the world. */
    public void drawFog(GuiGraphics graphics, MapView view, int color) {
        if (clipN < 3) {
            return;
        }
        for (int i = 0; i < clipN; i++) {
            us[i] = (float) (view.toWorldX(clipX[i], clipY[i]) * view.scale / FOG_TILE);
            vs[i] = (float) (view.toWorldZ(clipX[i], clipY[i]) * view.scale / FOG_TILE);
        }
        Mesh.textured(Art.fog, Samplers.tiled()).polygon(clipX, clipY, us, vs, clipN, color).submit(graphics);
    }

    /**
     * Draws every visible region. With {@code overview} the 4x4 thumbnail tiles are used instead,
     * which keeps the far zoom levels cheap.
     */
    public void drawRegions(GuiGraphics graphics, DimensionMap map, MapView view, boolean overview,
                            Sampler sampler, int color) {
        if (clipN < 3 || map == null) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < clipN; i++) {
            double wx = view.toWorldX(clipX[i], clipY[i]);
            double wz = view.toWorldZ(clipX[i], clipY[i]);
            minX = Math.min(minX, wx);
            minZ = Math.min(minZ, wz);
            maxX = Math.max(maxX, wx);
            maxZ = Math.max(maxZ, wz);
        }
        int tileShift = overview ? RegionData.SHIFT + 2 : RegionData.SHIFT;
        int tileSize = 1 << tileShift;
        int tx0 = (int) Math.floor(minX) >> tileShift;
        int tz0 = (int) Math.floor(minZ) >> tileShift;
        int tx1 = (int) Math.floor(maxX) >> tileShift;
        int tz1 = (int) Math.floor(maxZ) >> tileShift;
        if ((long) (tx1 - tx0 + 1) * (tz1 - tz0 + 1) > 4096) {
            return;
        }
        for (int tz = tz0; tz <= tz1; tz++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                GuiTexture texture;
                if (overview) {
                    OverviewTile tile = map.overviewTile(tx, tz);
                    texture = tile == null ? null : tile.texture();
                } else {
                    texture = map.isKnown(tx, tz) ? map.regionTexture(tx, tz) : null;
                }
                if (texture == null) {
                    continue;
                }
                double wx0 = (double) tx * tileSize;
                double wz0 = (double) tz * tileSize;
                int n = clipQuad(view, wx0, wz0, tileSize);
                if (n < 3) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    us[i] = (float) ((view.toWorldX(ax[i], ay[i]) - wx0) / tileSize);
                    vs[i] = (float) ((view.toWorldZ(ax[i], ay[i]) - wz0) / tileSize);
                }
                Mesh.textured(texture, sampler).polygon(ax, ay, us, vs, n, color).submit(graphics);
            }
        }
    }

    /** Clips the screen-space quad of a world square against the clip polygon. Result in ax/ay. */
    private int clipQuad(MapView view, double wx0, double wz0, int size) {
        ax[0] = view.toScreenX(wx0, wz0);
        ay[0] = view.toScreenY(wx0, wz0);
        ax[1] = view.toScreenX(wx0 + size, wz0);
        ay[1] = view.toScreenY(wx0 + size, wz0);
        ax[2] = view.toScreenX(wx0 + size, wz0 + size);
        ay[2] = view.toScreenY(wx0 + size, wz0 + size);
        ax[3] = view.toScreenX(wx0, wz0 + size);
        ay[3] = view.toScreenY(wx0, wz0 + size);
        int n = 4;
        for (int e = 0; e < clipN && n > 0; e++) {
            float ex0 = clipX[e];
            float ey0 = clipY[e];
            float ex1 = clipX[(e + 1) % clipN];
            float ey1 = clipY[(e + 1) % clipN];
            int m = 0;
            for (int i = 0; i < n; i++) {
                float px = ax[i];
                float py = ay[i];
                float qx = ax[(i + 1) % n];
                float qy = ay[(i + 1) % n];
                float dp = side(ex0, ey0, ex1, ey1, px, py);
                float dq = side(ex0, ey0, ex1, ey1, qx, qy);
                if (dp >= 0) {
                    m = push(m, px, py);
                }
                if ((dp >= 0) != (dq >= 0)) {
                    float t = dp / (dp - dq);
                    m = push(m, px + (qx - px) * t, py + (qy - py) * t);
                }
            }
            float[] tx = ax;
            float[] ty = ay;
            ax = bx;
            ay = by;
            bx = tx;
            by = ty;
            n = m;
        }
        return n;
    }

    private int push(int m, float x, float y) {
        if (m < bx.length) {
            bx[m] = x;
            by[m] = y;
            return m + 1;
        }
        return m;
    }

    private float side(float x0, float y0, float x1, float y1, float px, float py) {
        return ((x1 - x0) * (py - y0) - (y1 - y0) * (px - x0)) * clipSign;
    }
}
