package io.nightbeam.remnantminimaps.server;

import io.nightbeam.remnantminimaps.RemnantMinimaps;
import io.nightbeam.remnantminimaps.api.MapTeleportGuard;
import io.nightbeam.remnantminimaps.core.config.ServerConfig;
import io.nightbeam.remnantminimaps.core.teleport.SafeLanding;
import io.nightbeam.remnantminimaps.core.teleport.TeleportRules;
import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import io.nightbeam.remnantminimaps.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server side of "teleport by clicking the world map". The client only asks; this class checks the
 * permission, the dimension and the world border, loads the target chunk, picks a safe landing spot
 * and moves the player. Everything here runs on the server thread.
 */
public final class MapTeleport {
    /** What the loader provides: networking and its permission API. */
    public interface Hooks {
        /** True when the player's client has the mod and can receive {@link MapTeleportPayloads.Access}. */
        boolean hasChannel(ServerPlayer player);

        void send(ServerPlayer player, MapTeleportPayloads.Access payload);

        /**
         * The loader's permission node {@code remnantminimaps.map_teleport} when it has a permission
         * API, defaulting to {@link #meetsConfiguredLevel}; otherwise just that level check.
         */
        boolean permitted(ServerPlayer player);
    }

    private static final int ACCESS_SYNC_TICKS = 20;
    private static final int LOAD_TIMEOUT_TICKS = 200;
    // Radius 0: only the target chunk is brought to full status (no neighbour is fully generated).
    private static final int TICKET_RADIUS = 0;

    private static final List<MapTeleportGuard> GUARDS = new CopyOnWriteArrayList<>();
    private static final TeleportRules.Cooldowns COOLDOWNS = new TeleportRules.Cooldowns();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Boolean> ACCESS_SENT = new HashMap<>();

    private static Hooks hooks = new Hooks() {
        @Override
        public boolean hasChannel(ServerPlayer player) {
            return false;
        }

        @Override
        public void send(ServerPlayer player, MapTeleportPayloads.Access payload) {
        }

        @Override
        public boolean permitted(ServerPlayer player) {
            return meetsConfiguredLevel(player);
        }
    };
    private static ServerConfig config = new ServerConfig();

    private enum Phase { CHECKING, LOADING }

    private static final class Pending {
        final ResourceKey<Level> dimension;
        final int x;
        final int z;
        final int hintY;
        final int deadline;
        Phase phase = Phase.CHECKING;

        Pending(ResourceKey<Level> dimension, int x, int z, int hintY, int deadline) {
            this.dimension = dimension;
            this.x = x;
            this.z = z;
            this.hintY = hintY;
            this.deadline = deadline;
        }
    }

    private MapTeleport() {}

    public static void init(Hooks loaderHooks) {
        hooks = loaderHooks;
    }

    public static void registerGuard(MapTeleportGuard guard) {
        GUARDS.add(guard);
    }

    public static ServerConfig config() {
        return config;
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onServerStarting(MinecraftServer server) {
        ServerConfig.Loaded loaded = ServerConfig.load(Services.PLATFORM.getConfigDir().resolve(ServerConfig.FILE_NAME));
        config = loaded.config();
        if (loaded.problem() != null) {
            RemnantMinimaps.LOG.warn("Problem with {}: {}. Using defaults where needed.", ServerConfig.FILE_NAME, loaded.problem());
        }
        ServerConfig.MapTeleport t = config.mapTeleport;
        RemnantMinimaps.LOG.info("Map teleport {} (operator level {}, cooldown {} s, ungenerated chunks {})",
                t.enabled ? "enabled" : "disabled", t.permissionLevel, t.cooldownSeconds,
                t.allowUngeneratedChunks ? "allowed" : "refused");
        clearState();
    }

    public static void onServerStopped() {
        clearState();
    }

    private static void clearState() {
        PENDING.clear();
        ACCESS_SENT.clear();
        COOLDOWNS.clear();
    }

    public static void onPlayerLeave(ServerPlayer player) {
        UUID id = player.getUUID();
        PENDING.remove(id);
        ACCESS_SENT.remove(id);
        COOLDOWNS.forget(id);
    }

    public static void onServerTick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            tickPending(server);
        }
        if (server.getTickCount() % ACCESS_SYNC_TICKS == 0) {
            syncAccess(server);
        }
    }

    // ------------------------------------------------------------------ permission

    /** Vanilla operator level of a player, 0 to 4. */
    public static int operatorLevel(ServerPlayer player) {
        for (int level = TeleportRules.MAX_LEVEL; level > 0; level--) {
            if (player.hasPermissions(level)) {
                return level;
            }
        }
        return 0;
    }

    /** The default permission rule: the player's operator level reaches the configured one. */
    public static boolean meetsConfiguredLevel(ServerPlayer player) {
        return operatorLevel(player) >= TeleportRules.clampLevel(config.mapTeleport.permissionLevel);
    }

    private static TeleportRules.Verdict access(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return TeleportRules.access(config.mapTeleport.enabled, server != null && !server.isDedicatedServer(),
                operatorLevel(player), hooks.permitted(player));
    }

    /** Tells each modded client whether it may offer "Teleport here", whenever that changes. */
    private static void syncAccess(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!hooks.hasChannel(player)) {
                continue;
            }
            boolean allowed = access(player) == TeleportRules.Verdict.ALLOWED;
            Boolean previous = ACCESS_SENT.put(player.getUUID(), allowed);
            if (previous == null || previous != allowed) {
                hooks.send(player, new MapTeleportPayloads.Access(allowed));
            }
        }
    }

    // ------------------------------------------------------------------ requests

    public static String dimensionId(ResourceKey<Level> key) {
        return key.location().toString();
    }

    /** Handles a request from a client. Server thread. */
    public static void handleRequest(ServerPlayer player, MapTeleportPayloads.Request request) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ServerConfig.MapTeleport cfg = config.mapTeleport;
        UUID id = player.getUUID();
        int x = request.x();
        int z = request.z();
        boolean inside = Level.isInSpawnableBounds(new BlockPos(x, level.getMinBuildHeight(), z))
                && level.getWorldBorder().isWithinBounds(x + 0.5, z + 0.5);
        long now = System.currentTimeMillis();
        TeleportRules.Verdict verdict = TeleportRules.check(new TeleportRules.Request(
                cfg.enabled,
                !server.isDedicatedServer(),
                operatorLevel(player),
                hooks.permitted(player),
                dimensionId(level.dimension()).equals(request.dimension()),
                inside,
                PENDING.containsKey(id),
                COOLDOWNS.remainingMs(id, now, cfg.cooldownSeconds * 1000L),
                false));
        if (verdict != TeleportRules.Verdict.ALLOWED) {
            refuse(player, verdict, request, now);
            return;
        }
        COOLDOWNS.mark(id, now);
        int hint = request.yHint() == MapTeleportPayloads.UNKNOWN_Y ? player.getBlockY() : request.yHint();
        Pending pending = new Pending(level.dimension(), x, z, hint, server.getTickCount() + LOAD_TIMEOUT_TICKS);
        PENDING.put(id, pending);

        if (cfg.allowUngeneratedChunks || level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
            startLoading(level, pending);
            return;
        }
        // Only teleport into land that exists: ask the region storage whether the chunk was ever saved.
        level.getChunkSource().chunkMap.read(new ChunkPos(x >> 4, z >> 4)).whenComplete((tag, error) ->
                server.execute(() -> {
                    if (PENDING.get(id) != pending) {
                        return;
                    }
                    if (error != null || tag == null || tag.isEmpty()) {
                        PENDING.remove(id);
                        ServerPlayer online = server.getPlayerList().getPlayer(id);
                        if (online != null) {
                            tell(online, "ungenerated", "That area has never been generated.");
                        }
                        return;
                    }
                    ServerLevel target = server.getLevel(pending.dimension);
                    if (target != null) {
                        startLoading(target, pending);
                    }
                }));
    }

    private static void startLoading(ServerLevel level, Pending pending) {
        pending.phase = Phase.LOADING;
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pending.x >> 4, pending.z >> 4), TICKET_RADIUS,
                new BlockPos(pending.x, 0, pending.z));
    }

    private static void tickPending(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.level().dimension().equals(pending.dimension)) {
                it.remove();
                continue;
            }
            if (server.getTickCount() > pending.deadline) {
                it.remove();
                tell(player, "timeout", "That area took too long to load.");
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (pending.phase == Phase.LOADING && level.getChunkSource().getChunkNow(pending.x >> 4, pending.z >> 4) != null) {
                it.remove();
                land(player, level, pending);
            }
        }
    }

    private static void land(ServerPlayer player, ServerLevel level, Pending pending) {
        SafeLanding.Spot spot = SafeLanding.nearest((x, z) -> LandingCells.landingY(level, x, z, pending.hintY),
                pending.x, pending.z, config.mapTeleport.landingSearchRadius);
        if (spot == null) {
            tell(player, "unsafe", "No safe place to land there.");
            return;
        }
        BlockPos landing = new BlockPos(spot.x(), spot.y(), spot.z());
        for (MapTeleportGuard guard : GUARDS) {
            Component reason;
            try {
                reason = guard.check(player, level, landing);
            } catch (RuntimeException e) {
                RemnantMinimaps.LOG.warn("A map teleport guard failed; refusing the teleport", e);
                reason = Component.translatableWithFallback("remnantminimaps.teleport.vetoed", "Teleporting there is not allowed.");
            }
            if (reason != null) {
                RemnantMinimaps.LOG.info("Map teleport of {} to {} {} {} refused by another mod: {}",
                        player.getName().getString(), spot.x(), spot.y(), spot.z(), reason.getString());
                player.sendSystemMessage(reason);
                return;
            }
        }
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        double tx = spot.x() + 0.5;
        double ty = spot.y();
        double tz = spot.z() + 0.5;
        if (!player.teleportTo(level, tx, ty, tz, Set.of(), player.getYRot(), player.getXRot())) {
            tell(player, "failed", "The teleport failed.");
            return;
        }
        if (!player.isFallFlying()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            player.setOnGround(true);
        }
        player.resetFallDistance();
        RemnantMinimaps.LOG.info("{} teleported from the world map to {} {} {} in {}",
                player.getName().getString(), spot.x(), spot.y(), spot.z(), dimensionId(level.dimension()));
        // Same feedback and admin log line as /tp, so it shows up wherever commands are audited.
        player.createCommandSourceStack().sendSuccess(() -> Component.translatable("commands.teleport.success.location.single",
                player.getDisplayName(), format(tx), format(ty), format(tz)), true);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }

    private static void refuse(ServerPlayer player, TeleportRules.Verdict verdict, MapTeleportPayloads.Request request, long now) {
        RemnantMinimaps.LOG.info("Map teleport of {} to {} {} in {} refused: {}", player.getName().getString(),
                request.x(), request.z(), request.dimension(), verdict.name().toLowerCase(Locale.ROOT));
        switch (verdict) {
            case DISABLED -> tell(player, "disabled", "Map teleport is turned off on this server.");
            case CHEATS_OFF -> tell(player, "cheats_off", "Map teleport needs cheats in this world.");
            case NO_PERMISSION -> tell(player, "no_permission", "You are not allowed to teleport from the map.");
            case WRONG_DIMENSION -> tell(player, "wrong_dimension", "The map shows another dimension.");
            case OUTSIDE_WORLD -> tell(player, "outside", "That place is outside the world border.");
            case BUSY -> tell(player, "busy", "Already preparing a teleport.");
            case COOLDOWN -> {
                long seconds = (COOLDOWNS.remainingMs(player.getUUID(), now, config.mapTeleport.cooldownSeconds * 1000L) + 999) / 1000;
                player.sendSystemMessage(Component.translatableWithFallback("remnantminimaps.teleport.cooldown",
                        "Wait %s s before teleporting from the map again.", seconds));
            }
            default -> tell(player, "vetoed", "Teleporting there is not allowed.");
        }
    }

    private static void tell(ServerPlayer player, String key, String fallback) {
        player.sendSystemMessage(Component.translatableWithFallback("remnantminimaps.teleport." + key, fallback));
    }
}
