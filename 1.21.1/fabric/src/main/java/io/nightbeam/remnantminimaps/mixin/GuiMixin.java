package io.nightbeam.remnantminimaps.mixin;

import io.nightbeam.remnantminimaps.client.RemnantClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the minimap as a HUD layer right below the chat, matching NeoForge's layer order. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderChat", at = @At("HEAD"))
    private void remnantminimaps$renderMinimap(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        RemnantClient.renderHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
