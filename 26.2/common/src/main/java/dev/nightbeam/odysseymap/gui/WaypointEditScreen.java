package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.marker.Marker;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.marker.MarkerType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class WaypointEditScreen extends Screen {
    public static final int[] PRESET_COLORS = {
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
            0xFF55FFFF, 0xFF5555FF, 0xFFAA55FF, 0xFFFFFFFF,
            0xFFFF55AA, 0xFFAAAAAA
    };

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 230;
    private static final int PAD = 10;
    private static final int LABEL_H = 10;
    private static final int LABEL_GAP = 4;
    private static final int ROW_GAP = 12;
    private static final int SWATCH_SIZE = 14;
    private static final int SWATCH_PAD = 4;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;

    private final Screen parent;
    private final boolean editMode;
    private final UUID editId;
    private final ResourceKey<Level> dimension;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox hexColorBox;
    private int selectedColor;
    private int swatchX;
    private int swatchY;

    private int panelLeft;
    private int panelTop;
    private int nameLabelY;
    private int coordLabelY;
    private int colorLabelY;

    private final int pendingX;
    private final int pendingY;
    private final int pendingZ;
    private final String pendingName;

    public static WaypointEditScreen forCreate(Screen parent, ResourceKey<Level> dimension,
                                               int x, int y, int z, String defaultName) {
        return new WaypointEditScreen(parent, false, null, dimension, x, y, z,
                defaultName, MarkerType.WAYPOINT.defaultColor);
    }

    public static WaypointEditScreen forEdit(Screen parent, Marker marker) {
        return new WaypointEditScreen(parent, true, marker.getId(), marker.getDimension(),
                marker.getX(), marker.hasKnownY() ? marker.getY() : 64, marker.getZ(),
                marker.getLabel(), marker.getColor());
    }

    private WaypointEditScreen(Screen parent, boolean editMode, UUID editId,
                               ResourceKey<Level> dimension, int x, int y, int z,
                               String defaultName, int defaultColor) {
        super(Component.translatable(editMode ? "screen.odysseymap.waypoint_edit" : "screen.odysseymap.waypoint_create"));
        this.parent = parent;
        this.editMode = editMode;
        this.editId = editId;
        this.dimension = dimension;
        this.selectedColor = defaultColor;
        this.pendingX = x;
        this.pendingY = y;
        this.pendingZ = z;
        this.pendingName = defaultName;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;
        int y = panelTop + 28;

        nameLabelY = y;
        y += LABEL_H;
        nameBox = new EditBox(font, panelLeft + PAD, y, PANEL_W - PAD * 2, FIELD_H, Component.empty());
        nameBox.setValue(pendingName);
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);
        y += FIELD_H + ROW_GAP;

        coordLabelY = y;
        y += LABEL_H;
        int boxW = (PANEL_W - PAD * 2 - SWATCH_PAD * 2) / 3;
        xBox = new EditBox(font, panelLeft + PAD, y, boxW, FIELD_H, Component.empty());
        xBox.setValue(String.valueOf(pendingX));
        xBox.setMaxLength(8);
        addRenderableWidget(xBox);

        yBox = new EditBox(font, panelLeft + PAD + boxW + SWATCH_PAD, y, boxW, FIELD_H, Component.empty());
        yBox.setValue(String.valueOf(pendingY));
        yBox.setMaxLength(8);
        addRenderableWidget(yBox);

        zBox = new EditBox(font, panelLeft + PAD + (boxW + SWATCH_PAD) * 2, y, boxW, FIELD_H, Component.empty());
        zBox.setValue(String.valueOf(pendingZ));
        zBox.setMaxLength(8);
        addRenderableWidget(zBox);
        y += FIELD_H + ROW_GAP;

        colorLabelY = y;
        y += LABEL_H;
        swatchX = panelLeft + PAD;
        swatchY = y;
        y += SWATCH_SIZE + LABEL_GAP;

        hexColorBox = new EditBox(font, panelLeft + PAD, y, 90, FIELD_H, Component.empty());
        hexColorBox.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
        hexColorBox.setMaxLength(9);
        addRenderableWidget(hexColorBox);

        int btnW = (PANEL_W - PAD * 2 - SWATCH_PAD) / 2;
        int btnY = panelTop + PANEL_H - BTN_H - 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.save"), b -> save())
                .bounds(panelLeft + PAD, btnY, btnW, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.cancel"), b -> cancel())
                .bounds(panelLeft + PAD + btnW + SWATCH_PAD, btnY, btnW, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, 0xF0101018);
        graphics.outline(panelLeft, panelTop, PANEL_W, PANEL_H, 0xFF606070);

        graphics.centeredText(font, title, panelLeft + PANEL_W / 2, panelTop + 8, 0xFFFFFFFF);
        drawLabel(graphics, "screen.odysseymap.waypoint_name", nameLabelY);
        drawLabel(graphics, "screen.odysseymap.waypoint_coords", coordLabelY);
        drawLabel(graphics, "screen.odysseymap.waypoint_color", colorLabelY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int sx = swatchX + i * (SWATCH_SIZE + SWATCH_PAD);
            int sy = swatchY;
            int c = PRESET_COLORS[i];
            graphics.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFF000000 | (c & 0xFFFFFF));
            if ((c & 0xFFFFFF) == (selectedColor & 0xFFFFFF)) {
                graphics.outline(sx - 1, sy - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xFFFFFFFF);
            }
        }
    }

    private void drawLabel(GuiGraphicsExtractor graphics, String key, int y) {
        graphics.text(font, Component.translatable(key), panelLeft + PAD, y, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int sx = swatchX + i * (SWATCH_SIZE + SWATCH_PAD);
                int sy = swatchY;
                if (mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= sy && mouseY < sy + SWATCH_SIZE) {
                    selectedColor = PRESET_COLORS[i];
                    hexColorBox.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) name = "Waypoint";
        applyHexColor();
        int x = parseCoord(xBox.getValue(), pendingX);
        int y = parseCoord(yBox.getValue(), pendingY);
        int z = parseCoord(zBox.getValue(), pendingZ);

        if (editMode && editId != null) {
            MarkerManager.get().updateWaypoint(editId, name, selectedColor | 0xFF000000, x, y, z);
        } else {
            MarkerManager.get().addWaypoint(dimension, x, y, z, name, selectedColor | 0xFF000000);
        }
        returnToParent();
    }

    private void applyHexColor() {
        String hex = hexColorBox.getValue().trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        try { selectedColor = 0xFF000000 | (Integer.parseInt(hex, 16) & 0xFFFFFF); }
        catch (NumberFormatException ignored) {}
    }

    private static int parseCoord(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private void cancel() { returnToParent(); }

    private void returnToParent() {
        if (minecraft != null) {
            if (parent instanceof FullscreenMapScreen fullscreen) {
                fullscreen.refreshMarkerList();
            }
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
