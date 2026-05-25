package me.coolaid.wanderingTradesManager.data;

import java.util.Locale;

public record CustomHead(
        String name,
        String textureValue,
        int tradeIndex,
        String sourcePack,
        String sourceFunction,
        HeadType type
) {
    public CustomHead(String name, String textureValue, int tradeIndex, String sourcePack, String sourceFunction) {
        this(name, textureValue, tradeIndex, sourcePack, sourceFunction, HeadType.fromName(name));
    }

    public CustomHead {
        name = clean(name);
        textureValue = clean(textureValue);
        sourcePack = clean(sourcePack);
        sourceFunction = clean(sourceFunction);
        type = type == null ? HeadType.fromName(name) : type;
    }

    public boolean isValid() {
        return !name.isEmpty() && !textureValue.isEmpty();
    }

    public String dedupeKey() {
        if (!textureValue.isEmpty()) {
            return "texture:" + textureValue;
        }

        return "name:" + name.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public enum HeadType {
        MINIATURE("type.wanderingtradesmanager.miniature"),
        CUSTOM("type.wanderingtradesmanager.custom");

        private final String translationKey;

        HeadType(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public static HeadType fromName(String name) {
            String normalized = clean(name).toLowerCase(Locale.ROOT);
            return normalized.startsWith("mini") ? MINIATURE : CUSTOM;
        }
    }
}
