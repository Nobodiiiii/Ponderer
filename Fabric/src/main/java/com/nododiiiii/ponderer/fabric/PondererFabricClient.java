package com.nododiiiii.ponderer.fabric;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.blueprint.BlueprintHandler;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.compat.jei.PondererJeiPlugin;
import com.nododiiiii.ponderer.ponder.DynamicPonderPlugin;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.registry.ModItems;
import com.nododiiiii.ponderer.ui.FunctionScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.enums.PonderConfig;
import net.createmod.ponder.foundation.PonderIndex;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

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

    @Override
    public void onInitializeClient() {
        BlueprintHandler.INSTANCE = blueprintHandler;

        // Key bindings
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.OPEN_FUNCTION_PAGE);

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

        // Creative tab
        if (BlueprintFeature.shouldShowBlueprintInCreativeTab()) {
            ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
                entries.accept(new ItemStack(ModItems.BLUEPRINT.get()));
            });
        }

        // Client tick: key bindings + blueprint handler + player join notifications
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Key binding
            if (client.player != null && client.screen == null) {
                if (ModKeyBindings.OPEN_FUNCTION_PAGE.consumeClick()) {
                    ScreenOpener.transitionTo(new FunctionScreen());
                }
            }

            // Blueprint handler tick
            blueprintHandler.tick();

            // Player login notification (check once)
            if (!hasNotified && client.player != null) {
                hasNotified = true;
                showPendingNotifications();
            }
        });

        // JEI click interception on screen open
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (JeiCompat.isAvailable()) {
                ScreenMouseEvents.beforeMouseClick(screen).register((scr, mouseX, mouseY, button) -> {
                    PondererJeiPlugin.handleMouseClick(scr, mouseX, mouseY, button);
                });
            }
        });
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
                String versionChange = info.oldVersion != null
                        ? "v" + info.oldVersion + " → v" + info.newVersion
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
