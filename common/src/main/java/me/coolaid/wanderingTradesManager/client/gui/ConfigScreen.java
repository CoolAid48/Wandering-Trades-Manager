package me.coolaid.wanderingTradesManager.client.gui;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.data.DatapackEditResult;
import me.coolaid.wanderingTradesManager.data.TradeSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.OptionalInt;

public final class ConfigScreen extends Screen {
    private static final int FORM_WIDTH = 320;
    private static final int FIELD_WIDTH = 52;
    private static final int RESET_BUTTON_WIDTH = 50;
    private static final int LABEL_FIELD_GAP = 8;
    private static final int BUTTON_WIDTH = 220;
    private static final int ACTION_BUTTON_WIDTH = 190;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int ROW_GAP = 24;
    private static final int OPTIONS_TOP = 64;
    private static final int DONE_BOTTOM_MARGIN = 28;

    private final Screen parent;

    private Button chatInfoMessagesButton;
    private Button removeHeadWarningButton;
    private Button gridLayoutButton;
    private EditBox emeraldCostField;
    private EditBox maxUsesField;
    private EditBox minCustomHeadsField;
    private EditBox maxCustomHeadsField;
    private Button resetEmeraldCostButton;
    private Button resetMaxUsesButton;
    private Button resetMinCustomHeadsButton;
    private Button resetMaxCustomHeadsButton;
    private Button saveButton;
    private Button doneButton;
    private TradeSettings tradeSettings = TradeSettings.fallback();
    private TradeSettings defaultTradeSettings = TradeSettings.fallback();
    private WorldConfig.ChatInfoMessages chatInfoMessages = WorldConfig.ChatInfoMessages.ALL;
    private boolean removeHeadWarningEnabled = true;
    private WorldConfig.GridLayoutSize gridLayoutSize = WorldConfig.GridLayoutSize.DEFAULT;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("screen.wanderingtradesmanager.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.tradeSettings = WanderingTradesManager.datapackManager().tradeSettings();
        this.defaultTradeSettings = WorldConfig.tradeSettingsDefaults(this.tradeSettings);
        this.chatInfoMessages = WorldConfig.chatInfoMessages();
        this.removeHeadWarningEnabled = WorldConfig.removeHeadWarningEnabled();
        this.gridLayoutSize = WorldConfig.gridLayoutSize();

        this.maxUsesField = numericField("config.wanderingtradesmanager.max_uses", this.tradeSettings.maxUses());
        this.addRenderableWidget(this.maxUsesField);
        this.resetMaxUsesButton = resetButton(button -> this.maxUsesField.setValue(Integer.toString(this.defaultTradeSettings.maxUses())));
        this.addRenderableWidget(this.resetMaxUsesButton);

        this.emeraldCostField = numericField("config.wanderingtradesmanager.emerald_cost", this.tradeSettings.emeraldCost());
        this.addRenderableWidget(this.emeraldCostField);
        this.resetEmeraldCostButton = resetButton(button -> this.emeraldCostField.setValue(Integer.toString(this.defaultTradeSettings.emeraldCost())));
        this.addRenderableWidget(this.resetEmeraldCostButton);

        this.minCustomHeadsField = numericField("config.wanderingtradesmanager.min_custom_heads", this.tradeSettings.minCustomHeads());
        this.minCustomHeadsField.active = this.tradeSettings.hasCustomHeadRange();
        this.addRenderableWidget(this.minCustomHeadsField);
        this.resetMinCustomHeadsButton = resetButton(button -> resetOptionalField(this.minCustomHeadsField, this.defaultTradeSettings.minCustomHeads()));
        this.addRenderableWidget(this.resetMinCustomHeadsButton);

        this.maxCustomHeadsField = numericField("config.wanderingtradesmanager.max_custom_heads", this.tradeSettings.maxCustomHeads());
        this.maxCustomHeadsField.active = this.tradeSettings.hasCustomHeadRange();
        this.addRenderableWidget(this.maxCustomHeadsField);
        this.resetMaxCustomHeadsButton = resetButton(button -> resetOptionalField(this.maxCustomHeadsField, this.defaultTradeSettings.maxCustomHeads()));
        this.addRenderableWidget(this.resetMaxCustomHeadsButton);

        this.chatInfoMessagesButton = Button.builder(chatInfoMessagesMessage(), button -> this.toggleChatInfoMessages())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.chatInfoMessagesButton);

        this.removeHeadWarningButton = Button.builder(removeHeadWarningMessage(), button -> this.toggleRemoveHeadWarning())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.removeHeadWarningButton);

        this.gridLayoutButton = Button.builder(gridLayoutMessage(), button -> this.cycleGridLayout())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.gridLayoutButton);

        this.saveButton = Button.builder(Component.translatable("button.wanderingtradesmanager.save").withStyle(ChatFormatting.GREEN), button -> this.save())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.saveButton);

        this.doneButton = Button.builder(Component.translatable("button.wanderingtradesmanager.done"), button -> this.openHeadsManager())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.doneButton);

        this.maxUsesField.setResponder(ignored -> updateResetButtons());
        this.emeraldCostField.setResponder(ignored -> updateResetButtons());
        this.minCustomHeadsField.setResponder(ignored -> updateResetButtons());
        this.maxCustomHeadsField.setResponder(ignored -> updateResetButtons());
        updateResetButtons();
        updateWorldButtons();
        applyLayout();
    }

    @Override
    public void onClose() {
        openHeadsManager();
    }

    @Override
    protected void repositionElements() {
        applyLayout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.UNDERLINE),
                this.width / 2, 10, 0xFFFFFFFF);

        ConfigLayout layout = layout();
        graphics.text(this.font, Component.translatable("config.wanderingtradesmanager.max_uses"), layout.labelX(), layout.maxUsesY() + 6, 0xFFFFFFFF);
        graphics.text(this.font, Component.translatable("config.wanderingtradesmanager.emerald_cost"), layout.labelX(), layout.emeraldCostY() + 6, 0xFFFFFFFF);
        graphics.text(this.font, Component.translatable("config.wanderingtradesmanager.min_custom_heads"), layout.labelX(), layout.minCustomHeadsY() + 6, this.tradeSettings.hasCustomHeadRange() ? 0xFFFFFFFF : 0xFF777777);
        graphics.text(this.font, Component.translatable("config.wanderingtradesmanager.max_custom_heads"), layout.labelX(), layout.maxCustomHeadsY() + 6, this.tradeSettings.hasCustomHeadRange() ? 0xFFFFFFFF : 0xFF777777);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applyLayout() {
        ConfigLayout layout = layout();

        place(this.maxUsesField, layout.fieldX(), layout.maxUsesY(), FIELD_WIDTH, BUTTON_HEIGHT);
        place(this.resetMaxUsesButton, layout.resetX(), layout.maxUsesY(), RESET_BUTTON_WIDTH, BUTTON_HEIGHT);
        place(this.emeraldCostField, layout.fieldX(), layout.emeraldCostY(), FIELD_WIDTH, BUTTON_HEIGHT);
        place(this.resetEmeraldCostButton, layout.resetX(), layout.emeraldCostY(), RESET_BUTTON_WIDTH, BUTTON_HEIGHT);
        place(this.minCustomHeadsField, layout.fieldX(), layout.minCustomHeadsY(), FIELD_WIDTH, BUTTON_HEIGHT);
        place(this.resetMinCustomHeadsButton, layout.resetX(), layout.minCustomHeadsY(), RESET_BUTTON_WIDTH, BUTTON_HEIGHT);
        place(this.maxCustomHeadsField, layout.fieldX(), layout.maxCustomHeadsY(), FIELD_WIDTH, BUTTON_HEIGHT);
        place(this.resetMaxCustomHeadsButton, layout.resetX(), layout.maxCustomHeadsY(), RESET_BUTTON_WIDTH, BUTTON_HEIGHT);
        place(this.chatInfoMessagesButton, layout.buttonX(), layout.chatInfoMessagesY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.removeHeadWarningButton, layout.buttonX(), layout.removeHeadWarningY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.gridLayoutButton, layout.buttonX(), layout.gridLayoutY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.saveButton, layout.actionButtonX(), layout.saveY(), layout.actionButtonWidth(), BUTTON_HEIGHT);
        place(this.doneButton, layout.actionButtonX(), layout.doneY(), layout.actionButtonWidth(), BUTTON_HEIGHT);
    }

    private void cycleGridLayout() {
        if (!WorldConfig.hasCurrentWorld()) {
            return;
        }

        this.gridLayoutSize = this.gridLayoutSize.next();
        updateWorldButtons();
    }

    private void toggleChatInfoMessages() {
        if (!WorldConfig.hasCurrentWorld()) {
            return;
        }

        this.chatInfoMessages = this.chatInfoMessages.next();
        WorldConfig.saveChatInfoMessages(this.chatInfoMessages);
        updateWorldButtons();
    }

    private void toggleRemoveHeadWarning() {
        if (!WorldConfig.hasCurrentWorld()) {
            return;
        }

        this.removeHeadWarningEnabled = !this.removeHeadWarningEnabled;
        WorldConfig.saveRemoveHeadWarningEnabled(this.removeHeadWarningEnabled);
        updateWorldButtons();
    }

    private void updateWorldButtons() {
        boolean active = WorldConfig.hasCurrentWorld();
        updateButton(this.chatInfoMessagesButton, chatInfoMessagesMessage(), active);
        updateButton(this.removeHeadWarningButton, removeHeadWarningMessage(), active);
        updateButton(this.gridLayoutButton, gridLayoutMessage(), active);
    }

    private void save() {
        TradeSettings settings = parsedSettings();
        if (settings == null) {
            notifyPlayer(Component.translatable("message.wanderingtradesmanager.invalid_config_number").withStyle(ChatFormatting.RED), true);
            return;
        }

        boolean layoutChanged = WorldConfig.hasCurrentWorld() && this.gridLayoutSize != WorldConfig.gridLayoutSize();
        if (layoutChanged) {
            WorldConfig.saveGridLayoutSize(this.gridLayoutSize);
        }

        if (!WanderingTradesManager.datapackManager().lastScan().hasMatchingPacks()) {
            notifyPlayer(layoutChanged
                    ? Component.translatable("message.wanderingtradesmanager.updated_config").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("message.wanderingtradesmanager.no_editable_function").withStyle(ChatFormatting.RED),
                    !layoutChanged);
            return;
        }

        DatapackEditResult result = WanderingTradesManager.datapackManager().updateTradeSettings(settings);
        notifyPlayer(layoutChanged && !result.changed() && !result.failed()
                ? Component.translatable("message.wanderingtradesmanager.updated_config").withStyle(ChatFormatting.GREEN)
                : result.message(),
                result.failed());
        this.tradeSettings = WanderingTradesManager.datapackManager().tradeSettings();
        updateFields();
    }

    private Component gridLayoutMessage() {
        return Component.translatable(
                "button.wanderingtradesmanager.grid_layout",
                Component.translatable(this.gridLayoutSize.translationKey())
        );
    }

    private Component chatInfoMessagesMessage() {
        Component status = this.chatInfoMessages == WorldConfig.ChatInfoMessages.DISABLED
                ? Component.translatable(this.chatInfoMessages.translationKey()).withStyle(ChatFormatting.RED)
                : Component.translatable(this.chatInfoMessages.translationKey());
        return Component.translatable("button.wanderingtradesmanager.chat_info_messages", status);
    }

    private Component removeHeadWarningMessage() {
        return enabledDisabledMessage("button.wanderingtradesmanager.remove_head_warning", this.removeHeadWarningEnabled);
    }

    private Component enabledDisabledMessage(String translationKey, boolean enabled) {
        Component status = Component.translatable(enabled
                        ? "config.wanderingtradesmanager.enabled"
                        : "config.wanderingtradesmanager.disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        return Component.translatable(translationKey, status);
    }

    private void openHeadsManager() {
        this.minecraft.setScreen(new HeadsScreen(this.parent));
    }

    private EditBox numericField(String labelKey, int value) {
        EditBox field = new EditBox(this.font, 0, 0, FIELD_WIDTH, BUTTON_HEIGHT, Component.translatable(labelKey));
        field.setMaxLength(4);
        field.setValue(Integer.toString(value));
        return field;
    }

    private EditBox numericField(String labelKey, OptionalInt value) {
        EditBox field = new EditBox(this.font, 0, 0, FIELD_WIDTH, BUTTON_HEIGHT, Component.translatable(labelKey));
        field.setMaxLength(4);
        field.setValue(value.isPresent() ? Integer.toString(value.getAsInt()) : "N/A");
        return field;
    }

    private TradeSettings parsedSettings() {
        OptionalInt minCustomHeads = this.tradeSettings.hasCustomHeadRange()
                ? parsePositiveInt(this.minCustomHeadsField.getValue())
                : OptionalInt.empty();
        OptionalInt maxCustomHeads = this.tradeSettings.hasCustomHeadRange()
                ? parsePositiveInt(this.maxCustomHeadsField.getValue())
                : OptionalInt.empty();
        OptionalInt emeraldCost = parsePositiveInt(this.emeraldCostField.getValue());
        OptionalInt maxUses = parsePositiveInt(this.maxUsesField.getValue());

        if (emeraldCost.isEmpty() || maxUses.isEmpty()) {
            return null;
        }
        if (this.tradeSettings.hasCustomHeadRange() && (minCustomHeads.isEmpty() || maxCustomHeads.isEmpty())) {
            return null;
        }

        if (this.tradeSettings.hasCustomHeadRange() && minCustomHeads.getAsInt() > maxCustomHeads.getAsInt()) {
            maxCustomHeads = minCustomHeads;
        }

        return new TradeSettings(emeraldCost.getAsInt(), maxUses.getAsInt(), minCustomHeads, maxCustomHeads);
    }

    private static OptionalInt parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private void updateFields() {
        if (this.maxUsesField != null) {
            this.maxUsesField.setValue(Integer.toString(this.tradeSettings.maxUses()));
        }
        if (this.emeraldCostField != null) {
            this.emeraldCostField.setValue(Integer.toString(this.tradeSettings.emeraldCost()));
        }
        if (this.minCustomHeadsField != null) {
            this.minCustomHeadsField.setValue(this.tradeSettings.minCustomHeads().isPresent() ? Integer.toString(this.tradeSettings.minCustomHeads().getAsInt()) : "N/A");
            this.minCustomHeadsField.active = this.tradeSettings.hasCustomHeadRange();
        }
        if (this.maxCustomHeadsField != null) {
            this.maxCustomHeadsField.setValue(this.tradeSettings.maxCustomHeads().isPresent() ? Integer.toString(this.tradeSettings.maxCustomHeads().getAsInt()) : "N/A");
            this.maxCustomHeadsField.active = this.tradeSettings.hasCustomHeadRange();
        }
        updateResetButtons();
    }

    private void notifyPlayer(Component message) {
        notifyPlayer(message, false);
    }

    private void notifyPlayer(Component message, boolean error) {
        if (this.minecraft.player != null && WorldConfig.chatInfoMessages().allows(error)) {
            this.minecraft.player.sendSystemMessage(message);
        }
    }

    private ConfigLayout layout() {
        int formWidth = Math.min(FORM_WIDTH, Math.max(1, this.width - 32));
        int buttonWidth = Math.min(BUTTON_WIDTH, formWidth);
        int buttonX = this.width / 2 - buttonWidth / 2;
        int actionButtonWidth = Math.min(ACTION_BUTTON_WIDTH, formWidth);
        int actionButtonX = this.width / 2 - actionButtonWidth / 2;
        int maxLabelWidth = Math.min(maxConfigLabelWidth(), Math.max(1, formWidth - FIELD_WIDTH - RESET_BUTTON_WIDTH - BUTTON_GAP - LABEL_FIELD_GAP));
        int rowWidth = maxLabelWidth + LABEL_FIELD_GAP + FIELD_WIDTH + BUTTON_GAP + RESET_BUTTON_WIDTH;
        int formX = this.width / 2 - rowWidth / 2;
        int fieldX = formX + maxLabelWidth + LABEL_FIELD_GAP;
        int resetX = fieldX + FIELD_WIDTH + BUTTON_GAP;
        int contentHeight = BUTTON_HEIGHT * 6 + BUTTON_GAP * 5 + ROW_GAP * 3;
        int top = Math.clamp(OPTIONS_TOP, 28, Math.max(28, this.height - DONE_BOTTOM_MARGIN - contentHeight));
        int chatInfoMessagesY = top;
        int removeHeadWarningY = chatInfoMessagesY + BUTTON_HEIGHT + BUTTON_GAP;
        int gridLayoutY = removeHeadWarningY + BUTTON_HEIGHT + BUTTON_GAP;
        int inputsY = gridLayoutY + BUTTON_HEIGHT + BUTTON_GAP;
        int minSaveY = inputsY + ROW_GAP * 3 + BUTTON_HEIGHT + BUTTON_GAP;
        int doneY = Math.max(minSaveY + BUTTON_HEIGHT + BUTTON_GAP, this.height - DONE_BOTTOM_MARGIN);
        int saveY = doneY - BUTTON_HEIGHT - BUTTON_GAP;

        return new ConfigLayout(formX, fieldX, resetX, buttonX, buttonWidth, actionButtonX, actionButtonWidth, inputsY, chatInfoMessagesY, removeHeadWarningY, gridLayoutY, saveY, doneY);
    }

    private int maxConfigLabelWidth() {
        return Math.max(
                Math.max(
                        this.font.width(Component.translatable("config.wanderingtradesmanager.emerald_cost").getString()),
                        this.font.width(Component.translatable("config.wanderingtradesmanager.max_uses").getString())
                ),
                Math.max(
                        this.font.width(Component.translatable("config.wanderingtradesmanager.min_custom_heads").getString()),
                        this.font.width(Component.translatable("config.wanderingtradesmanager.max_custom_heads").getString())
                )
        );
    }

    private Button resetButton(Button.OnPress action) {
        return Button.builder(Component.translatable("button.wanderingtradesmanager.reset"), action)
                .bounds(0, 0, RESET_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
    }

    private void updateResetButtons() {
        setResetButtonActive(this.resetMaxUsesButton, fieldChangedFromDefault(this.maxUsesField, this.defaultTradeSettings.maxUses()));
        setResetButtonActive(this.resetEmeraldCostButton, fieldChangedFromDefault(this.emeraldCostField, this.defaultTradeSettings.emeraldCost()));
        setResetButtonActive(this.resetMinCustomHeadsButton, this.tradeSettings.hasCustomHeadRange() && fieldChangedFromDefault(this.minCustomHeadsField, this.defaultTradeSettings.minCustomHeads()));
        setResetButtonActive(this.resetMaxCustomHeadsButton, this.tradeSettings.hasCustomHeadRange() && fieldChangedFromDefault(this.maxCustomHeadsField, this.defaultTradeSettings.maxCustomHeads()));
    }

    private static void setResetButtonActive(Button button, boolean active) {
        if (button != null) {
            button.active = active;
        }
    }

    private static void updateButton(Button button, Component message, boolean active) {
        if (button != null) {
            button.setMessage(message);
            button.active = active;
        }
    }

    private static boolean fieldChangedFromDefault(EditBox field, int defaultValue) {
        if (field == null) {
            return false;
        }

        OptionalInt value = parsePositiveInt(field.getValue());
        return value.isEmpty() || value.getAsInt() != defaultValue;
    }

    private static boolean fieldChangedFromDefault(EditBox field, OptionalInt defaultValue) {
        return defaultValue.isPresent() && fieldChangedFromDefault(field, defaultValue.getAsInt());
    }

    private static void resetOptionalField(EditBox field, OptionalInt value) {
        if (value.isPresent()) {
            field.setValue(Integer.toString(value.getAsInt()));
        }
    }

    private static void place(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget != null) {
            widget.setX(x);
            widget.setY(y);
            widget.setWidth(width);
            widget.setHeight(height);
        }
    }

    private record ConfigLayout(int labelX, int fieldX, int resetX, int buttonX, int buttonWidth, int actionButtonX, int actionButtonWidth, int inputsY, int chatInfoMessagesY, int removeHeadWarningY, int gridLayoutY, int saveY, int doneY) {
        private int maxUsesY() {
            return inputsY;
        }

        private int emeraldCostY() {
            return maxUsesY() + ROW_GAP;
        }

        private int minCustomHeadsY() {
            return emeraldCostY() + ROW_GAP;
        }

        private int maxCustomHeadsY() {
            return minCustomHeadsY() + ROW_GAP;
        }
    }
}