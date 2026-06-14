package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.compat.jei.PondererJeiPlugin;
import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.TriggerManager;
import com.nododiiiii.ponderer.ui.CoordPickState;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import com.nododiiiii.ponderer.ui.NbtPickState;
import com.nododiiiii.ponderer.ui.ProjectorAnchorPickState;
import com.nododiiiii.ponderer.projector.client.ProjectorBlockEntityRenderer;
import com.nododiiiii.ponderer.projector.client.ProjectorWorldOverlayQueue;
import com.nododiiiii.ponderer.registry.ModBlockEntities;
import com.nododiiiii.ponderer.registry.ModMenuTypes;
import com.nododiiiii.ponderer.ui.ProjectorConfigScreen;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only event handlers for the Forge platform.
 * This class is only loaded when running on Dist.CLIENT,
 * keeping client-only class references away from the dedicated server.
 */
public class PondererForgeClient {

    private static final BlueprintHandler blueprintHandler = new BlueprintHandler();

    static void init(IEventBus modEventBus) {
        BlueprintHandler.INSTANCE = blueprintHandler;
        StickSnapshotFeature.onClientInit();

        modEventBus.addListener(PondererForgeClient::onClientSetup);
        modEventBus.addListener(PondererForgeClient::onRegisterKeyMappings);
        modEventBus.addListener(PondererForgeClient::onLoadComplete);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onBlueprintClientTick);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onMouseScrolled);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onMouseInput);
        MinecraftForge.EVENT_BUS.addListener(PondererForgeClient::onRenderLevelStage);
        // JEI click interception
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, PondererForgeClient::onScreenMouseClick);
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
            // The Ponder library's FMLLoadCompleteEvent will call PonderIndex.registerAll()
            // to perform the initial scene registration. Calling reload() here would cause
            // scenes to be registered twice (once by reload, once by registerAll).
            PonderConfig.Client().editingMode.set(false);
            BlockEntityRenderers.register(ModBlockEntities.PROJECTOR.get(), ctx -> new ProjectorBlockEntityRenderer());
            MenuScreens.register(ModMenuTypes.PROJECTOR.get(), ProjectorConfigScreen::new);
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (var keyMapping : ModKeyBindings.all()) {
            event.register(keyMapping);
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PondererClientCommands.register(event.getDispatcher());
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        ModContainer modContainer = ModList.get()
            .getModContainerById(Ponderer.MODID)
            .orElseThrow(() -> new IllegalStateException("Ponderer mod container missing after loadComplete"));
        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(
                (minecraft, previousScreen) -> new BaseConfigScreen(previousScreen, Ponderer.MODID)));

        BaseConfigScreen.setDefaultActionFor(Ponderer.MODID,
            base -> base.withSpecs(Config.CLIENT_SPEC, null, Config.SERVER_SPEC));
    }

    // --- Client tick for key bindings ---
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (NbtPickState.isActive()) {
            mc.player.displayClientMessage(Component.translatable("ponderer.ui.nbt_pick.middle_prompt"), true);
        }
        if (ProjectorAnchorPickState.isActive()) {
            ProjectorAnchorPickState.onClientTick();
        }
        if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
            mc.setScreen(new FunctionScreen());
        }
    }

    // --- Blueprint events ---
    private static void onBlueprintClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            blueprintHandler.tick();
            TriggerManager.tick();
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

        if (ProjectorAnchorPickState.isActive() && event.getButton() == 2 && event.getAction() == 1) {
            ProjectorAnchorPickState.handleMiddleClick();
            event.setCanceled(true);
            return;
        }

        if (blueprintHandler.onMouseInput(event.getButton(), event.getAction() == 1)) {
            event.setCanceled(true);
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ProjectorWorldOverlayQueue.beginFrame();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        ProjectorWorldOverlayQueue.render();
    }

    // --- JEI click interception ---
    private static void onScreenMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (JeiCompat.isAvailable()) {
            if (PondererJeiPlugin.handleMouseClick(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }
    }

    // --- Player join notifications ---
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(PondererForge.pendingPackUpdates);
        PondererForge.pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(PondererForge.pendingOrphanedPacks);
        PondererForge.pendingOrphanedPacks.clear();

        if (updates.isEmpty() && orphaned.isEmpty()) return;
        if (!Config.PACK_ORPHAN_PROMPT.get() && orphaned.isEmpty() && updates.isEmpty()) return;

        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
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
