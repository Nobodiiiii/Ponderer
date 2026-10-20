package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.feedback.PackFeedback;
import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.fabric.interfaces.FabricInterfaceEvents;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.AutoRemoteSyncClient;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.TriggerManager;
import com.nododiiiii.ponderer.projector.client.ProjectorBlockEntityRenderer;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;
import com.nododiiiii.ponderer.projector.client.ProjectorWorldOverlayQueue;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import com.nododiiiii.ponderer.registry.ModMenuTypes;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.ui.PonderPreviewTooltip;
import com.nododiiiii.ponderer.ui.PonderSceneTextLayer;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric client entrypoint.
 */
public class PondererFabricClient implements ClientModInitializer {

    private static List<String> pendingOrphanedPacks = new ArrayList<>();
    private static List<SceneStore.PackUpdateInfo> pendingPackUpdates = new ArrayList<>();

    private final BlueprintHandler blueprintHandler = new BlueprintHandler();
    private boolean hasNotified = false;
    private boolean triggerKeyWasDown = false;
    private boolean blueprintRightMouseWasDown = false;

    @Override
    public void onInitializeClient() {
        BlueprintHandler.INSTANCE = blueprintHandler;

        // Key bindings
        for (var keyMapping : ModKeyBindings.all()) {
            KeyBindingHelper.registerKeyBinding(keyMapping);
        }

        // Ponder init
        SceneStore.extractDefaultsIfNeeded();
        SceneStore.AutoLoadResult result = SceneStore.autoLoadPonderPacks();
        if (!result.orphanedPacks.isEmpty()) {
            pendingOrphanedPacks.addAll(result.orphanedPacks);
        }
        if (!result.updatedPacks.isEmpty()) {
            pendingPackUpdates.addAll(result.updatedPacks);
        }
        SceneStore.reloadFromDisk();
        PonderIndex.addPlugin(new DynamicPonderPlugin());
        // Do NOT call PonderIndex.reload() here.
        // The Ponder library's load-complete lifecycle will run registerAll()
        // for initial registration; calling reload() here causes duplicate scenes.
        PonderConfig.client().editingMode.set(false);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) {
                Minecraft.getInstance().execute(ProjectorClientCaches::onItemTagsUpdated);
            }
        });

        BlockEntityRendererRegistry.register(ModBlockEntities.PROJECTOR.get(), ctx -> new ProjectorBlockEntityRenderer());
        MenuScreens.register(ModMenuTypes.PROJECTOR.get(), ProjectorConfigScreen::new);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            PonderPreviewTooltip tooltip = PonderPreviewTooltip.create(screen);
            if (tooltip == null) return;
            Screens.getButtons(screen).add(tooltip);
            ScreenEvents.beforeRender(screen).register((scr, graphics, mouseX, mouseY, partialTick) ->
                PonderSceneTextLayer.beforeRender(scr));
            ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, partialTick) -> {
                PonderSceneTextLayer.afterRender(scr, graphics);
                tooltip.renderTooltip(graphics, mouseX, mouseY);
            });
            ScreenEvents.remove(screen).register(scr -> PonderSceneTextLayer.cancelPending());
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> PonderSceneTextLayer.cancelPending());
        WorldRenderEvents.START.register(context -> ProjectorWorldOverlayQueue.beginFrame());
        WorldRenderEvents.LAST.register(context -> {
            ProjectorWorldOverlayQueue.render();
        });

        // Register client commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PondererClientCommands.register(dispatcher);
        });

        // Client tick: key bindings + blueprint handler + player join notifications
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AutoRemoteSyncClient.tick();
            if (client.player == null) hasNotified = false;

            // Key binding
            if (client.player != null && client.screen == null) {
                if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
                    ScreenOpener.open(new FunctionScreen());
                }

            }

            // Blueprint handler tick
            blueprintHandler.tick();

            // Fabric has no Forge-style mouse button event here; route right-click edge to blueprint handler.
            if (client.player != null && client.screen == null) {
                long window = client.getWindow().getWindow();
                boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
                if (rightDown && !blueprintRightMouseWasDown) {
                    blueprintHandler.onMouseInput(1, true);
                }
                blueprintRightMouseWasDown = rightDown;
            } else {
                blueprintRightMouseWasDown = false;
            }

            // Trigger manager tick
            TriggerManager.tick();
            WorldFeedback.tick();

            // Trigger key: process after TriggerManager.tick() so activeScene is fresh.
            if (client.player != null && client.screen == null) {
                boolean triggerByBinding = ModKeyBindings.TRIGGER_PONDER.isDown();
                boolean triggerByRawC = GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
                boolean triggerPressed = triggerByBinding || triggerByRawC;

                if (triggerPressed && !triggerKeyWasDown) {
                    boolean opened = TriggerManager.onTriggerKeyPressed();
                }
                triggerKeyWasDown = triggerPressed;
            } else {
                triggerKeyWasDown = false;
            }

            // Player login notification (check once)
            if (!hasNotified && client.player != null) {
                hasNotified = true;
                showPendingNotifications();
            }

        });

        FabricInterfaceEvents.register();

        // Blueprint mouse events are handled via the tick method and client input directly
        // Fabric doesn't have direct mouse scroll/click events at the same level as Forge's InputEvent
        // The BlueprintHandler itself checks for key states in its tick() method
        prepareNativeConfigUi();
    }

    private static void prepareNativeConfigUi() {
        BaseConfigScreen.setDefaultActionFor(Ponderer.MODID,
            base -> base.withSpecs(Config.CLIENT_SPEC, null, Config.SERVER_SPEC));
    }

    private void showPendingNotifications() {
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(pendingPackUpdates);
        pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(pendingOrphanedPacks);
        pendingOrphanedPacks.clear();
        PackFeedback.show(updates, orphaned);
    }
}
