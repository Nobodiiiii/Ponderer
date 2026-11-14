package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.core.RuntimeStructureSources;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ordinary Ponder entry point uses the same host resource provider as direct compilation. */
@Mixin(PonderSceneRegistry.class)
public class PonderStructureSourceMixin {
    @Inject(method = "loadSchematic(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
        at = @At("HEAD"), cancellable = true)
    private static void ponderer$loadHostStructure(ResourceLocation id, CallbackInfoReturnable<StructureTemplate> cir) {
        StructureTemplate template = RuntimeStructureSources.load(id);
        if (template != null) cir.setReturnValue(template);
    }
}
