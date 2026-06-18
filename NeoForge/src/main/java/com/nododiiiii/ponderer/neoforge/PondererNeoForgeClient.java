package com.nododiiiii.ponderer.neoforge;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.compat.jei.PondererJeiPlugin;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.TriggerManager;
import com.nododiiiii.ponderer.neoforge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.projector.client.ProjectorBlockEntityRenderer;
import com.nododiiiii.ponderer.projector.client.ProjectorWorldOverlayQueue;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import com.nododiiiii.ponderer.registry.ModMenuTypes;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import com.nododiiiii.ponderer.ui.CoordPickState;
import com.nododiiiii.ponderer.ui.NbtPickState;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PondererNeoForgeClient::onScreenMouseClick);
        StickSnapshotFeature.onClientInit();
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
        if (NbtPickState.isActive()) {
            mc.player.displayClientMessage(Component.translatable("ponderer.ui.nbt_pick.middle_prompt"), true);
        }
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
        blueprintHandler.tick();
        TriggerManager.tick();
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

    // --- JEI click interception ---
    private static void onScreenMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (JeiCompat.isAvailable()) {
            if (PondererJeiPlugin.handleMouseClick(
                    event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }
    }

    // --- Player join notifications ---
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(PondererNeoForge.pendingPackUpdates);
        PondererNeoForge.pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(PondererNeoForge.pendingOrphanedPacks);
        PondererNeoForge.pendingOrphanedPacks.clear();

        if (updates.isEmpty() && orphaned.isEmpty()) return;
        if (!Config.PACK_ORPHAN_PROMPT.get() && orphaned.isEmpty() && updates.isEmpty()) return;

        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            for (SceneStore.PackUpdateInfo info : updates) {
                String versionChange = info.oldVersion != null
                        ? "v" + info.oldVersion + " \u2192 v" + info.newVersion
                        : "v" + info.newVersion;
                MutableComponent msg = Component.literal("[Ponderer] ")
                        .withStyle(Style.EMPTY.withColor(0xFFA500))
                        .append(Component.literal(info.packName).withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                        .append(Component.literal(": ").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
                if (info.oldVersion != null) {
                    msg.append(Component.translatable("ponderer.pack.update.version", versionChange)
                            .withStyle(Style.EMPTY.withColor(0x55FF55)));
                } else {
                    msg.append(Component.translatable("ponderer.pack.update.loaded", info.newVersion)
                            .withStyle(Style.EMPTY.withColor(0x55FF55)));
                }
                if (info.conflictCount > 0) {
                    msg.append(Component.literal(" ")
                            .append(Component.translatable("ponderer.pack.update.conflicts", info.conflictCount)
                                    .withStyle(Style.EMPTY.withColor(0xFFAA00))));
                }
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
