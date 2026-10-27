package com.nododiiiii.ponderer.editor.mixin;

import com.nododiiiii.ponderer.editor.capture.InterfaceCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class InterfaceLevelCaptureMixin {
    @Inject(method = "getBlockEntity", at = @At("RETURN"))
    private void ponderer$lookup(BlockPos pos, CallbackInfoReturnable<BlockEntity> ci) {
        InterfaceCapture.observeBlockEntity(this, pos, ci.getReturnValue());
    }
}
