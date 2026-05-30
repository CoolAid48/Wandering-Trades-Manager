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
    private static boolean headDisplayBufferPending;

    public static void init() {
        LOGGER.info("Initializing the Wandering Trades Manager");
    }

    public static void onServerStarting(MinecraftServer server) {
        resetHeadDisplayBuffer();
        DatapackScanResult scan = DATAPACK_MANAGER.refresh(server);
        logScan(scan);
    }

    public static void onServerStopping(MinecraftServer server) {
        DATAPACK_MANAGER.clear();
        clearHeadDisplayBuffer();
    }

    public static WanderingTradesDatapackManager datapackManager() {
        return DATAPACK_MANAGER;
    }

    public static synchronized boolean consumeHeadDisplayBuffer() {
        if (!headDisplayBufferPending) {
            return false;
        }

        headDisplayBufferPending = false;
        return true;
    }

    private static synchronized void resetHeadDisplayBuffer() {
        headDisplayBufferPending = true;
    }

    private static synchronized void clearHeadDisplayBuffer() {
        headDisplayBufferPending = false;
    }

    private static void logScan(DatapackScanResult scan) {
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
}
