package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.sandbox.SandboxLevel;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxWorldBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class SandboxWorldBridgeMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void ponderer$state(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.getBlockState(pos));
    }
    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void ponderer$entity(BlockPos pos, CallbackInfoReturnable<BlockEntity> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.getBlockEntity(pos));
    }
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void ponderer$fluid(BlockPos pos, CallbackInfoReturnable<FluidState> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.getFluidState(pos));
    }
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void ponderer$chunk(int x, int z, ChunkStatus status, boolean required, CallbackInfoReturnable<ChunkAccess> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.lookupChunk(x, z, required));
    }
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void ponderer$set(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.setBlock(pos, state, flags));
    }
    @Inject(method = "setBlockEntity", at = @At("HEAD"), cancellable = true)
    private void ponderer$setEntity(BlockEntity be, CallbackInfo ci) {
        if (SandboxWorldBridge.level(this) != null) ci.cancel();
    }
    @Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
    private void ponderer$removeEntity(BlockPos pos, CallbackInfo ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) { level.removeBlockEntity(pos); ci.cancel(); }
    }
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void ponderer$destroy(BlockPos pos, boolean drops, Entity entity, int recursion, CallbackInfoReturnable<Boolean> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.removeBlock(pos, false));
    }
}
