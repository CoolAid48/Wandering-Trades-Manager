package me.coolaid.wanderingTradesManager;

import me.coolaid.wanderingTradesManager.data.DatapackScanResult;
import me.coolaid.wanderingTradesManager.data.WanderingTradesDatapackManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WanderingTradesManager {
    public static final String MOD_ID = "wanderingtradesmanager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final WanderingTradesDatapackManager DATAPACK_MANAGER = new WanderingTradesDatapackManager();

    public static void init() {
        LOGGER.info("Wandering Trades Manager initialized");
    }

    public static void onServerStarting(MinecraftServer server) {
        DatapackScanResult scan = DATAPACK_MANAGER.refresh(server);

        scan.warnings().forEach(warning -> LOGGER.warn("[Wandering Trades scan] {}", warning));

        if (scan.hasMatchingPacks()) {
            LOGGER.info(
                    "Found {} Wandering Trades datapack(s) with {} custom head trade(s)",
                    scan.matchingPacks().size(),
                    scan.heads().size()
            );
        } else {
            LOGGER.warn("No installed Wandering Trades datapack was found in this world's datapacks folder");
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        DATAPACK_MANAGER.clear();
    }

    public static WanderingTradesDatapackManager datapackManager() {
        return DATAPACK_MANAGER;
    }
}
