package io.nightbeam.remnantminimaps.client.ui.widget;

import io.nightbeam.remnantminimaps.client.ui.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Text field drawn as a dark inset with a gold underline instead of vanilla's grey box. */
public class RField extends EditBox {
    public RField(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x + 4, y + (height - 8) / 2, width - 8, 9, narration);
        setBordered(false);
        setTextColor(Ui.IVORY);
        this.frameX = x;
        this.frameY = y;
        this.frameW = width;
        this.frameH = height;
    }

    private final int frameX;
    private final int frameY;
    private final int frameW;
    private final int frameH;

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (isVisible()) {
            Ui.fill(g, frameX, frameY, frameX + frameW, frameY + frameH, 0x99000000);
            int line = isFocused() ? Ui.GOLD_BRIGHT : isHovered() ? Ui.GOLD : Ui.GOLD_DEEP;
            Ui.fill(g, frameX, frameY + frameH - 1, frameX + frameW, frameY + frameH, line);
        }
        super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isVisible() && mouseX >= frameX && mouseX < frameX + frameW && mouseY >= frameY && mouseY < frameY + frameH;
    }
}
