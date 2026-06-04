package dev.nightbeam.odysseymap.color;

import net.minecraft.world.level.material.MapColor;

/**
 * Converts map colors and applies brightness shading (ported from VanillaWorldMinimapRenderer).
 */
public final class ColorPalette {
    private ColorPalette() {}

    public static int mapColorToRgb(MapColor mapColor, MapColor.Brightness brightness) {
        if (mapColor == null || mapColor == MapColor.NONE) {
            return 0x00000000;
        }
        int rgb = mapColor.calculateRGBColor(brightness);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static MapColor.Brightness waterBrightness(int fluidDepth, int x, int z) {
        double d2 = fluidDepth * 0.1 + (double) ((x + z) & 1) * 0.2;
        if (d2 < 0.5) {
            return MapColor.Brightness.HIGH;
        } else if (d2 > 0.9) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    public static MapColor.Brightness terrainBrightness(double heightDelta, int x, int z) {
        double d2 = heightDelta * 4.0 / 5.0 + ((double) ((x + z) & 1) - 0.5) * 0.4;
        if (d2 > 0.6) {
            return MapColor.Brightness.HIGH;
        } else if (d2 < -0.6) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    public static int applyBiomeTint(int rgb, int tintRgb) {
        int ar = (rgb >> 16) & 0xFF;
        int ag = (rgb >> 8) & 0xFF;
        int ab = rgb & 0xFF;
        int tr = (tintRgb >> 16) & 0xFF;
        int tg = (tintRgb >> 8) & 0xFF;
        int tb = tintRgb & 0xFF;
        int r = ar * tr / 255;
        int g = ag * tg / 255;
        int b = ab * tb / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int moddedFallbackRgb(net.minecraft.world.level.block.state.BlockState state) {
        int hash = state.getBlock().hashCode() ^ state.hashCode();
        int r = 40 + (hash & 0x7F);
        int g = 40 + ((hash >> 8) & 0x7F);
        int b = 40 + ((hash >> 16) & 0x7F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
