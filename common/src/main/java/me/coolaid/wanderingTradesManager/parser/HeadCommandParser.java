package me.coolaid.wanderingTradesManager.parser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.coolaid.wanderingTradesManager.data.CustomHead;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeadCommandParser {
    private static final Pattern TRADE_INDEX_PATTERN = Pattern.compile("execute\\s+if\\s+score\\s+@s\\s+wt_tradeIndex\\s+matches\\s+(\\d+)");
    private static final Pattern ITEM_NAME_PATTERN = Pattern.compile("\"minecraft:(?:item_name|custom_name)\"\\s*:\\s*(?:'([^']*)'|\"([^\"]*)\")");
    private static final Pattern MINI_NAME_PATTERN = Pattern.compile("\\bMini\\s+([^\"']+)");
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("value\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");

    private HeadCommandParser() {
    }

    public static List<CustomHead> parseFunction(String content, String sourcePack, String sourceFunction) {
        List<CustomHead> heads = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return heads;
        }

        Matcher matcher = TRADE_INDEX_PATTERN.matcher(content);
        int entryStart = -1;

        while (matcher.find()) {
            if (entryStart >= 0) {
                parseTradeEntry(content.substring(entryStart, matcher.start()), sourcePack, sourceFunction)
                        .ifPresent(heads::add);
            }
            entryStart = matcher.start();
        }

        if (entryStart >= 0) {
            parseTradeEntry(content.substring(entryStart), sourcePack, sourceFunction)
                    .ifPresent(heads::add);
        }

        return heads;
    }

    public static CustomHead parseBase64String(String input) {
        String textureValue = sanitizeTextureValue(input);
        if (textureValue.length() < 80 || !isValidBase64(textureValue)) {
            throw new IllegalArgumentException("This does not look like a valid player head texture value");
        }

        String extractedName = extractNameFromTexture(textureValue);
        String name = cleanDisplayName(extractedName);
        return new CustomHead(name, textureValue, -1, "", "", CustomHead.HeadType.fromName(extractedName));
    }

    private static Optional<CustomHead> parseTradeEntry(String entry, String sourcePack, String sourceFunction) {
        Matcher indexMatcher = TRADE_INDEX_PATTERN.matcher(entry);
        Matcher textureMatcher = TEXTURE_PATTERN.matcher(entry);

        if (!indexMatcher.find() || !textureMatcher.find()) {
            return Optional.empty();
        }

        int tradeIndex = Integer.parseInt(indexMatcher.group(1));
        String textureValue = textureMatcher.group(1);
        HeadName headName = findHeadName(entry);
        String name = headName.name();
        CustomHead.HeadType type = headName.type();

        if (name.isBlank()) {
            name = cleanDisplayName(extractNameFromTexture(textureValue));
            type = CustomHead.HeadType.CUSTOM;
        }

        CustomHead head = new CustomHead(
                name,
                textureValue,
                tradeIndex,
                sourcePack,
                sourceFunction,
                type
        );

        return head.isValid() ? Optional.of(head) : Optional.empty();
    }

    private static HeadName findHeadName(String entry) {
        Matcher itemNameMatcher = ITEM_NAME_PATTERN.matcher(entry);
        if (itemNameMatcher.find()) {
            String name = itemNameMatcher.group(1) != null ? itemNameMatcher.group(1) : itemNameMatcher.group(2);
            name = stripWrappingQuotes(name);
            return new HeadName(cleanDisplayName(name), CustomHead.HeadType.fromName(name));
        }

        Matcher miniNameMatcher = MINI_NAME_PATTERN.matcher(entry);
        if (miniNameMatcher.find()) {
            String name = "Mini " + miniNameMatcher.group(1);
            return new HeadName(cleanDisplayName(name), CustomHead.HeadType.MINIATURE);
        }

        return new HeadName("", CustomHead.HeadType.CUSTOM);
    }

    private static String stripWrappingQuotes(String value) {
        String stripped = value.strip();
        while (stripped.length() >= 2
                && ((stripped.charAt(0) == '"' && stripped.charAt(stripped.length() - 1) == '"')
                || (stripped.charAt(0) == '\'' && stripped.charAt(stripped.length() - 1) == '\''))) {
            stripped = stripped.substring(1, stripped.length() - 1).strip();
        }
        return stripped;
    }

    private static String sanitizeTextureValue(String input) {
        if (input == null) {
            return "";
        }

        return input.trim()
                .replaceFirst("^[\"'\\s]+", "")
                .replaceFirst("[\"'\\s]+$", "")
                .replaceFirst("^value\\s*:\\s*[\"']?", "");
    }

    private static boolean isValidBase64(String value) {
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String extractNameFromTexture(String textureValue) {
        try {
            String jsonString = new String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

            if (json.has("profileName")) {
                return json.get("profileName").getAsString();
            }

            if (json.has("textures")) {
                JsonObject textures = json.getAsJsonObject("textures");
                if (textures.has("SKIN") && textures.getAsJsonObject("SKIN").has("url")) {
                    String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
                    String fileName = url.substring(url.lastIndexOf('/') + 1).replace(".png", "");
                    return fileName.replace('_', ' ').replace('-', ' ');
                }
            }
        } catch (RuntimeException ignored) {
        }

        return "Custom Head";
    }

    private static String cleanDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "Custom Head";
        }

        return name.replaceAll("[&][0-9a-fk-orA-FK-OR]", "")
                .replaceAll("[^\\w\\s\\-().,'!]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record HeadName(String name, CustomHead.HeadType type) {
    }
}
