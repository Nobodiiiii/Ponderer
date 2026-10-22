package com.nododiiiii.ponderer.mixin;

import com.nododiiiii.ponderer.interfaces.sandbox.SandboxScope;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps background-triggered global overlays outside the embedded sandbox render. */
@Mixin(Screen.class)
public class SandboxScreenBackgroundEventsMixin {

    @Redirect(method = "renderBackground", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z",
        remap = false))
    private boolean ponderer$renderBackgroundOverlaysOnHost(IEventBus bus, Event event) {
        // Screens such as Tetra's workbench draw their background before the container
        // render. JEI must not draw or adopt that embedded screen's bounds here; the
        // host's render event handles its overlay after the sandbox scope has closed.
        if (SandboxScope.isActive() && event instanceof ScreenEvent.BackgroundRendered) {
            return false;
        }
        return bus.post(event);
    }
}
