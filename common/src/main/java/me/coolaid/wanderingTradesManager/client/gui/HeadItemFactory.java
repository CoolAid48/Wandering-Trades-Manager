package me.coolaid.wanderingTradesManager.client.gui;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import me.coolaid.wanderingTradesManager.data.CustomHead;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class HeadItemFactory {
    private HeadItemFactory() {
    }

    public static ItemStack create(CustomHead head) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(head.name()));
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(createProfile(head)));

        return stack;
    }

    public static GameProfile createProfile(CustomHead head) {
        Multimap<String, Property> properties = ArrayListMultimap.create();
        properties.put("textures", new Property("textures", head.textureValue()));

        UUID profileId = UUID.nameUUIDFromBytes(head.textureValue().getBytes(StandardCharsets.UTF_8));
        return new GameProfile(profileId, "wtm_head", new PropertyMap(properties));
    }
}