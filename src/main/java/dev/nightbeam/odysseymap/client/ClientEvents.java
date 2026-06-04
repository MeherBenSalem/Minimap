package dev.nightbeam.odysseymap.client;

import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.gui.FullscreenMapScreen;
import dev.nightbeam.odysseymap.gui.WaypointEditScreen;
import dev.nightbeam.odysseymap.marker.MarkerManager;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEvents {
    private static boolean minimapVisible = true;
    private int scanFrameCounter;

    public static boolean isMinimapVisible() {
        return minimapVisible && OdysseyConfig.ENABLED.get();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        scanFrameCounter++;
        if (scanFrameCounter >= OdysseyConfig.effectiveScanInterval()) {
            scanFrameCounter = 0;
            OdysseyMapClient.getScanner().tick(mc);
        }

        MarkerManager.get().tick(mc);
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        while (OdysseyKeyMappings.TOGGLE_MINIMAP.consumeClick()) {
            minimapVisible = !minimapVisible;
        }
        while (OdysseyKeyMappings.OPEN_FULLSCREEN.consumeClick()) {
            mc.setScreen(new FullscreenMapScreen());
        }
        while (OdysseyKeyMappings.ZOOM_IN.consumeClick()) {
            RuntimeClientState.zoomIn();
            OdysseyMapClient.getTileCache().markAllDirty();
        }
        while (OdysseyKeyMappings.ZOOM_OUT.consumeClick()) {
            RuntimeClientState.zoomOut();
            OdysseyMapClient.getTileCache().markAllDirty();
        }
        while (OdysseyKeyMappings.CREATE_WAYPOINT.consumeClick()) {
            LocalPlayer player = mc.player;
            ResourceKey<Level> dim = player.level().dimension();
            BlockPos pos = player.blockPosition();
            String defaultName = "Waypoint " + (MarkerManager.get().getWaypoints().size() + 1);
            mc.setScreen(WaypointEditScreen.forCreate(null, dim, pos.getX(), pos.getY(), pos.getZ(), defaultName));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        OdysseyMapClient.reset();
        MarkerManager.get().clearSession();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            OdysseyMapClient.getTileCache().clear();
        }
    }

    @SubscribeEvent
    public void onSleep(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            MarkerManager.get().setBedPoint(
                    player.level().dimension(),
                    event.getPos().getX(),
                    event.getPos().getZ()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer().isDeadOrDying()) {
            LocalPlayer player = event.getNewPlayer();
            MarkerManager.get().setDeathPoint(
                    player.level().dimension(),
                    player.blockPosition().getX(),
                    player.blockPosition().getZ()
            );
        }
    }
}
