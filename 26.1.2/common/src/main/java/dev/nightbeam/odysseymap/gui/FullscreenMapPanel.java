package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.marker.MarkerRenderer;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class FullscreenMapPanel extends AbstractWidget {
    private final FullscreenMapScreen parent;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public FullscreenMapPanel(int x, int y, int width, int height, FullscreenMapScreen parent) {
        super(x, y, width, height, Component.empty());
        this.parent = parent;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xE0101018);

        double panX = parent.getPanX();
        double panZ = parent.getPanZ();
        int blocksPerPixel = parent.getBlocksPerPixel();

        var texture = OdysseyMapClient.getMinimapTexture();
        texture.compose(mc, getWidth(), getHeight(), panX, panZ, false, blocksPerPixel);

        var tex = texture.getTextureLocation();
        if (tex != null) {
            int tw = texture.getTextureWidth();
            int th = texture.getTextureHeight();
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex, getX(), getY(), 0f, 0f,
                    getWidth(), getHeight(), tw, th, tw, th);
        }

        if (OdysseyConfig.FULLSCREEN_SHOW_GRID.get()) {
            drawGrid(graphics, blocksPerPixel);
        }

        MarkerRenderer.renderFullscreen(graphics, mc, getX(), getY(), getWidth(), getHeight(),
                panX, panZ, blocksPerPixel);
    }

    private void drawGrid(GuiGraphicsExtractor graphics, int blocksPerPixel) {
        int stepPx = Math.max(4, (int) Math.round(16.0 / blocksPerPixel));
        for (int x = getX(); x < getX() + getWidth(); x += stepPx) {
            graphics.fill(x, getY(), x + 1, getY() + getHeight(), 0x40FFFFFF);
        }
        for (int y = getY(); y < getY() + getHeight(); y += stepPx) {
            graphics.fill(getX(), y, getX() + getWidth(), y + 1, 0x40FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !isValidClickButton(event.buttonInfo())) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        if (mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY() && mouseY < getY() + getHeight()) {
            dragging = true;
            setFocused(true);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isValidClickButton(event.buttonInfo()) && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging || !isValidClickButton(event.buttonInfo())) return false;
        int bpp = parent.getBlocksPerPixel();
        double mouseX = event.x();
        double mouseY = event.y();
        double deltaX = mouseX - lastMouseX;
        double deltaY = mouseY - lastMouseY;
        parent.addPan(-deltaX * bpp, -deltaY * bpp);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY() && mouseY < getY() + getHeight()) {
            parent.adjustZoom(scrollY);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
