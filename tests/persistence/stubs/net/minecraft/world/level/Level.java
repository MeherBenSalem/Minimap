package net.minecraft.world.level;
import net.minecraft.resources.ResourceKey;
public class Level {
    public ResourceKey<Level> dimension;
    public boolean loaded = true;
    public int color = 0xff123456;
    public Level(ResourceKey<Level> dimension) { this.dimension = dimension; }
    public ResourceKey<Level> dimension() { return dimension; }
    public boolean hasChunkAt(int x, int z) { return loaded; }
    public long getGameTime() { return 20; }
}
