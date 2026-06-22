package me.coolaid.wanderingTradesManager.data;

import java.util.OptionalInt;

public record TradeSettings(
        int emeraldCost,
        int maxUses,
        OptionalInt minCustomHeads,
        OptionalInt maxCustomHeads
) {
    public static TradeSettings fallback() {
        return new TradeSettings(1, 1, OptionalInt.empty(), OptionalInt.empty());
    }

    public boolean hasCustomHeadRange() {
        return this.minCustomHeads.isPresent() && this.maxCustomHeads.isPresent();
    }
}