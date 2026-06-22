package me.coolaid.wanderingTradesManager.client.gui;

import me.coolaid.wanderingTradesManager.data.CustomHead;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class RemoveHeadConfirmScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int TITLE_Y = 24;
    private static final int MESSAGE_LINE_GAP = 2;
    private static final int MESSAGE_BUTTON_GAP = 14;
    private static final int HEAD_PREVIEW_SIZE = 48;
    private static final int TITLE_PREVIEW_GAP = 6;
    private static final int PREVIEW_MESSAGE_GAP = 8;

    private final HeadDetailsScreen parent;

    private Button removeButton;
    private Button cancelButton;
    private CompletableFuture<Optional<PlayerSkin>> skinFuture;

    public RemoveHeadConfirmScreen(HeadDetailsScreen parent) {
        super(Component.translatable("screen.wanderingtradesmanager.confirm_remove"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.removeButton = Button.builder(Component.translatable("button.wanderingtradesmanager.remove").withStyle(ChatFormatting.RED), button -> this.parent.confirmRemoveHead())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.removeButton);

        this.cancelButton = Button.builder(Component.translatable("button.wanderingtradesmanager.cancel"), button -> this.minecraft.setScreenAndShow(this.parent))
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.addRenderableWidget(this.cancelButton);

        CustomHead head = this.parent.head();
        if (head != null) {
            this.skinFuture = this.minecraft.getSkinManager().get(HeadItemFactory.createProfile(head));
        }

        applyLayout();
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

        ConfirmLayout layout = layout();
        graphics.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), layout.centerX(), TITLE_Y, 0xFFFFFFFF);
        renderHeadPreview(graphics, layout.previewX(), layout.previewY());
        graphics.centeredText(this.font, Component.translatable("text.wanderingtradesmanager.confirm_remove_1").withStyle(ChatFormatting.RED), layout.centerX(), layout.messageY(), 0xFFFF5555);
        graphics.centeredText(this.font, removeWarningSecondLine(), layout.centerX(), layout.messageSecondLineY(), 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void applyLayout() {
        ConfirmLayout layout = layout();

        place(this.removeButton, layout.buttonX(), layout.removeButtonY(), layout.buttonWidth(), BUTTON_HEIGHT);
        place(this.cancelButton, layout.buttonX(), layout.cancelButtonY(), layout.buttonWidth(), BUTTON_HEIGHT);
    }

    private static void place(Button button, int x, int y, int width, int height) {
        if (button != null) {
            button.setX(x);
            button.setY(y);
            button.setWidth(width);
            button.setHeight(height);
        }
    }

    private ConfirmLayout layout() {
        int centerX = this.width / 2;
        int buttonWidth = Math.min(BUTTON_WIDTH, Math.max(1, this.width - 32));
        int buttonX = centerX - buttonWidth / 2;
        int messageHeight = messageHeight();
        int previewY = TITLE_Y + this.font.lineHeight + TITLE_PREVIEW_GAP;
        int messageY = previewY + HEAD_PREVIEW_SIZE + PREVIEW_MESSAGE_GAP;
        int removeButtonY = messageY + messageHeight + MESSAGE_BUTTON_GAP;

        return new ConfirmLayout(centerX, previewY, messageY, buttonX, buttonWidth, removeButtonY, this.font.lineHeight + MESSAGE_LINE_GAP);
    }

    private int messageHeight() {
        return this.font.lineHeight * 2 + MESSAGE_LINE_GAP;
    }

    private Component removeWarningSecondLine() {
        return Component.translatable("text.wanderingtradesmanager.confirm_remove_2")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(" "))
                .append(Component.translatable("text.wanderingtradesmanager.confirm_remove_undone")
                        .withStyle(ChatFormatting.DARK_RED)
                        .withStyle(ChatFormatting.BOLD));
    }

    private void renderHeadPreview(GuiGraphicsExtractor graphics, int x, int y) {
        Identifier skinTexture = resolvedSkinTexture();
        if (skinTexture != null) {
            HeadFaceRenderer.render(graphics, this.minecraft, skinTexture, x, y, HEAD_PREVIEW_SIZE);
            return;
        }

        CustomHead head = this.parent.head();
        if (head != null) {
            ItemStack stack = HeadItemFactory.create(head);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(HEAD_PREVIEW_SIZE / 16.0F, HEAD_PREVIEW_SIZE / 16.0F);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
        }
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

    private record ConfirmLayout(int centerX, int previewY, int messageY, int buttonX, int buttonWidth, int removeButtonY, int messageLineStep) {
        private int previewX() {
            return centerX - HEAD_PREVIEW_SIZE / 2;
        }

        private int messageSecondLineY() {
            return messageY + messageLineStep;
        }

        private int cancelButtonY() {
            return removeButtonY + BUTTON_HEIGHT + BUTTON_GAP;
        }
    }
}