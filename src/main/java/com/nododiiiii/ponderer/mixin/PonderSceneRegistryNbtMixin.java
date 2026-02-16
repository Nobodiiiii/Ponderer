package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.ponder.NbtSceneFilter;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Filters compiled PonderScene list based on NBT filter when an ItemStack
 * is available via the ThreadLocal set by PonderUINbtMixin.
 */
@Mixin(PonderSceneRegistry.class)
public class PonderSceneRegistryNbtMixin {

    @Inject(
        method = "compile(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void ponderer$filterByNbt(ResourceLocation id, CallbackInfoReturnable<List<PonderScene>> cir) {
        ItemStack stack = NbtSceneFilter.getCurrentStack();
        if (stack == null || stack.isEmpty()) return;

        List<PonderScene> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<PonderScene> filtered = NbtSceneFilter.filter(stack, original);
        if (filtered != original) {
            cir.setReturnValue(filtered);
        }
    }
}
