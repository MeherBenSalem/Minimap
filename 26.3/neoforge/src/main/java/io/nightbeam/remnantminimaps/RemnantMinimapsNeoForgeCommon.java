package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import io.nightbeam.remnantminimaps.server.MapTeleport;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Runs on both sides. The mod stays a client mod; this only adds the optional server half of map
 * teleport, used in single-player and on servers that install the mod too. The channel is optional,
 * so vanilla clients and servers are unaffected.
 */
@Mod(RemnantMinimaps.MOD_ID)
public class RemnantMinimapsNeoForgeCommon {
    /** Permission node; permission mods can grant or deny it, the default is the configured level. */
    public static final PermissionNode<Boolean> MAP_TELEPORT = new PermissionNode<>(RemnantMinimaps.MOD_ID, "map_teleport",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> player != null && MapTeleport.meetsConfiguredLevel(player));

    public RemnantMinimapsNeoForgeCommon(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
                .playToServer(MapTeleportPayloads.Request.TYPE, MapTeleportPayloads.Request.CODEC, (payload, context) ->
                        context.enqueueWork(() -> {
                            if (context.player() instanceof ServerPlayer player) {
                                MapTeleport.handleRequest(player, payload);
                            }
                        }))
                .playToClient(MapTeleportPayloads.Access.TYPE, MapTeleportPayloads.Access.CODEC));

        MapTeleport.init(new MapTeleport.Hooks() {
            @Override
            public boolean hasChannel(ServerPlayer player) {
                return player.connection.hasChannel(MapTeleportPayloads.Access.TYPE);
            }

            @Override
            public void send(ServerPlayer player, MapTeleportPayloads.Access payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }

            @Override
            public boolean permitted(ServerPlayer player) {
                return PermissionAPI.getPermission(player, MAP_TELEPORT);
            }
        });
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, event -> event.addNodes(MAP_TELEPORT));
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> MapTeleport.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> MapTeleport.onServerStopped());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> MapTeleport.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                MapTeleport.onPlayerLeave(player);
            }
        });
    }
}
