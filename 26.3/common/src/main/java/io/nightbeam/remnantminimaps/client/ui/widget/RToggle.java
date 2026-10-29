package io.nightbeam.remnantminimaps.client.ui.widget;

import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Gold-framed checkbox with a label to its right. */
public class RToggle extends AbstractButton {
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public RToggle(int x, int y, int width, int height, Component label, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        setter.accept(!getter.getAsBoolean());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        int box = Math.min(getHeight() - 2, 11);
        int bx = getX();
        int by = getY() + (getHeight() - box) / 2;
        if (hot) {
            Ui.highlight(g, getX() - 4, getY(), getWidth() + 8, getHeight(), 0.8f);
        }
        boolean on = getter.getAsBoolean();
        Ui.sprite(g, Art.checkbox, bx, by, box, box, on ? 0.5f : 0.0f, 0.0f, on ? 1.0f : 0.5f, 1.0f, 0xFFFFFFFF);
        int color = !active ? Ui.FAINT : hot ? Ui.GOLD_BRIGHT : on ? Ui.IVORY : Ui.MUTED;
        Ui.text(g, Minecraft.getInstance().font, getMessage(), bx + box + 5, getY() + (getHeight() - 8) / 2, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
