package io.nightbeam.remnantminimaps.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.session.DimensionMap;
import io.nightbeam.remnantminimaps.core.config.RemnantConfig;
import io.nightbeam.remnantminimaps.core.map.Columns;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Pillars of light rising from graces, beacons and bloodstains, with a floating label. Pillars
 * further away than the render distance are pulled in along the line of sight so they stay visible
 * on the horizon, the way beacons do in the Lands Between.
 */
public final class WorldBeacons {
    private static final int GRACE_COLOR = 0xFFF0CC6E;
    private static final int BEACON_COLOR = 0xFFFFE9A8;
    private static final int DEATH_COLOR = 0xFFC2412F;

    private WorldBeacons() {}

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        RemnantConfig cfg = RemnantClient.config();
        ClientLevel level = mc.level;
        if (cfg == null || !cfg.worldBeams || level == null || mc.player == null || RemnantClient.session() == null) {
            return;
        }
        String dimension = RemnantClient.currentDimension();
        DimensionMap map = RemnantClient.currentMap();
        Vec3 cam = state.cameraRenderState.pos;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float animation = (float) Math.floorMod(level.getGameTime(), 40L) + partial;
        double horizon = Math.max(48.0, mc.options.getEffectiveRenderDistance() * 16.0 - 24.0);
        Font font = mc.font;

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
            int height = Math.max(32, level.getMaxY() + 320 - baseY);

            poseStack.pushPose();
            poseStack.translate(dx - 0.5, baseY - cam.y, dz - 0.5);
            float width = (float) Math.max(1.0, shown / 24.0);
            BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0f, animation,
                    0, height, color, 0.22f * width, 0.48f * width);
            poseStack.popPose();

            if (cfg.worldLabels && marker.showLabel) {
                String text = marker.name.isEmpty() ? Component.translatable(marker.icon().translationKey()).getString() : marker.name;
                text = text + "  " + Math.round(dist) + "m";
                submitLabel(poseStack, collector, state, font, text, dx, mc.player.getEyeY() + 1.2 - cam.y, dz, shown, color);
            }
        }
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

    private static void submitLabel(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state, Font font,
                                    String text, double x, double y, double z, double distance, int color) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(state.cameraRenderState.orientation);
        float scale = (float) Math.max(0.025, distance * 0.0032);
        poseStack.scale(scale, -scale, scale);
        FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
        collector.submitText(poseStack, -font.width(seq) / 2.0f, 0.0f, seq, false, Font.DisplayMode.SEE_THROUGH,
                0xF000F0, color, 0x50000000, 0);
        poseStack.popPose();
    }
}
