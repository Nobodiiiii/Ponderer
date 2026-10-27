package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxLevel;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxWorldBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class SandboxClientLevelMixin {
    // Intercept before ClientLevel's prediction handler can mutate its real bookkeeping.
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void ponderer$set(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.setBlock(pos, state, flags));
    }
    @Inject(method = "getEntity", at = @At("HEAD"), cancellable = true)
    private void ponderer$entity(int id, CallbackInfoReturnable<Entity> ci) {
        if (SandboxWorldBridge.level(this) != null) ci.setReturnValue(null);
    }
}
