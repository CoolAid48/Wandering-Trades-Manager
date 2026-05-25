package me.coolaid.wanderingTradesManager.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.client.gui.WanderingTradesHeadsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = WanderingTradesManager.MOD_ID, dist = Dist.CLIENT)
public final class WanderingTradesManagerNeoForgeClient {
    private static final KeyMapping OPEN_HEADS_MANAGER = new KeyMapping(
            "key.wanderingtradesmanager.open_heads_manager",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            KeyMapping.Category.MISC
    );

    public WanderingTradesManagerNeoForgeClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_HEADS_MANAGER);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (OPEN_HEADS_MANAGER.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new WanderingTradesHeadsScreen(null));
            }
        }
    }
}
