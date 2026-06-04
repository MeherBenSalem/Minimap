package dev.nightbeam.odysseymap.color;

import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class BlockColorRegistry {
    private BlockColorRegistry() {}

    public static MapColor resolveMapColor(Level level, BlockPos pos, BlockState state) {
        MapColor override = BlockOverrideConfig.getResolvedOverrides().get(state);
        if (override != null) {
            return override;
        }
        MapColor color = state.getMapColor(level, pos);
        if (color == null || color == MapColor.NONE) {
            return MapColor.byId(11); // stone-like fallback id
        }
        return color;
    }

    public static int resolveRgb(Level level, BlockPos pos, BlockState state,
                                 MapColor mapColor, MapColor.Brightness brightness) {
        if (mapColor == MapColor.NONE || mapColor == null) {
            return 0x00000000;
        }
        return ColorPalette.mapColorToRgb(mapColor, brightness);
    }

    public static int moddedFallback(Level level, BlockPos pos, BlockState state) {
        return ColorPalette.mapColorToRgb(MapColor.byId(11), MapColor.Brightness.NORMAL);
    }
}
