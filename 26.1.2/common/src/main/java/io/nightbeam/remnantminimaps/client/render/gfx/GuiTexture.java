package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A GPU texture with an explicit mip chain that we fill ourselves from ARGB int arrays. Not
 * registered with the texture manager: map regions come and go far too often for that.
 */
public final class GuiTexture implements AutoCloseable {
    private static ByteBuffer staging;

    private final GpuTexture texture;
    private final GpuTextureView view;
    private final int width;
    private final int height;
    private final int mipLevels;
    private boolean closed;

    private GuiTexture(GpuTexture texture, GpuTextureView view, int width, int height, int mipLevels) {
        this.texture = texture;
        this.view = view;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
    }

    public static GuiTexture create(String label, int width, int height, int mipLevels) {
        GpuDevice device = RenderSystem.getDevice();
        GpuTexture texture = device.createTexture(() -> label,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, width, height, 1, mipLevels);
        GpuTextureView view = device.createTextureView(texture);
        return new GuiTexture(texture, view, width, height, mipLevels);
    }

    public GpuTextureView view() {
        return view;
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

    /**
     * Uploads a sub-rectangle of one mip level from an ARGB array laid out with the given row stride.
     */
    public void upload(int level, int x, int y, int w, int h, int[] argb, int stride) {
        if (closed || w <= 0 || h <= 0) {
            return;
        }
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
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture, buffer, NativeImage.Format.RGBA, level, 0, x, y, w, h);
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
            view.close();
            texture.close();
        }
    }
}
