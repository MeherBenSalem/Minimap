package dev.nightbeam.odysseymap;

import dev.nightbeam.odysseymap.config.BlockOverrideConfig;
import dev.nightbeam.odysseymap.config.OdysseyConfig;
import dev.nightbeam.odysseymap.world.OdysseyMapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OdysseyMapCommon {
    public static final String MOD_ID = "odysseymap";
    public static final Logger LOGGER = LoggerFactory.getLogger("OdysseyMap");

    public static void init() {
        OdysseyConfig.load();
        BlockOverrideConfig.reload();
        OdysseyMapClient.init();
    }
}
