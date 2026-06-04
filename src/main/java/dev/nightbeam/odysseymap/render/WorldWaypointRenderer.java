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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.List;

public class WorldWaypointRenderer {
    private static final ResourceLocation BEAM_TEXTURE =
            new ResourceLocation("textures/entity/beacon_beam.png");
    private static final int MAX_RENDER_DISTANCE = 256;

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!OdysseyConfig.SHOW_WAYPOINTS.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        List<Marker> waypoints = MarkerManager.get().getWaypointsInDimension(mc.level.dimension());
        if (waypoints.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float partialTick = event.getPartialTick();
        long gameTime = mc.level.getGameTime();

        for (Marker marker : waypoints) {
            if (marker.getType() != MarkerType.WAYPOINT) {
                continue;
            }

            double wx = marker.getX() + 0.5;
            double wz = marker.getZ() + 0.5;
            double wy = resolveY(mc.level, marker);

            double dist = mc.player.distanceToSqr(wx, wy, wz);
            if (dist > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(wx - camPos.x, wy - camPos.y, wz - camPos.z);

            renderBeam(poseStack, buffer, partialTick, gameTime, mc.level, wy, marker.getColor());

            String label = marker.getLabel() + " (" + (int) Math.sqrt(dist) + "m)";
            renderLabel(poseStack, buffer, font, camera, label);

            poseStack.popPose();
        }

        buffer.endBatch();
    }

    private static double resolveY(Level level, Marker marker) {
        if (marker.hasKnownY()) {
            return marker.getY();
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, marker.getX(), marker.getZ());
    }

    private static void renderBeam(PoseStack poseStack, MultiBufferSource buffer, float partialTick,
                                    long gameTime, Level level, double baseY, int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float[] color = new float[]{r, g, b};

        int height = Math.max(16, level.getMaxBuildHeight() - (int) baseY);
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                buffer,
                BEAM_TEXTURE,
                partialTick,
                1.0F,
                gameTime,
                0,
                height,
                color,
                0.15F,
                0.2F
        );
    }

    private static void renderLabel(PoseStack poseStack, MultiBufferSource buffer, Font font,
                                     Camera camera, String text) {
        poseStack.pushPose();
        poseStack.translate(0.0, 2.5, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float textWidth = font.width(text);
        int light = 0xF000F0;
        font.drawInBatch(
                text,
                -textWidth / 2.0F,
                0.0F,
                0xFFFFFFFF,
                false,
                matrix,
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                light,
                0
        );
        poseStack.popPose();
    }
}
