package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxScope;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps global container overlays on the host screen, outside the embedded sandbox render. */
@Mixin(AbstractContainerScreen.class)
public class SandboxContainerRenderEventsMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
        remap = false))
    private Event ponderer$renderOverlaysOnHost(IEventBus bus, Event event) {
        // JEI 19.42 draws its search field from both container render events. Letting an
        // embedded container post them draws JEI before PonderUI can hide its overlay.
        // The host's screen event still renders JEI for slot editing and try/exercise mode.
        if (SandboxScope.isActive() && event instanceof ContainerScreenEvent.Render) {
            return event;
        }
        return bus.post(event);
    }
}
