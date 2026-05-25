package me.coolaid.wanderingTradesManager.neoforge;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(WanderingTradesManager.MOD_ID)
public final class WanderingTradesManagerNeoForge {
    public WanderingTradesManagerNeoForge() {
        // Run our common setup.
        WanderingTradesManager.init();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        WanderingTradesManager.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WanderingTradesManager.onServerStopping(event.getServer());
    }
}
