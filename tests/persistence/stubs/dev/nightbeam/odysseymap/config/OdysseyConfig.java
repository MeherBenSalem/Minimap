package dev.nightbeam.odysseymap.config;
public class OdysseyConfig {
    public static final Value MAP_SAFE_RENDER_MODE = new Value(false);
    public static final Value ENABLED = new Value(); public static final Value MAP_FULLSCREEN_ENABLED = new Value();
    public static int effectiveScanInterval() { return 1; } public static int effectiveColumnsPerTick() { return 4; }
    public static int effectiveScanRadius() { return 0; }
    public static class Value { private final boolean value; public Value() { this(true); } public Value(boolean value) { this.value = value; } public boolean get() { return value; } }
}
