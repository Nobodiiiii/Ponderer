package com.nododiiiii.ponderer.neoforge.runtime;

import com.nododiiiii.ponderer.neoforge.interfaces.NeoForgeInterfaceEvents;
import com.nododiiiii.ponderer.neoforge.interfaces.NeoForgeInterfacePlatform;
import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform;
import com.nododiiiii.ponderer.runtime.viewer.PonderPreviewTooltip;
import com.nododiiiii.ponderer.runtime.viewer.PonderSceneTextLayer;
import com.nododiiiii.ponderer.scene.LocalizedText;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class PondererLibNeoForgeClient {
    static void install() {
        LocalizedText.useLanguage(() -> Minecraft.getInstance().getLanguageManager().getSelected());
        if (com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.interfacesEnabled()) {
            InterfacePlatform.install(new NeoForgeInterfacePlatform());
            NeoForgeInterfaceEvents.register();
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> com.nododiiiii.ponderer.runtime.render.PonderRenderCache.tick());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            PonderPreviewTooltip tooltip = PonderPreviewTooltip.create(event.getScreen());
            if (tooltip != null) event.addListener(tooltip);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) -> PonderSceneTextLayer.beforeRender(event.getScreen()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            PonderSceneTextLayer.afterRender(event.getScreen(), event.getGuiGraphics());
            PonderPreviewTooltip.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> PonderSceneTextLayer.cancelPending());
    }
}
