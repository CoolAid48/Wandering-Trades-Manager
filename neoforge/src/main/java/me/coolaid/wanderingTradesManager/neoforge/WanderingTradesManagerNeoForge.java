package me.coolaid.wanderingTradesManager.neoforge;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import net.neoforged.fml.common.Mod;

@Mod(WanderingTradesManager.MOD_ID)
public final class WanderingTradesManagerNeoForge {
    public WanderingTradesManagerNeoForge() {
        // Run our common setup.
        WanderingTradesManager.init();
    }
}
