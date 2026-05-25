package me.coolaid.wanderingTradesManager.data;

import net.minecraft.network.chat.Component;

public record DatapackEditResult(boolean success, Component message) {
    public static DatapackEditResult success(Component message) {
        return new DatapackEditResult(true, message);
    }

    public static DatapackEditResult failure(Component message) {
        return new DatapackEditResult(false, message);
    }
}
