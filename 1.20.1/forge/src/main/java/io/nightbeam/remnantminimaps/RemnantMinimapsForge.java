package io.nightbeam.remnantminimaps;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entry point. The mod is client-only: on a dedicated server it loads but does nothing, and
 * the client classes are never touched.
 */
@Mod(RemnantMinimaps.MOD_ID)
public class RemnantMinimapsForge {
    public RemnantMinimapsForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RemnantMinimapsForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
