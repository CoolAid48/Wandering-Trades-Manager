package me.coolaid.wanderingTradesManager.client.gui;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.data.CustomHead;
import me.coolaid.wanderingTradesManager.data.DatapackEditResult;
import me.coolaid.wanderingTradesManager.parser.HeadCommandParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class HeadDetailsScreen extends Screen {
    private static final int FIELD_TOP = 108;
    private static final int FIELD_VERTICAL_GAP = 46;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_LABEL_OFFSET = 12;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 3;
    private static final int BUTTON_TOP_GAP = 42;
    private static final int DONE_BOTTOM_MARGIN = 32;
    private static final int TEXTURE_HELP_GAP = 6;
    private static final int HELP_ACTION_GAP = 8;
    private static final int HEAD_PREVIEW_SIZE = 48;
    private static final int MIN_HEAD_PREVIEW_SIZE = 24;
    private static final int PREVIEW_Y = 40;
    private static final int MIN_FIELD_TOP = 48;

    private final HeadsScreen parent;
    private final CustomHead head;
    private final boolean creating;

    private EditBox nameField;
    private EditBox textureField;
    private Button saveButton;
    private Button copyTextureButton;
    private Button deleteButton;
    private Button doneButton;
    private CustomHead previewHead;
    private String previewTextureValue = "";
    private CompletableFuture<Optional<PlayerSkin>> skinFuture;

    public HeadDetailsScreen(HeadsScreen parent, CustomHead head) {
        super(Component.translatable(head == null ? "screen.wanderingtradesmanager.add_head" : "screen.wanderingtradesmanager.head_details"));
        this.parent = parent;
        this.head = head;
        this.creating = head == null;
    }

    @Override
    protected void init() {
        DetailsLayout layout = layout();
        this.copyTextureButton = null;
        this.deleteButton = null;

        this.nameField = new EditBox(this.font, layout.formX(), layout.nameFieldY(), layout.formWidth(), FIELD_HEIGHT, Component.translatable("screen.wanderingtradesmanager.name"));
        this.nameField.setHint(italic("placeholder.wanderingtradesmanager.head_name"));
        this.nameField.setMaxLength(96);
        this.nameField.setValue(this.head == null ? "" : this.head.name());
        this.nameField.setResponder(ignored -> this.updatePreviewHead());
        this.addRenderableWidget(this.nameField);

        this.textureField = new EditBox(this.font, layout.formX(), layout.textureFieldY(), layout.formWidth(), FIELD_HEIGHT, Component.translatable("screen.wanderingtradesmanager.texture"));
        this.textureField.setHint(italic("placeholder.wanderingtradesmanager.texture_value"));
        this.textureField.setMaxLength(4096);
        this.textureField.setValue(this.head == null ? "" : this.head.textureValue());
        this.textureField.setResponder(ignored -> this.updatePreviewHead());
        this.addRenderableWidget(this.textureField);

        this.saveButton = Button.builder(green(this.creating ? "button.wanderingtradesmanager.add_head" : "button.wanderingtradesmanager.save"), button -> this.save())
                .bounds(layout.buttonX(), layout.actionButtonsY(), layout.buttonWidth(), BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.saveButton);

        if (!this.creating) {
            this.copyTextureButton = Button.builder(gold("button.wanderingtradesmanager.copy_texture"), button -> this.copyTexture())
                    .bounds(layout.buttonX(), layout.copyButtonY(), layout.buttonWidth(), BUTTON_HEIGHT)
                    .build();
            this.addRenderableWidget(this.copyTextureButton);

            this.deleteButton = Button.builder(red("button.wanderingtradesmanager.remove"), button -> this.delete())
                    .bounds(layout.buttonX(), layout.deleteButtonY(), layout.buttonWidth(), BUTTON_HEIGHT)
                    .build();
            this.addRenderableWidget(this.deleteButton);
        }

        this.doneButton = Button.builder(Component.translatable("button.wanderingtradesmanager.done"), button -> this.minecraft.setScreenAndShow(this.parent))
                .bounds(layout.buttonX(), layout.doneButtonY(), layout.buttonWidth(), BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.doneButton);

        applyLayout();

        if (!this.creating) {
            this.skinFuture = this.minecraft.getSkinManager().get(HeadItemFactory.createProfile(this.head));
        } else {
            updatePreviewHead();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    protected void repositionElements() {
        applyLayout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        DetailsLayout layout = layout();
        graphics.centeredText(this.font, this.title.copy().withStyle(style -> style.withBold(true)), layout.centerX(), 16, 0xFFFFFFFF);

        if (layout.showPreview()) {
            renderHeadPreview(graphics, layout.centerX() - layout.previewSize() / 2, PREVIEW_Y, layout.previewSize());
        }

        graphics.text(this.font, Component.translatable("screen.wanderingtradesmanager.name"), layout.formX(), layout.nameLabelY(), 0xFFFFFFFF);
        graphics.text(this.font, Component.translatable("screen.wanderingtradesmanager.texture"), layout.formX(), layout.textureLabelY(), 0xFFFFFFFF);

        if (this.creating) {
            renderAddTextureHelp(graphics, mouseX, mouseY, layout);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int formWidth() {
        return Math.min(420, Math.max(1, this.width - 32));
    }

    private DetailsLayout layout() {
        int formWidth = formWidth();
        int centerX = this.width / 2;
        int buttonWidth = Math.min(BUTTON_WIDTH, formWidth);
        int buttonX = centerX - buttonWidth / 2;
        int buttonStackHeight = actionButtonStackHeight();
        int preferredDoneY = Math.max(0, this.height - DONE_BOTTOM_MARGIN);
        int maxActionY = preferredDoneY - BUTTON_GAP - buttonStackHeight;
        int actionOffset = actionOffsetFromNameField();

        int maxFieldTop = maxActionY - actionOffset;
        int previewSize = previewSize(maxFieldTop);
        int minFieldTop = minimumFieldTop(previewSize);

        int fieldTop = Math.clamp(FIELD_TOP, minFieldTop, Math.max(minFieldTop, maxFieldTop));
        int textureFieldY = fieldTop + FIELD_VERTICAL_GAP;
        int actionY = textureFieldY + (this.creating ? FIELD_HEIGHT + TEXTURE_HELP_GAP + this.font.lineHeight + HELP_ACTION_GAP : BUTTON_TOP_GAP);
        int doneY = Math.max(preferredDoneY, actionY + buttonStackHeight + BUTTON_GAP);

        return new DetailsLayout(centerX, centerX - formWidth / 2, formWidth, buttonX, buttonWidth, fieldTop, textureFieldY, actionY, doneY, previewSize);
    }

    private void applyLayout() {
        DetailsLayout layout = layout();
        place(this.nameField, layout.formX(), layout.nameFieldY(), layout.formWidth(), FIELD_HEIGHT);
        place(this.textureField, layout.formX(), layout.textureFieldY(), layout.formWidth(), FIELD_HEIGHT);
        place(this.saveButton, layout.buttonX(), layout.actionButtonsY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.copyTextureButton, layout.buttonX(), layout.copyButtonY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.deleteButton, layout.buttonX(), layout.deleteButtonY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.doneButton, layout.buttonX(), layout.doneButtonY(), layout.buttonWidth(), BUTTON_HEIGHT);
    }

    private static void place(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget != null) {
            widget.setX(x);
            widget.setY(y);
            widget.setWidth(width);
            widget.setHeight(height);
        }
    }

    private int actionButtonStackHeight() {
        return this.creating ? BUTTON_HEIGHT : BUTTON_HEIGHT * 3 + BUTTON_GAP * 2;
    }

    private int actionOffsetFromNameField() {
        return FIELD_VERTICAL_GAP + (this.creating
                ? FIELD_HEIGHT + TEXTURE_HELP_GAP + this.font.lineHeight + HELP_ACTION_GAP
                : BUTTON_TOP_GAP);
    }

    private int previewSize(int maxFieldTop) {
        if (previewHead() == null) {
            return 0;
        }

        int availablePreviewSize = maxFieldTop - PREVIEW_Y - FIELD_LABEL_OFFSET - 8;
        return Math.clamp(availablePreviewSize, MIN_HEAD_PREVIEW_SIZE, HEAD_PREVIEW_SIZE);
    }

    private static int minimumFieldTop(int previewSize) {
        return previewSize > 0 ? PREVIEW_Y + previewSize + FIELD_LABEL_OFFSET + 8 : MIN_FIELD_TOP;
    }

    private void renderHeadPreview(GuiGraphicsExtractor graphics, int x, int y, int size) {
        CustomHead previewHead = previewHead();
        if (previewHead == null) {
            return;
        }

        Identifier skinTexture = resolvedSkinTexture();
        if (skinTexture == null) {
            ItemStack stack = HeadItemFactory.create(previewHead);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(size / 16.0F, size / 16.0F);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
            return;
        }

        HeadFaceRenderer.render(graphics, this.minecraft, skinTexture, x, y, size);
    }

    private CustomHead previewHead() {
        return this.creating ? this.previewHead : this.head;
    }

    private Identifier resolvedSkinTexture() {
        if (this.skinFuture == null || !this.skinFuture.isDone() || this.skinFuture.isCompletedExceptionally()) {
            return null;
        }

        return this.skinFuture.getNow(Optional.empty())
                .map(PlayerSkin::body)
                .map(body -> body.texturePath())
                .orElse(null);
    }

    private void renderAddTextureHelp(GuiGraphicsExtractor graphics, int mouseX, int mouseY, DetailsLayout layout) {
        Component help = Component.translatable("text.wanderingtradesmanager.add_head_texture");
        int x = layout.formX();
        int y = layout.textureHelpY();
        Component fittedHelp = fit(help, layout.formWidth());
        graphics.text(this.font, fittedHelp, x, y, 0xFFAAAAAA);

        if (contains(mouseX, mouseY, x, y, textWidth(fittedHelp), textHeight(fittedHelp))) {
            graphics.setComponentTooltipForNextFrame(
                    this.font,
                    List.of(
                            Component.translatable("tooltip.wanderingtradesmanager.texture_value1"),
                            Component.translatable("tooltip.wanderingtradesmanager.texture_value2"),
                            Component.translatable("tooltip.wanderingtradesmanager.texture_value3")
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    private int textWidth(Component component) {
        int width = 0;
        for (String line : component.getString().split("\\R", -1)) {
            width = Math.max(width, this.font.width(line));
        }
        return width;
    }

    private int textHeight(Component component) {
        return Math.max(1, component.getString().split("\\R", -1).length) * this.font.lineHeight;
    }

    private Component fit(Component value, int maxWidth) {
        String text = value.getString();
        if (this.font.width(text) <= maxWidth) {
            return value;
        }

        int ellipsisWidth = this.font.width("...");
        return Component.literal(this.font.plainSubstrByWidth(text, Math.max(1, maxWidth - ellipsisWidth)) + "...");
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void save() {
        String name = this.nameField.getValue().trim();
        String texture = this.textureField.getValue().trim();

        handleResult(this.creating
                ? WanderingTradesManager.datapackManager().addHead(name, texture)
                : WanderingTradesManager.datapackManager().updateHead(this.head, name, texture));
    }

    private void delete() {
        if (this.head == null) {
            return;
        }

        if (WorldConfig.removeHeadWarningEnabled()) {
            this.minecraft.setScreenAndShow(new RemoveHeadConfirmScreen(this));
            return;
        }

        confirmRemoveHead();
    }

    private void updatePreviewHead() {
        if (!this.creating || this.textureField == null) {
            return;
        }

        boolean hadPreview = this.previewHead != null;
        this.previewHead = parsePreviewHead();
        String textureValue = this.previewHead == null ? "" : this.previewHead.textureValue();
        if (!this.previewTextureValue.equals(textureValue)) {
            this.previewTextureValue = textureValue;
            this.skinFuture = this.previewHead == null
                    ? null
                    : this.minecraft.getSkinManager().get(HeadItemFactory.createProfile(this.previewHead));
        }

        if (hadPreview != (this.previewHead != null)) {
            applyLayout();
        }
    }

    private CustomHead parsePreviewHead() {
        try {
            CustomHead parsed = HeadCommandParser.parseBase64String(this.textureField.getValue());
            String name = this.nameField == null || this.nameField.getValue().isBlank()
                    ? parsed.name()
                    : this.nameField.getValue().trim();
            return new CustomHead(name, parsed.textureValue(), -1, "", "", parsed.type());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    void confirmRemoveHead() {
        if (this.head != null) {
            handleResult(WanderingTradesManager.datapackManager().removeHead(this.head));
        }
    }

    CustomHead head() {
        return this.head;
    }

    private void handleResult(DatapackEditResult result) {
        notifyPlayer(result.message(), result.failed());
        if (result.changed()) {
            this.parent.reloadHeadsFromChild();
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private void copyTexture() {
        if (this.head == null) {
            return;
        }

        this.minecraft.keyboardHandler.setClipboard(this.head.textureValue());
        notifyPlayer(Component.translatable("message.wanderingtradesmanager.copied_texture"));
    }

    private void notifyPlayer(Component message) {
        notifyPlayer(message, false);
    }

    private void notifyPlayer(Component message, boolean error) {
        if (this.minecraft.player != null) {
            if (WorldConfig.chatInfoMessages().allows(error)) {
                this.minecraft.player.sendSystemMessage(message);
            }
            return;
        }

        this.parent.showTemporaryEmptyMessage(message);
        this.minecraft.setScreenAndShow(this.parent);
    }

    private static Component green(String translationKey) {
        return Component.translatable(translationKey).withStyle(ChatFormatting.GREEN);
    }

    private static Component red(String translationKey) {
        return Component.translatable(translationKey).withStyle(ChatFormatting.RED);
    }

    private static Component gold(String translationKey) {
        return Component.translatable(translationKey).withStyle(ChatFormatting.GOLD);
    }

    private static Component italic(String translationKey) {
        return Component.translatable(translationKey)
                .withStyle(ChatFormatting.GRAY)
                .withStyle(ChatFormatting.ITALIC);
    }

    private record DetailsLayout(int centerX, int formX, int formWidth, int buttonX, int buttonWidth, int nameFieldY, int textureFieldY, int actionButtonsY, int doneButtonY, int previewSize) {
        private boolean showPreview() {
            return previewSize > 0;
        }

        private int nameLabelY() {
            return nameFieldY - FIELD_LABEL_OFFSET;
        }

        private int textureLabelY() {
            return textureFieldY - FIELD_LABEL_OFFSET;
        }

        private int textureHelpY() {
            return textureFieldY + FIELD_HEIGHT + TEXTURE_HELP_GAP;
        }

        private int copyButtonY() {
            return actionButtonsY + BUTTON_HEIGHT + BUTTON_GAP;
        }

        private int deleteButtonY() {
            return copyButtonY() + BUTTON_HEIGHT + BUTTON_GAP;
        }
    }
}