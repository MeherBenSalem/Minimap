package io.nightbeam.remnantminimaps.client.render;

import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import net.minecraft.client.gui.GuiGraphics;

/** How a single marker looks wherever it is drawn: glow, icon and tint. */
public final class MarkerPainter {
    public static final int GRACE_GLOW = 0xFFFFD77A;
    public static final int DEATH_GLOW = 0xFFB0302A;

    private MarkerPainter() {}

    public static void draw(GuiGraphics g, Marker marker, float x, float y, float size, float alpha, boolean emphasized) {
        MarkerIcon icon = marker.icon();
        double t = System.currentTimeMillis() / 1000.0;
        if (icon == MarkerIcon.GRACE || marker.kind() == Marker.Kind.BEACON) {
            float pulse = 0.75f + 0.25f * (float) Math.sin(t * 2.4 + (marker.x * 0.13 + marker.z * 0.07));
            Ui.glow(g, x, y, size * 1.25f * (0.9f + 0.1f * pulse), Ui.alpha(GRACE_GLOW, 0.55f * pulse * alpha));
        } else if (icon == MarkerIcon.DEATH) {
            Ui.glow(g, x, y, size * 0.95f, Ui.alpha(DEATH_GLOW, 0.4f * alpha));
        } else if (emphasized) {
            Ui.glow(g, x, y, size * 1.05f, Ui.alpha(0xFFFFF2D0, 0.35f * alpha));
        }
        float drawSize = emphasized ? size * 1.18f : size;
        Ui.icon(g, icon, x, y, drawSize, marker.tint(), alpha);
    }

    /** Small arrow on the minimap rim pointing toward something outside the visible area. */
    public static void edgeArrow(GuiGraphics g, float x, float y, float radians, float size, int color) {
        Ui.spriteRotated(g, Art.offscreenArrow, x, y, size, size, radians, color);
    }
}
