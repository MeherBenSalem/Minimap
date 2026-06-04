package dev.nightbeam.odysseymap.color;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class BiomeTint {
    private BiomeTint() {}

    public static int tintColor(Level level, BlockPos pos, BlockState state, MapColor baseColor) {
        if (baseColor == MapColor.NONE) {
            return ColorPalette.mapColorToRgb(MapColor.NONE, MapColor.Brightness.NORMAL);
        }

        var block = state.getBlock();
        Biome biome = level.getBiome(pos).value();
        int tint = -1;

        if (block == Blocks.GRASS_BLOCK || block == Blocks.TALL_GRASS || block == Blocks.FERN) {
            tint = biome.getGrassColor(pos.getX(), pos.getZ());
        } else if (block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES
                || block == Blocks.SPRUCE_LEAVES || block == Blocks.JUNGLE_LEAVES
                || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES
                || block == Blocks.MANGROVE_LEAVES || block == Blocks.CHERRY_LEAVES
                || block == Blocks.AZALEA_LEAVES || block == Blocks.FLOWERING_AZALEA_LEAVES
                || state.getMapColor(level, pos) == MapColor.PLANT) {
            tint = biome.getFoliageColor();
        } else if (!state.getFluidState().isEmpty() || baseColor == MapColor.WATER) {
            tint = biome.getWaterColor();
        }

        int rgb = ColorPalette.mapColorToRgb(baseColor, MapColor.Brightness.NORMAL);
        if (tint != -1) {
            return ColorPalette.applyBiomeTint(rgb, tint);
        }
        return rgb;
    }
}
