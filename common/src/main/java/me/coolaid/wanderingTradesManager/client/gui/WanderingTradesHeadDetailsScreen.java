package me.coolaid.wanderingTradesManager.client.gui;

import me.coolaid.wanderingTradesManager.WanderingTradesManager;
import me.coolaid.wanderingTradesManager.data.CustomHead;
import me.coolaid.wanderingTradesManager.data.DatapackEditResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class WanderingTradesHeadDetailsScreen extends Screen {
    private final WanderingTradesHeadsScreen parent;
    private final CustomHead head;
    private final boolean creating;

    private EditBox nameField;
    private EditBox textureField;
    private Button saveButton;
    private Button deleteButton;

    public WanderingTradesHeadDetailsScreen(WanderingTradesHeadsScreen parent, CustomHead head) {
        super(Component.translatable(head == null ? "screen.wanderingtradesmanager.add_head" : "screen.wanderingtradesmanager.head_details"));
        this.parent = parent;
        this.head = head;
        this.creating = head == null;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int formWidth = Math.min(420, this.width - 32);
        int formX = centerX - formWidth / 2;
        int top = 108;
        int textureTop = top + 46;

        this.nameField = new EditBox(this.font, formX, top, formWidth, 20, Component.translatable("field.wanderingtradesmanager.name"));
        this.nameField.setHint(Component.translatable("hint.wanderingtradesmanager.head_name"));
        this.nameField.setMaxLength(96);
        this.nameField.setValue(this.head == null ? "" : this.head.name());
        this.addRenderableWidget(this.nameField);

        this.textureField = new EditBox(this.font, formX, textureTop, formWidth, 20, Component.translatable("field.wanderingtradesmanager.texture"));
        this.textureField.setHint(Component.translatable("hint.wanderingtradesmanager.texture_value"));
        this.textureField.setMaxLength(4096);
        this.textureField.setValue(this.head == null ? "" : this.head.textureValue());
        this.addRenderableWidget(this.textureField);

        int buttonWidth = Math.min(180, formWidth);
        int buttonX = centerX - buttonWidth / 2;
        int buttonGap = 3;
        int buttonY = textureTop + 42;

        this.saveButton = Button.builder(green(this.creating ? "button.wanderingtradesmanager.add_head" : "button.wanderingtradesmanager.save"), button -> this.save())
                .bounds(buttonX, buttonY, buttonWidth, 20)
                .build();
        this.addRenderableWidget(this.saveButton);
        buttonY += 20 + buttonGap;

        if (!this.creating) {
            this.addRenderableWidget(Button.builder(gold("button.wanderingtradesmanager.copy_texture"), button -> this.copyTexture())
                    .bounds(buttonX, buttonY, buttonWidth, 20)
                    .build());
            buttonY += 20 + buttonGap;

            this.deleteButton = Button.builder(red("button.wanderingtradesmanager.remove"), button -> this.delete())
                    .bounds(buttonX, buttonY, buttonWidth, 20)
                    .build();
            this.addRenderableWidget(this.deleteButton);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("button.wanderingtradesmanager.done"), button -> this.minecraft.setScreen(this.parent))
                .bounds(buttonX, this.height - 32, buttonWidth, 20)
                .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        graphics.centeredText(this.font, this.title.copy().withStyle(style -> style.withBold(true)), centerX, 16, 0xFFFFFFFF);

        if (!this.creating) {
            ItemStack stack = HeadItemFactory.create(this.head);
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX - 24.0F, 40.0F);
            graphics.pose().scale(3.0F, 3.0F);
            graphics.item(stack, 0, 0);
            graphics.pose().popMatrix();
        }

        int formWidth = Math.min(420, this.width - 32);
        int formX = centerX - formWidth / 2;
        graphics.text(this.font, Component.translatable("field.wanderingtradesmanager.name"), formX, 96, 0xFFFFFFFF);
        graphics.text(this.font, Component.translatable("field.wanderingtradesmanager.texture"), formX, 142, 0xFFFFFFFF);

        if (this.creating) {
            graphics.text(this.font, Component.translatable("message.wanderingtradesmanager.add_help"), formX, 176, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void save() {
        String name = this.nameField.getValue().trim();
        String texture = this.textureField.getValue().trim();

        DatapackEditResult result;
        if (this.creating) {
            result = WanderingTradesManager.datapackManager().addHead(name, texture);
        } else {
            result = WanderingTradesManager.datapackManager().updateHead(this.head, name, texture);
        }

        notifyPlayer(result.message());
        if (result.success()) {
            this.parent.reloadHeadsFromChild();
            this.minecraft.setScreen(this.parent);
        }
    }

    private void delete() {
        if (this.head == null) {
            return;
        }

        DatapackEditResult result = WanderingTradesManager.datapackManager().removeHead(this.head);
        notifyPlayer(result.message());
        if (result.success()) {
            this.parent.reloadHeadsFromChild();
            this.minecraft.setScreen(this.parent);
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
        if (this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(message);
        }
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
}
