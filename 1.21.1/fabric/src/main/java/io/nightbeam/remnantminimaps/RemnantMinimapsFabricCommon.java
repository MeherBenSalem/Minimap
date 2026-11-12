package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import io.nightbeam.remnantminimaps.server.MapTeleport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs on both sides. The mod stays a client mod; this only adds the optional server half of map
 * teleport, used in single-player and on servers that install the mod too.
 */
public class RemnantMinimapsFabricCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(MapTeleportPayloads.Request.TYPE, MapTeleportPayloads.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(MapTeleportPayloads.Access.TYPE, MapTeleportPayloads.Access.CODEC);

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
                // Fabric API for 1.21.1 has no permission module: the operator level decides.
                return MapTeleport.meetsConfiguredLevel(player);
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
