package io.nightbeam.remnantminimaps.mixin;

import io.nightbeam.remnantminimaps.client.RemnantClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the minimap right below the chat, matching Forge's overlay order. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/GuiGraphics;III)V"))
    private void remnantminimaps$renderMinimap(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        RemnantClient.renderHud(graphics, partialTick);
    }
}
