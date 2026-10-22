package io.nightbeam.remnantminimaps.client.screen;

import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.render.MarkerPainter;
import io.nightbeam.remnantminimaps.client.render.gfx.Art;
import io.nightbeam.remnantminimaps.client.render.gfx.Clip;
import io.nightbeam.remnantminimaps.client.ui.Ui;
import io.nightbeam.remnantminimaps.client.ui.widget.RButton;
import io.nightbeam.remnantminimaps.client.ui.widget.RField;
import io.nightbeam.remnantminimaps.client.ui.widget.RToggle;
import io.nightbeam.remnantminimaps.core.marker.Marker;
import io.nightbeam.remnantminimaps.core.marker.MarkerIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import javax.annotation.Nullable;
import org.lwjgl.glfw.GLFW;

/** Create or edit a marker: name, icon, tint, position and visibility. */
public class MarkerEditScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 262;
    private static final int ICON_CELL = 22;
    private static final int ICON_COLS = 10;
    private static final int SWATCH = 13;
    private static final int[] SWATCHES = {
            -1, 0xE9DFC6, 0xF2DFA0, 0xE8C35E, 0xE9A15E, 0xE0705A, 0xC9A7FF, 0x9FD3E0, 0x9FD68A, 0xB8B0A0, 0x8C7A5C
    };

    private final @Nullable Screen parent;
    private final Marker marker;
    private final boolean creating;
    private final MarkerIcon[] icons = MarkerIcon.selectable();

    private RField nameField;
    private RField xField;
    private RField yField;
    private RField zField;
    private int left;
    private int top;
    private int iconsY;
    private int swatchY;
    private long openedAt;

    public MarkerEditScreen(@Nullable Screen parent, Marker marker, boolean creating) {
        super(Component.translatable(creating ? "remnantminimaps.edit.title_new" : "remnantminimaps.edit.title"));
        this.parent = parent;
        this.marker = marker.copy();
        this.creating = creating;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        if (parent instanceof WorldMapScreen) {
            parent.init(minecraft, width, height);
        }
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int x = left + 16;
        int inner = PANEL_W - 32;
        int y = top + 34;

        nameField = new RField(font, x, y + 10, inner - 40, 16, Component.translatable("remnantminimaps.edit.name"));
        nameField.setMaxLength(48);
        nameField.setValue(marker.name);
        addRenderableWidget(nameField);
        y += 34;

        iconsY = y + 10;
        int rows = (icons.length + ICON_COLS - 1) / ICON_COLS;
        y = iconsY + rows * ICON_CELL + 4;

        swatchY = y + 10;
        y = swatchY + SWATCH + 8;

        int fieldW = (inner - 16) / 3;
        xField = coordField(x, y + 10, fieldW, marker.x, "X");
        yField = coordField(x + fieldW + 8, y + 10, fieldW, marker.hasY ? marker.y : 64, "Y");
        zField = coordField(x + (fieldW + 8) * 2, y + 10, fieldW, marker.z, "Z");
        y += 34;

        int toggleW = (inner - 8) / 3;
        addRenderableWidget(new RToggle(x, y, toggleW, 14, Component.translatable("remnantminimaps.edit.minimap"),
                () -> marker.showOnMinimap, v -> marker.showOnMinimap = v));
        addRenderableWidget(new RToggle(x + toggleW + 4, y, toggleW, 14, Component.translatable("remnantminimaps.edit.beam"),
                () -> marker.showBeam, v -> marker.showBeam = v));
        addRenderableWidget(new RToggle(x + (toggleW + 4) * 2, y, toggleW, 14, Component.translatable("remnantminimaps.edit.label"),
                () -> marker.showLabel, v -> marker.showLabel = v));

        int by = top + PANEL_H - 28;
        int bw = 80;
        addRenderableWidget(new RButton(left + PANEL_W / 2 - bw - 46, by, bw, 18,
                Component.translatable("remnantminimaps.edit.save"), RButton.Style.FRAMED, b -> save()));
        addRenderableWidget(new RButton(left + PANEL_W / 2 + 46, by, bw, 18,
                Component.translatable("gui.cancel"), RButton.Style.FRAMED, b -> onClose()));
        if (!creating) {
            addRenderableWidget(new RButton(left + PANEL_W / 2 - 40, by, 80, 18,
                    Component.translatable("remnantminimaps.edit.delete"), RButton.Style.PLAIN, b -> delete()));
        }
        setInitialFocus(nameField);
    }

    private RField coordField(int x, int y, int w, int value, String axis) {
        RField field = new RField(font, x, y, w, 16, Component.literal(axis));
        field.setMaxLength(9);
        field.setValue(Integer.toString(value));
        addRenderableWidget(field);
        return field;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Clip.reset();
        Art.ensureLoaded();
        renderParent(g, partialTick);
        float fade = Math.min(1.0f, (System.currentTimeMillis() - openedAt) / 180.0f);
        Ui.fill(g, 0, 0, width, height, Ui.alpha(0xB0000000, fade));
        Ui.panel(g, left, top, PANEL_W, PANEL_H, 0.97f);
        Ui.centered(g, font, title, left + PANEL_W / 2, top + 12, Ui.GOLD_BRIGHT);
        Ui.divider(g, left + PANEL_W / 2f, top + 25, PANEL_W - 60, 0.9f);

        int x = left + 16;
        label(g, "remnantminimaps.edit.name", x, top + 34);
        // Preview of the chosen icon next to the name.
        float px = left + PANEL_W - 30;
        float py = top + 52;
        MarkerPainter.draw(g, marker, px, py, 24, 1.0f, true);

        label(g, "remnantminimaps.edit.icon", x, iconsY - 10);
        for (int i = 0; i < icons.length; i++) {
            int cx = x + (i % ICON_COLS) * ICON_CELL + ICON_CELL / 2;
            int cy = iconsY + (i / ICON_COLS) * ICON_CELL + ICON_CELL / 2;
            boolean selected = icons[i] == marker.icon();
            boolean hover = Math.abs(mouseX - cx) <= ICON_CELL / 2 && Math.abs(mouseY - cy) <= ICON_CELL / 2;
            if (selected) {
                Ui.glow(g, cx, cy, 14, Ui.alpha(0xFFFFD77A, 0.55f));
            } else if (hover) {
                Ui.glow(g, cx, cy, 12, Ui.alpha(0xFFFFFFFF, 0.25f));
            }
            int tint = icons[i].tintable() ? (selected && marker.color >= 0 ? marker.color : icons[i].defaultColor()) : 0xFFFFFF;
            Ui.icon(g, icons[i], cx, cy, selected ? 19 : 16, tint, selected || hover ? 1.0f : 0.8f);
            if (hover) {
                setTooltipForNextRenderPass(Component.translatable(icons[i].translationKey()));
            }
        }

        label(g, "remnantminimaps.edit.color", x, swatchY - 10);
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x + i * (SWATCH + 5);
            int c = SWATCHES[i];
            boolean selected = marker.color == c || (c < 0 && marker.color < 0);
            boolean fixed = !marker.icon().tintable();
            Ui.fill(g, sx - 1, swatchY - 1, sx + SWATCH + 1, swatchY + SWATCH + 1, selected && !fixed ? Ui.GOLD_BRIGHT : 0xFF2A241C);
            if (c < 0) {
                Ui.fill(g, sx, swatchY, sx + SWATCH, swatchY + SWATCH, 0xFF15120D);
                Ui.centered(g, font, "A", sx + SWATCH / 2 + 1, swatchY + 3, Ui.MUTED);
            } else {
                Ui.fill(g, sx, swatchY, sx + SWATCH, swatchY + SWATCH, (fixed ? 0x50000000 : 0xFF000000) | c);
            }
        }
        if (!marker.icon().tintable()) {
            Component note = Component.translatable("remnantminimaps.edit.fixed_color");
            Ui.text(g, font, note, left + PANEL_W - 16 - font.width(note), swatchY - 10, Ui.FAINT);
        }

        label(g, "remnantminimaps.edit.position", x, xField.getY() - 14);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        Ui.text(g, font, Component.translatable(key), x, y, Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = left + 16;
            for (int i = 0; i < icons.length; i++) {
                int cx = x + (i % ICON_COLS) * ICON_CELL + ICON_CELL / 2;
                int cy = iconsY + (i / ICON_COLS) * ICON_CELL + ICON_CELL / 2;
                if (Math.abs(mx - cx) <= ICON_CELL / 2.0 && Math.abs(my - cy) <= ICON_CELL / 2.0) {
                    marker.icon = icons[i];
                    if (icons[i] == MarkerIcon.GRACE) {
                        marker.showBeam = true;
                    }
                    return true;
                }
            }
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = x + i * (SWATCH + 5);
                if (mx >= sx && mx < sx + SWATCH && my >= swatchY && my < swatchY + SWATCH) {
                    marker.color = SWATCHES[i];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        if (RemnantClient.session() == null) {
            onClose();
            return;
        }
        String name = nameField.getValue().trim();
        marker.name = name.isEmpty() ? Component.translatable(marker.icon().translationKey()).getString() : name;
        marker.x = parse(xField.getValue(), marker.x);
        marker.z = parse(zField.getValue(), marker.z);
        String yText = yField.getValue().trim();
        if (!yText.isEmpty() && !yText.equals("-")) {
            marker.y = parse(yText, marker.y);
            marker.hasY = true;
        }
        RemnantClient.session().markers.put(marker);
        onClose();
    }

    private void delete() {
        if (RemnantClient.session() != null) {
            RemnantClient.session().markers.remove(marker);
        }
        onClose();
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderParent(GuiGraphics graphics, float partialTick) {
        if (parent instanceof WorldMapScreen) {
            Ui.backdrop(graphics, parent, partialTick);
        }
    }
}
