package com.nododiiiii.ponderer.neoforge.interfaces;

import com.nododiiiii.ponderer.interfaces.InterfaceSmokeTest;
import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Routes PonderUI screen input to the interface sandbox host (design doc 11.1).
 */
public final class NeoForgeInterfaceEvents {

    private NeoForgeInterfaceEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NeoForgeInterfaceEvents::onMousePressed);
        NeoForge.EVENT_BUS.addListener(NeoForgeInterfaceEvents::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(NeoForgeInterfaceEvents::onMouseScrolled);
        NeoForge.EVENT_BUS.addListener(NeoForgeInterfaceEvents::onKeyPressed);
        NeoForge.EVENT_BUS.addListener(NeoForgeInterfaceEvents::onCharTyped);
        NeoForge.EVENT_BUS.addListener(NeoForgeInterfaceEvents::onClientTick);
    }

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (InterfaceHost.mouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (InterfaceHost.mouseReleased(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (InterfaceHost.mouseScrolled(event.getScreen(), event.getMouseX(), event.getMouseY(),
            event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (InterfaceHost.keyPressed(event.getScreen(), event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (InterfaceHost.charTyped(event.getScreen(), event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        InterfaceHost.tick();
        InterfaceSmokeTest.tick();
    }
}
