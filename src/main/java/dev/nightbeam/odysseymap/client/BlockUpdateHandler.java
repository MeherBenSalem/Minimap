package dev.nightbeam.odysseymap.client;

import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Marks minimap tiles dirty when blocks change in loaded client chunks.
 */
public class BlockUpdateHandler {
    @SubscribeEvent
    public void onBlockChange(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && OdysseyMapClient.getTileCache() != null) {
                var pos = event.getPos();
                OdysseyMapClient.getTileCache().invalidateArea(mc.level, pos.getX(), pos.getZ(), pos.getX(), pos.getZ());
            }
        }
    }
}
