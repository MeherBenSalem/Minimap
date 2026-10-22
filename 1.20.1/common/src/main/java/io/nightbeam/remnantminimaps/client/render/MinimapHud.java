package io.nightbeam.remnantminimaps.client.render;

import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.TimeOfDay;
import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.Samplers;
import io.nightbeam.remnantminimaps.client.screen.WorldMapScreen;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** The heads-up minimap: parchment disc (or square), gold frame, markers, radar and info lines. */
public final class MinimapHud {
    private static final MapPainter PAINTER = new MapPainter();
    private static final MapView VIEW = new MapView();
    private static final int MAX_RADAR = 96;

    private MinimapHud() {}

    public static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        RemnantConfig cfg = RemnantClient.config();
        if (cfg == null || !cfg.minimapEnabled || RemnantClient.isMinimapHidden()) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }
        if (mc.screen instanceof WorldMapScreen) {
            return;
        }
        DimensionMap map = RemnantClient.currentMap();
        if (map == null) {
            return;
        }
        Art.ensureLoaded();

        int size = cfg.minimapSize;
        int margin = cfg.minimapMargin;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        List<Component> info = infoLines(mc, cfg);
        int infoHeight = info.size() * 10 + (info.isEmpty() ? 0 : 3);
        boolean right = cfg.minimapAnchor == RemnantConfig.Anchor.TOP_RIGHT || cfg.minimapAnchor == RemnantConfig.Anchor.BOTTOM_RIGHT;
        boolean bottom = cfg.minimapAnchor == RemnantConfig.Anchor.BOTTOM_LEFT || cfg.minimapAnchor == RemnantConfig.Anchor.BOTTOM_RIGHT;
        int x = right ? screenW - size - margin : margin;
        int y = bottom ? screenH - size - margin - infoHeight : margin;
        if (!bottom && right) {
            y += effectRowsOffset(player);
        }

        float half = size / 2.0f;
        float cx = x + half;
        float cy = y + half;
        boolean circle = cfg.minimapShape == RemnantConfig.Shape.CIRCLE;
        float inner = circle ? half * Art.RING_HOLE : half * Art.SQUARE_HOLE;

        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        float yaw = player.getViewYRot(partialTick);
        float rotation = cfg.minimapRotate ? (float) (Math.PI - Math.toRadians(yaw)) : 0.0f;
        float zoom = RemnantClient.animatedZoom();
        VIEW.set(cx, cy, px, pz, zoom, rotation);

        float alpha = cfg.minimapOpacity;
        int white = Ui.alpha(0xFFFFFFFF, alpha);
        if (circle) {
            PAINTER.setCircleClip(cx, cy, inner + 1.0f, 96);
        } else {
            PAINTER.setRectClip(cx - inner - 1, cy - inner - 1, cx + inner + 1, cy + inner + 1);
        }
        PAINTER.drawFog(g, VIEW, white);
        double guiScale = mc.getWindow().getGuiScale();
        boolean crisp = cfg.pixelatedZoom && zoom * guiScale >= 2.5;
        PAINTER.drawRegions(g, map, VIEW, false, crisp ? Samplers.crisp() : Samplers.smooth(), white);

        if (circle) {
            Ui.sprite(g, Art.vignetteDisc, cx - inner - 1, cy - inner - 1, inner * 2 + 2, inner * 2 + 2, white);
        } else {
            Ui.sprite(g, Art.vignetteSquare, cx - inner - 1, cy - inner - 1, inner * 2 + 2, inner * 2 + 2, white);
        }

        drawRadar(g, mc, cfg, circle, inner, partialTick, alpha);
        drawMarkers(g, mc, cfg, circle, inner, px, pz, alpha);

        float arrowSize = 14.0f * cfg.iconScale;
        float arrowRotation = cfg.minimapRotate ? 0.0f : (float) (Math.toRadians(yaw) + Math.PI);
        Ui.glow(g, cx, cy, arrowSize * 0.9f, Ui.alpha(0xFFFFE6A8, 0.35f * alpha));
        Ui.spriteRotated(g, Art.playerArrow, cx, cy, arrowSize, arrowSize, arrowRotation, white);

        if (circle) {
            Ui.spriteRotated(g, Art.ring, cx, cy, size, size, rotation, white);
        } else {
            Ui.sprite(g, Art.square, x, y, size, size, white);
        }

        if (cfg.minimapCardinals) {
            drawCardinals(g, mc.font, cx, cy, inner, circle, alpha);
        }

        int textY = y + size + 2;
        for (Component line : info) {
            String s = line.getString();
            Ui.outlinedCentered(g, mc.font, s, Math.round(cx), textY, Ui.alpha(Ui.IVORY, Math.max(0.6f, alpha)), Ui.alpha(0xFF000000, 0.75f * alpha));
            textY += 10;
        }
    }

    private static int effectRowsOffset(LocalPlayer player) {
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        if (harmful) {
            return 52;
        }
        return beneficial ? 26 : 0;
    }

    private static List<Component> infoLines(Minecraft mc, RemnantConfig cfg) {
        List<Component> lines = new ArrayList<>(3);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return lines;
        }
        BlockPos pos = player.blockPosition();
        if (cfg.minimapCoordinates) {
            lines.add(Component.literal(pos.getX() + "  " + pos.getY() + "  " + pos.getZ()));
        }
        if (cfg.minimapTimeOfDay && TimeOfDay.hasCycle(mc.level)) {
            lines.add(TimeOfDay.label(mc.level));
        }
        if (cfg.minimapBiome) {
            mc.level.getBiome(pos).unwrapKey().ifPresent(key ->
                    lines.add(Component.translatable("biome." + key.location().getNamespace() + "." + key.location().getPath())));
        }
        return lines;
    }

    private static void drawCardinals(GuiGraphics g, Font font, float cx, float cy, float inner, boolean circle, float alpha) {
        float sin = VIEW.sin();
        float cos = VIEW.cos();
        float[][] dirs = {{sin, -cos}, {cos, sin}, {-sin, cos}, {-cos, -sin}};
        String[] letters = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            float dx = dirs[i][0];
            float dy = dirs[i][1];
            float reach = inner - 6.5f;
            if (!circle) {
                reach /= Math.max(Math.abs(dx), Math.abs(dy));
            }
            float lx = cx + dx * reach;
            float ly = cy + dy * reach;
            int color = i == 0 ? Ui.alpha(Ui.GOLD_BRIGHT, alpha) : Ui.alpha(Ui.IVORY, 0.85f * alpha);
            Ui.outlinedCentered(g, font, letters[i], Math.round(lx), Math.round(ly - 3.5f), color, Ui.alpha(0xFF000000, 0.8f * alpha));
        }
    }

    private static void drawMarkers(GuiGraphics g, Minecraft mc, RemnantConfig cfg, boolean circle, float inner,
                                    double px, double pz, float alpha) {
        if (!cfg.markersOnMinimap || RemnantClient.session() == null) {
            return;
        }
        String dimension = RemnantClient.currentDimension();
        float size = 11.0f * cfg.iconScale;
        float limit = inner - size * 0.45f;
        for (Marker marker : RemnantClient.session().markers.inDimension(dimension)) {
            if (marker.hidden || !marker.showOnMinimap) {
                continue;
            }
            float sx = VIEW.toScreenX(marker.x + 0.5, marker.z + 0.5);
            float sy = VIEW.toScreenY(marker.x + 0.5, marker.z + 0.5);
            float dx = sx - VIEW.screenX;
            float dy = sy - VIEW.screenY;
            float reach = circle ? (float) Math.sqrt(dx * dx + dy * dy) : Math.max(Math.abs(dx), Math.abs(dy));
            if (reach <= limit) {
                MarkerPainter.draw(g, marker, sx, sy, size, alpha, false);
            } else if (cfg.markersAtEdge && marker.isPinned() && reach > 0.001f) {
                float k = (inner - size * 0.62f) / reach;
                float ex = VIEW.screenX + dx * k;
                float ey = VIEW.screenY + dy * k;
                float angle = (float) Math.atan2(dy, dx) + (float) (Math.PI / 2);
                MarkerPainter.draw(g, marker, ex, ey, size * 0.78f, alpha * 0.9f, false);
                float ax = VIEW.screenX + dx * ((inner - 2.5f) / reach);
                float ay = VIEW.screenY + dy * ((inner - 2.5f) / reach);
                MarkerPainter.edgeArrow(g, ax, ay, angle, 6.0f, Ui.alpha(0xFFFFFFFF, alpha));
            }
        }
    }

    private static void drawRadar(GuiGraphics g, Minecraft mc, RemnantConfig cfg, boolean circle, float inner,
                                  float partialTick, float alpha) {
        if (!cfg.radarPlayers && !cfg.radarHostiles && !cfg.radarPassives) {
            return;
        }
        int drawn = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.isInvisible()) {
                continue;
            }
            boolean isPlayer = entity instanceof Player;
            boolean hostile = entity instanceof Enemy;
            boolean passive = !hostile && entity instanceof Mob;
            if (!(isPlayer && cfg.radarPlayers || hostile && cfg.radarHostiles || passive && cfg.radarPassives)) {
                continue;
            }
            double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
            double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
            float sx = VIEW.toScreenX(ex, ez);
            float sy = VIEW.toScreenY(ex, ez);
            float dx = sx - VIEW.screenX;
            float dy = sy - VIEW.screenY;
            float reach = circle ? (float) Math.sqrt(dx * dx + dy * dy) : Math.max(Math.abs(dx), Math.abs(dy));
            if (reach > inner - 3) {
                continue;
            }
            if (isPlayer) {
                float rot = (float) (Math.toRadians(entity.getViewYRot(partialTick)) + Math.PI) + VIEW.rotation;
                Ui.spriteRotated(g, Art.playerOther, sx, sy, 10.0f * cfg.iconScale, 10.0f * cfg.iconScale, rot, Ui.alpha(0xFFFFFFFF, alpha));
            } else {
                int color = hostile ? 0xD84A3A : 0xEDE3CC;
                float s = 5.0f * cfg.iconScale;
                Ui.sprite(g, Art.entityDot, sx - s / 2, sy - s / 2, s, s, Ui.rgb(color, alpha));
            }
            if (++drawn >= MAX_RADAR) {
                break;
            }
        }
    }
}
