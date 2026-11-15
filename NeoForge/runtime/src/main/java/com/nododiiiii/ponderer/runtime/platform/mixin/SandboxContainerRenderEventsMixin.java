package com.nododiiiii.ponderer.runtime.platform.mixin;

import com.nododiiiii.ponderer.runtime.interfaces.sandbox.InterfaceExclusions;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxScope;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserves the original JEI container-event workaround, controlled by resource rules. */
@Mixin(AbstractContainerScreen.class)
public class SandboxContainerRenderEventsMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
        remap = false))
    private Event ponderer$renderOverlaysOnHost(IEventBus bus, Event event) {
        // Keep the old loader-event path: do not inject JEI classes or depend on its
        // internal draw-method names. The host still posts its normal screen events.
        if (SandboxScope.isActive() && event instanceof ContainerScreenEvent.Render
            && ModList.get().isLoaded("jei") && InterfaceExclusions.isWholeModExcluded("jei")) {
            return event;
        }
        return bus.post(event);
    }
}
