package io.nightbeam.remnantminimaps.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/** Day-cycle phases named the way the Lands Between names them. */
public final class TimeOfDay {
    private TimeOfDay() {}

    public static boolean hasCycle(ClientLevel level) {
        return level.dimensionType().defaultClock().isPresent();
    }

    /** Ticks into the current day, 0 = sunrise. */
    public static long ticks(ClientLevel level) {
        return Math.floorMod(level.getDefaultClockTime(), 24000L);
    }

    /** Dial angle in radians: 0 when the sun is at its peak, PI at midnight. */
    public static float dialAngle(ClientLevel level, float partialTick) {
        float t = (ticks(level) + partialTick) / 24000.0f;
        return (float) ((t - 0.25f) * Math.PI * 2.0);
    }

    public static Component label(ClientLevel level) {
        if (!hasCycle(level)) {
            return Component.translatable("remnantminimaps.time.timeless");
        }
        long t = ticks(level);
        String key;
        if (t < 1000) key = "dawn";
        else if (t < 5000) key = "early_day";
        else if (t < 7000) key = "noon";
        else if (t < 11000) key = "afternoon";
        else if (t < 13000) key = "dusk";
        else if (t < 17000) key = "early_night";
        else if (t < 19000) key = "midnight";
        else if (t < 23000) key = "late_night";
        else key = "dawn";
        return Component.translatable("remnantminimaps.time." + key);
    }
}
