package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.core.teleport.TeleportRules;
import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import io.nightbeam.remnantminimaps.server.MapTeleport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Runs on both sides. The mod stays a client mod; this only adds the optional server half of map
 * teleport, used in single-player and on servers that install the mod too.
 */
public class RemnantMinimapsFabricCommon implements ModInitializer {
    /** Fabric permission node; permission mods can grant or deny it, the default is the configured level. */
    public static final Identifier MAP_TELEPORT_PERMISSION = RemnantMinimaps.id("map_teleport");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(MapTeleportPayloads.Request.TYPE, MapTeleportPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MapTeleportPayloads.Access.TYPE, MapTeleportPayloads.Access.CODEC);

        MapTeleport.init(new MapTeleport.Hooks() {
            @Override
            public boolean hasChannel(ServerPlayer player) {
                return ServerPlayNetworking.canSend(player, MapTeleportPayloads.Access.TYPE);
            }

            @Override
            public void send(ServerPlayer player, MapTeleportPayloads.Access payload) {
                ServerPlayNetworking.send(player, payload);
            }

            @Override
            public boolean permitted(ServerPlayer player) {
                int level = TeleportRules.clampLevel(MapTeleport.config().mapTeleport.permissionLevel);
                return ((PermissionContextOwner) player).checkPermission(MAP_TELEPORT_PERMISSION, PermissionLevel.byId(level));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MapTeleportPayloads.Request.TYPE,
                (payload, context) -> context.server().execute(() -> MapTeleport.handleRequest(context.player(), payload)));
        ServerLifecycleEvents.SERVER_STARTING.register(MapTeleport::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> MapTeleport.onServerStopped());
        ServerTickEvents.END_SERVER_TICK.register(MapTeleport::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MapTeleport.onPlayerLeave(handler.player));
    }
}
