package com.nododiiiii.ponderer;

import com.nododiiiii.ponderer.ui.FunctionScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ModKeyEvents {

    private ModKeyEvents() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;

        if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
            ScreenOpener.transitionTo(new FunctionScreen());
        }
    }
}
