package dev.nightbeam.odysseymap.client;

import dev.nightbeam.odysseymap.OdysseyMap;
import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import dev.nightbeam.odysseymap.render.MinimapHudRenderer;
import dev.nightbeam.odysseymap.render.WorldWaypointRenderer;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onRegisterOverlays);
        modBus.addListener(ClientSetup::onConfigLoad);
        modBus.addListener(ClientSetup::onClientSetup);

        MinecraftForge.EVENT_BUS.register(new ClientEvents());
        MinecraftForge.EVENT_BUS.register(new BlockUpdateHandler());
        MinecraftForge.EVENT_BUS.register(new WorldWaypointRenderer());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(OdysseyMapClient::init);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        OdysseyKeyMappings.register(event);
    }

    private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("minimap", MinimapHudRenderer.OVERLAY);
    }

    private static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == BlockOverrideConfig.SPEC) {
            BlockOverrideConfig.reload();
            if (OdysseyMapClient.getTileCache() != null) {
                OdysseyMapClient.getTileCache().markAllDirty();
            }
        }
    }
}
