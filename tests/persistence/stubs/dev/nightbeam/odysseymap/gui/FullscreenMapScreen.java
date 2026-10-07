package dev.nightbeam.odysseymap.gui;
public class FullscreenMapScreen {
    public int getBlocksPerPixel() { return 1; }
    public Panel getMapPanel() { return new Panel(); }
    public static class Panel { public int getWidth() { return 128; } public int getHeight() { return 128; } } public double getPanX() { return 0; } public double getPanZ() { return 0; } }
