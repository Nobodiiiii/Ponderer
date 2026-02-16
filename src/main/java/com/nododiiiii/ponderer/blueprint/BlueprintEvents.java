package com.nododiiiii.ponderer.blueprint;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;

/**
 * Bridges Forge events to {@link BlueprintHandler} (client-only).
 */
@Mod.EventBusSubscriber(modid = Ponderer.MODID, value = Dist.CLIENT)
public class BlueprintEvents {

    /** Singleton handler instance. */
    public static final BlueprintHandler HANDLER = new BlueprintHandler();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            HANDLER.tick();
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (HANDLER.mouseScrolled(event.getScrollDelta())) {
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
