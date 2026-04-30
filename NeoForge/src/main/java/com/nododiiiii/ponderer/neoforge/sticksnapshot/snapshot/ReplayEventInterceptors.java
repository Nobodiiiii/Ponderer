package com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot;

import com.nododiiiii.ponderer.neoforge.sticksnapshot.StickSnapshotFeature;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

public final class ReplayEventInterceptors {
    private ReplayEventInterceptors() {
    }

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }

        ReplayGuard.auditBlocked("event", "advancement", player.getScoreboardName());
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }

        if (event.getAmount() != 0) {
            ReplayGuard.auditBlocked("event", "xpChange(" + event.getAmount() + ")", player.getScoreboardName());
            event.setAmount(0);
        }
    }

    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }

        event.setCanceled(true);
        ReplayGuard.auditBlocked("event", "xpPickup", player.getScoreboardName());
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }

        event.setCanPickup(TriState.FALSE);
        ReplayGuard.auditBlocked("event", "itemPickup", player.getScoreboardName());
    }

    @SubscribeEvent
    public static void onItemCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }
        ReplayGuard.auditBlocked("event", "itemCraft", player.getScoreboardName());
    }

    @SubscribeEvent
    public static void onItemSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }
        ReplayGuard.auditBlocked("event", "itemSmelt", player.getScoreboardName());
    }
}
