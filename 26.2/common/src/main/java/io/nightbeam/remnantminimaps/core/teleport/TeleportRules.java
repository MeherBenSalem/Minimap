package io.nightbeam.remnantminimaps.core.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who may teleport from the world map, and when. Pure logic so that the server-side rules can be
 * tested without a running game; the Minecraft glue only gathers the facts.
 */
public final class TeleportRules {
    /** Operator level the vanilla {@code /tp} command needs. */
    public static final int TELEPORT_COMMAND_LEVEL = 2;
    /** Highest vanilla operator level (server owner). */
    public static final int MAX_LEVEL = 4;

    /** Outcome of a check. Everything except {@link #ALLOWED} is a refusal. */
    public enum Verdict {
        ALLOWED,
        /** The server owner turned map teleport off. */
        DISABLED,
        /** Single-player or LAN world without cheats. */
        CHEATS_OFF,
        /** The player's operator level, or a permission plugin, says no. */
        NO_PERMISSION,
        /** The map shows another dimension than the one the player is in. */
        WRONG_DIMENSION,
        /** Target outside the world border or the world's limits. */
        OUTSIDE_WORLD,
        /** The player teleported too recently. */
        COOLDOWN,
        /** A teleport for this player is already being prepared. */
        BUSY,
        /** Another mod refused it. */
        VETOED
    }

    private TeleportRules() {}

    /** The configured level, clamped to the vanilla range. */
    public static int clampLevel(int configured) {
        return Math.max(0, Math.min(MAX_LEVEL, configured));
    }

    /**
     * Whether a player may use map teleport at all. In single-player and LAN worlds it always needs
     * cheats (the level {@code /tp} needs), whatever level the configuration asks for.
     *
     * @param enabled          the server configuration switch
     * @param integratedServer true in single-player and LAN worlds
     * @param playerLevel      the player's vanilla operator level, 0 to 4
     * @param permitted        result of the permission check: the loader's permission node if there
     *                         is one, otherwise {@code playerLevel >= configured level}
     */
    public static Verdict access(boolean enabled, boolean integratedServer, int playerLevel, boolean permitted) {
        if (!enabled) {
            return Verdict.DISABLED;
        }
        if (integratedServer && playerLevel < TELEPORT_COMMAND_LEVEL) {
            return Verdict.CHEATS_OFF;
        }
        return permitted ? Verdict.ALLOWED : Verdict.NO_PERMISSION;
    }

    /** Facts about one teleport request, gathered on the server thread. */
    public record Request(
            boolean enabled,
            boolean integratedServer,
            int playerLevel,
            boolean permitted,
            boolean sameDimension,
            boolean insideWorld,
            boolean busy,
            long cooldownRemainingMs,
            boolean vetoed) {}

    /** Full check of one request, in the order a player would want to hear about problems. */
    public static Verdict check(Request r) {
        Verdict access = access(r.enabled(), r.integratedServer(), r.playerLevel(), r.permitted());
        if (access != Verdict.ALLOWED) {
            return access;
        }
        if (!r.sameDimension()) {
            return Verdict.WRONG_DIMENSION;
        }
        if (!r.insideWorld()) {
            return Verdict.OUTSIDE_WORLD;
        }
        if (r.busy()) {
            return Verdict.BUSY;
        }
        if (r.cooldownRemainingMs() > 0) {
            return Verdict.COOLDOWN;
        }
        if (r.vetoed()) {
            return Verdict.VETOED;
        }
        return Verdict.ALLOWED;
    }

    /** Remembers when each player last teleported from the map. Server thread only. */
    public static final class Cooldowns {
        private final Map<UUID, Long> last = new HashMap<>();

        /** Milliseconds until the player may teleport again, or 0. */
        public long remainingMs(UUID player, long nowMs, long cooldownMs) {
            Long previous = last.get(player);
            if (previous == null || cooldownMs <= 0) {
                return 0;
            }
            long left = previous + cooldownMs - nowMs;
            return Math.max(0, Math.min(cooldownMs, left));
        }

        public void mark(UUID player, long nowMs) {
            last.put(player, nowMs);
        }

        public void forget(UUID player) {
            last.remove(player);
        }

        public void clear() {
            last.clear();
        }
    }
}
