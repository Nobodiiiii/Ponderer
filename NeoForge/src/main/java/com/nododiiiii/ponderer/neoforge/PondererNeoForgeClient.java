package com.nododiiiii.ponderer.neoforge;

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
import com.nododiiiii.ponderer.neoforge.interfaces.NeoForgeInterfaceEvents;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only event handlers for the NeoForge platform.
 * This class is only loaded when running on the client side,
 * keeping client-only class references away from the dedicated server.
 */
public class PondererNeoForgeClient {

    private static final BlueprintHandler blueprintHandler = new BlueprintHandler();
    private static boolean triggerKeyWasDown = false;

    static void init(IEventBus modEventBus) {
        PondererAttribution.iconModelLocation();
        BlueprintHandler.INSTANCE = blueprintHandler;

        modEventBus.addListener(PondererNeoForgeClient::onClientSetup);
        modEventBus.addListener(PondererNeoForgeClient::onRegisterKeyMappings);
        modEventBus.addListener(PondererNeoForgeClient::onRegisterRenderers);
        modEventBus.addListener(PondererNeoForgeClient::onRegisterMenuScreens);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onBlueprintClientTick);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onMouseScrolled);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onMouseInput);
        NeoForge.EVENT_BUS.addListener(PondererNeoForgeClient::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            PonderPreviewTooltip tooltip = PonderPreviewTooltip.create(event.getScreen());
            if (tooltip != null) event.addListener(tooltip);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre event) ->
            PonderSceneTextLayer.beforeRender(event.getScreen()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> {
            PonderSceneTextLayer.afterRender(event.getScreen(), event.getGuiGraphics());
            PonderPreviewTooltip.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> PonderSceneTextLayer.cancelPending());
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
                Minecraft.getInstance().execute(ProjectorClientCaches::onItemTagsUpdated);
            }
        });
        NeoForgeInterfaceEvents.register();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SceneStore.extractDefaultsIfNeeded();
            SceneStore.AutoLoadResult result = SceneStore.autoLoadPonderPacks();
            if (!result.orphanedPacks.isEmpty()) {
                PondererNeoForge.pendingOrphanedPacks.addAll(result.orphanedPacks);
            }
            if (!result.updatedPacks.isEmpty()) {
                PondererNeoForge.pendingPackUpdates.addAll(result.updatedPacks);
            }
            SceneStore.reloadFromDisk();
            PonderIndex.addPlugin(new DynamicPonderPlugin());
            // Do NOT call PonderIndex.reload() here.
            // The Ponder library's load-complete lifecycle will run registerAll()
            // for initial registration; calling reload() here causes duplicate scenes.
            PonderConfig.client().editingMode.set(false);
        });
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.PROJECTOR.get(), ProjectorConfigScreen::new);
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
    private static void onClientTick(ClientTickEvent.Post event) {
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
    private static void onBlueprintClientTick(ClientTickEvent.Post event) {
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
        if (blueprintHandler.mouseScrolled(event.getScrollDeltaY())) {
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
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(PondererNeoForge.pendingPackUpdates);
        PondererNeoForge.pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(PondererNeoForge.pendingOrphanedPacks);
        PondererNeoForge.pendingOrphanedPacks.clear();

        PackFeedback.show(updates, orphaned);
    }
}
