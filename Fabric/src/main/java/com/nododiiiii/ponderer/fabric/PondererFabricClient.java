package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.compat.jei.PondererJeiPlugin;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.TriggerManager;
import com.nododiiiii.ponderer.registry.ModBlocks;
import com.nododiiiii.ponderer.registry.ModItems;
import com.nododiiiii.ponderer.registry.ModMenuTypes;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import com.nododiiiii.ponderer.ui.CoordPickState;
import com.nododiiiii.ponderer.ui.NbtPickState;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import com.nododiiiii.ponderer.projector.client.ProjectorBlockEntityRenderer;
import com.nododiiiii.ponderer.projector.client.ProjectorWorldOverlayQueue;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
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
    private boolean jeiLeftMouseWasDown = false;
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
        // The Ponder library's CLIENT_STARTED event will call PonderIndex.registerAll()
        // to perform the initial scene registration. Calling reload() here would cause
        // scenes to be registered twice (once by reload, once by registerAll).
        PonderConfig.Client().editingMode.set(false);

        BlockEntityRendererRegistry.register(ModBlockEntities.PROJECTOR.get(), ctx -> new ProjectorBlockEntityRenderer());
        MenuScreens.register(ModMenuTypes.PROJECTOR.get(), ProjectorConfigScreen::new);
        WorldRenderEvents.START.register(context -> ProjectorWorldOverlayQueue.beginFrame());
        WorldRenderEvents.LAST.register(context -> {
            ProjectorWorldOverlayQueue.render();
        });

        // Register client commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PondererClientCommands.register(dispatcher);
        });

        // Creative tab
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                entries.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
            entries.accept(new ItemStack(ModBlocks.MINIATURE_PROJECTOR_ITEM.get()));
            entries.accept(new ItemStack(ModBlocks.LIFE_SIZE_PROJECTOR_ITEM.get()));
        });

        // Client tick: key bindings + blueprint handler + player join notifications + JEI interception
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.screen == null && NbtPickState.isActive()) {
                client.player.displayClientMessage(Component.translatable("ponderer.ui.nbt_pick.middle_prompt"), true);
            }

            // Key binding
            if (client.player != null && client.screen == null) {
                if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
                    client.setScreen(new FunctionScreen());
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

            // Trigger key: process after TriggerManager.tick() so activeScene is fresh.
            if (client.player != null && client.screen == null) {
                boolean triggerByBinding = ModKeyBindings.TRIGGER_PONDER.isDown();
                boolean triggerPressed = triggerByBinding;

                if (triggerPressed && !triggerKeyWasDown) {
                    TriggerManager.onTriggerKeyPressed();
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

            // JEI click fallback for Fabric: catches clicks that may bypass screen mouse callbacks.
            if (JeiCompat.isAvailable() && client.screen != null) {
                long window = client.getWindow().getWindow();
                boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                if (leftDown && !jeiLeftMouseWasDown) {
                    double mouseX = client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth();
                    double mouseY = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight();
                    PondererJeiPlugin.handleMouseClick(client.screen, mouseX, mouseY, 0);
                }
                jeiLeftMouseWasDown = leftDown;
            } else {
                jeiLeftMouseWasDown = false;
            }
        });

        // JEI click interception on screen open
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (JeiCompat.isAvailable()) {
                ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseX, mouseY, button) ->
                        !PondererJeiPlugin.handleMouseClick(scr, mouseX, mouseY, button));
            }
        });

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

        if (updates.isEmpty() && orphaned.isEmpty()) return;
        if (!Config.PACK_ORPHAN_PROMPT.get() && orphaned.isEmpty() && updates.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            var player = mc.player;
            if (player == null) return;

            for (SceneStore.PackUpdateInfo info : updates) {
                MutableComponent msg = Component.literal("[Ponderer] ")
                        .withStyle(Style.EMPTY.withColor(0xFFA500))
                        .append(Component.translatable("ponderer.pack.update.readonly_newer_source",
                                info.packName, info.newVersion, info.oldVersion)
                                .withStyle(Style.EMPTY.withColor(0x55FF55)));
                player.displayClientMessage(msg, false);
            }

            if (Config.PACK_ORPHAN_PROMPT.get()) {
                for (String packName : orphaned) {
                    MutableComponent msg = Component.literal("[Ponderer] ")
                            .withStyle(Style.EMPTY.withColor(0xFFA500))
                            .append(Component.literal(packName).withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                            .append(Component.literal(": ").withStyle(Style.EMPTY.withColor(0xAAAAAA)))
                            .append(Component.translatable("ponderer.pack.orphan.message")
                                    .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
                    player.displayClientMessage(msg, false);

                    MutableComponent removeBtn = Component.literal("  [")
                            .withStyle(Style.EMPTY.withColor(0x888888))
                            .append(Component.translatable("ponderer.pack.orphan.remove")
                                    .withStyle(Style.EMPTY
                                            .withColor(0xFF6666)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                                    "/ponderer unregister_pack " + packName))
                                            .withUnderlined(true)))
                            .append(Component.literal("]").withStyle(Style.EMPTY.withColor(0x888888)));
                    player.displayClientMessage(removeBtn, false);
                }
            }
        });
    }
}
