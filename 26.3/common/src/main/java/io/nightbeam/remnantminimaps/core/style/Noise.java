package io.nightbeam.remnantminimaps.core.style;

/** Deterministic world-anchored noise so styling never swims while the map moves. */
public final class Noise {
    private Noise() {}

    public static int hash(int x, int z, int seed) {
        int h = x * 0x27D4EB2D + z * 0x165667B1 + seed * 0x61C88647;
        h = (h ^ (h >>> 15)) * 0x2C1B3C6D;
        h = (h ^ (h >>> 12)) * 0x297A2D39;
        return h ^ (h >>> 15);
    }

    /** Uniform value in [0, 1). */
    public static float hash01(int x, int z, int seed) {
        return (hash(x, z, seed) >>> 8) * (1.0f / 16777216.0f);
    }

    /** Smooth value noise in [0, 1). */
    public static float value(float x, float z, int seed) {
        int ix = floor(x);
        int iz = floor(z);
        float fx = x - ix;
        float fz = z - iz;
        float ux = fx * fx * (3.0f - 2.0f * fx);
        float uz = fz * fz * (3.0f - 2.0f * fz);
        float a = hash01(ix, iz, seed);
        float b = hash01(ix + 1, iz, seed);
        float c = hash01(ix, iz + 1, seed);
        float d = hash01(ix + 1, iz + 1, seed);
        float top = a + (b - a) * ux;
        float bottom = c + (d - c) * ux;
        return top + (bottom - top) * uz;
    }

    /** Fractal value noise in roughly [0, 1). */
    public static float fbm(float x, float z, int seed, int octaves) {
        float sum = 0.0f;
        float amp = 0.5f;
        float norm = 0.0f;
        for (int i = 0; i < octaves; i++) {
            sum += value(x, z, seed + i * 1013) * amp;
            norm += amp;
            x *= 2.03f;
            z *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    public static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
