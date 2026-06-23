package dev.nightbeam.odysseymap.marker;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class Marker {
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;

    private final UUID id;
    private final MarkerType type;
    private final ResourceKey<Level> dimension;
    private int x;
    private int y;
    private int z;
    private String label;
    private int color;
    private boolean visible = true;
    private boolean favorite = false;

    public Marker(UUID id, MarkerType type, ResourceKey<Level> dimension, int x, int z, String label) {
        this(id, type, dimension, x, UNKNOWN_Y, z, label, type.defaultColor);
    }

    public Marker(UUID id, MarkerType type, ResourceKey<Level> dimension, int x, int y, int z,
                  String label, int color) {
        this.id = id;
        this.type = type;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.color = color;
    }

    public UUID getId() { return id; }
    public MarkerType getType() { return type; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public boolean hasKnownY() { return y != UNKNOWN_Y; }
}
