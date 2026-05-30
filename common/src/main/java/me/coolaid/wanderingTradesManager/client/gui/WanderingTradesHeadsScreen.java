package me.coolaid.wanderingTradesManager.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.data.CustomHead;
import me.coolaid.wanderingTradesManager.data.CustomHead.HeadType;
import me.coolaid.wanderingTradesManager.data.DatapackScanResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WanderingTradesHeadsScreen extends Screen {
    private static final int SCREEN_PADDING = 8;
    private static final int CONTROL_TOP = 8;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_GAP = 4;
    private static final int GRID_VERTICAL_GAP = 6;
    private static final int FOOTER_HEIGHT = 28;
    private static final int SEARCH_MIN_WIDTH = 80;
    private static final int TYPE_BUTTON_WIDTH = 96;
    private static final int SORT_BUTTON_WIDTH = 96;
    private static final int ADD_BUTTON_WIDTH = 52;
    private static final int OPEN_FOLDER_BUTTON_WIDTH = 78;
    private static final int COMPACT_ADD_BUTTON_WIDTH = 40;
    private static final int COMPACT_OPEN_FOLDER_BUTTON_WIDTH = 74;
    private static final int TARGET_COLUMNS = 7;
    private static final int TARGET_VISIBLE_ROWS = 5;
    private static final int MIN_TILE_WIDTH = 46;
    private static final int MIN_ROW_HEIGHT = 36;
    private static final int TILE_GAP = 2;
    private static final int ITEM_BASE_SIZE = 16;
    private static final int MAX_ICON_SIZE = 128;
    private static final int ICON_SIZE_TRIM = 4;
    private static final int RESERVED_NAME_LINES = 2;
    private static final float MAX_NAME_SCALE = 0.9F;
    private static final float MIN_NAME_SCALE = 0.55F;
    private static final float NAME_SCALE_STEP = 0.05F;
    private static final int PACK_TEXT_COLOR = 0xFF8FA8C8;
    private static final int FOOTER_PACK_GAP = 28;
    private static final long TEMPORARY_EMPTY_MESSAGE_DURATION_MILLIS = 3000L;
    private static final long HEAD_DISPLAY_BUFFER_MILLIS = 1500L;

    private final Screen parent;
    private final List<CustomHead> allHeads = new ArrayList<>();
    private final List<CustomHead> filteredHeads = new ArrayList<>();
    private final Map<CustomHead, ItemStack> stackCache = new HashMap<>();
    private final Map<CustomHead, List<Component>> tooltipCache = new HashMap<>();

    private EditBox searchBox;
    private HeadList headList;
    private Button sortButton;
    private Button typeFilterButton;
    private SortMode sortMode = SortMode.NEWEST;
    private HeadType selectedType;
    private Component loadedPackName = Component.translatable("footer.wanderingtradesmanager.nothing_loaded_pack");
    private Component emptyMessage = Component.translatable("text.wanderingtradesmanager.nothing_loaded");
    private Component temporaryEmptyMessage;
    private boolean waitingForHeadDisplayBuffer;
    private long headDisplayReadyMillis;
    private long temporaryEmptyMessageUntilMillis;

    public WanderingTradesHeadsScreen(Screen parent) {
        super(Component.translatable("screen.wanderingtradesmanager.heads.title"));
        this.parent = parent;
        ScreenPreferences preferences = ScreenPreferences.load();
        this.sortMode = preferences.sortMode();
        this.selectedType = preferences.selectedType();
    }

    @Override
    protected void init() {
        String previousSearch = this.searchBox == null ? "" : this.searchBox.getValue();
        HeadsLayout layout = layout();

        this.searchBox = new EditBox(this.font, layout.searchX(), layout.searchY(), layout.searchWidth(), CONTROL_HEIGHT, Component.translatable("screen.wanderingtradesmanager.search"));
        this.searchBox.setHint(Component.translatable("placeholder.wanderingtradesmanager.search_heads")
                .withStyle(ChatFormatting.GRAY)
                .withStyle(ChatFormatting.ITALIC));
        this.searchBox.setMaxLength(128);
        this.searchBox.setValue(previousSearch);
        this.searchBox.setResponder(ignored -> this.applyFilters());
        this.addRenderableWidget(this.searchBox);

        this.typeFilterButton = Button.builder(Component.empty(), button -> this.cycleTypeFilter())
                .bounds(layout.typeX(), layout.buttonY(), layout.typeWidth(), CONTROL_HEIGHT)
                .build();
        this.addRenderableWidget(this.typeFilterButton);

        this.sortButton = Button.builder(Component.empty(), button -> this.cycleSort())
                .bounds(layout.sortX(), layout.buttonY(), layout.sortWidth(), CONTROL_HEIGHT)
                .build();
        this.addRenderableWidget(this.sortButton);

        this.addRenderableWidget(Button.builder(Component.translatable("button.wanderingtradesmanager.add"), button -> this.openNewHead())
                .bounds(layout.addX(), layout.buttonY(), layout.addWidth(), CONTROL_HEIGHT)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("button.wanderingtradesmanager.open_folder"), button -> this.openDatapacksFolder())
                .bounds(layout.openFolderX(), layout.buttonY(), layout.openFolderWidth(), CONTROL_HEIGHT)
                .build());

        int gridHeight = Math.max(48, this.height - layout.gridTop() - FOOTER_HEIGHT);
        int rowHeight = Math.max(MIN_ROW_HEIGHT, gridHeight / TARGET_VISIBLE_ROWS);
        GridMetrics gridMetrics = GridMetrics.create(layout.rowWidth(), rowHeight);
        this.headList = new HeadList(this.minecraft, this.width, gridHeight, layout.gridTop(), gridMetrics);
        this.addRenderableWidget(this.headList);

        updateSortButton();
        updateTypeFilterButton();
        reloadHeads(false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    protected void repositionElements() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.waitingForHeadDisplayBuffer || System.currentTimeMillis() < this.headDisplayReadyMillis) {
            return;
        }

        this.waitingForHeadDisplayBuffer = false;
        reloadHeads(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        Component count = Component.translatable("footer.wanderingtradesmanager.head_count", this.filteredHeads.size(), this.allHeads.size());
        Component pack = Component.translatable("footer.wanderingtradesmanager.loaded_pack", this.loadedPackName);
        renderFooter(graphics, count, pack);

        if (this.filteredHeads.isEmpty()) {
            Component message = this.allHeads.isEmpty()
                    ? currentEmptyMessage()
                    : Component.translatable("text.wanderingtradesmanager.no_matching_heads");
            int listTop = layout().gridTop();
            int listBottom = Math.max(listTop, this.height - FOOTER_HEIGHT);
            graphics.centeredText(this.font, message, this.width / 2, listTop + (listBottom - listTop) / 2, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void reloadHeadsFromChild() {
        reloadHeads(true);
    }

    void showTemporaryEmptyMessage(Component message) {
        this.temporaryEmptyMessage = message;
        this.temporaryEmptyMessageUntilMillis = System.currentTimeMillis() + TEMPORARY_EMPTY_MESSAGE_DURATION_MILLIS;
    }

    private void renderFooter(GuiGraphicsExtractor graphics, Component count, Component pack) {
        int availableWidth = Math.max(1, this.width - SCREEN_PADDING * 2);
        int countWidth = this.font.width(count.getString());
        int packX = SCREEN_PADDING + countWidth + FOOTER_PACK_GAP;
        int footerY = this.height - 13;

        graphics.text(this.font, count, SCREEN_PADDING, footerY, 0xFFB0B0B0);

        if (packX + 80 <= this.width - SCREEN_PADDING) {
            graphics.text(this.font, fit(pack, this.width - SCREEN_PADDING - packX), packX, footerY, PACK_TEXT_COLOR);
            return;
        }

        graphics.text(this.font, fit(pack, availableWidth), SCREEN_PADDING, this.height - 24, PACK_TEXT_COLOR);
    }

    private Component currentEmptyMessage() {
        if (this.temporaryEmptyMessage != null && System.currentTimeMillis() < this.temporaryEmptyMessageUntilMillis) {
            return this.temporaryEmptyMessage;
        }

        return this.emptyMessage;
    }

    private HeadsLayout layout() {
        int availableWidth = Math.max(1, this.width - SCREEN_PADDING * 2);
        int oneRowWidth = SEARCH_MIN_WIDTH
                + CONTROL_GAP
                + TYPE_BUTTON_WIDTH
                + CONTROL_GAP
                + SORT_BUTTON_WIDTH
                + CONTROL_GAP
                + ADD_BUTTON_WIDTH
                + CONTROL_GAP
                + OPEN_FOLDER_BUTTON_WIDTH;

        if (availableWidth >= oneRowWidth) {
            int openFolderX = this.width - SCREEN_PADDING - OPEN_FOLDER_BUTTON_WIDTH;
            int addX = openFolderX - CONTROL_GAP - ADD_BUTTON_WIDTH;
            int sortX = addX - CONTROL_GAP - SORT_BUTTON_WIDTH;
            int typeX = sortX - CONTROL_GAP - TYPE_BUTTON_WIDTH;
            int searchWidth = Math.max(SEARCH_MIN_WIDTH, typeX - CONTROL_GAP - SCREEN_PADDING);
            int gridTop = CONTROL_TOP + CONTROL_HEIGHT + GRID_VERTICAL_GAP;
            return new HeadsLayout(
                    SCREEN_PADDING,
                    CONTROL_TOP,
                    searchWidth,
                    typeX,
                    sortX,
                    addX,
                    openFolderX,
                    CONTROL_TOP,
                    TYPE_BUTTON_WIDTH,
                    SORT_BUTTON_WIDTH,
                    ADD_BUTTON_WIDTH,
                    OPEN_FOLDER_BUTTON_WIDTH,
                    gridTop,
                    Math.max(1, availableWidth - 8)
            );
        }

        int buttonY = CONTROL_TOP + CONTROL_HEIGHT + CONTROL_GAP;
        int buttonSpace = Math.max(1, availableWidth - CONTROL_GAP * 3);
        int addWidth = Math.clamp(buttonSpace / 6, COMPACT_ADD_BUTTON_WIDTH, ADD_BUTTON_WIDTH);
        int openFolderWidth = Math.clamp(buttonSpace / 5, COMPACT_OPEN_FOLDER_BUTTON_WIDTH, OPEN_FOLDER_BUTTON_WIDTH);
        int filterWidth = Math.max(1, (buttonSpace - addWidth - openFolderWidth) / 2);
        int typeX = SCREEN_PADDING;
        int sortX = typeX + filterWidth + CONTROL_GAP;
        int addX = sortX + filterWidth + CONTROL_GAP;
        int openFolderX = addX + addWidth + CONTROL_GAP;
        int openFolderRemainder = Math.max(1, this.width - SCREEN_PADDING - openFolderX);
        int gridTop = buttonY + CONTROL_HEIGHT + GRID_VERTICAL_GAP;

        return new HeadsLayout(
                SCREEN_PADDING,
                CONTROL_TOP,
                availableWidth,
                typeX,
                sortX,
                addX,
                openFolderX,
                buttonY,
                filterWidth,
                filterWidth,
                addWidth,
                openFolderRemainder,
                gridTop,
                Math.max(1, availableWidth - 8)
        );
    }

    private void reloadHeads(boolean forceRescan) {
        if (this.minecraft.getSingleplayerServer() == null) {
            this.allHeads.clear();
            this.loadedPackName = Component.translatable("footer.wanderingtradesmanager.nothing_loaded_pack");
            this.emptyMessage = Component.translatable("text.wanderingtradesmanager.nothing_loaded");
            this.waitingForHeadDisplayBuffer = false;
            clearHeadCaches();
            applyFilters();
            return;
        }

        if (!forceRescan) {
            if (this.waitingForHeadDisplayBuffer) {
                if (System.currentTimeMillis() < this.headDisplayReadyMillis) {
                    showHeadDisplayLoading();
                    return;
                }

                this.waitingForHeadDisplayBuffer = false;
                forceRescan = true;
            } else if (WanderingTradesManager.consumeHeadDisplayBuffer()) {
                startHeadDisplayBuffer();
                return;
            }
        }

        this.waitingForHeadDisplayBuffer = false;
        DatapackScanResult scan = getCurrentScan(forceRescan);

        this.allHeads.clear();
        this.allHeads.addAll(scan.heads());
        clearHeadCaches();
        this.loadedPackName = scan.matchingPacks().stream()
                .findFirst()
                .<Component>map(path -> Component.literal(path.getFileName().toString()))
                .orElseGet(() -> Component.translatable("footer.wanderingtradesmanager.nothing_loaded_pack"));
        this.emptyMessage = scan.hasMatchingPacks()
                ? Component.translatable("text.wanderingtradesmanager.no_heads_pack")
                : Component.translatable("text.wanderingtradesmanager.nothing_loaded");
        if (!scan.warnings().isEmpty()) {
            this.emptyMessage = Component.translatable("text.wanderingtradesmanager.scan_warning");
        }

        applyFilters();
    }

    private void startHeadDisplayBuffer() {
        this.headDisplayReadyMillis = System.currentTimeMillis() + HEAD_DISPLAY_BUFFER_MILLIS;
        this.waitingForHeadDisplayBuffer = true;
        showHeadDisplayLoading();
    }

    private void showHeadDisplayLoading() {
        this.allHeads.clear();
        this.loadedPackName = Component.translatable("footer.wanderingtradesmanager.nothing_loaded_pack");
        this.emptyMessage = Component.translatable("text.wanderingtradesmanager.loading_datapack");
        applyFilters();
    }

    private DatapackScanResult getCurrentScan(boolean forceRescan) {
        MinecraftServer server = this.minecraft.getSingleplayerServer();
        DatapackScanResult lastScan = WanderingTradesManager.datapackManager().lastScan();

        if (server != null && (forceRescan || !lastScan.hasMatchingPacks())) {
            return WanderingTradesManager.datapackManager().refresh(server);
        }

        return lastScan;
    }

    private void applyFilters() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.filteredHeads.clear();

        for (CustomHead head : this.allHeads) {
            if (matchesTypeFilter(head) && (query.isEmpty() || matches(head, query))) {
                this.filteredHeads.add(head);
            }
        }

        this.filteredHeads.sort(this.sortMode.comparator());

        if (this.headList != null) {
            this.headList.setHeads(this.filteredHeads);
        }
    }

    private boolean matches(CustomHead head, String query) {
        return head.name().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesTypeFilter(CustomHead head) {
        return this.selectedType == null || head.type() == this.selectedType;
    }

    private ItemStack stackFor(CustomHead head) {
        return this.stackCache.computeIfAbsent(head, HeadItemFactory::create);
    }

    private List<Component> tooltipFor(CustomHead head) {
        return this.tooltipCache.computeIfAbsent(head, this::createTooltip);
    }

    private List<Component> createTooltip(CustomHead head) {
        return List.of(
                Component.literal(head.name()).withStyle(ChatFormatting.YELLOW),
                Component.translatable(head.type().translationKey())
                        .withStyle(style -> style.withColor(TextColor.fromRgb(PACK_TEXT_COLOR & 0xFFFFFF))),
                Component.translatable("tooltip.wanderingtradesmanager.click_details")
                        .withStyle(ChatFormatting.WHITE)
        );
    }

    private void clearHeadCaches() {
        this.stackCache.clear();
        this.tooltipCache.clear();
    }

    private void cycleSort() {
        this.sortMode = this.sortMode.next();
        savePreferences();
        updateSortButton();
        applyFilters();
    }

    private void cycleTypeFilter() {
        if (this.selectedType == null) {
            this.selectedType = HeadType.MINIATURE;
        } else if (this.selectedType == HeadType.MINIATURE) {
            this.selectedType = HeadType.CUSTOM;
        } else {
            this.selectedType = null;
        }
        savePreferences();
        updateTypeFilterButton();
        applyFilters();
    }

    private void savePreferences() {
        ScreenPreferences.save(this.sortMode, this.selectedType);
    }

    private void updateSortButton() {
        if (this.sortButton != null) {
            Component sort = Component.translatable(this.sortMode.translationKey);
            this.sortButton.setMessage(labelThatFits(this.sortButton, Component.translatable("button.wanderingtradesmanager.sort", sort), sort));
        }
    }

    private void updateTypeFilterButton() {
        if (this.typeFilterButton != null) {
            Component type = this.selectedType == null
                    ? Component.translatable("type.wanderingtradesmanager.all")
                    : Component.translatable(this.selectedType.translationKey());
            this.typeFilterButton.setMessage(labelThatFits(this.typeFilterButton, Component.translatable("button.wanderingtradesmanager.type_filter", type), type));
        }
    }

    private Component labelThatFits(Button button, Component fullLabel, Component compactLabel) {
        int maxTextWidth = Math.max(1, button.getWidth() - 12);
        return this.font.width(fullLabel.getString()) <= maxTextWidth ? fullLabel : compactLabel;
    }

    private void openDetails(CustomHead head) {
        this.minecraft.setScreen(new WanderingTradesHeadDetailsScreen(this, head));
    }

    private void openNewHead() {
        if (this.minecraft.getSingleplayerServer() == null) {
            showTemporaryEmptyMessage(Component.translatable("text.wanderingtradesmanager.open_world_before_add").withStyle(ChatFormatting.RED));
            return;
        }

        this.minecraft.setScreen(new WanderingTradesHeadDetailsScreen(this, null));
    }

    private void openDatapacksFolder() {
        MinecraftServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            notifyPlayer(Component.translatable("text.wanderingtradesmanager.open_world_before_folder").withStyle(ChatFormatting.RED));
            return;
        }

        Path datapacksDirectory = server.getWorldPath(LevelResource.DATAPACK_DIR);
        try {
            Files.createDirectories(datapacksDirectory);
            Util.getPlatform().openPath(datapacksDirectory);
        } catch (IOException | RuntimeException e) {
            notifyPlayer(Component.translatable("message.wanderingtradesmanager.open_folder_failed", e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private void notifyPlayer(Component message) {
        if (this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(message);
            return;
        }

        showTemporaryEmptyMessage(message);
    }

    private String fit(String value, int maxWidth) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (this.font.width(value) <= maxWidth) {
            return value;
        }

        int ellipsisWidth = this.font.width("...");
        return this.font.plainSubstrByWidth(value, Math.max(1, maxWidth - ellipsisWidth)) + "...";
    }

    private Component fit(Component value, int maxWidth) {
        return Component.literal(fit(value.getString(), maxWidth));
    }

    private record HeadsLayout(
            int searchX,
            int searchY,
            int searchWidth,
            int typeX,
            int sortX,
            int addX,
            int openFolderX,
            int buttonY,
            int typeWidth,
            int sortWidth,
            int addWidth,
            int openFolderWidth,
            int gridTop,
            int rowWidth
    ) {
    }

    private record GridMetrics(int rowWidth, int rowHeight, int tileWidth, int tileHeight, int columns) {
        private static GridMetrics create(int rowWidth, int rowHeight) {
            int columns = columnsFor(rowWidth);
            int tileWidth = Math.max(1, (rowWidth - TILE_GAP * (columns - 1)) / columns);
            int tileHeight = Math.max(1, rowHeight - 4);
            return new GridMetrics(rowWidth, rowHeight, tileWidth, tileHeight, columns);
        }

        private static int columnsFor(int rowWidth) {
            int targetWidth = TARGET_COLUMNS * MIN_TILE_WIDTH + TILE_GAP * (TARGET_COLUMNS - 1);
            if (rowWidth >= targetWidth) {
                return TARGET_COLUMNS;
            }

            return Math.max(1, (rowWidth + TILE_GAP) / (MIN_TILE_WIDTH + TILE_GAP));
        }
    }

    private enum SortMode {
        NEWEST("sort.wanderingtradesmanager.newest", Comparator.comparingInt(CustomHead::tradeIndex).reversed().thenComparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER)),
        ALPHABETICAL("sort.wanderingtradesmanager.az", Comparator.comparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER)),
        REVERSE_ALPHABETICAL("sort.wanderingtradesmanager.za", Comparator.comparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER).reversed()),
        OLDEST("sort.wanderingtradesmanager.oldest", Comparator.comparingInt(CustomHead::tradeIndex).thenComparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER));

        private final String translationKey;
        private final Comparator<CustomHead> comparator;

        SortMode(String translationKey, Comparator<CustomHead> comparator) {
            this.translationKey = translationKey;
            this.comparator = comparator;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        private Comparator<CustomHead> comparator() {
            return this.comparator;
        }
    }

    private record ScreenPreferences(SortMode sortMode, HeadType selectedType) {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final String FILE_NAME = WanderingTradesManager.MOD_ID + ".json";
        private static final String HEADS_KEY = "heads";
        private static final String SORT_MODE_KEY = "sortMode";
        private static final String SELECTED_TYPE_KEY = "selectedType";
        private static final String ALL_TYPES_VALUE = "ALL";

        private static ScreenPreferences load() {
            Path path = preferencesPath();
            SortMode sortMode = SortMode.NEWEST;
            HeadType selectedType = null;

            if (Files.isRegularFile(path)) {
                try {
                    JsonElement rootElement = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                    if (rootElement.isJsonObject()) {
                        JsonObject preferences = preferencesObject(rootElement.getAsJsonObject());
                        sortMode = parseSortMode(stringProperty(preferences, SORT_MODE_KEY));
                        selectedType = parseSelectedType(stringProperty(preferences, SELECTED_TYPE_KEY));
                    }
                } catch (IOException | RuntimeException e) {
                    WanderingTradesManager.LOGGER.warn("Failed to load Wandering Trades Manager config", e);
                }
            }

            return new ScreenPreferences(sortMode, selectedType);
        }

        private static void save(SortMode sortMode, HeadType selectedType) {
            Path path = preferencesPath();
            JsonObject root = loadRoot(path);
            JsonObject heads = new JsonObject();
            heads.addProperty(SORT_MODE_KEY, sortMode.name());
            heads.addProperty(SELECTED_TYPE_KEY, selectedType == null ? ALL_TYPES_VALUE : selectedType.name());

            root.add(HEADS_KEY, heads);

            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            } catch (IOException e) {
                WanderingTradesManager.LOGGER.warn("Failed to save Wandering Trades Manager config", e);
            }
        }

        private static Path preferencesPath() {
            return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
        }

        private static JsonObject loadRoot(Path path) {
            if (Files.isRegularFile(path)) {
                try {
                    JsonElement rootElement = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                    if (rootElement.isJsonObject()) {
                        return rootElement.getAsJsonObject();
                    }
                } catch (IOException | RuntimeException e) {
                    WanderingTradesManager.LOGGER.warn("Failed to preserve existing Wandering Trades Manager config values", e);
                }
            }

            return new JsonObject();
        }

        private static JsonObject preferencesObject(JsonObject root) {
            if (root.has(HEADS_KEY) && root.get(HEADS_KEY).isJsonObject()) {
                return root.getAsJsonObject(HEADS_KEY);
            }

            return root;
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

        private static SortMode parseSortMode(String value) {
            if (value != null && !value.isBlank()) {
                try {
                    return SortMode.valueOf(value);
                } catch (IllegalArgumentException ignored) {
                }
            }

            return SortMode.NEWEST;
        }

        private static HeadType parseSelectedType(String value) {
            if (value == null || value.isBlank() || ALL_TYPES_VALUE.equals(value)) {
                return null;
            }

            try {
                return HeadType.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private final class HeadList extends ContainerObjectSelectionList<HeadList.HeadRow> {
        private final GridMetrics metrics;

        private HeadList(Minecraft minecraft, int width, int height, int y, GridMetrics metrics) {
            super(minecraft, width, height, y, metrics.rowHeight());
            this.metrics = metrics;
            this.centerListVertically = false;
        }

        private void setHeads(List<CustomHead> heads) {
            this.clearEntries();
            int columns = this.metrics.columns();
            for (int i = 0; i < heads.size(); i += columns) {
                this.addEntry(new HeadRow(heads.subList(i, Math.min(i + columns, heads.size()))));
            }
        }

        @Override
        public int getRowWidth() {
            return this.metrics.rowWidth();
        }

        private int rowLeft() {
            return SCREEN_PADDING + 4;
        }

        private boolean mouseInList(double mouseY) {
            return mouseY >= this.getY() && mouseY < this.getBottom();
        }

        private final class HeadRow extends ContainerObjectSelectionList.Entry<HeadRow> {
            private final List<HeadTile> tiles;

            private HeadRow(List<CustomHead> heads) {
                this.tiles = heads.stream()
                        .map(head -> createTile(head, HeadList.this.metrics.tileWidth(), HeadList.this.metrics.tileHeight()))
                        .toList();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int tileWidth = HeadList.this.metrics.tileWidth();
                int tileHeight = HeadList.this.metrics.tileHeight();
                int y = this.getY() + 2;
                boolean mouseInList = HeadList.this.mouseInList(mouseY);

                for (int i = 0; i < this.tiles.size(); i++) {
                    HeadTile tile = this.tiles.get(i);
                    int x = HeadList.this.rowLeft() + i * (tileWidth + TILE_GAP);
                    boolean tileHovered = mouseInList && contains(mouseX, mouseY, x, y, tileWidth, tileHeight);

                    int borderColor = tileHovered ? 0xFF8FA8C8 : 0x553A3A3A;
                    graphics.fill(x, y, x + tileWidth, y + tileHeight, tileHovered ? 0x66425D78 : 0x33101010);
                    graphics.fill(x, y, x + tileWidth, y + 1, borderColor);
                    graphics.fill(x, y + tileHeight - 1, x + tileWidth, y + tileHeight, borderColor);
                    graphics.fill(x, y, x + 1, y + tileHeight, borderColor);
                    graphics.fill(x + tileWidth - 1, y, x + tileWidth, y + tileHeight, borderColor);

                    int availableIconSize = Math.min(tileWidth - 12, tileHeight - tile.reservedLabelHeight() - 6);
                    int iconSize = iconSize(availableIconSize);
                    float iconScale = iconSize / (float) ITEM_BASE_SIZE;
                    int iconX = x + (tileWidth - iconSize) / 2;
                    int iconY = y + Math.max(2, (tileHeight - iconSize - tile.reservedLabelHeight() - 2) / 2);

                    graphics.pose().pushMatrix();
                    graphics.pose().translate(iconX, iconY);
                    graphics.pose().scale(iconScale, iconScale);
                    graphics.item(tile.stack(), 0, 0);
                    graphics.pose().popMatrix();

                    if (!tile.nameLines().isEmpty()) {
                        int nameY = y + tileHeight - tile.reservedLabelHeight();
                        for (int lineIndex = 0; lineIndex < tile.nameLines().size(); lineIndex++) {
                            scaledCenteredText(
                                    graphics,
                                    WanderingTradesHeadsScreen.this.font,
                                    tile.nameLines().get(lineIndex),
                                    x + tileWidth / 2,
                                    nameY + lineIndex * tile.nameLineHeight(),
                                    tile.nameScale(),
                                    0xFFF2F2F2
                            );
                        }
                    }

                    if (tileHovered) {
                        graphics.setComponentTooltipForNextFrame(
                                WanderingTradesHeadsScreen.this.font,
                                tile.tooltip(),
                                mouseX,
                                mouseY
                        );
                    }
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != 0) {
                    return false;
                }

                CustomHead clicked = headAt(event.x(), event.y());
                if (clicked != null) {
                    WanderingTradesHeadsScreen.this.openDetails(clicked);
                    return true;
                }

                return false;
            }

            private CustomHead headAt(double mouseX, double mouseY) {
                if (!HeadList.this.mouseInList(mouseY)) {
                    return null;
                }

                int tileWidth = HeadList.this.metrics.tileWidth();
                int tileHeight = HeadList.this.metrics.tileHeight();
                int y = this.getY() + 2;

                for (int i = 0; i < this.tiles.size(); i++) {
                    int x = HeadList.this.rowLeft() + i * (tileWidth + TILE_GAP);
                    if (contains(mouseX, mouseY, x, y, tileWidth, tileHeight)) {
                        return this.tiles.get(i).head();
                    }
                }

                return null;
            }

            private HeadTile createTile(CustomHead head, int tileWidth, int tileHeight) {
                ItemStack stack = stackFor(head);
                List<Component> tooltip = tooltipFor(head);
                boolean showLabel = tileHeight >= 34 && tileWidth >= 40;
                if (!showLabel) {
                    return new HeadTile(head, stack, List.of(), 0.0F, 0, 0, tooltip);
                }

                int maxNameWidth = Math.max(1, tileWidth - 8);
                float nameScale = chooseNameScale(head.name(), maxNameWidth, Math.max(8, tileHeight - 24));
                List<Component> nameLines = wrap(head.name(), Math.round(maxNameWidth / nameScale)).stream()
                        .map(line -> (Component) Component.literal(line))
                        .toList();
                int nameLineHeight = Math.max(7, Math.round(10 * nameScale));
                int reservedNameLines = Math.max(RESERVED_NAME_LINES, nameLines.size());
                int reservedLabelHeight = reservedNameLines * nameLineHeight + 3;

                return new HeadTile(head, stack, nameLines, nameScale, nameLineHeight, reservedLabelHeight, tooltip);
            }

            private int iconSize(int availableSize) {
                return Math.clamp(availableSize - ICON_SIZE_TRIM, ITEM_BASE_SIZE, MAX_ICON_SIZE);
            }

            private boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
                return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            }

            private float chooseNameScale(String value, int maxWidth, int maxHeight) {
                int steps = Math.round((MAX_NAME_SCALE - MIN_NAME_SCALE) / NAME_SCALE_STEP);
                for (int step = 0; step <= steps; step++) {
                    float scale = MAX_NAME_SCALE - step * NAME_SCALE_STEP;
                    int lineHeight = Math.max(7, Math.round(10 * scale));
                    int scaledWidth = Math.max(1, Math.round(maxWidth / scale));
                    if (wrap(value, scaledWidth).size() <= RESERVED_NAME_LINES && RESERVED_NAME_LINES * lineHeight <= maxHeight) {
                        return scale;
                    }
                }

                return MIN_NAME_SCALE;
            }

            private List<String> wrap(String value, int maxWidth) {
                if (value == null || value.isBlank()) {
                    return List.of();
                }

                List<String> lines = new ArrayList<>();
                String remaining = value.trim();

                while (!remaining.isEmpty()) {
                    if (WanderingTradesHeadsScreen.this.font.width(remaining) <= maxWidth) {
                        lines.add(remaining);
                        break;
                    }

                    String line = WanderingTradesHeadsScreen.this.font.plainSubstrByWidth(remaining, maxWidth);
                    int breakAt = Math.max(line.lastIndexOf(' '), line.lastIndexOf('-'));
                    if (breakAt > 0) {
                        int breakLength = line.charAt(breakAt) == '-' ? breakAt + 1 : breakAt;
                        line = line.substring(0, breakLength).trim();
                    }

                    if (line.isBlank()) {
                        line = remaining.substring(0, Math.min(1, remaining.length()));
                    }

                    remaining = remaining.substring(Math.min(line.length(), remaining.length())).trim();
                    lines.add(line);
                }

                return lines;
            }

            private void scaledCenteredText(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, Component text, int centerX, int y, float scale, int color) {
                graphics.pose().pushMatrix();
                graphics.pose().scale(scale, scale);
                graphics.centeredText(font, text, Math.round(centerX / scale), Math.round(y / scale), color);
                graphics.pose().popMatrix();
            }

            private record HeadTile(CustomHead head, ItemStack stack, List<Component> nameLines, float nameScale, int nameLineHeight, int reservedLabelHeight, List<Component> tooltip) {
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }
    }
}
