package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.feedback.PackFeedback;
import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererAttribution;
import com.nododiiiii.ponderer.ponder.AutoRemoteSyncClient;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.TriggerManager;
import com.nododiiiii.ponderer.forge.interfaces.ForgeInterfaceEvents;
import com.nododiiiii.ponderer.projector.client.ProjectorBlockEntityRenderer;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;
import com.nododiiiii.ponderer.projector.client.ProjectorWorldOverlayQueue;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import com.nododiiiii.ponderer.registry.ModMenuTypes;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import com.nododiiiii.ponderer.ui.NbtPickState;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.PonderPreviewTooltip;
import com.nododiiiii.ponderer.ui.PonderSceneTextLayer;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only event handlers for the Forge platform.
 * This class is only loaded when running on the client side,
 * keeping client-only class references away from the dedicated server.
 */
public class PondererForgeClient {

    private static final BlueprintHandler blueprintHandler = new BlueprintHandler();
    private static boolean triggerKeyWasDown = false;

    static void init(IEventBus modEventBus) {
        PondererAttribution.iconModelLocation();
        BlueprintHandler.INSTANCE = blueprintHandler;

        modEventBus.addListener(PondererForgeClient::onClientSetup);
        modEventBus.addListener(PondererForgeClient::onRegisterKeyMappings);
        modEventBus.addListener(PondererForgeClient::onRegisterRenderers);
        modEventBus.addListener(PondererForgeClient::onLoadComplete);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onRegisterClientCommands);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onClientTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onBlueprintClientTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onRenderLevelStage);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onMouseScrolled);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onMouseInput);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onPlayerLoggedIn);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            PonderPreviewTooltip tooltip = PonderPreviewTooltip.create(event.getScreen());
            if (tooltip != null) event.addListener(tooltip);
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) ->
            PonderSceneTextLayer.beforeRender(event.getScreen()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            PonderSceneTextLayer.afterRender(event.getScreen(), event.getGuiGraphics());
            PonderPreviewTooltip.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> { if (event.phase == TickEvent.Phase.START) PonderSceneTextLayer.cancelPending(); });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
                Minecraft.getInstance().execute(ProjectorClientCaches::onItemTagsUpdated);
            }
        });
        ForgeInterfaceEvents.register();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SceneStore.extractDefaultsIfNeeded();
            SceneStore.AutoLoadResult result = SceneStore.autoLoadPonderPacks();
            if (!result.orphanedPacks.isEmpty()) {
                PondererForge.pendingOrphanedPacks.addAll(result.orphanedPacks);
            }
            if (!result.updatedPacks.isEmpty()) {
                PondererForge.pendingPackUpdates.addAll(result.updatedPacks);
            }
            SceneStore.reloadFromDisk();
            PonderIndex.addPlugin(new DynamicPonderPlugin());
            // Do NOT call PonderIndex.reload() here.
            // The Ponder library's load-complete lifecycle will run registerAll()
            // for initial registration; calling reload() here causes duplicate scenes.
            PonderConfig.Client().editingMode.set(false);
            MenuScreens.register(ModMenuTypes.PROJECTOR.get(), ProjectorConfigScreen::new);
        });
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        ModList.get().getModContainerById(Ponderer.MODID).orElseThrow().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory(
                (minecraft, previousScreen) -> new BaseConfigScreen(previousScreen, Ponderer.MODID)));
        BaseConfigScreen.setDefaultActionFor(Ponderer.MODID,
            base -> base.withSpecs(Config.CLIENT_SPEC, null, Config.SERVER_SPEC));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PROJECTOR.get(), ProjectorBlockEntityRenderer::new);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (var keyMapping : ModKeyBindings.all()) {
            event.register(keyMapping);
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PondererClientCommands.register(event.getDispatcher());
    }

    // --- Client tick for key bindings ---
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
            ScreenOpener.open(new FunctionScreen());
        }
        boolean triggerPressed = ModKeyBindings.TRIGGER_PONDER.isDown();
        if (triggerPressed && !triggerKeyWasDown) {
            TriggerManager.onTriggerKeyPressed();
        }
        triggerKeyWasDown = triggerPressed;
    }

    // --- Blueprint events ---
    private static void onBlueprintClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        AutoRemoteSyncClient.tick();
        blueprintHandler.tick();
        TriggerManager.tick();
        WorldFeedback.tick();
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ProjectorWorldOverlayQueue.beginFrame();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            ProjectorWorldOverlayQueue.render();
        }
    }

    private static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (blueprintHandler.mouseScrolled(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    private static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (NbtPickState.isActive() && event.getButton() == 2 && event.getAction() == 1) {
            NbtPickState.handleUseClick();
            event.setCanceled(true);
            return;
        }

        if (blueprintHandler.onMouseInput(event.getButton(), event.getAction() == 1)) {
            event.setCanceled(true);
        }
    }

    // --- Player join notifications ---
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(PondererForge.pendingPackUpdates);
        PondererForge.pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(PondererForge.pendingOrphanedPacks);
        PondererForge.pendingOrphanedPacks.clear();

        PackFeedback.show(updates, orphaned);
    }
}
