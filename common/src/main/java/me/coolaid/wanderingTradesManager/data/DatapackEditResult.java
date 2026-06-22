package me.coolaid.wanderingTradesManager.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public record DatapackEditResult(boolean changed, boolean failed, Component message) {
    public static DatapackEditResult success(Component message) {
        return new DatapackEditResult(true, false, message);
    }

    public static DatapackEditResult unchanged(Component message) {
        return new DatapackEditResult(false, false, message.copy().withStyle(ChatFormatting.GRAY));
    }

    public static DatapackEditResult failure(Component message) {
        return new DatapackEditResult(false, true, message.copy().withStyle(ChatFormatting.RED));
    }
}