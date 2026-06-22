package me.coolaid.wanderingTradesManager.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.coolaid.wanderingTradesManager.client.gui.HeadsScreen;

public final class WanderingTradesManagerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HeadsScreen::new;
    }
}
