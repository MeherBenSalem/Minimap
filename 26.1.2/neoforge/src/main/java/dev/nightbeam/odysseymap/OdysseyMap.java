package dev.nightbeam.odysseymap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.client.OdysseyKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@Mod(OdysseyMapCommon.MOD_ID)
public class OdysseyMap {

    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping OPEN_FULLSCREEN;
    private static KeyMapping ZOOM_IN;
    private static KeyMapping ZOOM_OUT;
    private static KeyMapping CREATE_WAYPOINT;

    private final ClientEvents events = new ClientEvents();
    private boolean wasDead;
    private boolean wasSleeping;

    public OdysseyMap(IEventBus modEventBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            OdysseyMapCommon.init();
            modEventBus.addListener(this::onRegisterKeys);
            NeoForge.EVENT_BUS.register(this);
        }
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        TOGGLE_MINIMAP = new KeyMapping("key.odysseymap.toggle", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_TOGGLE_MINIMAP, OdysseyKeyMappings.CATEGORY);
        OPEN_FULLSCREEN = new KeyMapping("key.odysseymap.fullscreen", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_OPEN_FULLSCREEN, OdysseyKeyMappings.CATEGORY);
        ZOOM_IN = new KeyMapping("key.odysseymap.zoom_in", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_IN, OdysseyKeyMappings.CATEGORY);
        ZOOM_OUT = new KeyMapping("key.odysseymap.zoom_out", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_OUT, OdysseyKeyMappings.CATEGORY);
        CREATE_WAYPOINT = new KeyMapping("key.odysseymap.waypoint", InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_CREATE_WAYPOINT, OdysseyKeyMappings.CATEGORY);
        event.register(TOGGLE_MINIMAP);
        event.register(OPEN_FULLSCREEN);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(CREATE_WAYPOINT);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        events.onClientTick(mc);

        LocalPlayer player = mc.player;
        if (player != null) {
            if (wasDead && !player.isDeadOrDying()) {
                events.onPlayerDeath(player);
            }
            wasDead = player.isDeadOrDying();

            if (player.isSleeping() && !wasSleeping) {
                player.getSleepingPos().ifPresent(bedPos -> events.onSleep(player, bedPos));
            }
            wasSleeping = player.isSleeping();
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (TOGGLE_MINIMAP.consumeClick()) events.onToggleMinimap();
        while (OPEN_FULLSCREEN.consumeClick()) events.onOpenFullscreen(mc);
        while (ZOOM_IN.consumeClick()) events.onZoomIn();
        while (ZOOM_OUT.consumeClick()) events.onZoomOut();
        while (CREATE_WAYPOINT.consumeClick()) events.onCreateWaypoint(mc);
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        events.onPlayerLogout();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) events.onLevelUnload();
    }

    @SubscribeEvent
    public void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer().isDeadOrDying()) {
            events.onPlayerDeath(event.getNewPlayer());
        }
    }

    @SubscribeEvent
    public void onBlockChange(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) {
            var pos = event.getPos();
            events.onBlockChange(Minecraft.getInstance(), pos.getX(), pos.getZ());
        }
    }
}
