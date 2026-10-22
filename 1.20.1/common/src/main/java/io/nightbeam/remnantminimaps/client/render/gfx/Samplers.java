package io.nightbeam.remnantminimaps.client.render.gfx;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Filtering and wrapping for {@link GuiTexture}s. OpenGL keeps these per texture, so a sampler is
 * applied to the texture right before each draw that uses it.
 */
public final class Samplers {
    public record Sampler(int wrap, int minFilter, int magFilter) {}

    private static final Sampler SMOOTH = new Sampler(GL12.GL_CLAMP_TO_EDGE, GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR);
    private static final Sampler CRISP = new Sampler(GL12.GL_CLAMP_TO_EDGE, GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_NEAREST);
    private static final Sampler TILED = new Sampler(GL11.GL_REPEAT, GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR);

    private Samplers() {}

    /** Smooth trilinear sampling, for icons, frames and minified map tiles. */
    public static Sampler smooth() {
        return SMOOTH;
    }

    /** Crisp block pixels when magnified, still smooth when minified. */
    public static Sampler crisp() {
        return CRISP;
    }

    /** Repeating smooth sampling, for tiled paper and fog textures. */
    public static Sampler tiled() {
        return TILED;
    }
}
