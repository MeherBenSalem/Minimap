package dev.nightbeam.odysseymap.world;

public class Tile {
    public static final int SIZE = 128;

    private final int[] pixels = new int[SIZE * SIZE];
    private boolean dirty = true;
    private long lastUpdatedTick;
    // Rendering dirtiness is cleared independently of durable storage.
    private long revision;
    private long savedRevision;
    private final java.util.BitSet changedPixels = new java.util.BitSet(SIZE * SIZE);

    public synchronized int getPixel(int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= SIZE || localZ >= SIZE) return 0;
        return pixels[(127 - localZ) * SIZE + (127 - localX)];
    }

    public synchronized void setPixel(int localX, int localZ, int argb) {
        if (localX < 0 || localZ < 0 || localX >= SIZE || localZ >= SIZE) return;
        int index = (127 - localZ) * SIZE + (127 - localX);
        changedPixels.set(index);
        if (pixels[index] != argb) { pixels[index] = argb; dirty = true; revision++; }
    }

    public synchronized boolean needsSaving() { return revision != savedRevision; }
    public synchronized Snapshot snapshot() { return new Snapshot(pixels.clone(), revision); }
    public synchronized void markSaved(long storedRevision) { savedRevision = storedRevision; }

    synchronized void restore(int[] storedPixels) {
        if (storedPixels.length != pixels.length) throw new IllegalArgumentException("Invalid tile size");
        System.arraycopy(storedPixels, 0, pixels, 0, pixels.length);
        revision = savedRevision = 0;
        dirty = true;
        changedPixels.clear();
    }

    synchronized void mergeExisting(int[] previousPixels) {
        boolean observationsChangedStoredData = false;
        for (int i = 0; i < pixels.length; i++) {
            if (!changedPixels.get(i)) pixels[i] = previousPixels[i];
            else if (pixels[i] != previousPixels[i]) observationsChangedStoredData = true;
        }
        if (observationsChangedStoredData) revision++;
        dirty = true;
    }

    public record Snapshot(int[] pixels, long revision) {}

    public int[] getPixels() { return pixels; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public void markDirty() { this.dirty = true; }
    public void clearDirty() { this.dirty = false; }
    public long getLastUpdatedTick() { return lastUpdatedTick; }
    public void setLastUpdatedTick(long tick) { this.lastUpdatedTick = tick; }
}
