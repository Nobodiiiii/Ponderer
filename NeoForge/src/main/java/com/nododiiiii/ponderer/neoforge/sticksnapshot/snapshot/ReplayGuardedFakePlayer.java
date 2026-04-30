package com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot;

import com.nododiiiii.ponderer.neoforge.sticksnapshot.StickSnapshotFeature;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stat;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FriendlyByteBufUtil;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalInt;
import java.util.function.Consumer;

public class ReplayGuardedFakePlayer extends FakePlayer {
    @Nullable
    private static final Method NEXT_CONTAINER_COUNTER = findMethod("nextContainerCounter");
    @Nullable
    private static final Method INIT_MENU = findMethod("initMenu", AbstractContainerMenu.class);
    @Nullable
    private static final Field CONTAINER_COUNTER = findField("containerCounter");

    public ReplayGuardedFakePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menuProvider) {
        return openMenu(menuProvider, (Consumer<RegistryFriendlyByteBuf>) null);
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menuProvider,
            @Nullable Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        if (menuProvider == null) {
            return OptionalInt.empty();
        }

        if (this.containerMenu != this.inventoryMenu) {
            if (menuProvider.shouldTriggerClientSideContainerClosingOnOpen()) {
                this.closeContainer();
            } else {
                this.doCloseContainer();
            }
        }

        if (!nextContainerCounter()) {
            return OptionalInt.empty();
        }

        int containerId = containerCounter();
        AbstractContainerMenu menu = menuProvider.createMenu(containerId, this.getInventory(), this);
        if (menu == null) {
            return OptionalInt.empty();
        }

        byte[] extraData = FriendlyByteBufUtil.writeCustomData(buffer -> {
            menuProvider.writeClientSideData(menu, buffer);
            if (extraDataWriter != null) {
                extraDataWriter.accept(buffer);
            }
        }, registryAccess());

        if (extraData.length != 0) {
            this.connection.send(new AdvancedOpenScreenPayload(menu.containerId, menu.getType(),
                    menuProvider.getDisplayName(), extraData));
        } else {
            this.connection.send(new ClientboundOpenScreenPacket(menu.containerId, menu.getType(),
                    menuProvider.getDisplayName()));
        }

        initMenu(menu);
        this.containerMenu = menu;
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(this, this.containerMenu));
        return OptionalInt.of(containerId);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void awardStat(Stat stat) {
        if (ReplayGuard.isActive()) {
            ReplayGuard.auditBlocked("api", "awardStat", getScoreboardName());
            return;
        }
        super.awardStat(stat);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void awardStat(Stat stat, int amount) {
        if (ReplayGuard.isActive()) {
            ReplayGuard.auditBlocked("api", "awardStat(" + amount + ")", getScoreboardName());
            return;
        }
        super.awardStat(stat, amount);
    }

    @Override
    public void giveExperiencePoints(int points) {
        if (ReplayGuard.isActive()) {
            ReplayGuard.auditBlocked("api", "giveExperience(" + points + ")", getScoreboardName());
            return;
        }
        super.giveExperiencePoints(points);
    }

    private boolean nextContainerCounter() {
        if (NEXT_CONTAINER_COUNTER == null) {
            StickSnapshotFeature.LOGGER.warn("Virtual replay cannot open menu: nextContainerCounter unavailable");
            return false;
        }
        try {
            NEXT_CONTAINER_COUNTER.invoke(this);
            return true;
        } catch (ReflectiveOperationException ex) {
            StickSnapshotFeature.LOGGER.warn("Virtual replay failed to advance menu counter", ex);
            return false;
        }
    }

    private int containerCounter() {
        if (CONTAINER_COUNTER == null) {
            return this.containerMenu.containerId;
        }
        try {
            return CONTAINER_COUNTER.getInt(this);
        } catch (ReflectiveOperationException ex) {
            StickSnapshotFeature.LOGGER.warn("Virtual replay failed to read menu counter", ex);
            return this.containerMenu.containerId;
        }
    }

    private void initMenu(AbstractContainerMenu menu) {
        if (INIT_MENU == null) {
            return;
        }
        try {
            INIT_MENU.invoke(this, menu);
        } catch (ReflectiveOperationException ex) {
            StickSnapshotFeature.LOGGER.warn("Virtual replay failed to initialize menu {}", menu.getClass().getName(), ex);
        }
    }

    @Nullable
    private static Method findMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = net.minecraft.server.level.ServerPlayer.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            StickSnapshotFeature.LOGGER.warn("Unable to access ServerPlayer#{}", name, ex);
            return null;
        }
    }

    @Nullable
    private static Field findField(String name) {
        try {
            Field field = net.minecraft.server.level.ServerPlayer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            StickSnapshotFeature.LOGGER.warn("Unable to access ServerPlayer#{}", name, ex);
            return null;
        }
    }
}
