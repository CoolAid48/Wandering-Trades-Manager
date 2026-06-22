package me.coolaid.wanderingTradesManager.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.coolaid.wanderingTradesManager.client.gui.HeadsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class WanderingTradesManagerFabricClient implements ClientModInitializer {
    private static final KeyMapping OPEN_HEADS_MANAGER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.wanderingtradesmanager.open_heads_manager",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_H,
            KeyMapping.Category.MISC
    ));

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_HEADS_MANAGER.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.setScreenAndShow(new HeadsScreen(null));
                }
            }
        });
    }
}
