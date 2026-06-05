package dev.nightbeam.odysseymap.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public final class DimensionUtil {
    private DimensionUtil() {}

    public static String dimensionId(ResourceKey<Level> dimension) {
        Identifier loc = dimension.identifier();
        return loc.getNamespace() + ":" + loc.getPath();
    }
}
