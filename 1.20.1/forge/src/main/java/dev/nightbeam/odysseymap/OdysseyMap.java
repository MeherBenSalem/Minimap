package dev.nightbeam.odysseymap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nightbeam.odysseymap.client.ClientEvents;
import dev.nightbeam.odysseymap.client.OdysseyKeyMappings;
import dev.nightbeam.odysseymap.render.MinimapHudRenderer;
import dev.nightbeam.odysseymap.render.WorldWaypointRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(OdysseyMapCommon.MOD_ID)
public class OdysseyMap {

    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping OPEN_FULLSCREEN;
    private static KeyMapping ZOOM_IN;
    private static KeyMapping ZOOM_OUT;
    private static KeyMapping CREATE_WAYPOINT;

    public OdysseyMap() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            OdysseyMapCommon.init();
            var modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener(this::onRegisterKeys);
            modBus.addListener(this::onRegisterOverlays);
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        TOGGLE_MINIMAP = new KeyMapping("key.odysseymap.toggle",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_TOGGLE_MINIMAP, OdysseyKeyMappings.CATEGORY);
        OPEN_FULLSCREEN = new KeyMapping("key.odysseymap.fullscreen",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_OPEN_FULLSCREEN, OdysseyKeyMappings.CATEGORY);
        ZOOM_IN = new KeyMapping("key.odysseymap.zoom_in",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_IN, OdysseyKeyMappings.CATEGORY);
        ZOOM_OUT = new KeyMapping("key.odysseymap.zoom_out",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_ZOOM_OUT, OdysseyKeyMappings.CATEGORY);
        CREATE_WAYPOINT = new KeyMapping("key.odysseymap.waypoint",
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                OdysseyKeyMappings.KEY_CREATE_WAYPOINT, OdysseyKeyMappings.CATEGORY);
        event.register(TOGGLE_MINIMAP);
        event.register(OPEN_FULLSCREEN);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(CREATE_WAYPOINT);
    }

    private void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("minimap", (gui, graphics, partialTick, screenWidth, screenHeight) ->
                MinimapHudRenderer.render(graphics, partialTick, screenWidth, screenHeight));
    }

    private final ClientEvents events = new ClientEvents();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        events.onClientTick(Minecraft.getInstance());
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
    public void onSleep(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            events.onSleep(player, event.getPos());
        }
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

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WorldWaypointRenderer.render(
                event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.font, event.getCamera(), mc.level,
                event.getCamera().getPosition(), event.getPartialTick(), mc.level.getGameTime());
    }
}
