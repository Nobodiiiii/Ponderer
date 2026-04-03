package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = com.nododiiiii.ponderer.Ponderer.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ReplaySessionManager {
    private static final Map<UUID, ReplaySession> SESSIONS = new ConcurrentHashMap<>();

    private ReplaySessionManager() {
    }

    public static BlockPos getSandboxPos(ServerPlayer player) {
        long hi = player.getUUID().getMostSignificantBits();
        long lo = player.getUUID().getLeastSignificantBits();
        int hash = (int) (hi ^ lo ^ (hi >>> 32) ^ (lo >>> 32));
        int x = 2_000_000 + (hash & 0x3FFF) * 32;
        int z = 2_000_000 + ((hash >>> 14) & 0x3FFF) * 32;
        int y = player.serverLevel().getMinBuildHeight() + 4;
        return new BlockPos(x, y, z);
    }

    public static void beginSession(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState originalState, CompoundTag originalBeTag, int containerId) {
        SESSIONS.put(player.getUUID(), new ReplaySession(level.dimension(), pos.immutable(), originalState, originalBeTag, containerId));
    }

    public static void restoreSession(ServerPlayer player) {
        ReplaySession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }

        ServerLevel level = player.server.getLevel(session.dimension);
        if (level == null || !level.hasChunkAt(session.pos)) {
            return;
        }

        restoreBlock(level, session.pos, session.originalState, session.originalBeTag);
    }

    public static void restoreBlock(ServerLevel level, BlockPos pos, BlockState originalState, CompoundTag originalBeTag) {
        level.setBlock(pos, originalState, 0);
        if (originalBeTag != null) {
            BlockEntity restored = BlockEntity.loadStatic(pos, originalState, originalBeTag);
            if (restored != null) {
                level.setBlockEntity(restored);
            }
        } else {
            level.removeBlockEntity(pos);
        }
    }

    public static void disableReachabilityCheck(AbstractContainerMenu menu) {
        try {
            for (Field field : AbstractContainerMenu.class.getDeclaredFields()) {
                if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                field.setBoolean(menu, false);
            }
        } catch (ReflectiveOperationException ex) {
            StickSnapshotFeature.LOGGER.warn("Failed to disable menu reachability check for {}",
                    menu.getClass().getName(), ex);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ReplaySession session = SESSIONS.get(serverPlayer.getUUID());
            if (session != null && event.getContainer().containerId == session.containerId) {
                restoreSession(serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            restoreSession(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            restoreSession(serverPlayer);
        }
    }

    private static class ReplaySession {
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private final BlockState originalState;
        private final CompoundTag originalBeTag;
        private final int containerId;

        private ReplaySession(ResourceKey<Level> dimension, BlockPos pos, BlockState originalState, CompoundTag originalBeTag, int containerId) {
            this.dimension = dimension;
            this.pos = pos;
            this.originalState = originalState;
            this.originalBeTag = originalBeTag;
            this.containerId = containerId;
        }
    }
}
