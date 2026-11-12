package io.nightbeam.remnantminimaps;

import io.nightbeam.remnantminimaps.client.command.RemnantCommands;
import io.nightbeam.remnantminimaps.client.Keys;
import io.nightbeam.remnantminimaps.client.MapTeleportClient;
import io.nightbeam.remnantminimaps.client.RemnantClient;
import io.nightbeam.remnantminimaps.client.screen.SettingsScreen;
import io.nightbeam.remnantminimaps.network.MapTeleportPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

@Mod(value = RemnantMinimaps.MOD_ID, dist = Dist.CLIENT)
public class RemnantMinimapsNeoForge {
    public RemnantMinimapsNeoForge(IEventBus modBus, ModContainer container) {
        RemnantClient.init();
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> Keys.ALL.forEach(event::register));
        modBus.addListener(RegisterGuiLayersEvent.class, event ->
                event.registerBelow(VanillaGuiLayers.CHAT, RemnantMinimaps.id("minimap"), RemnantClient::renderHud));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(MapTeleportPayloads.Access.TYPE,
                (payload, context) -> context.enqueueWork(() -> MapTeleportClient.onAccess(payload.allowed()))));
        MapTeleportClient.init(new MapTeleportClient.Hooks() {
            @Override
            public boolean serverSupports() {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                return connection != null && connection.hasChannel(MapTeleportPayloads.Request.TYPE);
            }

            @Override
            public void send(MapTeleportPayloads.Request request) {
                ClientPacketDistributor.sendToServer(request);
            }
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> RemnantClient.onClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ClientLevel level) {
                RemnantClient.onChunkLoaded(level, event.getChunk());
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> RemnantClient.onDisconnect());
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, event ->
                RemnantClient.submitWorld(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState()));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> RemnantCommands.register(event.getDispatcher()));
    }
}
