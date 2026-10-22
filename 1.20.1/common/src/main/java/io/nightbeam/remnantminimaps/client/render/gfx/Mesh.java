package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

/**
 * Builder for textured or flat-colored quads and convex polygons at float precision. Vanilla GUI
 * draws snap to integer coordinates; these are what let the maps rotate and scroll smoothly at
 * sub-pixel precision.
 */
public final class Mesh {
    private final GuiTexture texture;
    private final Samplers.Sampler sampler;
    private float[] vertices = new float[64];
    private int[] colors = new int[16];
    private int count;

    private Mesh(GuiTexture texture, Samplers.Sampler sampler) {
        this.texture = texture;
        this.sampler = sampler;
    }

    public static Mesh textured(GuiTexture texture, Samplers.Sampler sampler) {
        return new Mesh(texture, sampler);
    }

    public static Mesh colored() {
        return new Mesh(null, null);
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
     * Fills a convex polygon given as parallel arrays, as a fan of quads, emitted in vanilla's
     * winding whichever way the input runs.
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

    /** Draws the collected geometry right away (the GUI is immediate mode here) and resets the builder. */
    public void submit(GuiGraphics graphics) {
        if (count == 0) {
            return;
        }
        if (texture != null && texture.isClosed()) {
            count = 0;
            return;
        }
        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Painter's order, like the newer GUI: text drawn earlier sits slightly in front in the depth
        // buffer and must not poke through panels and overlays drawn after it. Drawing with an
        // always-passing depth test also resets the depth under the mesh, so text drawn on top of
        // it later is not cut by whatever was below.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        if (texture != null) {
            // position_tex_color discards everything below 10% alpha, which would cut hard edges
            // into soft art (vignettes, glows, fades). position_tex only drops fully transparent
            // texels, so draw with it and pass each run of equally tinted quads as the shader color.
            texture.bind(sampler);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            int start = 0;
            while (start < count) {
                int color = colors[start];
                int end = start + 1;
                while (end < count && colors[end] == color) {
                    end++;
                }
                RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f,
                        (color & 0xFF) / 255.0f, ((color >>> 24) & 0xFF) / 255.0f);
                BufferBuilder builder = Tesselator.getInstance().getBuilder();
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                for (int i = start; i < end; i++) {
                    int o = i * 4;
                    builder.vertex(pose, vertices[o], vertices[o + 1], 0.0f).uv(vertices[o + 2], vertices[o + 3]).endVertex();
                }
                BufferUploader.drawWithShader(builder.end());
                start = end;
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < count; i++) {
                builder.vertex(pose, vertices[i * 4], vertices[i * 4 + 1], 0.0f).color(colors[i]).endVertex();
            }
            BufferUploader.drawWithShader(builder.end());
        }
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        count = 0;
    }
}
