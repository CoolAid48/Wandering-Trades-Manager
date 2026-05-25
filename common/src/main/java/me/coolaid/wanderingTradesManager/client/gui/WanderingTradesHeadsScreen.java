package me.coolaid.wanderingTradesManager.client.gui;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.data.CustomHead;
import me.coolaid.wanderingTradesManager.data.CustomHead.HeadType;
import me.coolaid.wanderingTradesManager.data.DatapackScanResult;
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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WanderingTradesHeadsScreen extends Screen {
    private static final int SCREEN_PADDING = 8;
    private static final int CONTROL_TOP = 8;
    private static final int GRID_TOP = 34;
    private static final int FOOTER_HEIGHT = 18;
    private static final int TARGET_COLUMNS = 7;
    private static final int TARGET_VISIBLE_ROWS = 5;
    private static final int MIN_TILE_WIDTH = 46;
    private static final int MIN_ROW_HEIGHT = 36;
    private static final int TILE_GAP = 2;
    private static final int PACK_TEXT_COLOR = 0xFF8FA8C8;

    private final Screen parent;
    private final List<CustomHead> allHeads = new ArrayList<>();
    private final List<CustomHead> filteredHeads = new ArrayList<>();

    private EditBox searchBox;
    private HeadList headList;
    private Button sortButton;
    private Button typeFilterButton;
    private SortMode sortMode = SortMode.ALPHABETICAL;
    private HeadType selectedType;
    private Component loadedPackName = Component.translatable("text.wanderingtradesmanager.no_pack_loaded");
    private Component emptyMessage = Component.translatable("message.wanderingtradesmanager.no_heads_world");

    public WanderingTradesHeadsScreen(Screen parent) {
        super(Component.translatable("screen.wanderingtradesmanager.heads.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int refreshWidth = 66;
        int addWidth = 46;
        int sortWidth = 96;
        int typeWidth = 96;
        int controlGap = 4;
        int refreshX = this.width - SCREEN_PADDING - refreshWidth;
        int addX = refreshX - controlGap - addWidth;
        int sortX = addX - controlGap - sortWidth;
        int typeX = sortX - controlGap - typeWidth;
        int searchWidth = Math.max(80, typeX - controlGap - SCREEN_PADDING);

        this.searchBox = new EditBox(this.font, SCREEN_PADDING, CONTROL_TOP, searchWidth, 20, Component.translatable("field.wanderingtradesmanager.search"));
        this.searchBox.setHint(Component.translatable("hint.wanderingtradesmanager.search_heads"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setResponder(ignored -> this.applyFilters());
        this.addRenderableWidget(this.searchBox);

        this.typeFilterButton = Button.builder(Component.empty(), button -> this.cycleTypeFilter())
                .bounds(typeX, CONTROL_TOP, typeWidth, 20)
                .build();
        this.addRenderableWidget(this.typeFilterButton);

        this.sortButton = Button.builder(Component.empty(), button -> this.cycleSort())
                .bounds(sortX, CONTROL_TOP, sortWidth, 20)
                .build();
        this.addRenderableWidget(this.sortButton);

        this.addRenderableWidget(Button.builder(Component.translatable("button.wanderingtradesmanager.add"), button -> this.openNewHead())
                .bounds(addX, CONTROL_TOP, addWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("button.wanderingtradesmanager.refresh"), button -> this.reloadHeads(true))
                .bounds(refreshX, CONTROL_TOP, refreshWidth, 20)
                .build());

        int gridHeight = Math.max(48, this.height - GRID_TOP - FOOTER_HEIGHT);
        int rowHeight = Math.max(MIN_ROW_HEIGHT, gridHeight / TARGET_VISIBLE_ROWS);
        this.headList = new HeadList(this.minecraft, this.width, gridHeight, GRID_TOP, rowHeight);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        Component count = Component.translatable("text.wanderingtradesmanager.head_count", this.filteredHeads.size(), this.allHeads.size());
        Component pack = Component.translatable("text.wanderingtradesmanager.loaded_pack", this.loadedPackName);
        graphics.text(this.font, count, SCREEN_PADDING, this.height - 13, 0xFFB0B0B0);
        graphics.text(this.font, fit(pack, Math.max(80, this.width - 210)), 160, this.height - 13, PACK_TEXT_COLOR);

        if (this.filteredHeads.isEmpty()) {
            Component message = this.allHeads.isEmpty()
                    ? this.emptyMessage
                    : Component.translatable("message.wanderingtradesmanager.no_matching_heads");
            graphics.centeredText(this.font, message, this.width / 2, this.height / 2, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void reloadHeadsFromChild() {
        reloadHeads(true);
    }

    private void reloadHeads(boolean forceRescan) {
        if (this.minecraft.getSingleplayerServer() == null) {
            this.allHeads.clear();
            this.loadedPackName = Component.translatable("text.wanderingtradesmanager.nothing_loaded");
            this.emptyMessage = Component.translatable("message.wanderingtradesmanager.nothing_loaded_main_menu");
            applyFilters();
            return;
        }

        DatapackScanResult scan = getCurrentScan(forceRescan);

        this.allHeads.clear();
        this.allHeads.addAll(scan.heads());
        this.loadedPackName = scan.matchingPacks().stream()
                .findFirst()
                .<Component>map(path -> Component.literal(path.getFileName().toString()))
                .orElseGet(() -> Component.translatable("text.wanderingtradesmanager.no_pack_loaded"));
        this.emptyMessage = scan.hasMatchingPacks()
                ? Component.translatable("message.wanderingtradesmanager.no_heads_pack")
                : Component.translatable("message.wanderingtradesmanager.no_datapack");
        if (!scan.warnings().isEmpty()) {
            this.emptyMessage = Component.translatable("message.wanderingtradesmanager.scan_warning");
        }

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

    private void cycleSort() {
        this.sortMode = this.sortMode.next();
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
        updateTypeFilterButton();
        applyFilters();
    }

    private void updateSortButton() {
        if (this.sortButton != null) {
            this.sortButton.setMessage(Component.translatable("button.wanderingtradesmanager.sort", Component.translatable(this.sortMode.translationKey)));
        }
    }

    private void updateTypeFilterButton() {
        if (this.typeFilterButton != null) {
            Component type = this.selectedType == null
                    ? Component.translatable("type.wanderingtradesmanager.all")
                    : Component.translatable(this.selectedType.translationKey());
            this.typeFilterButton.setMessage(Component.translatable("button.wanderingtradesmanager.type_filter", type));
        }
    }

    private void openDetails(CustomHead head) {
        this.minecraft.setScreen(new WanderingTradesHeadDetailsScreen(this, head));
    }

    private void openNewHead() {
        this.minecraft.setScreen(new WanderingTradesHeadDetailsScreen(this, null));
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

    private enum SortMode {
        ALPHABETICAL("sort.wanderingtradesmanager.az", Comparator.comparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER)),
        REVERSE_ALPHABETICAL("sort.wanderingtradesmanager.za", Comparator.comparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER).reversed()),
        NEWEST("sort.wanderingtradesmanager.newest", Comparator.comparingInt(CustomHead::tradeIndex).reversed().thenComparing(CustomHead::name, String.CASE_INSENSITIVE_ORDER)),
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

    private final class HeadList extends ContainerObjectSelectionList<HeadList.HeadRow> {
        private final int rowHeight;

        private HeadList(Minecraft minecraft, int width, int height, int y, int rowHeight) {
            super(minecraft, width, height, y, rowHeight);
            this.rowHeight = rowHeight;
            this.centerListVertically = false;
        }

        private void setHeads(List<CustomHead> heads) {
            this.clearEntries();
            int columns = getColumns();
            for (int i = 0; i < heads.size(); i += columns) {
                this.addEntry(new HeadRow(heads.subList(i, Math.min(i + columns, heads.size()))));
            }
        }

        private int getColumns() {
            int rowWidth = this.getRowWidth();
            int targetWidth = TARGET_COLUMNS * MIN_TILE_WIDTH + TILE_GAP * (TARGET_COLUMNS - 1);
            if (rowWidth >= targetWidth) {
                return TARGET_COLUMNS;
            }

            return Math.max(1, (rowWidth + TILE_GAP) / (MIN_TILE_WIDTH + TILE_GAP));
        }

        @Override
        public int getRowWidth() {
            return Math.max(1, WanderingTradesHeadsScreen.this.width - SCREEN_PADDING * 2 - 8);
        }

        private final class HeadRow extends ContainerObjectSelectionList.Entry<HeadRow> {
            private final List<CustomHead> heads;
            private final List<ItemStack> stacks;

            private HeadRow(List<CustomHead> heads) {
                this.heads = List.copyOf(heads);
                this.stacks = this.heads.stream().map(HeadItemFactory::create).toList();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int columns = HeadList.this.getColumns();
                int tileWidth = Math.max(1, (this.getWidth() - TILE_GAP * (columns - 1)) / columns);
                int tileHeight = Math.max(1, HeadList.this.rowHeight - 4);
                int y = this.getY() + 2;

                for (int i = 0; i < this.heads.size(); i++) {
                    CustomHead head = this.heads.get(i);
                    int x = this.getX() + i * (tileWidth + TILE_GAP);
                    boolean tileHovered = contains(mouseX, mouseY, x, y, tileWidth, tileHeight);

                    int borderColor = tileHovered ? 0xFF8FA8C8 : 0x553A3A3A;
                    graphics.fill(x, y, x + tileWidth, y + tileHeight, tileHovered ? 0x66425D78 : 0x33101010);
                    graphics.fill(x, y, x + tileWidth, y + 1, borderColor);
                    graphics.fill(x, y + tileHeight - 1, x + tileWidth, y + tileHeight, borderColor);
                    graphics.fill(x, y, x + 1, y + tileHeight, borderColor);
                    graphics.fill(x + tileWidth - 1, y, x + tileWidth, y + tileHeight, borderColor);

                    boolean showLabel = tileHeight >= 34 && tileWidth >= 40;
                    float nameScale = 0.9F;
                    int nameLines = tileHeight >= 70 ? 2 : 1;
                    int nameLineHeight = Math.round(10 * nameScale);
                    int nameHeight = showLabel ? nameLines * nameLineHeight : 0;
                    int reservedLabelHeight = nameHeight + 3;
                    int availableIconSize = Math.min(tileWidth - 12, tileHeight - reservedLabelHeight - 6);
                    int iconSize = availableIconSize >= 40 ? 48 : availableIconSize >= 24 ? 32 : 16;
                    float iconScale = iconSize / 16.0F;
                    float iconX = x + (tileWidth - iconSize) / 2.0F;
                    float iconY = y + Math.max(4, (tileHeight - iconSize - reservedLabelHeight - 2) / 2.0F);

                    graphics.pose().pushMatrix();
                    graphics.pose().translate(iconX, iconY);
                    graphics.pose().scale(iconScale, iconScale);
                    graphics.item(this.stacks.get(i), 0, 0);
                    graphics.pose().popMatrix();

                    if (showLabel) {
                        List<String> nameLinesToRender = wrap(head.name(), Math.round((tileWidth - 8) / nameScale), nameLines);
                        int nameY = y + tileHeight - reservedLabelHeight;
                        for (int lineIndex = 0; lineIndex < nameLinesToRender.size(); lineIndex++) {
                            scaledCenteredText(
                                    graphics,
                                    WanderingTradesHeadsScreen.this.font,
                                    nameLinesToRender.get(lineIndex),
                                    x + tileWidth / 2,
                                    nameY + lineIndex * nameLineHeight,
                                    nameScale,
                                    0xFFF2F2F2
                            );
                        }
                    }

                    if (tileHovered) {
                        graphics.setComponentTooltipForNextFrame(
                                WanderingTradesHeadsScreen.this.font,
                                List.of(
                                        Component.literal(head.name()),
                                        Component.translatable(head.type().translationKey()),
                                        Component.translatable("tooltip.wanderingtradesmanager.click_details")
                                                .withStyle(style -> style.withColor(TextColor.fromRgb(PACK_TEXT_COLOR & 0xFFFFFF)))
                                ),
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
                int columns = HeadList.this.getColumns();
                int tileWidth = Math.max(1, (this.getWidth() - TILE_GAP * (columns - 1)) / columns);
                int tileHeight = Math.max(1, HeadList.this.rowHeight - 4);
                int y = this.getY() + 2;

                for (int i = 0; i < this.heads.size(); i++) {
                    int x = this.getX() + i * (tileWidth + TILE_GAP);
                    if (contains(mouseX, mouseY, x, y, tileWidth, tileHeight)) {
                        return this.heads.get(i);
                    }
                }

                return null;
            }

            private boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
                return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            }

            private List<String> wrap(String value, int maxWidth, int maxLines) {
                if (value == null || value.isBlank() || maxLines <= 0) {
                    return List.of();
                }

                List<String> lines = new ArrayList<>();
                String remaining = value.trim();

                while (!remaining.isEmpty() && lines.size() < maxLines) {
                    if (WanderingTradesHeadsScreen.this.font.width(remaining) <= maxWidth) {
                        lines.add(remaining);
                        break;
                    }

                    String line = WanderingTradesHeadsScreen.this.font.plainSubstrByWidth(remaining, maxWidth);
                    int breakAt = Math.max(line.lastIndexOf(' '), line.lastIndexOf('-'));
                    if (breakAt > 0 && lines.size() + 1 < maxLines) {
                        line = line.substring(0, breakAt).trim();
                    }

                    if (line.isBlank()) {
                        line = remaining.substring(0, Math.min(1, remaining.length()));
                    }

                    remaining = remaining.substring(Math.min(line.length(), remaining.length())).trim();
                    if (lines.size() + 1 == maxLines && !remaining.isEmpty()) {
                        lines.add(fit(line + " " + remaining, maxWidth));
                        break;
                    }

                    lines.add(line);
                }

                return lines;
            }

            private void scaledCenteredText(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, String text, int centerX, int y, float scale, int color) {
                graphics.pose().pushMatrix();
                graphics.pose().scale(scale, scale);
                graphics.centeredText(font, Component.literal(text), Math.round(centerX / scale), Math.round(y / scale), color);
                graphics.pose().popMatrix();
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
