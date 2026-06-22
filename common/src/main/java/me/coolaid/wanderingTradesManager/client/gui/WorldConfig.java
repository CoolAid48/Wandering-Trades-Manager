package me.coolaid.wanderingTradesManager.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.data.TradeSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

final class WorldConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = WanderingTradesManager.MOD_ID + ".json";
    private static final String WORLDS_KEY = "worlds";
    private static final String GRID_LAYOUT_KEY = "gridLayout";
    private static final boolean DEFAULT_REMOVE_HEAD_WARNING_ENABLED = true;
    private static final String REMOVE_HEAD_WARNING_KEY = "removeHeadWarning";
    private static final String CHAT_INFO_MESSAGES_KEY = "chatInfoMessages";
    private static final String TRADE_DEFAULTS_KEY = "tradeDefaults";
    private static final String EMERALD_COST_KEY = "emeraldCost";
    private static final String MAX_USES_KEY = "maxUses";
    private static final String MIN_CUSTOM_HEADS_KEY = "minCustomHeads";
    private static final String MAX_CUSTOM_HEADS_KEY = "maxCustomHeads";

    private WorldConfig() {
    }

    static boolean hasCurrentWorld() {
        return currentWorldKeys().isPresent();
    }

    static GridLayoutSize gridLayoutSize() {
        return currentWorldKeys()
                .map(WorldConfig::gridLayoutSize)
                .orElse(GridLayoutSize.DEFAULT);
    }

    static GridLayoutSize gridLayoutSize(MinecraftServer server) {
        return worldKeys(server)
                .map(WorldConfig::gridLayoutSize)
                .orElse(GridLayoutSize.DEFAULT);
    }

    static boolean removeHeadWarningEnabled() {
        return currentWorldKeys()
                .map(worldKeys -> worldBoolean(worldKeys, REMOVE_HEAD_WARNING_KEY, DEFAULT_REMOVE_HEAD_WARNING_ENABLED))
                .orElse(DEFAULT_REMOVE_HEAD_WARNING_ENABLED);
    }

    static ChatInfoMessages chatInfoMessages() {
        return currentWorldKeys()
                .map(WorldConfig::chatInfoMessages)
                .orElse(ChatInfoMessages.ALL);
    }

    static void saveGridLayoutSize(GridLayoutSize gridLayoutSize) {
        saveWorldString(GRID_LAYOUT_KEY, (gridLayoutSize == null ? GridLayoutSize.DEFAULT : gridLayoutSize).name());
    }

    static void saveRemoveHeadWarningEnabled(boolean enabled) {
        saveWorldBoolean(REMOVE_HEAD_WARNING_KEY, enabled);
    }

    static void saveChatInfoMessages(ChatInfoMessages mode) {
        saveWorldString(CHAT_INFO_MESSAGES_KEY, mode == null ? ChatInfoMessages.ALL.name() : mode.name());
    }

    static TradeSettings tradeSettingsDefaults(TradeSettings detectedDefaults) {
        Optional<WorldKeys> worldKeys = currentWorldKeys();
        if (worldKeys.isEmpty()) {
            return detectedDefaults;
        }

        Path path = preferencesPath();
        JsonObject root = loadRoot(path);
        JsonObject worlds = objectChild(root, WORLDS_KEY).orElseGet(JsonObject::new);
        JsonObject world = worldConfigObject(worlds, worldKeys.get()).orElseGet(JsonObject::new);
        Optional<TradeSettings> savedDefaults = objectChild(world, TRADE_DEFAULTS_KEY).map(WorldConfig::tradeSettings);

        if (savedDefaults.isPresent()) {
            return savedDefaults.get();
        }

        world.add(TRADE_DEFAULTS_KEY, tradeSettingsObject(detectedDefaults));
        worlds.add(worldKeys.get().key(), world);
        worldKeys.get().legacyKey().ifPresent(worlds::remove);
        root.add(WORLDS_KEY, worlds);
        saveRoot(path, root);

        return detectedDefaults;
    }

    private static GridLayoutSize gridLayoutSize(WorldKeys worldKeys) {
        JsonObject root = loadRoot(preferencesPath());
        return objectChild(root, WORLDS_KEY)
                .flatMap(worlds -> worldConfigObject(worlds, worldKeys))
                .map(world -> stringProperty(world, GRID_LAYOUT_KEY))
                .map(GridLayoutSize::parse)
                .orElse(GridLayoutSize.DEFAULT);
    }

    private static ChatInfoMessages chatInfoMessages(WorldKeys worldKeys) {
        JsonObject root = loadRoot(preferencesPath());
        return objectChild(root, WORLDS_KEY)
                .flatMap(worlds -> worldConfigObject(worlds, worldKeys))
                .map(world -> stringProperty(world, CHAT_INFO_MESSAGES_KEY))
                .map(ChatInfoMessages::parse)
                .orElse(ChatInfoMessages.ALL);
    }

    private static boolean worldBoolean(WorldKeys worldKeys, String key, boolean fallback) {
        JsonObject root = loadRoot(preferencesPath());
        return objectChild(root, WORLDS_KEY)
                .flatMap(worlds -> worldConfigObject(worlds, worldKeys))
                .flatMap(world -> booleanProperty(world, key))
                .orElse(fallback);
    }

    private static void saveWorldBoolean(String key, boolean value) {
        saveWorldValue(world -> world.addProperty(key, value));
    }

    private static void saveWorldString(String key, String value) {
        saveWorldValue(world -> world.addProperty(key, value));
    }

    private static void saveWorldValue(Consumer<JsonObject> writer) {
        Optional<WorldKeys> worldKeys = currentWorldKeys();
        if (worldKeys.isEmpty()) {
            return;
        }

        Path path = preferencesPath();
        JsonObject root = loadRoot(path);
        JsonObject worlds = objectChild(root, WORLDS_KEY).orElseGet(JsonObject::new);
        JsonObject world = worldConfigObject(worlds, worldKeys.get()).orElseGet(JsonObject::new);

        writer.accept(world);
        worlds.add(worldKeys.get().key(), world);
        worldKeys.get().legacyKey().ifPresent(worlds::remove);
        root.add(WORLDS_KEY, worlds);

        saveRoot(path, root);
    }

    private static Optional<WorldKeys> currentWorldKeys() {
        return worldKeys(Minecraft.getInstance().getSingleplayerServer());
    }

    private static Optional<WorldKeys> worldKeys(MinecraftServer server) {
        if (server == null) {
            return Optional.empty();
        }

        Path datapacksDirectory = server.getWorldPath(LevelResource.DATAPACK_DIR).toAbsolutePath().normalize();
        Path worldDirectory = datapacksDirectory.getParent();
        if (worldDirectory == null || worldDirectory.getFileName() == null) {
            String fallbackKey = datapacksDirectory.toString();
            return Optional.of(new WorldKeys(fallbackKey, Optional.empty()));
        }

        String key = worldDirectory.getFileName().toString();
        String legacyKey = datapacksDirectory.toString();
        return Optional.of(new WorldKeys(key, key.equals(legacyKey) ? Optional.empty() : Optional.of(legacyKey)));
    }

    private static Path preferencesPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static void saveRoot(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            WanderingTradesManager.LOGGER.warn("Failed to save Wandering Trades Manager world config", e);
        }
    }

    private static JsonObject loadRoot(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                JsonElement rootElement = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (rootElement.isJsonObject()) {
                    return rootElement.getAsJsonObject();
                }
            } catch (IOException | RuntimeException e) {
                WanderingTradesManager.LOGGER.warn("Failed to load Wandering Trades Manager world config", e);
            }
        }

        return new JsonObject();
    }

    private static Optional<JsonObject> objectChild(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonObject()) {
            return Optional.of(object.getAsJsonObject(key));
        }

        return Optional.empty();
    }

    private static Optional<JsonObject> worldConfigObject(JsonObject worlds, WorldKeys worldKeys) {
        Optional<JsonObject> world = objectChild(worlds, worldKeys.key());
        if (world.isPresent()) {
            return world;
        }

        return worldKeys.legacyKey().flatMap(legacyKey -> objectChild(worlds, legacyKey));
    }

    private static String stringProperty(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return object.get(key).getAsString();
            } catch (RuntimeException ignored) {
            }
        }

        return null;
    }

    private static Optional<Boolean> booleanProperty(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return Optional.of(object.get(key).getAsBoolean());
            } catch (RuntimeException ignored) {
            }
        }

        return Optional.empty();
    }

    private static TradeSettings tradeSettings(JsonObject object) {
        return new TradeSettings(
                intProperty(object, EMERALD_COST_KEY).orElse(1),
                intProperty(object, MAX_USES_KEY).orElse(1),
                intProperty(object, MIN_CUSTOM_HEADS_KEY),
                intProperty(object, MAX_CUSTOM_HEADS_KEY)
        );
    }

    private static JsonObject tradeSettingsObject(TradeSettings settings) {
        JsonObject object = new JsonObject();
        object.addProperty(EMERALD_COST_KEY, settings.emeraldCost());
        object.addProperty(MAX_USES_KEY, settings.maxUses());
        settings.minCustomHeads().ifPresent(value -> object.addProperty(MIN_CUSTOM_HEADS_KEY, value));
        settings.maxCustomHeads().ifPresent(value -> object.addProperty(MAX_CUSTOM_HEADS_KEY, value));
        return object;
    }

    private static OptionalInt intProperty(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) {
            try {
                return OptionalInt.of(object.get(key).getAsInt());
            } catch (RuntimeException ignored) {
            }
        }

        return OptionalInt.empty();
    }

    private record WorldKeys(String key, Optional<String> legacyKey) {
    }

    enum GridLayoutSize {
        DEFAULT(7, 5, 36, "grid_layout.wanderingtradesmanager.default"),
        COMPACT(10, 5, 36, "grid_layout.wanderingtradesmanager.compact"),
        LARGE(4, 4, 54, "grid_layout.wanderingtradesmanager.large");

        private final int columns;
        private final int visibleRows;
        private final int minRowHeight;
        private final String translationKey;

        GridLayoutSize(int columns, int visibleRows, int minRowHeight, String translationKey) {
            this.columns = columns;
            this.visibleRows = visibleRows;
            this.minRowHeight = minRowHeight;
            this.translationKey = translationKey;
        }

        int columns() {
            return this.columns;
        }

        int visibleRows() {
            return this.visibleRows;
        }

        int minRowHeight() {
            return this.minRowHeight;
        }

        String translationKey() {
            return this.translationKey;
        }

        GridLayoutSize next() {
            GridLayoutSize[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private static GridLayoutSize parse(String value) {
            if (value != null && !value.isBlank()) {
                try {
                    return GridLayoutSize.valueOf(value);
                } catch (IllegalArgumentException ignored) {
                }
            }

            return DEFAULT;
        }
    }

    enum ChatInfoMessages {
        ALL("chat_info_messages.wanderingtradesmanager.all"),
        ERRORS_ONLY("chat_info_messages.wanderingtradesmanager.errors_only"),
        DISABLED("chat_info_messages.wanderingtradesmanager.disabled");

        private final String translationKey;

        ChatInfoMessages(String translationKey) {
            this.translationKey = translationKey;
        }

        boolean allows(boolean error) {
            return this == ALL || (this == ERRORS_ONLY && error);
        }

        String translationKey() {
            return this.translationKey;
        }

        ChatInfoMessages next() {
            ChatInfoMessages[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private static ChatInfoMessages parse(String value) {
            if (value != null && !value.isBlank()) {
                if ("true".equalsIgnoreCase(value)) {
                    return ALL;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return DISABLED;
                }

                try {
                    return ChatInfoMessages.valueOf(value);
                } catch (IllegalArgumentException ignored) {
                }
            }

            return ALL;
        }
    }
}