package dev.nightbeam.odysseymap.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

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
            graphics.fill((int) ox, (int) oy, (int) ox + 1, (int) oy + 1, color);
        }
    }

    public static void drawMarkerDot(GuiGraphics graphics, int x, int y, int color, int size) {
        graphics.fill(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
    }

    public static void drawPlayerHead(GuiGraphics graphics, int x, int y, int size) {
        LocalPlayer lp = Minecraft.getInstance().player;
        if (lp == null) { drawMarkerDot(graphics, x, y, 0xFFFFFFFF, 5); return; }
        try {
            ResourceLocation tex = lp.getSkinTextureLocation();
            if (tex == null) {
                drawMarkerDot(graphics, x, y, 0xFFFFFFFF, 5);
                return;
            }
            float scale = size / 8.0f;
            RenderSystem.enableBlend();
            RenderSystem.setShaderTexture(0, tex);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.blit(tex, -4, -4, 8, 8, 8f, 8f, 8, 8, 64, 64);
            graphics.blit(tex, -4, -4, 8, 8, 40f, 8f, 8, 8, 64, 64);
            graphics.pose().popPose();
        } catch (Exception e) {
            drawMarkerDot(graphics, x, y, 0xFFFFFFFF, 5);
        }
    }
}
