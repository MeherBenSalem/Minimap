package dev.nightbeam.odysseymap;

import dev.nightbeam.odysseymap.client.ClientSetup;
import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(OdysseyMap.MOD_ID)
public class OdysseyMap {
    public static final String MOD_ID = "odysseymap";
    public static final Logger LOGGER = LogManager.getLogger();

    public OdysseyMap() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, OdysseyConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BlockOverrideConfig.SPEC, "odysseymap-blocks.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
