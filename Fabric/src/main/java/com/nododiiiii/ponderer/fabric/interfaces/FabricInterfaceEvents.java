package com.nododiiiii.ponderer.fabric.interfaces;

import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.render.PonderRenderCache;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/**
 * Routes PonderUI screen input to the interface sandbox host (design doc 11.1).
 * Fabric's screen API has no drag or char-typed callbacks: drags are synthesized by the host,
 * typed characters are not forwarded yet.
 */
public final class FabricInterfaceEvents {

    private FabricInterfaceEvents() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PonderUI)) {
                return;
            }
            ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseX, mouseY, button) ->
                !InterfaceHost.mouseClicked(scr, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((scr, mouseX, mouseY, button) ->
                !InterfaceHost.mouseReleased(scr, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseScroll(screen).register((scr, mouseX, mouseY, scrollX, scrollY) ->
                !InterfaceHost.mouseScrolled(scr, mouseX, mouseY, scrollX, scrollY));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key, scanCode, modifiers) ->
                !InterfaceHost.keyPressed(scr, key, scanCode, modifiers));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PonderRenderCache.tick();
            InterfaceHost.tick();
        });
    }
}
