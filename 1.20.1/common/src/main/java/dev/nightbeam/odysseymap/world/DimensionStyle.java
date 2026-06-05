package dev.nightbeam.odysseymap.world;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;

public final class DimensionStyle {
    private DimensionStyle() {}

    public static boolean hasCeiling(Level level) {
        return level.dimensionType().hasCeiling();
    }

    public static MapColor ceilingDitherColor(int worldX, int worldZ, Level level) {
        int i3 = worldX + worldZ * 231871;
        i3 = i3 * i3 * 31287121 + i3 * 11;
        if ((i3 >> 20 & 1) == 0) {
            return Blocks.DIRT.defaultBlockState().getMapColor(level, net.minecraft.core.BlockPos.ZERO);
        }
        return Blocks.STONE.defaultBlockState().getMapColor(level, net.minecraft.core.BlockPos.ZERO);
    }

    public static int voidBackgroundArgb() { return 0xFF0A0A12; }
}
