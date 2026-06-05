package dev.nightbeam.odysseymap.util;

import net.minecraft.util.Mth;

public final class MathUtil {
    private MathUtil() {}

    public static double distanceXZ(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Mth.sqrt((float) (dx * dx + dz * dz));
    }
}
