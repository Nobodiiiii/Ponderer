package com.nododiiiii.ponderer.forge.sticksnapshot.client;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.MirrorForgeOpenPacket;
import io.netty.buffer.Unpooled;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MirrorForgeOpenClient {
    private static InjectedClientBlock injectedClientBlock;
    private static final UUID SHADOW_PLAYER_UUID = UUID.nameUUIDFromBytes(
            "ponderer:mirror-shadow-player".getBytes(StandardCharsets.UTF_8));
    private static final GameProfile SHADOW_PROFILE = new GameProfile(SHADOW_PLAYER_UUID, "PondererShadow");

    private MirrorForgeOpenClient() {
    }

    public static void open(MirrorForgeOpenPacket msg) {
        try {
            Minecraft mc = Minecraft.getInstance();
            StickSnapshotFeature.LOGGER.debug(
                    "[client][mirror-debug] forge-open enter windowId={} menuTypeId={} title={} playerNull={} screen={}",
                    msg.windowId(), msg.menuTypeId(), msg.title().getString(), mc.player == null,
                    mc.screen == null ? "null" : mc.screen.getClass().getName());

            if (mc.player == null) {
                return;
            }

            MenuType<?> menuType = BuiltInRegistries.MENU.byId(msg.menuTypeId());
            String menuKey = String.valueOf(menuType == null ? null : BuiltInRegistries.MENU.getKey(menuType));
            if (menuType == null) {
                StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] forge-open failed: unknown menuTypeId={}",
                        msg.menuTypeId());
                return;
            }

            ensureClientTileContext(msg);

            FriendlyByteBuf extraData = new FriendlyByteBuf(Unpooled.wrappedBuffer(msg.extraData()));
            MenuScreens.getScreenFactory(menuType, mc, msg.windowId(), msg.title()).ifPresentOrElse(screenFactory -> {
                Inventory shadowInventory = createShadowInventory(mc);
                AbstractContainerMenu menu = menuType.create(msg.windowId(), shadowInventory, extraData);
                if (menu == null) {
                    StickSnapshotFeature.LOGGER.debug(
                            "[client][mirror-debug] forge-open failed: menu factory returned null windowId={} key={}",
                            msg.windowId(), menuKey);
                    return;
                }
                ClientInputHandler.bindMirrorMenu(menu);

                @SuppressWarnings("unchecked")
                Screen screen = ((MenuScreens.ScreenConstructor<AbstractContainerMenu, ?>) screenFactory)
                    .create(menu, shadowInventory, msg.title());
                if (mc.screen instanceof PonderUI) {
                    ClientInputHandler.attachMirrorToPonder(screen);
                } else {
                    mc.setScreen(screen);
                }
                StickSnapshotFeature.LOGGER.debug(
                        "[client][mirror-debug] forge-open success windowId={} key={} screen={} extraBytes={}",
                        msg.windowId(), menuKey, screen.getClass().getName(), msg.extraData().length);
            }, () -> StickSnapshotFeature.LOGGER.debug(
                    "[client][mirror-debug] forge-open failed: no screen factory windowId={} key={}",
                    msg.windowId(), menuKey));
        } catch (Exception ex) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos requestedPos = readFirstBlockPos(msg.extraData());
            BlockEntity be = mc.level != null && requestedPos != null ? mc.level.getBlockEntity(requestedPos) : null;
            StickSnapshotFeature.LOGGER.debug(
                    "[client][mirror-debug] forge-open exception: {} requestedPos={} bePresent={} state={}",
                    ex.toString(), requestedPos, be != null,
                    mc.level != null && requestedPos != null ? mc.level.getBlockState(requestedPos) : null);
        }
    }

    public static void restoreInjectedBlock() {
        try {
            if (injectedClientBlock == null) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                injectedClientBlock = null;
                return;
            }

            mc.level.setBlock(injectedClientBlock.pos, injectedClientBlock.originalState, 0);
            if (injectedClientBlock.originalBlockEntityTag != null) {
                BlockEntity restored = BlockEntity.loadStatic(injectedClientBlock.pos, injectedClientBlock.originalState,
                        injectedClientBlock.originalBlockEntityTag);
                if (restored != null) {
                    mc.level.setBlockEntity(restored);
                }
            } else {
                mc.level.removeBlockEntity(injectedClientBlock.pos);
            }
            StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] restored injected client block at {}",
                    injectedClientBlock.pos);
        } catch (Exception ex) {
            StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] restore injected block failed: {}", ex.toString());
        } finally {
            injectedClientBlock = null;
        }
    }

    private static void ensureClientTileContext(MirrorForgeOpenPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || msg.extraData().length < Long.BYTES) {
            return;
        }

        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(msg.extraData()));
        BlockPos pos = BlockPos.of(data.readLong());
        if (mc.level.getBlockEntity(pos) != null) {
            return;
        }

        BlockState originalState = mc.level.getBlockState(pos);
        BlockEntity originalBe = mc.level.getBlockEntity(pos);
        CompoundTag originalBeTag = originalBe != null ? originalBe.saveWithFullMetadata() : null;

        BlockState snapshotState = Block.stateById(msg.snapshotStateId());
        mc.level.setBlock(pos, snapshotState, 0);
        if (msg.snapshotBlockEntityTag() != null) {
            CompoundTag beTag = msg.snapshotBlockEntityTag().copy();
            int beforeX = beTag.contains("x") ? beTag.getInt("x") : Integer.MIN_VALUE;
            int beforeY = beTag.contains("y") ? beTag.getInt("y") : Integer.MIN_VALUE;
            int beforeZ = beTag.contains("z") ? beTag.getInt("z") : Integer.MIN_VALUE;
            beTag.putInt("x", pos.getX());
            beTag.putInt("y", pos.getY());
            beTag.putInt("z", pos.getZ());
            BlockEntity replayBe = BlockEntity.loadStatic(pos, snapshotState, beTag);
            if (replayBe != null) {
                mc.level.setBlockEntity(replayBe);
            }
            StickSnapshotFeature.LOGGER.debug(
                    "[client][mirror-debug] normalized injected be tag pos {} -> {}", 
                    new BlockPos(beforeX == Integer.MIN_VALUE ? pos.getX() : beforeX,
                            beforeY == Integer.MIN_VALUE ? pos.getY() : beforeY,
                            beforeZ == Integer.MIN_VALUE ? pos.getZ() : beforeZ),
                    pos);
        }

        injectedClientBlock = new InjectedClientBlock(pos, originalState, originalBeTag);
        StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] injected client block context at {} state={} hasBeTag={}",
                pos, snapshotState, msg.snapshotBlockEntityTag() != null);
    }

    private static BlockPos readFirstBlockPos(byte[] extraData) {
        if (extraData.length < Long.BYTES) {
            return null;
        }
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(extraData));
        return BlockPos.of(data.readLong());
    }

    private static Inventory createShadowInventory(Minecraft mc) {
        RemotePlayer shadowPlayer = new RemotePlayer(mc.level, SHADOW_PROFILE);
        shadowPlayer.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        shadowPlayer.setYRot(mc.player.getYRot());
        shadowPlayer.setXRot(mc.player.getXRot());
        return new Inventory(shadowPlayer);
    }

    private record InjectedClientBlock(BlockPos pos, BlockState originalState, CompoundTag originalBlockEntityTag) {
    }
}
