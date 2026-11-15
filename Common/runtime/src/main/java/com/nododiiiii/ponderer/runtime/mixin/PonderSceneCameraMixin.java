package com.nododiiiii.ponderer.runtime.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nododiiiii.ponderer.runtime.api.RuntimeScenes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Shares Ponder's camera with billboard renderers for the entire scene pass. */
@Mixin(PonderScene.class)
public abstract class PonderSceneCameraMixin {
    @Shadow(remap = false) @Final private PonderScene.SceneCamera camera;

    @WrapMethod(method = "renderScene(Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/client/gui/GuiGraphics;F)V")
    private void ponderer$renderWithSceneCamera(SuperRenderTypeBuffer buffer, GuiGraphics graphics,
                                               float partialTicks, Operation<Void> original) {
        PonderScene scene = (PonderScene) (Object) this;
        if (!RuntimeScenes.owns(scene.getWorld())) {
            original.call(buffer, graphics, partialTicks);
            return;
        }

        // Upstream initializes this camera after rendering block entities. Set it before
        // the whole pass so both block-entity and entity billboards use the same view.
        PonderScene.SceneTransform transform = scene.getTransform();
        camera.set(-transform.xRotation.getValue(partialTicks),
            transform.yRotation.getValue(partialTicks) + 180);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf previousOrientation = dispatcher.cameraOrientation();
        try {
            dispatcher.overrideCameraOrientation(new Quaternionf(camera.rotation()));
            original.call(buffer, graphics, partialTicks);
        } finally {
            dispatcher.overrideCameraOrientation(previousOrientation);
        }
    }
}
