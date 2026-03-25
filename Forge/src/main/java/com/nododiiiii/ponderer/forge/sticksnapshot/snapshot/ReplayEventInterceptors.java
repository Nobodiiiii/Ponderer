package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.nododiiiii.ponderer.Ponderer.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReplayEventInterceptors {
    private ReplayEventInterceptors() {
    }

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }

        if (event.isCancelable()) {
            event.setCanceled(true);
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

        if (event.isCancelable()) {
            event.setCanceled(true);
        }
        ReplayGuard.auditBlocked("event", "xpPickup", player.getScoreboardName());
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReplayGuard.shouldBlockPlayer(player)) {
            return;
        }

        if (event.isCancelable()) {
            event.setCanceled(true);
        }
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
