package me.coolaid.wanderingTradesManager.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public record DatapackEditResult(boolean changed, Component message) {
    public static DatapackEditResult success(Component message) {
        return new DatapackEditResult(true, message);
    }

    public static DatapackEditResult unchanged(Component message) {
        return new DatapackEditResult(false, message.copy().withStyle(ChatFormatting.GRAY));
    }

    public static DatapackEditResult failure(Component message) {
        return new DatapackEditResult(false, message.copy().withStyle(ChatFormatting.RED));
    }
}
