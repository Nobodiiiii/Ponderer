package com.nododiiiii.ponderer.blueprint;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Bridges Forge events to {@link BlueprintHandler} (client-only).
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class BlueprintEvents {

    /** Singleton handler instance. */
    public static final BlueprintHandler HANDLER = new BlueprintHandler();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        HANDLER.tick();
    }

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (HANDLER.mouseScrolled(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (HANDLER.onMouseInput(event.getButton(), event.getAction() == 1)) {
            event.setCanceled(true);
        }
    }
}
