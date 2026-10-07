package dev.nightbeam.odysseymap.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Expected to fail against unmodified release-source commit d4bf73e (1.3.0). */
public class BaselineReproduction {
    public static void main(String[] args) {
        ResourceKey<Level> overworld = new ResourceKey<>(new ResourceLocation("minecraft:overworld"));
        ResourceKey<Level> nether = new ResourceKey<>(new ResourceLocation("minecraft:the_nether"));
        int failures = 0;
        TileCache cache = new TileCache(new ColumnSampler());
        cache.getOrCreate(overworld, 0, 0).setPixel(64, 64, 0xff123456);
        cache.clear();
        if (cache.getOrCreate(overworld, 0, 0).getPixel(64, 64) != 0xff123456) {
            System.out.println("FAIL: unload/reopen loses explored pixel"); failures++;
        }
        cache.getOrCreate(overworld, 0, 0).setPixel(64, 64, 0xff123456);
        cache.getOrCreate(nether, 0, 0);
        if (cache.getOrCreate(overworld, 0, 0).getPixel(64, 64) != 0xff123456) {
            System.out.println("FAIL: dimension round-trip loses explored pixel"); failures++;
        }
        cache.getOrCreate(overworld, 0, 0).setPixel(64, 64, 0xff123456);
        Level level = new Level(overworld); level.loaded = false;
        cache.samplePixel(level, 0, 0);
        if (cache.getOrCreate(overworld, 0, 0).getPixel(64, 64) != 0xff123456) {
            System.out.println("FAIL: sampling unloaded chunk erases explored pixel"); failures++;
        }
        if (failures != 0) throw new AssertionError(failures + " verified baseline regressions");
    }
}
