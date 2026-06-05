package dev.nightbeam.odysseymap.marker;

import dev.nightbeam.odysseymap.client.RuntimeClientState;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.render.MapRenderMath;
import dev.nightbeam.odysseymap.util.MathUtil;
import dev.nightbeam.odysseymap.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.List;

public final class MarkerRenderer {
    private MarkerRenderer() {}

    public static void renderHud(GuiGraphicsExtractor graphics, Minecraft mc, int mapX, int mapY, int size,
                                  float partialTick, boolean headingUp, float yaw) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ResourceKey<Level> dim = mc.level.dimension();
        List<Marker> markers = MarkerManager.get().getVisibleMarkers(dim);

        int centerX = mapX + size / 2;
        int centerY = mapY + size / 2;
        int stride = RuntimeClientState.getZoomBlocksPerPixel();
        float half = size / 2.0f;
        float radius = half - 4;

        if (!markers.isEmpty()) {
            for (Marker marker : markers) {
                float dx = (float) ((marker.getX() - player.getX()) / stride);
                float dz = (float) ((marker.getZ() - player.getZ()) / stride);

                if (headingUp) {
                    float rad = (float) Math.toRadians(yaw);
                    float rx = -dx * Mth.cos(rad) - dz * Mth.sin(rad);
                    float rz = dx * Mth.sin(rad) - dz * Mth.cos(rad);
                    dx = rx;
                    dz = rz;
                }

                float[] clamped = new float[2];
                if (OdysseyConfig.STICK_MARKERS_TO_BORDER.get()) {
                    MapRenderMath.clampToCircle(dx, dz, radius, clamped);
                } else {
                    clamped[0] = dx;
                    clamped[1] = dz;
                }

                int px = centerX + (int) clamped[0];
                int py = centerY + (int) clamped[1];

                if (!OdysseyConfig.STICK_MARKERS_TO_BORDER.get()) {
                    if (Math.abs(dx) > radius || Math.abs(dz) > radius) continue;
                }

                RenderUtil.drawMarkerDot(graphics, px, py, marker.getColor() | 0xFF000000, 4);

                if (OdysseyConfig.SHOW_MARKER_DISTANCE.get()) {
                    double dist = MathUtil.distanceXZ(player.getX(), player.getZ(), marker.getX(), marker.getZ());
                    String text = marker.getLabel() + " " + (int) dist + "m";
                    graphics.centeredText(mc.font, text, px, py - 10, 0xFFFFFF);
                }
            }
        }

        if (OdysseyConfig.SHOW_PLAYER_HEAD.get()) {
            RenderUtil.drawPlayerHead(graphics, centerX, centerY, 14);
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            if (!headingUp) {
                graphics.pose().rotate((float)Math.toRadians(-yaw));
            }
            RenderUtil.drawMarkerDot(graphics, 0, 0, 0xFFFFFFFF, 5);
            graphics.pose().popMatrix();
        }
    }

    public static void renderFullscreen(GuiGraphicsExtractor graphics, Minecraft mc, int mapX, int mapY, int mapW, int mapH,
                                         double centerWorldX, double centerWorldZ, int blocksPerPixel) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ResourceKey<Level> dim = mc.level.dimension();
        List<Marker> markers = MarkerManager.get().getVisibleMarkers(dim);
        int cx = mapX + mapW / 2;
        int cy = mapY + mapH / 2;
        int bpp = Math.max(1, blocksPerPixel);

        double playerDx = (player.getX() - centerWorldX) / bpp;
        double playerDz = (player.getZ() - centerWorldZ) / bpp;
        int playerPx = cx + (int) playerDx;
        int playerPy = cy + (int) playerDz;

        boolean waypointOnPlayer = false;
        for (Marker marker : markers) {
            if (marker.getType() == MarkerType.WAYPOINT) {
                double dist = MathUtil.distanceXZ(player.getX(), player.getZ(), marker.getX(), marker.getZ());
                if (dist < 2.0) { waypointOnPlayer = true; break; }
            }
        }

        if (!waypointOnPlayer && playerPx >= mapX && playerPy >= mapY && playerPx <= mapX + mapW && playerPy <= mapY + mapH) {
            if (OdysseyConfig.SHOW_PLAYER_HEAD.get()) {
                RenderUtil.drawPlayerHead(graphics, playerPx, playerPy, 16);
            } else {
                RenderUtil.drawMarkerDot(graphics, playerPx, playerPy, 0xFFFFFFFF, 6);
            }
        }

        for (Marker marker : markers) {
            if (marker.getType() == MarkerType.PLAYER) continue;
            double dx = (marker.getX() - centerWorldX) / (double) bpp;
            double dz = (marker.getZ() - centerWorldZ) / (double) bpp;
            int px = cx + (int) dx;
            int py = cy + (int) dz;
            if (px < mapX || py < mapY || px > mapX + mapW || py > mapY + mapH) continue;
            RenderUtil.drawMarkerDot(graphics, px, py, marker.getColor() | 0xFF000000, 5);
            String label = marker.getLabel();
            int labelW = mc.font.width(label);
            graphics.text(mc.font, label, px - labelW / 2, py - 10, marker.getColor() | 0xFF000000, false);
        }
    }
}
