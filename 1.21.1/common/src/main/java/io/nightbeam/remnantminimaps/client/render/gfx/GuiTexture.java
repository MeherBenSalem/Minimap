package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * An OpenGL texture with an explicit mip chain that we fill ourselves from ARGB int arrays. Not
 * registered with the texture manager: map regions come and go far too often for that.
 */
public final class GuiTexture implements AutoCloseable {
    private static ByteBuffer staging;

    private final int id;
    private final int width;
    private final int height;
    private final int mipLevels;
    private Samplers.Sampler applied;
    private boolean closed;

    private GuiTexture(int id, int width, int height, int mipLevels) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
    }

    public static GuiTexture create(String label, int width, int height, int mipLevels) {
        RenderSystem.assertOnRenderThreadOrInit();
        int id = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(NativeImage.InternalGlFormat.RGBA, id, mipLevels - 1, width, height);
        return new GuiTexture(id, width, height, mipLevels);
    }

    public int id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int mipLevels() {
        return mipLevels;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Binds the texture with the given filtering, ready to be sampled by the next draw. */
    void bind(Samplers.Sampler sampler) {
        RenderSystem.bindTexture(id);
        if (applied != sampler) {
            applied = sampler;
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, sampler.wrap());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, sampler.wrap());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, sampler.minFilter());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, sampler.magFilter());
        }
        RenderSystem.setShaderTexture(0, id);
    }

    /**
     * Uploads a sub-rectangle of one mip level from an ARGB array laid out with the given row stride.
     */
    public void upload(int level, int x, int y, int w, int h, int[] argb, int stride) {
        if (closed || w <= 0 || h <= 0) {
            return;
        }
        RenderSystem.assertOnRenderThreadOrInit();
        ByteBuffer buffer = staging(w * h * 4);
        IntBuffer ints = buffer.asIntBuffer();
        for (int row = 0; row < h; row++) {
            int src = (y + row) * stride + x;
            for (int col = 0; col < w; col++) {
                int c = argb[src + col];
                ints.put((c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
            }
        }
        buffer.limit(w * h * 4);
        GlStateManager._bindTexture(id);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, level, x, y, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                MemoryUtil.memAddress(buffer));
    }

    /**
     * Uploads every mip level of a square pyramid for the level-0 rectangle given (inclusive).
     */
    public void uploadPyramid(int[][] levels, int size, int x0, int y0, int x1, int y1) {
        for (int level = 0; level < Math.min(levels.length, mipLevels); level++) {
            int levelSize = size >> level;
            if (levelSize < 1) {
                break;
            }
            int lx0 = x0 >> level;
            int ly0 = y0 >> level;
            int lx1 = Math.min(levelSize - 1, x1 >> level);
            int ly1 = Math.min(levelSize - 1, y1 >> level);
            upload(level, lx0, ly0, lx1 - lx0 + 1, ly1 - ly0 + 1, levels[level], levelSize);
        }
    }

    private static ByteBuffer staging(int bytes) {
        if (staging == null || staging.capacity() < bytes) {
            if (staging != null) {
                MemoryUtil.memFree(staging);
            }
            staging = MemoryUtil.memAlloc(Math.max(bytes, 256 * 256 * 4));
        }
        staging.clear();
        return staging;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            TextureUtil.releaseTextureId(id);
        }
    }
}
