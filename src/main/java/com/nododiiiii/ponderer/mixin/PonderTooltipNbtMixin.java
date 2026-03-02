package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.NbtSceneFilter;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent showing the ponder tooltip for items whose scenes are ALL
 * filtered out by NBT filters.
 */
@Mixin(PonderTooltipHandler.class)
public class PonderTooltipNbtMixin {

    @Inject(
        method = "updateHovered(Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void ponderer$checkNbtFilter(ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) return;

        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!PonderIndex.getSceneAccess().doScenesExistForId(itemId)) return;

            // Scenes exist for this item type, but do any pass the NBT filter?
            if (NbtSceneFilter.hasFilters(itemId) && !NbtSceneFilter.hasVisibleScenes(stack, itemId)) {
                ci.cancel();
            }
        } catch (Exception ignored) {
            // If anything goes wrong, let the original method handle it
        }
    }
}
