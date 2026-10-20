package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;

public final class Samplers {
    private Samplers() {}

    /** Smooth trilinear sampling, for icons, frames and minified map tiles. */
    public static GpuSampler smooth() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
    }

    /** Crisp block pixels when magnified, still smooth when minified. */
    public static GpuSampler crisp() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.NEAREST, true);
    }

    /** Repeating smooth sampling, for tiled paper and fog textures. */
    public static GpuSampler tiled() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
    }
}
