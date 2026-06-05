package dev.nightbeam.odysseymap.mixin;

import dev.nightbeam.odysseymap.render.MinimapHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void odysseymap$renderMinimap(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        MinimapHudRenderer.render(graphics, partialTick,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
    }
}
