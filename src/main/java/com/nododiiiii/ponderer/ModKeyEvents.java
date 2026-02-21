package com.nododiiiii.ponderer;

import com.nododiiiii.ponderer.ui.FunctionScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ponderer.MODID, value = Dist.CLIENT)
public class ModKeyEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null) return;

        if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
            ScreenOpener.transitionTo(new FunctionScreen());
        }
    }
}
