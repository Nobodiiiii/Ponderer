package com.nododiiiii.ponderer.blueprint;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.FeatureAvailability;
import com.nododiiiii.ponderer.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Utility class that resolves the carrier item for the Blueprint tool
 * based on the config and runtime conditions.
 */
public final class BlueprintFeature {

    private static final String BUILTIN_BLUEPRINT = "ponderer:blueprint";
    private static final String DEFAULT_CARRIER = "minecraft:paper";

    private BlueprintFeature() {
    }

    /**
     * Returns true when the server config enables Ponderer's built-in Blueprint item.
     */
    public static boolean isBuiltinBlueprintItemEnabled() {
        return FeatureAvailability.isBlueprintEnabled();
    }

    /**
     * Returns the configured carrier item id string.
     */
    public static String getCarrierId() {
        try {
            String carrierId = Config.BLUEPRINT_CARRIER_ITEM.get();
            return carrierId == null || carrierId.isBlank() ? DEFAULT_CARRIER : carrierId.trim();
        } catch (Exception e) {
            return DEFAULT_CARRIER;
        }
    }

    /**
     * Resolves the configured carrier item from the registry.
     * Falls back to paper if the config value is invalid.
     * Returns air when the client selected the built-in Blueprint item but
     * the server has disabled that item.
     */
    public static Item resolveCarrierItem() {
        String id = getCarrierId();
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return Items.PAPER;
        }
        if (BUILTIN_BLUEPRINT.equals(id)) {
            if (!isBuiltinBlueprintItemEnabled()) {
                return Items.AIR;
            }
            return ModItems.BLUEPRINT.get();
        }
        return BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.PAPER);
    }

    /**
     * Returns an ItemStack of the carrier item (for display purposes).
     */
    public static ItemStack getCarrierStack() {
        Item item = resolveCarrierItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Returns true if the given stack matches the configured carrier item.
     */
    public static boolean matchesCarrierStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(resolveCarrierItem());
    }

    /**
     * Returns true if the built-in Blueprint item should be shown in
     * creative tabs.
     */
    public static boolean shouldShowBlueprintInCreativeTab() {
        return isBuiltinBlueprintItemEnabled();
    }
}
