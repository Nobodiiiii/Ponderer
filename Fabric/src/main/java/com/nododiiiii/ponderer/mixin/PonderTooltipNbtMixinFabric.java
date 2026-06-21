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

@Mixin(PonderTooltipHandler.class)
public class PonderTooltipNbtMixinFabric {

    @Inject(
        method = "updateHovered(Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void ponderer$checkNbtFilterFabric(ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty()) return;

        try {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!PonderIndex.getSceneAccess().doScenesExistForId(itemId)) return;

            if (NbtSceneFilter.hasFilters(itemId) && !NbtSceneFilter.hasVisibleScenes(stack, itemId)) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }
}
