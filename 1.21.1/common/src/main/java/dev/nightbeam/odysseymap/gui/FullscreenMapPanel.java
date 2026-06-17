package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.marker.MarkerRenderer;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xE0101018);

        var texture = OdysseyMapClient.getMinimapTexture();
        var tex = texture.getTextureLocation();
        if (tex != null) {
            int tw = texture.getTextureWidth();
            int th = texture.getTextureHeight();
            graphics.blit(tex, getX(), getY(), getWidth(), getHeight(), 0f, 0f, tw, th, tw, th);
        } else {
            String loading = "Loading map...";
            int textW = mc.font.width(loading);
            graphics.drawString(mc.font, loading,
                    getX() + (getWidth() - textW) / 2,
                    getY() + getHeight() / 2 - 4,
                    0xFFAAAAAA, false);
        }

        if (OdysseyConfig.FULLSCREEN_SHOW_GRID.get()) {
            drawGrid(graphics, parent.getBlocksPerPixel());
        }

        MarkerRenderer.renderFullscreen(graphics, mc, getX(), getY(), getWidth(), getHeight(),
                parent.getPanX(), parent.getPanZ(), parent.getBlocksPerPixel());
    }

    private void drawGrid(GuiGraphics graphics, int blocksPerPixel) {
        int stepPx = Math.max(4, (int) Math.round(16.0 / blocksPerPixel));
        for (int x = getX(); x < getX() + getWidth(); x += stepPx) {
            graphics.vLine(x, getY(), getY() + getHeight(), 0x40FFFFFF);
        }
        for (int y = getY(); y < getY() + getHeight(); y += stepPx) {
            graphics.hLine(getX(), getX() + getWidth(), y, 0x40FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0) return false;
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging || button != 0) return false;
        int bpp = parent.getBlocksPerPixel();
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
