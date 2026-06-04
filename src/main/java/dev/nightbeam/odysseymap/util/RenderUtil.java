package dev.nightbeam.odysseymap.util;

import net.minecraft.client.gui.GuiGraphics;

public final class RenderUtil {
    private RenderUtil() {}

    public static void drawCircleBorder(GuiGraphics graphics, int x, int y, int size, int thickness, int color) {
        float centerX = x + size / 2.0f;
        float centerY = y + size / 2.0f;
        float outer = size / 2.0f + thickness;
        float inner = size / 2.0f;
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            float ox = centerX + (float) (Math.cos(rad) * outer);
            float oy = centerY + (float) (Math.sin(rad) * outer);
            float ix = centerX + (float) (Math.cos(rad) * inner);
            float iy = centerY + (float) (Math.sin(rad) * inner);
            graphics.fill((int) ox, (int) oy, (int) ox + 1, (int) oy + 1, color);
        }
    }

    public static void drawMarkerDot(GuiGraphics graphics, int x, int y, int color, int size) {
        graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
    }
}
