package dev.nightbeam.odysseymap.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class RenderUtil {
    private RenderUtil() {}

    public static void drawCircleBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int thickness, int color) {
        float centerX = x + size / 2.0f;
        float centerY = y + size / 2.0f;
        float outer = size / 2.0f + thickness;
        float inner = size / 2.0f;
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            float ox = centerX + (float) (Math.cos(rad) * outer);
            float oy = centerY + (float) (Math.sin(rad) * outer);
            graphics.fill((int) ox, (int) oy, (int) ox + 1, (int) oy + 1, color);
        }
    }

    public static void drawMarkerDot(GuiGraphicsExtractor graphics, int x, int y, int color, int size) {
        graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
    }

    public static void drawPlayerHead(GuiGraphicsExtractor graphics, int x, int y, int size) {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) {
            drawMarkerDot(graphics, x, y, 0xFFFFFFFF, 5);
            return;
        }
        Identifier skin = lp.getSkin().body().texturePath();
        float scale = size / 8.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, -4, -4, 8f, 8f, 8, 8, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, -4, -4, 40f, 8f, 8, 8, 8, 8, 64, 64);
        graphics.pose().popMatrix();
    }
}
