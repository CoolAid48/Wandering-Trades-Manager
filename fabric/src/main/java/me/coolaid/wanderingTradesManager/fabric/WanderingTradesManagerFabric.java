package me.coolaid.wanderingTradesManager.fabric;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class WanderingTradesManagerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        WanderingTradesManager.init();

        ServerLifecycleEvents.SERVER_STARTING.register(WanderingTradesManager::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(WanderingTradesManager::onServerStopping);
    }
}
