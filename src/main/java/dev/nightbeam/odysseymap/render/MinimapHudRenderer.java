package dev.nightbeam.odysseymap.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.gui.FullscreenMapScreen;
import dev.nightbeam.odysseymap.marker.MarkerRenderer;
import dev.nightbeam.odysseymap.util.RenderUtil;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class MinimapHudRenderer {
    public static final IGuiOverlay OVERLAY = MinimapHudRenderer::render;

    private MinimapHudRenderer() {}

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientEvents.isMinimapVisible()) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen instanceof FullscreenMapScreen) {
            return;
        }
        if (OdysseyConfig.HIDE_WHEN_SCREEN_OPEN.get() && mc.screen != null) {
            return;
        }
        if (mc.options.renderDebug) {
            return;
        }

        int size = OdysseyConfig.MINIMAP_SIZE.get();
        int margin = 8;
        int[] pos = MapRenderMath.hudPosition(size, margin);
        int x = pos[0];
        int y = pos[1];

        OdysseyMapClient.getMinimapTexture().compose(mc, size);

        float alpha = OdysseyConfig.TRANSPARENCY.get().floatValue();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        float yaw = MapRenderMath.interpolatedYaw(partialTick);
        boolean headingUp = OdysseyConfig.ROTATION_MODE.get() == OdysseyConfig.RotationMode.HEADING_UP;
        float rotation = headingUp ? (180 - yaw) : 0;

        graphics.pose().pushPose();
        graphics.pose().translate(x + size / 2.0f, y + size / 2.0f, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        graphics.pose().translate(-size / 2.0f, -size / 2.0f, 0);

        var tex = OdysseyMapClient.getMinimapTexture().getTextureLocation();
        if (tex != null) {
            graphics.blit(tex, 0, 0, 0, 0, size, size, size, size);
        }

        graphics.pose().popPose();

        drawBorder(graphics, x, y, size);
        if (OdysseyConfig.SHOW_COMPASS.get()) {
            drawCompass(graphics, x, y, size, headingUp ? (180 - yaw) : 0);
        }

        MarkerRenderer.renderHud(graphics, mc, x, y, size, partialTick, headingUp, yaw);

        if (OdysseyConfig.SHOW_COORDINATES.get()) {
            LocalPlayer player = mc.player;
            String coords = String.format("%d / %d", Mth.floor(player.getX()), Mth.floor(player.getZ()));
            graphics.drawString(mc.font, coords, x, y + size + 4, 0xFFFFFF, true);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int size) {
        int color = OdysseyConfig.BORDER_COLOR.get();
        int t = OdysseyConfig.BORDER_THICKNESS.get();
        if (t <= 0) {
            return;
        }
        if (OdysseyConfig.SHAPE.get() == OdysseyConfig.MinimapShape.CIRCLE) {
            RenderUtil.drawCircleBorder(graphics, x, y, size, t, color);
        } else {
            graphics.fill(x - t, y - t, x + size + t, y, color);
            graphics.fill(x - t, y + size, x + size + t, y + size + t, color);
            graphics.fill(x - t, y, x, y + size, color);
            graphics.fill(x + size, y, x + size + t, y + size, color);
        }
    }

    private static void drawCompass(GuiGraphics graphics, int x, int y, int size, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 + 6;
        float rad = (float) Math.toRadians(-yaw);
        String[] labels = {"N", "E", "S", "W"};
        float[] angles = {0, 90, 180, 270};
        for (int i = 0; i < 4; i++) {
            float a = (float) Math.toRadians(angles[i]) + rad;
            int lx = cx + (int) (Math.sin(a) * r);
            int ly = cy - (int) (Math.cos(a) * r);
            graphics.drawCenteredString(mc.font, labels[i], lx, ly - 4, 0xFFDDDDDD);
        }
    }
}
