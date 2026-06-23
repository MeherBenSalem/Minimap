package dev.nightbeam.odysseymap.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Shadow
    SubmitNodeStorage submitNodeStorage = null;
}
