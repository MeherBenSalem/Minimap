package dev.nightbeam.odysseymap.mixin;

import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void onSetLevel(ClientLevel level, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
        if (level == null && OdysseyMapClient.getTileCache() != null) {
            ClientEvents events = new ClientEvents();
            events.onLevelUnload();
        }
    }
}
