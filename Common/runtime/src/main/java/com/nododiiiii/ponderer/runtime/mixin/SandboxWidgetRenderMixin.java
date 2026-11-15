package com.nododiiiii.ponderer.runtime.mixin;

import com.nododiiiii.ponderer.runtime.interfaces.sandbox.InterfaceExclusions;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxScope;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also covers screens that draw widgets directly instead of through Screen.render. */
@Mixin(AbstractWidget.class)
public class SandboxWidgetRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ponderer$excludeWidget(CallbackInfo ci) {
        var session = SandboxScope.activeSession();
        if (session != null && InterfaceExclusions.isExcluded(this, session.currentScreen())) ci.cancel();
    }
}
