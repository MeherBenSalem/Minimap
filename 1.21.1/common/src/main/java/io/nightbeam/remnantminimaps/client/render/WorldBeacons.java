package io.nightbeam.remnantminimaps.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.map.Columns;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Pillars of light rising from graces, beacons and bloodstains, with a floating label. Pillars
 * further away than the render distance are pulled in along the line of sight so they stay visible
 * on the horizon, the way beacons do in the Lands Between.
 */
public final class WorldBeacons {
    private static final int GRACE_COLOR = 0xFFF0CC6E;
    private static final int BEACON_COLOR = 0xFFFFE9A8;
    private static final int DEATH_COLOR = 0xFFC2412F;
    private static final int LABEL_BACKDROP = 0x50000000;
    private static final int FULL_BRIGHT = 0xF000F0;

    private WorldBeacons() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        RemnantConfig cfg = RemnantClient.config();
        ClientLevel level = mc.level;
        if (cfg == null || !cfg.worldBeams || level == null || mc.player == null || RemnantClient.session() == null) {
            return;
        }
        String dimension = RemnantClient.currentDimension();
        DimensionMap map = RemnantClient.currentMap();
        Vec3 cam = camera.getPosition();
        long gameTime = level.getGameTime();
        double horizon = Math.max(48.0, mc.options.getEffectiveRenderDistance() * 16.0 - 24.0);
        Font font = mc.font;
        List<Runnable> labels = new ArrayList<>();

        for (Marker marker : RemnantClient.session().markers.inDimension(dimension)) {
            if (marker.hidden || !marker.showBeam) {
                continue;
            }
            double dx = marker.x + 0.5 - cam.x;
            double dz = marker.z + 0.5 - cam.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > cfg.worldBeamDistance) {
                continue;
            }
            double shown = dist;
            if (dist > horizon) {
                double k = horizon / dist;
                dx *= k;
                dz *= k;
                shown = horizon;
            }
            int baseY = baseY(marker, map, level);
            int color = color(marker);
            int height = Math.max(32, level.getMaxBuildHeight() - 1 + 320 - baseY);

            poseStack.pushPose();
            poseStack.translate(dx - 0.5, baseY - cam.y, dz - 0.5);
            float width = (float) Math.max(1.0, shown / 24.0);
            BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION, partial, 1.0f, gameTime,
                    0, height, color, 0.22f * width, 0.48f * width);
            poseStack.popPose();

            if (cfg.worldLabels && marker.showLabel) {
                String text = marker.name.isEmpty() ? Component.translatable(marker.icon().translationKey()).getString() : marker.name;
                String label = text + "  " + Math.round(dist) + "m";
                double lx = dx;
                double ly = mc.player.getEyeY() + 1.2 - cam.y;
                double lz = dz;
                double ld = shown;
                labels.add(() -> renderLabel(poseStack, buffers, camera, font, label, lx, ly, lz, ld, color));
            }
        }
        // Labels after every pillar, so no later pillar is drawn over an earlier label.
        labels.forEach(Runnable::run);
    }

    private static int baseY(Marker marker, DimensionMap map, ClientLevel level) {
        if (marker.hasY) {
            return marker.y;
        }
        if (map != null && Columns.explored(map.columnColor(marker.x, marker.z))) {
            return map.columnHeight(marker.x, marker.z) + 1;
        }
        return level.getSeaLevel();
    }

    private static int color(Marker marker) {
        if (marker.kind() == Marker.Kind.BEACON) {
            return BEACON_COLOR;
        }
        if (marker.icon() == MarkerIcon.GRACE) {
            return GRACE_COLOR;
        }
        if (marker.kind() == Marker.Kind.DEATH) {
            return DEATH_COLOR;
        }
        return 0xFF000000 | marker.tint();
    }

    private static void renderLabel(PoseStack poseStack, MultiBufferSource buffers, Camera camera, Font font,
                                    String text, double x, double y, double z, double distance, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        float scale = (float) Math.max(0.025, distance * 0.0032);
        poseStack.scale(scale, -scale, scale);
        FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
        int width = font.width(seq);
        float left = -width / 2.0f;
        Matrix4f pose = poseStack.last().pose();
        // Font's own backdrop shares the glyphs' depth-sorted batch and ends up drawn over part of
        // the text. A separate backdrop batch is flushed before the glyphs, so the text stays on top.
        VertexConsumer backdrop = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        float x0 = left - 1.0f;
        float x1 = left + width + 1.0f;
        backdrop.addVertex(pose, x0, 9.0f, 0.01f).setColor(LABEL_BACKDROP).setLight(FULL_BRIGHT);
        backdrop.addVertex(pose, x1, 9.0f, 0.01f).setColor(LABEL_BACKDROP).setLight(FULL_BRIGHT);
        backdrop.addVertex(pose, x1, -1.0f, 0.01f).setColor(LABEL_BACKDROP).setLight(FULL_BRIGHT);
        backdrop.addVertex(pose, x0, -1.0f, 0.01f).setColor(LABEL_BACKDROP).setLight(FULL_BRIGHT);
        font.drawInBatch(seq, left, 0.0f, color, false, pose, buffers, Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
        poseStack.popPose();
    }
}
