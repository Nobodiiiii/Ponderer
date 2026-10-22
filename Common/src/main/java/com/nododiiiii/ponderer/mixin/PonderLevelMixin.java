package com.nododiiiii.ponderer.mixin;

import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderWorldParticles;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(PonderLevel.class)
public class PonderLevelMixin {

    @Shadow(remap = false)
    protected PonderWorldParticles particles;

    @Shadow(remap = false)
    boolean currentlyTickingEntities;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void ponderer$tickEntitiesSafely(CallbackInfo ci) {
        currentlyTickingEntities = true;

        List<Entity> liveEntities = ((SchematicLevel) (Object) this).getEntityList();
        try {
            particles.tick();

            List<Entity> snapshot = new ArrayList<>(liveEntities);
            for (Entity entity : snapshot) {
                entity.tickCount++;
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
                entity.tick();

                if (entity.getY() <= -.5f) {
                    entity.discard();
                }
            }

            liveEntities.removeIf(entity -> !entity.isAlive());
        } finally {
            currentlyTickingEntities = false;
        }

        ci.cancel();
    }
}
