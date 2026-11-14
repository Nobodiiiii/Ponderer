package com.nododiiiii.ponderer.fabric.runtime;

import com.nododiiiii.ponderer.fabric.interfaces.FabricInterfaceEvents;
import com.nododiiiii.ponderer.fabric.interfaces.FabricInterfacePlatform;
import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform;
import com.nododiiiii.ponderer.runtime.viewer.PonderPreviewTooltip;
import com.nododiiiii.ponderer.runtime.viewer.PonderSceneTextLayer;
import com.nododiiiii.ponderer.scene.LocalizedText;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;

/** Client runtime services shared by all content hosts. No product is required. */
public final class PondererLibFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // Flywheel is client-only, so a common metadata dependency would reject dedicated servers.
        var loader = FabricLoader.getInstance();
        var metadata = loader.getModContainer("pondererlib").orElseThrow().getMetadata();
        for (var dependency : metadata.getDependencies()) {
            if (!dependency.getModId().equals("flywheel")) continue;
            var installed = loader.getModContainer("flywheel");
            if (installed.isEmpty() || !dependency.matches(installed.get().getMetadata().getVersion()))
                throw new IllegalStateException("Ponderer Lib client requires " + dependency);
        }
        LocalizedText.useLanguage(() -> Minecraft.getInstance().getLanguageManager().getSelected());
        com.nododiiiii.ponderer.runtime.api.ResourceSceneContents.install();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override public net.minecraft.resources.ResourceLocation getFabricId() {
                    return net.minecraft.resources.ResourceLocation.parse("pondererlib:content");
                }
                @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resources) {
                    com.nododiiiii.ponderer.runtime.api.ResourceSceneContents.reload(resources);
                }
            });
        ClientTickEvents.END_CLIENT_TICK.register(client -> com.nododiiiii.ponderer.runtime.api.ResourceSceneContents.tick());
        net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) Minecraft.getInstance().execute(com.nododiiiii.ponderer.runtime.api.ResourceSceneContents::onItemTagsUpdated);
        });
        if (com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.interfacesEnabled()) {
            InterfacePlatform.install(new FabricInterfacePlatform());
            FabricInterfaceEvents.register();
        }
        if (com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.enabled(com.nododiiiii.ponderer.runtime.api.RuntimeFeature.RENDER_CACHE))
            ClientTickEvents.END_CLIENT_TICK.register(client -> com.nododiiiii.ponderer.runtime.render.PonderRenderCache.tick());
        if (!com.nododiiiii.ponderer.runtime.api.RuntimeFeatures.enabled(com.nododiiiii.ponderer.runtime.api.RuntimeFeature.PLAYER)) return;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            PonderPreviewTooltip tooltip = PonderPreviewTooltip.create(screen);
            if (tooltip == null) return;
            Screens.getButtons(screen).add(tooltip);
            ScreenEvents.beforeRender(screen).register((scr, graphics, x, y, partial) -> PonderSceneTextLayer.beforeRender(scr));
            ScreenEvents.afterRender(screen).register((scr, graphics, x, y, partial) -> {
                PonderSceneTextLayer.afterRender(scr, graphics);
                tooltip.renderTooltip(graphics, x, y);
            });
            ScreenEvents.remove(screen).register(scr -> PonderSceneTextLayer.cancelPending());
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> PonderSceneTextLayer.cancelPending());
    }
}
