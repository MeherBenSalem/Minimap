package dev.nightbeam.odysseymap.world;

public class Tile {
    public static final int SIZE = 128;

    private final int[] pixels = new int[SIZE * SIZE];
    private boolean dirty = true;
    private long lastUpdatedTick;

    public int getPixel(int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= SIZE || localZ >= SIZE) {
            return 0;
        }
        return pixels[(127 - localZ) * SIZE + (127 - localX)];
    }

    public void setPixel(int localX, int localZ, int argb) {
        if (localX < 0 || localZ < 0 || localX >= SIZE || localZ >= SIZE) {
            return;
        }
        int index = (127 - localZ) * SIZE + (127 - localX);
        if (pixels[index] != argb) {
            pixels[index] = argb;
            dirty = true;
        }
    }

    public int[] getPixels() {
        return pixels;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public long getLastUpdatedTick() {
        return lastUpdatedTick;
    }

    public void setLastUpdatedTick(long tick) {
        this.lastUpdatedTick = tick;
    }
}
