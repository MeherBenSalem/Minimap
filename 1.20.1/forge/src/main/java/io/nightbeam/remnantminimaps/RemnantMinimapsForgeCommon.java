package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.MapTeleportClient;
import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import io.nightbeam.remnantminimaps.server.MapTeleport;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Both sides: the optional server half of map teleport. The channel accepts peers without the mod,
 * so vanilla clients and servers are unaffected.
 */
final class RemnantMinimapsForgeCommon {
    private static final String PROTOCOL = "1";

    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(MapTeleportPayloads.REQUEST_ID, () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    /** Permission node; permission mods can grant or deny it, the default is the configured level. */
    static final PermissionNode<Boolean> MAP_TELEPORT = new PermissionNode<>(RemnantMinimaps.MOD_ID, "map_teleport",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> player != null && MapTeleport.meetsConfiguredLevel(player));

    private RemnantMinimapsForgeCommon() {}

    static void init() {
        CHANNEL.messageBuilder(MapTeleportPayloads.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapTeleportPayloads.Request::encode)
                .decoder(MapTeleportPayloads.Request::decode)
                .consumerMainThread((request, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) {
                        MapTeleport.handleRequest(player, request);
                    }
                })
                .add();
        CHANNEL.messageBuilder(MapTeleportPayloads.Access.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapTeleportPayloads.Access::encode)
                .decoder(MapTeleportPayloads.Access::decode)
                .consumerMainThread((access, context) -> MapTeleportClient.onAccess(access.allowed()))
                .add();

        MapTeleport.init(new MapTeleport.Hooks() {
            @Override
            public boolean hasChannel(ServerPlayer player) {
                return CHANNEL.isRemotePresent(player.connection.connection);
            }

            @Override
            public void send(ServerPlayer player, MapTeleportPayloads.Access payload) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
            }

            @Override
            public boolean permitted(ServerPlayer player) {
                return PermissionAPI.getPermission(player, MAP_TELEPORT);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PermissionGatherEvent.Nodes.class,
                event -> event.addNodes(MAP_TELEPORT));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartingEvent.class,
                event -> MapTeleport.onServerStarting(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class,
                event -> MapTeleport.onServerStopped());
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                MapTeleport.onServerTick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                MapTeleport.onPlayerLeave(player);
            }
        });
    }
}
