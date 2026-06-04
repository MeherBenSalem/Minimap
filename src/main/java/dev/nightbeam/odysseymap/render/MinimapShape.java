package dev.nightbeam.odysseymap.render;

import dev.nightbeam.odysseymap.config.OdysseyConfig;

public final class MinimapShape {
    private static float[] circleMask;
    private static int maskSize;

    private MinimapShape() {}

    public static void ensureMask(int size) {
        if (circleMask != null && maskSize == size) {
            return;
        }
        maskSize = size;
        circleMask = new float[size * size];
        float center = (size - 1) / 2.0f;
        float radius = center;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center;
                float dy = y - center;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                circleMask[y * size + x] = dist <= radius ? 1.0f : 0.0f;
            }
        }
    }

    public static int applyMask(int argb, int x, int y) {
        if (OdysseyConfig.SHAPE.get() != OdysseyConfig.MinimapShape.CIRCLE) {
            return argb;
        }
        float alpha = circleMask[y * maskSize + x];
        if (alpha <= 0) {
            return 0;
        }
        int a = (argb >> 24) & 0xFF;
        int newA = (int) (a * alpha);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
