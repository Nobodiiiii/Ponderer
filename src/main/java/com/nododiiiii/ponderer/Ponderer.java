package com.nododiiiii.ponderer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModLoadingContext;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.registry.ModItems;
import com.nododiiiii.ponderer.network.PondererNetwork;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Mod(Ponderer.MODID)
public class Ponderer {
    public static final String MODID = "ponderer";

    /** Orphaned pack names detected during startup, to be shown on first world join. */
    private static List<String> pendingOrphanedPacks = new ArrayList<>();
    /** Pack updates detected during startup, to be shown on first world join. */
    private static List<SceneStore.PackUpdateInfo> pendingPackUpdates = new ArrayList<>();

    public Ponderer() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onBuildCreativeTab);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::onClientSetup);
            modEventBus.addListener(ModKeyBindings::register);
            MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
            MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        PondererNetwork.register();
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SceneStore.extractDefaultsIfNeeded();
            // Auto-load Ponderer packs from resourcepacks directory
            SceneStore.AutoLoadResult result = SceneStore.autoLoadPonderPacks();
            if (!result.orphanedPacks.isEmpty()) {
                pendingOrphanedPacks.addAll(result.orphanedPacks);
            }
            if (!result.updatedPacks.isEmpty()) {
                pendingPackUpdates.addAll(result.updatedPacks);
            }
            SceneStore.reloadFromDisk();
            PonderIndex.addPlugin(new DynamicPonderPlugin());
            PonderIndex.reload();
            // Ensure editing mode is off so Create's ponder text uses I18n (localized).
            // Ponderer's own text is handled by PonderLocalizationMixin regardless.
            PonderConfig.Client().editingMode.set(false);
        });
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Copy and clear pending notifications
        List<SceneStore.PackUpdateInfo> updates = new ArrayList<>(pendingPackUpdates);
        pendingPackUpdates.clear();
        List<String> orphaned = new ArrayList<>(pendingOrphanedPacks);
        pendingOrphanedPacks.clear();

        if (updates.isEmpty() && orphaned.isEmpty()) return;
        if (!Config.PACK_ORPHAN_PROMPT.get() && orphaned.isEmpty() && updates.isEmpty()) return;

        // Defer message to next tick so chat is ready
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            // Show pack update notifications
            for (SceneStore.PackUpdateInfo info : updates) {
                String versionChange = info.oldVersion != null
                        ? "v" + info.oldVersion + " → v" + info.newVersion
                        : "v" + info.newVersion;
                MutableComponent msg = Component.literal("[Ponderer] ")
                        .withStyle(Style.EMPTY.withColor(0xFFA500))
                        .append(Component.literal(info.packName)
                                .withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                        .append(Component.literal(": ")
                                .withStyle(Style.EMPTY.withColor(0xAAAAAA)));

                if (info.oldVersion != null) {
                    // Version update
                    msg.append(Component.translatable("ponderer.pack.update.version", versionChange)
                            .withStyle(Style.EMPTY.withColor(0x55FF55)));
                } else {
                    // First load
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

            // Show orphaned pack notifications
            if (Config.PACK_ORPHAN_PROMPT.get()) {
                for (String packName : orphaned) {
                    MutableComponent msg = Component.literal("[Ponderer] ")
                            .withStyle(Style.EMPTY.withColor(0xFFA500))
                            .append(Component.literal(packName)
                                    .withStyle(Style.EMPTY.withColor(0xFFFFFF)))
                            .append(Component.literal(": ")
                                    .withStyle(Style.EMPTY.withColor(0xAAAAAA)))
                            .append(Component.translatable("ponderer.pack.orphan.message")
                                    .withStyle(Style.EMPTY.withColor(0xAAAAAA)));
                    player.displayClientMessage(msg, false);

                    MutableComponent removeBtn = Component.literal("  [")
                            .withStyle(Style.EMPTY.withColor(0x888888))
                            .append(Component.translatable("ponderer.pack.orphan.remove")
                                    .withStyle(Style.EMPTY
                                            .withColor(0xFF6666)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ponderer unregister_pack " + packName))
                                            .withUnderlined(true)))
                            .append(Component.literal("]")
                                    .withStyle(Style.EMPTY.withColor(0x888888)));
                    player.displayClientMessage(removeBtn, false);
                }
            }
        });
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        PondererClientCommands.register(event);
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
                event.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            }
        }
    }
}
