package io.nightbeam.remnantminimaps.client.render;

/**
 * World-to-screen projection for a map: a world point at (centerX, centerZ) lands on the screen
 * point (screenX, screenY), scaled by {@code scale} GUI pixels per block and rotated by
 * {@code rotation} radians (clockwise on screen).
 */
public final class MapView {
    public float screenX;
    public float screenY;
    public double centerX;
    public double centerZ;
    public float scale = 1.0f;
    public float rotation;
    private float cos = 1.0f;
    private float sin;

    public MapView set(float screenX, float screenY, double centerX, double centerZ, float scale, float rotation) {
        this.screenX = screenX;
        this.screenY = screenY;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = scale;
        this.rotation = rotation;
        this.cos = (float) Math.cos(rotation);
        this.sin = (float) Math.sin(rotation);
        return this;
    }

    public float toScreenX(double x, double z) {
        float dx = (float) (x - centerX) * scale;
        float dz = (float) (z - centerZ) * scale;
        return screenX + dx * cos - dz * sin;
    }

    public float toScreenY(double x, double z) {
        float dx = (float) (x - centerX) * scale;
        float dz = (float) (z - centerZ) * scale;
        return screenY + dx * sin + dz * cos;
    }

    public double toWorldX(float sx, float sy) {
        float dx = sx - screenX;
        float dy = sy - screenY;
        return centerX + (dx * cos + dy * sin) / scale;
    }

    public double toWorldZ(float sx, float sy) {
        float dx = sx - screenX;
        float dy = sy - screenY;
        return centerZ + (-dx * sin + dy * cos) / scale;
    }

    public float cos() {
        return cos;
    }

    public float sin() {
        return sin;
    }
}
