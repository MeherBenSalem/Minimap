package io.nightbeam.remnantminimaps.client.render.gfx;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * A GUI element made of arbitrary float-precision quads. Vanilla GUI elements snap to integer
 * coordinates; this one is what lets the maps rotate and scroll smoothly at sub-pixel precision.
 */
public final class MeshElement implements GuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final Matrix3x2f pose;
    private final float[] vertices;
    private final int[] colors;
    private final int count;
    private final boolean textured;
    private final @Nullable ScreenRectangle scissor;
    private final @Nullable ScreenRectangle bounds;

    MeshElement(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] vertices, int[] colors,
                int count, boolean textured, @Nullable ScreenRectangle scissor, @Nullable ScreenRectangle bounds) {
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        this.pose = pose;
        this.vertices = vertices;
        this.colors = colors;
        this.count = count;
        this.textured = textured;
        this.scissor = scissor;
        this.bounds = bounds;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < count; i++) {
            int o = i * 4;
            VertexConsumer v = consumer.addVertexWith2DPose(pose, vertices[o], vertices[o + 1]);
            if (textured) {
                v.setUv(vertices[o + 2], vertices[o + 3]);
            }
            v.setColor(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textureSetup;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }
}
