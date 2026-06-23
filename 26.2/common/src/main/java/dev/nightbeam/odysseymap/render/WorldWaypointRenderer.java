package dev.nightbeam.odysseymap.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.marker.Marker;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.marker.MarkerType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class WorldWaypointRenderer {
    private static final int MAX_RENDER_DISTANCE = 256;

    public static boolean shouldRender() {
        return OdysseyConfig.SHOW_WAYPOINTS.get();
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector submitCollector,
                              Font font, Camera camera,
                              Level level, Vec3 camPos, float partialTick, long gameTime) {
        if (!shouldRender()) return;
        List<Marker> waypoints = MarkerManager.get().getWaypointsInDimension(level.dimension());
        if (waypoints.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (Marker marker : waypoints) {
            if (marker.getType() != MarkerType.WAYPOINT) continue;

            double wx = marker.getX() + 0.5;
            double wz = marker.getZ() + 0.5;
            double wy = resolveY(level, marker);

            double dist = mc.player.distanceToSqr(wx, wy, wz);
            if (dist > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) continue;

            poseStack.pushPose();
            poseStack.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);

            renderBeam(poseStack, submitCollector, partialTick, gameTime, level, wy, marker.getColor());

            String label = marker.getLabel() + " (" + (int) Math.sqrt(dist) + "m)";
            renderLabel(poseStack, submitCollector, font, camera, label);

            poseStack.popPose();
        }
    }

    public static double resolveY(Level level, Marker marker) {
        if (marker.hasKnownY()) return marker.getY();
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, marker.getX(), marker.getZ());
    }

    public static void renderBeam(PoseStack poseStack, SubmitNodeCollector submitCollector, float partialTick,
                                  long gameTime, Level level, double baseY, int argb) {
        int height = Math.max(16, level.getMaxY() - (int) baseY);
        BeaconRenderer.submitBeaconBeam(poseStack, submitCollector, BeaconRenderer.BEAM_LOCATION, 1.0F, gameTime,
                0, height, argb, 0.15F, 0.2F);
    }

    public static void renderLabel(PoseStack poseStack, SubmitNodeCollector submitCollector,
                                   Font font, Camera camera, String text) {
        poseStack.pushPose();
        poseStack.translate(0.0, 2.5, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        int light = 0xF000F0;
        FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
        submitCollector.submitText(
                poseStack,
                -font.width(seq) / 2.0F, 0.0F,
                seq,
                false,
                Font.DisplayMode.SEE_THROUGH,
                light,
                0xFFFFFFFF,
                0,
                0);
        poseStack.popPose();
    }
}
