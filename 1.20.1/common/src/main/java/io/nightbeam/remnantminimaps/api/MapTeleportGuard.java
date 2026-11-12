package io.nightbeam.remnantminimaps.api;

import io.nightbeam.remnantminimaps.server.MapTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets other mods refuse a teleport from the world map, for example inside claimed land or during
 * combat. Guards run on the server thread right before the player is moved, after the operator
 * level (or permission node) and the safe landing were checked.
 *
 * <pre>{@code
 * MapTeleportGuard.register((player, level, landing) ->
 *         Claims.isForeign(player, landing) ? Component.literal("This land is claimed") : null);
 * }</pre>
 */
@FunctionalInterface
public interface MapTeleportGuard {
    /**
     * @param landing block the player's feet will be in
     * @return null to allow the teleport, or the reason shown to the player to refuse it
     */
    Component check(ServerPlayer player, ServerLevel level, BlockPos landing);

    /** Adds a guard. Safe to call from any mod's setup on either side. */
    static void register(MapTeleportGuard guard) {
        MapTeleport.registerGuard(guard);
    }
}
