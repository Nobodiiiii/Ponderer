package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.sandbox.SandboxLevel;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxWorldBridge;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class SandboxChunkCacheMixin {
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void ponderer$chunk(int x, int z, ChunkStatus status, boolean required, CallbackInfoReturnable<LevelChunk> ci) {
        SandboxLevel level = SandboxWorldBridge.level(this);
        if (level != null) ci.setReturnValue(level.lookupChunk(x, z, required));
    }
}
