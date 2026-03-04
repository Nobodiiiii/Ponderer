package com.nododiiiii.ponderer.forge;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.compat.jei.PondererJeiPlugin;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.registry.ModItems;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.ArrayList;
import java.util.List;

@Mod(Ponderer.MODID)
public class PondererForge {

    /** Orphaned pack names detected during startup. */
    private static List<String> pendingOrphanedPacks = new ArrayList<>();
    /** Pack updates detected during startup. */
    private static List<SceneStore.PackUpdateInfo> pendingPackUpdates = new ArrayList<>();

    public PondererForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Force static init of ModItems FIRST so all items are added to DeferredRegister
        // BEFORE init() registers the DeferredRegister to the mod event bus.
        // If ModItems class loads after RegisterEvent fires, Forge throws IllegalStateException.
        ModItems.init();
        // Now register DeferredRegister to event bus
        PondererServices.REGISTRATION.init();

        // Config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onBuildCreativeTab);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::onClientSetup);
            modEventBus.addListener(this::onRegisterKeyMappings);
            MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
            MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
            MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(this::onBlueprintClientTick);
            MinecraftForge.EVENT_BUS.addListener(this::onMouseScrolled);
            MinecraftForge.EVENT_BUS.addListener(this::onMouseInput);
            // JEI click interception
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, this::onScreenMouseClick);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        PondererServices.NETWORK.registerPackets();
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
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
            // The Ponder library's FMLLoadCompleteEvent will call PonderIndex.registerAll()
            // to perform the initial scene registration. Calling reload() here would cause
            // scenes to be registered twice (once by reload, once by registerAll).
            PonderConfig.Client().editingMode.set(false);
        });
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.OPEN_FUNCTION_PAGE);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PondererClientCommands.register(event.getDispatcher());
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                event.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
        }
    }

    // --- Client tick for key bindings ---
    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
            ScreenOpener.transitionTo(new FunctionScreen());
        }
    }

    // --- Blueprint events ---
    private static final com.nododiiiii.ponderer.blueprint.BlueprintHandler blueprintHandler =
            new com.nododiiiii.ponderer.blueprint.BlueprintHandler();

    {
        com.nododiiiii.ponderer.blueprint.BlueprintHandler.INSTANCE = blueprintHandler;
    }

    private void onBlueprintClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            blueprintHandler.tick();
        }
    }

    private void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (blueprintHandler.mouseScrolled(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    private void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (blueprintHandler.onMouseInput(event.getButton(), event.getAction() == 1)) {
            event.setCanceled(true);
        }
    }

    // --- JEI click interception ---
    private void onScreenMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (JeiCompat.isAvailable()) {
            if (PondererJeiPlugin.handleMouseClick(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }
    }

    // --- Player join notifications ---
    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(pendingPackUpdates);
        pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(pendingOrphanedPacks);
        pendingOrphanedPacks.clear();

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
