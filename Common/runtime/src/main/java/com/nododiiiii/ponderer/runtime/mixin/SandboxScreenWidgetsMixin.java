package com.nododiiiii.ponderer.runtime.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.InterfaceExclusions;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxScope;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Keep excluded controls out of embedded rendering, focus and mouse/keyboard routing. */
@Mixin(Screen.class)
public class SandboxScreenWidgetsMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/Renderable;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void ponderer$renderControl(Renderable control, GuiGraphics graphics, int x, int y, float partialTicks,
                                        Operation<Void> original) {
        if (!InterfaceExclusions.isExcluded(control, (Screen) (Object) this))
            original.call(control, graphics, x, y, partialTicks);
    }

    @Inject(method = "children", at = @At("RETURN"), cancellable = true)
    private void ponderer$filterInput(CallbackInfoReturnable<List<? extends GuiEventListener>> ci) {
        if (!SandboxScope.isActive()) return;
        List<? extends GuiEventListener> children = ci.getReturnValue();
        Screen screen = (Screen) (Object) this;
        if (children.stream().anyMatch(child -> InterfaceExclusions.isExcluded(child, screen)))
            ci.setReturnValue(children.stream().filter(child -> !InterfaceExclusions.isExcluded(child, screen)).toList());
    }
}
