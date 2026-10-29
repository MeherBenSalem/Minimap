package io.nightbeam.remnantminimaps.core.map;

/** Inclusive integer rectangle that grows to cover every area marked dirty. */
public final class DirtyRect {
    private int x0;
    private int z0;
    private int x1;
    private int z1;
    private boolean empty = true;

    public void add(int ax0, int az0, int ax1, int az1) {
        if (empty) {
            x0 = ax0;
            z0 = az0;
            x1 = ax1;
            z1 = az1;
            empty = false;
        } else {
            x0 = Math.min(x0, ax0);
            z0 = Math.min(z0, az0);
            x1 = Math.max(x1, ax1);
            z1 = Math.max(z1, az1);
        }
    }

    public void addAll(int size) {
        add(0, 0, size - 1, size - 1);
    }

    public void clear() {
        empty = true;
    }

    public void set(int ax0, int az0, int ax1, int az1) {
        empty = true;
        add(ax0, az0, ax1, az1);
    }

    public boolean isEmpty() {
        return empty;
    }

    public int x0() { return x0; }
    public int z0() { return z0; }
    public int x1() { return x1; }
    public int z1() { return z1; }
}
