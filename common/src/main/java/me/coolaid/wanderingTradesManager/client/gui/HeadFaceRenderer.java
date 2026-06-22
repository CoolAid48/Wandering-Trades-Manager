package me.coolaid.wanderingTradesManager.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

final class HeadFaceRenderer {
    private static final float SKIN_TEXTURE_SIZE = 64.0F;

    private HeadFaceRenderer() {
    }

    static void render(GuiGraphicsExtractor graphics, Minecraft minecraft, Identifier skinTexture, int x, int y, int size) {
        AbstractTexture texture = minecraft.getTextureManager().getTexture(skinTexture);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        blitSkinFace(graphics, texture, sampler, x, y, size, 8.0F, 8.0F);
        blitSkinFace(graphics, texture, sampler, x, y, size, 40.0F, 8.0F);
    }

    private static void blitSkinFace(GuiGraphicsExtractor graphics, AbstractTexture texture, GpuSampler sampler, int x, int y, int size, float u, float v) {
        graphics.blit(
                texture.getTextureView(),
                sampler,
                x,
                y,
                x + size,
                y + size,
                u / SKIN_TEXTURE_SIZE,
                (u + 8.0F) / SKIN_TEXTURE_SIZE,
                v / SKIN_TEXTURE_SIZE,
                (v + 8.0F) / SKIN_TEXTURE_SIZE
        );
    }
}