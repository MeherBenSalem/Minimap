package com.mojang.blaze3d.platform;
public class NativeImage { private final int[] pixels; private final int width;
    public NativeImage(int width, int height, boolean clear) { this.width = width; pixels = new int[width * height]; }
    public void setPixelRGBA(int x, int y, int color) { pixels[y * width + x] = color; }
    public void setPixel(int x, int y, int color) { setPixelRGBA(x, y, color); }
    public int getPixelRGBA(int x, int y) { return pixels[y * width + x]; }
}
