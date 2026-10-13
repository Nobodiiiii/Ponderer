package com.nododiiiii.ponderer.ui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;

/**
 * Defines the type of ID that a text field expects,
 * used by JEI integration to filter and resolve ingredients.
 */
public enum IdFieldMode {
    /** Accepts only items with a block form (BlockItem). Resolves to Block registry name. */
    BLOCK,
    /** Accepts all items. Resolves to Item registry name. */
    ITEM,
    /** Accepts spawn eggs and entity items. Resolves to EntityType registry name. */
    ENTITY,
    /** Accepts any JEI-registered ingredient (items, fluids, chemicals, etc.). Resolves to registry ID. */
    INGREDIENT;

    @Nullable
    public String resolveItemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return switch (this) {
            case ITEM, INGREDIENT -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            case BLOCK -> stack.getItem() instanceof BlockItem blockItem
                ? BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString() : null;
            case ENTITY -> {
                if (stack.getItem() instanceof SpawnEggItem egg) {
                    yield BuiltInRegistries.ENTITY_TYPE.getKey(egg.getType(stack)).toString();
                }
                ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (stack.getItem() instanceof BoatItem) {
                    String entityName = itemKey.getPath().contains("chest") ? "chest_boat" : "boat";
                    yield ResourceLocation.fromNamespaceAndPath(itemKey.getNamespace(), entityName).toString();
                }
                yield BuiltInRegistries.ENTITY_TYPE.containsKey(itemKey) ? itemKey.toString() : null;
            }
        };
    }
}
