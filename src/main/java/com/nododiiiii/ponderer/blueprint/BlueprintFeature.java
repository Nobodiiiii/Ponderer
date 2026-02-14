package com.nododiiiii.ponderer.blueprint;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

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
     * Returns the configured carrier item id string.
     */
    public static String getCarrierId() {
        try {
            return Config.BLUEPRINT_CARRIER_ITEM.get();
        } catch (Exception e) {
            return DEFAULT_CARRIER;
        }
    }

    /**
     * Resolves the configured carrier item from the registry.
     * Falls back to the built-in blueprint if the config value is invalid.
     */
    public static Item resolveCarrierItem() {
        String id = getCarrierId();
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return ModItems.BLUEPRINT.get();
        }
        return BuiltInRegistries.ITEM.getOptional(loc).orElse(ModItems.BLUEPRINT.get());
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
     * Returns true if Create mod is loaded.
     */
    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    /**
     * Returns true if the built-in blueprint item should be registered in
     * creative tabs. This is the case only when:
     * <ul>
     *   <li>The carrier item is explicitly set to "ponderer:blueprint"</li>
     *   <li>Create is NOT loaded</li>
     * </ul>
     */
    public static boolean shouldShowBlueprintInCreativeTab() {
        return BUILTIN_BLUEPRINT.equals(getCarrierId()) && !isCreateLoaded();
    }
}
