package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.nightbeam.remnantminimaps.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.Arrays;

/** Builder for {@link MeshElement}s: textured or flat-colored quads and convex polygons. */
public final class Mesh {
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final boolean textured;
    private float[] vertices = new float[64];
    private int[] colors = new int[16];
    private int count;

    private Mesh(RenderPipeline pipeline, TextureSetup textureSetup, boolean textured) {
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        this.textured = textured;
    }

    public static Mesh textured(GpuTextureView view, GpuSampler sampler) {
        return new Mesh(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(view, sampler), true);
    }

    public static Mesh textured(GuiTexture texture, GpuSampler sampler) {
        return textured(texture.view(), sampler);
    }

    public static Mesh colored() {
        return new Mesh(RenderPipelines.GUI, TextureSetup.noTexture(), false);
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public Mesh vertex(float x, float y, float u, float v, int color) {
        if (count * 4 + 4 > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        int o = count * 4;
        vertices[o] = x;
        vertices[o + 1] = y;
        vertices[o + 2] = u;
        vertices[o + 3] = v;
        colors[count] = color;
        count++;
        return this;
    }

    /** Axis-aligned rectangle; vertex order matches vanilla blits (top-left, bottom-left, bottom-right, top-right). */
    public Mesh rect(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
        vertex(x0, y0, u0, v0, color);
        vertex(x0, y1, u0, v1, color);
        vertex(x1, y1, u1, v1, color);
        vertex(x1, y0, u1, v0, color);
        return this;
    }

    public Mesh rect(float x0, float y0, float x1, float y1, int color) {
        return rect(x0, y0, x1, y1, 0, 0, 1, 1, color);
    }

    /** A textured square centered at (cx, cy), rotated by {@code radians}. */
    public Mesh sprite(float cx, float cy, float halfW, float halfH, float radians, float u0, float v0, float u1, float v1, int color) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float ax = -halfW * cos + halfH * sin;
        float ay = -halfW * sin - halfH * cos;
        float bx = -halfW * cos - halfH * sin;
        float by = -halfW * sin + halfH * cos;
        vertex(cx + ax, cy + ay, u0, v0, color);
        vertex(cx + bx, cy + by, u0, v1, color);
        vertex(cx - ax, cy - ay, u1, v1, color);
        vertex(cx - bx, cy - by, u1, v0, color);
        return this;
    }

    /**
     * Fills a convex polygon given as parallel arrays, as a fan of quads. The GUI pipelines cull
     * back faces, so the fan is emitted in vanilla's winding whichever way the input runs.
     */
    public Mesh polygon(float[] xs, float[] ys, float[] us, float[] vs, int n, int color) {
        if (n < 3) {
            return this;
        }
        float area = 0.0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += xs[i] * ys[j] - xs[j] * ys[i];
        }
        boolean reverse = area > 0.0f;
        for (int i = 1; i + 1 < n; i += 2) {
            int a = reverse ? n - i : i;
            int b = reverse ? n - i - 1 : i + 1;
            int c = reverse ? Math.max(n - i - 2, 1) : Math.min(i + 2, n - 1);
            vertex(xs[0], ys[0], us[0], vs[0], color);
            vertex(xs[a], ys[a], us[a], vs[a], color);
            vertex(xs[b], ys[b], us[b], vs[b], color);
            vertex(xs[c], ys[c], us[c], vs[c], color);
        }
        return this;
    }

    /** Line segment drawn as a thin quad. */
    public Mesh line(float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4f) {
            return this;
        }
        float nx = -dy / len * width * 0.5f;
        float ny = dx / len * width * 0.5f;
        vertex(x0 - nx, y0 - ny, 0, 0, color);
        vertex(x0 + nx, y0 + ny, 0, 1, color);
        vertex(x1 + nx, y1 + ny, 1, 1, color);
        vertex(x1 - nx, y1 - ny, 1, 0, color);
        return this;
    }

    public void submit(GuiGraphicsExtractor graphics) {
        if (count == 0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = Clip.current();
        Vector2f tmp = new Vector2f();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            pose.transformPosition(vertices[i * 4], vertices[i * 4 + 1], tmp);
            minX = Math.min(minX, tmp.x);
            minY = Math.min(minY, tmp.y);
            maxX = Math.max(maxX, tmp.x);
            maxY = Math.max(maxY, tmp.y);
        }
        int bx = (int) Math.floor(minX);
        int by = (int) Math.floor(minY);
        ScreenRectangle bounds = new ScreenRectangle(bx, by,
                Math.max(1, (int) Math.ceil(maxX) - bx), Math.max(1, (int) Math.ceil(maxY) - by));
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                count = 0;
                return;
            }
        }
        ((GuiGraphicsExtractorAccessor) graphics).remnantminimaps$getGuiRenderState().addGuiElement(
                new MeshElement(pipeline, textureSetup, pose,
                        Arrays.copyOf(vertices, count * 4), Arrays.copyOf(colors, count),
                        count, textured, scissor, bounds));
        count = 0;
    }
}
