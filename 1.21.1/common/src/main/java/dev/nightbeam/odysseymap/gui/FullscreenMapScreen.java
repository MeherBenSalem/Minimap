package dev.nightbeam.odysseymap.gui;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.marker.Marker;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.client.RuntimeClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class FullscreenMapScreen extends Screen {
    private static final int SIDEBAR_W = 130;
    private static final int TOP_BAR_H = 22;

    private double panX;
    private double panZ;
    private int zoom = 1;
    private FullscreenMapPanel mapPanel;
    private MarkerListWidget markerList;

    public FullscreenMapScreen() {
        super(Component.translatable("screen.odysseymap.fullscreen"));
        zoom = OdysseyConfig.FULLSCREEN_DEFAULT_ZOOM.get();
    }

    public double getPanX() { return panX; }
    public double getPanZ() { return panZ; }
    public int getZoom() { return zoom; }

    public int getBlocksPerPixel() {
        return RuntimeClientState.getZoomBlocksPerPixel() * zoom;
    }

    public void addPan(double dx, double dz) { panX += dx; panZ += dz; }

    public void adjustZoom(double scrollDelta) {
        if (scrollDelta > 0 && zoom > 1) zoom--;
        else if (scrollDelta < 0 && zoom < 16) zoom++;
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player != null) {
            panX = minecraft.player.getX();
            panZ = minecraft.player.getZ();
        }

        int mapX = 4;
        int mapY = TOP_BAR_H + 4;
        int mapW = width - SIDEBAR_W - 8;
        int mapH = height - TOP_BAR_H - 8;

        mapPanel = new FullscreenMapPanel(mapX, mapY, mapW, mapH, this);
        addRenderableWidget(mapPanel);

        int sidebarX = width - SIDEBAR_W + 4;
        int listY = mapY + 14;
        int listH = mapH - 14;
        markerList = new MarkerListWidget(sidebarX, listY, SIDEBAR_W - 8, listH,
                this::onWaypointSelected, this::editWaypoint);
        addRenderableWidget(markerList);

        addRenderableWidget(Button.builder(Component.literal("Recenter"), b -> recenter())
                .bounds(width - SIDEBAR_W - 84, 2, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ WP"), b -> createWaypoint())
                .bounds(4, 2, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.edit"), b -> editSelected())
                .bounds(50, 2, 36, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.delete"), b -> deleteSelected())
                .bounds(88, 2, 36, 18).build());
    }

    private void recenter() {
        if (minecraft.player != null) {
            panX = minecraft.player.getX();
            panZ = minecraft.player.getZ();
        }
    }

    private void createWaypoint() {
        if (minecraft.player == null) return;
        int y = minecraft.player.blockPosition().getY();
        String defaultName = "Waypoint " + (MarkerManager.get().getWaypoints().size() + 1);
        minecraft.setScreen(WaypointEditScreen.forCreate(
                this, minecraft.player.level().dimension(),
                Mth.floor(panX), y, Mth.floor(panZ), defaultName));
    }

    private void editSelected() {
        Marker selected = markerList.getSelected();
        if (selected != null) editWaypoint(selected);
    }

    private void editWaypoint(Marker marker) {
        if (minecraft != null) {
            minecraft.setScreen(WaypointEditScreen.forEdit(this, marker));
        }
    }

    public void refreshMarkerList() {
        if (markerList != null) markerList.refresh();
    }

    private void deleteSelected() {
        Marker selected = markerList.getSelected();
        if (selected != null) {
            MarkerManager.get().removeWaypoint(selected.getId());
            markerList.refresh();
        }
    }

    private void onWaypointSelected(Marker marker) {
        if (marker != null) { panX = marker.getX(); panZ = marker.getZ(); }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xC0000000);
        graphics.fill(width - SIDEBAR_W, TOP_BAR_H, width, height, 0xE0181820);
        graphics.vLine(width - SIDEBAR_W, TOP_BAR_H, height, 0xFF404050);
        super.render(graphics, mouseX, mouseY, partialTick);

        int worldX = screenToWorldX(mouseX);
        int worldZ = screenToWorldZ(mouseY);
        if (mapPanel != null) {
            graphics.drawString(font, "X: " + worldX + "  Z: " + worldZ,
                    mapPanel.getX(), mapPanel.getY() + mapPanel.getHeight() - 10, 0xFFCCCCCC, true);
        }
        graphics.drawCenteredString(font, title, width / 2 - SIDEBAR_W / 2, 6, 0xFFFFFFFF);
        graphics.drawString(font, "Waypoints", width - SIDEBAR_W + 8, TOP_BAR_H + 4, 0xFFAAAAAA, false);
    }

    private int screenToWorldX(int screenX) {
        if (mapPanel == null) return Mth.floor(panX);
        int cx = mapPanel.getX() + mapPanel.getWidth() / 2;
        return Mth.floor(panX + (screenX - cx) * getBlocksPerPixel());
    }

    private int screenToWorldZ(int screenY) {
        if (mapPanel == null) return Mth.floor(panZ);
        int cy = mapPanel.getY() + mapPanel.getHeight() / 2;
        return Mth.floor(panZ + (screenY - cy) * getBlocksPerPixel());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
