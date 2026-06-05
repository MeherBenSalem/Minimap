package dev.nightbeam.odysseymap.mixin;

import dev.nightbeam.odysseymap.client.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class MixinLevel {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void onSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == ((Object) this)) {
            ClientEvents events = new ClientEvents();
            events.onBlockChange(mc, pos.getX(), pos.getZ());
        }
    }
}
