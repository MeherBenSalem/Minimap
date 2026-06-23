package dev.nightbeam.odysseymap.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nightbeam.odysseymap.render.WorldWaypointRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void odysseymap$renderWaypoints(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = new PoseStack();
        GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
        WorldWaypointRenderer.render(
                poseStack,
                ((LevelRendererAccessor) mc.levelRenderer).submitNodeStorage,
                mc.font,
                gr.mainCamera,
                mc.level,
                gr.mainCamera.position(),
                partialTick,
                mc.level.getGameTime());
    }
}
