package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import io.nightbeam.remnantminimaps.server.MapTeleport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs on both sides. The mod stays a client mod; this only adds the optional server half of map
 * teleport, used in single-player and on servers that install the mod too.
 */
public class RemnantMinimapsFabricCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        MapTeleport.init(new MapTeleport.Hooks() {
            @Override
            public boolean hasChannel(ServerPlayer player) {
                return ServerPlayNetworking.canSend(player, MapTeleportPayloads.ACCESS_ID);
            }

            @Override
            public void send(ServerPlayer player, MapTeleportPayloads.Access payload) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                payload.encode(buf);
                ServerPlayNetworking.send(player, MapTeleportPayloads.ACCESS_ID, buf);
            }

            @Override
            public boolean permitted(ServerPlayer player) {
                // Fabric API for 1.20.1 has no permission module: the operator level decides.
                return MapTeleport.meetsConfiguredLevel(player);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MapTeleportPayloads.REQUEST_ID, (server, player, handler, buf, sender) -> {
            MapTeleportPayloads.Request request;
            try {
                request = MapTeleportPayloads.Request.decode(buf);
            } catch (RuntimeException e) {
                return; // malformed: ignore
            }
            server.execute(() -> MapTeleport.handleRequest(player, request));
        });
        ServerLifecycleEvents.SERVER_STARTING.register(MapTeleport::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> MapTeleport.onServerStopped());
        ServerTickEvents.END_SERVER_TICK.register(MapTeleport::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MapTeleport.onPlayerLeave(handler.player));
    }
}
