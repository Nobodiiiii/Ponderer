package com.nododiiiii.ponderer.forge.interfaces;

import com.nododiiiii.ponderer.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.ponder.PonderRenderCache;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ScreenEvent;

/**
 * Routes PonderUI screen input to the interface sandbox host (design doc 11.1).
 */
public final class ForgeInterfaceEvents {

    private ForgeInterfaceEvents() {
    }

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeInterfaceEvents::onMousePressed);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeInterfaceEvents::onMouseReleased);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeInterfaceEvents::onMouseScrolled);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeInterfaceEvents::onKeyPressed);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeInterfaceEvents::onCharTyped);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeInterfaceEvents::onClientTick);
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
            event.getScrollDelta() * 0, event.getScrollDelta())) {
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

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PonderRenderCache.tick();
        InterfaceHost.tick();
    }
}
