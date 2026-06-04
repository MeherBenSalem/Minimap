package dev.nightbeam.odysseymap.render;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class MapRenderMath {
    private MapRenderMath() {}

    public static int[] hudPosition(int size, int margin) {
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return switch (OdysseyConfig.POSITION.get()) {
            case TOP_LEFT -> new int[]{margin, margin};
            case TOP_RIGHT -> new int[]{sw - size - margin, margin};
            case BOTTOM_LEFT -> new int[]{margin, sh - size - margin};
            case BOTTOM_RIGHT -> new int[]{sw - size - margin, sh - size - margin};
        };
    }

    public static float interpolatedYaw(float partialTick) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        return Mth.lerp(partialTick, player.yRotO, player.getYRot());
    }

    public static void worldToLocal(int centerX, int centerZ, int worldX, int worldZ, int stride,
                                      float[] out) {
        out[0] = (worldX - centerX) / (float) stride;
        out[1] = (worldZ - centerZ) / (float) stride;
    }

    public static void clampToCircle(float x, float y, float radius, float[] out) {
        float dist = Mth.sqrt(x * x + y * y);
        if (dist <= radius) {
            out[0] = x;
            out[1] = y;
            return;
        }
        float scale = radius / dist;
        out[0] = x * scale;
        out[1] = y * scale;
    }
}
