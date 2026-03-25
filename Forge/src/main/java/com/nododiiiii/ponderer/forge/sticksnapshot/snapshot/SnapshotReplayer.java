package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.MirrorForgeOpenPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ModNetworking;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class SnapshotReplayer {
    private static final AtomicInteger MIRROR_CONTAINER_COUNTER = new AtomicInteger(100);

    private SnapshotReplayer() {
    }

    public static void replay(ServerPlayer player, BlockSnapshot snapshot) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = ReplaySessionManager.getSandboxPos(player);
        level.getChunkAt(pos);
        StickSnapshotFeature.LOGGER.debug("[server] replay begin player={} sandboxPos={} srcBlock={} srcPos={} srcDim={}",
                player.getScoreboardName(), pos, snapshot.getBlockId(), snapshot.getPos(), snapshot.getDimensionId());

        // Always restore any previous replay context before opening a new one.
        ReplaySessionManager.restoreSession(player);

        BlockState snapshotState = Block.stateById(snapshot.getStateId());
        BlockState originalState = level.getBlockState(pos);
        BlockEntity originalBe = level.getBlockEntity(pos);
        CompoundTag originalBeTag = null;
        if (originalBe != null) {
            originalBeTag = originalBe.saveWithFullMetadata();
        }
        List<SandboxInjectedBlock> sandboxInjectedBlocks = injectReferencedContextBlocks(level, snapshot, pos);
        List<SandboxInjectedBlock> sandboxClearedAirBlocks = clearSandboxNeighborsToAir(level, pos);
        List<GuardedBlock> sourceGuard = captureSourceGuard(level, snapshot);

        boolean keepSandbox = false;
        try {
            level.setBlock(pos, snapshotState, 0);
            if (snapshot.getBlockEntityTag() != null) {
                CompoundTag normalizedBeTag = normalizeBlockEntityTagForPos(
                        snapshot.getBlockEntityTag(), snapshot.getPos(), pos, "server-sandbox");
                BlockEntity replayBe = BlockEntity.loadStatic(pos, snapshotState, normalizedBeTag);
                if (replayBe != null) {
                    level.setBlockEntity(replayBe);
                }
            }

            Vec3 hitOffset = snapshot.getHitLocation().subtract(Vec3.atLowerCornerOf(snapshot.getPos()));
            Vec3 sandboxHit = Vec3.atLowerCornerOf(pos).add(hitOffset);
            BlockHitResult hitResult = new BlockHitResult(sandboxHit, snapshot.getFace(), pos, snapshot.isInside());

            MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
            Component menuTitle = provider != null ? provider.getDisplayName()
                    : Component.literal(snapshot.getBlockId().toString());

            CapturedPackets capturedPackets = runVirtualUse(level, player, snapshotState, hitResult, sandboxHit);
            if (capturedPackets != null && !capturedPackets.packets.isEmpty()) {
                mirrorCapturedPacketsToRealPlayer(player, level, capturedPackets, menuTitle, snapshot);
                StickSnapshotFeature.LOGGER.debug("[server] replay success (captured-packet mirror) player={} packetCount={}",
                        player.getScoreboardName(), capturedPackets.packets.size());
            }
        } finally {
            if (!sourceGuard.isEmpty()) {
                logGuardedAreaMutations(level, sourceGuard, player.getScoreboardName());
            }
            if (!sandboxClearedAirBlocks.isEmpty()) {
                restoreInjectedContextBlocks(level, sandboxClearedAirBlocks);
            }
            if (!sandboxInjectedBlocks.isEmpty()) {
                restoreInjectedContextBlocks(level, sandboxInjectedBlocks);
            }
            if (!keepSandbox) {
                ReplaySessionManager.restoreBlock(level, pos, originalState, originalBeTag);
                StickSnapshotFeature.LOGGER.debug("[server] replay finished without menu, sandbox restored for player={}",
                        player.getScoreboardName());
            }
        }
    }

    private static List<SandboxInjectedBlock> injectReferencedContextBlocks(ServerLevel level, BlockSnapshot snapshot,
            BlockPos sandboxPos) {
        CompoundTag snapshotBeTag = snapshot.getBlockEntityTag();
        if (snapshotBeTag == null || !level.dimension().location().equals(snapshot.getDimensionId())) {
            return List.of();
        }

        int dx = sandboxPos.getX() - snapshot.getPos().getX();
        int dy = sandboxPos.getY() - snapshot.getPos().getY();
        int dz = sandboxPos.getZ() - snapshot.getPos().getZ();

        Set<BlockPos> referencedSourcePositions = new LinkedHashSet<>();
        collectEmbeddedPositions(snapshotBeTag, referencedSourcePositions);
        referencedSourcePositions.remove(snapshot.getPos());

        if (referencedSourcePositions.isEmpty()) {
            return List.of();
        }

        List<SandboxInjectedBlock> injected = new ArrayList<>();
        int injectedCount = 0;
        for (BlockPos sourcePos : referencedSourcePositions) {
            if (injectedCount >= 8) {
                break;
            }
            if (!level.hasChunkAt(sourcePos)) {
                continue;
            }

            BlockPos targetPos = sourcePos.offset(dx, dy, dz);
            if (targetPos.equals(sandboxPos)) {
                continue;
            }

            BlockState oldState = level.getBlockState(targetPos);
            BlockEntity oldBe = level.getBlockEntity(targetPos);
            CompoundTag oldBeTag = oldBe != null ? oldBe.saveWithFullMetadata() : null;
            injected.add(new SandboxInjectedBlock(targetPos.immutable(), oldState, oldBeTag));

            BlockState sourceState = level.getBlockState(sourcePos);
            BlockEntity sourceBe = level.getBlockEntity(sourcePos);
            CompoundTag sourceBeTag = sourceBe != null ? sourceBe.saveWithFullMetadata() : null;

            level.setBlock(targetPos, sourceState, 0);
            if (sourceBeTag != null) {
                CompoundTag normalizedBeTag = normalizeBlockEntityTagForPos(sourceBeTag, sourcePos, targetPos,
                        "server-context");
                BlockEntity copiedBe = BlockEntity.loadStatic(targetPos, sourceState, normalizedBeTag);
                if (copiedBe != null) {
                    level.setBlockEntity(copiedBe);
                }
            } else {
                level.removeBlockEntity(targetPos);
            }

            StickSnapshotFeature.LOGGER.debug(
                    "[server][mirror-debug] injected sandbox context block sourcePos={} targetPos={} state={} hasBeTag={}",
                    sourcePos, targetPos, sourceState, sourceBeTag != null);
            injectedCount++;
        }

        return injected;
    }

    private static void restoreInjectedContextBlocks(ServerLevel level, List<SandboxInjectedBlock> injectedBlocks) {
        for (SandboxInjectedBlock injected : injectedBlocks) {
            ReplaySessionManager.restoreBlock(level, injected.pos(), injected.originalState(), injected.originalBeTag());
        }
    }

    private static List<SandboxInjectedBlock> clearSandboxNeighborsToAir(ServerLevel level, BlockPos sandboxPos) {
        List<SandboxInjectedBlock> cleared = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = sandboxPos.relative(direction);
            BlockState oldState = level.getBlockState(targetPos);
            BlockEntity oldBe = level.getBlockEntity(targetPos);
            CompoundTag oldBeTag = oldBe != null ? oldBe.saveWithFullMetadata() : null;
            cleared.add(new SandboxInjectedBlock(targetPos.immutable(), oldState, oldBeTag));

            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 0);
            level.removeBlockEntity(targetPos);
        }

        StickSnapshotFeature.LOGGER.debug("[server][mirror-debug] sandbox neighbors set to air center={} count={}",
                sandboxPos, cleared.size());
        return cleared;
    }

    private static List<GuardedBlock> captureSourceGuard(ServerLevel level, BlockSnapshot snapshot) {
        if (!level.dimension().location().equals(snapshot.getDimensionId())) {
            return List.of();
        }

        BlockPos center = snapshot.getPos();
        List<GuardedBlock> guarded = new ArrayList<>();
        int radius = 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos samplePos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(samplePos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(samplePos);
                    BlockEntity be = level.getBlockEntity(samplePos);
                    CompoundTag beTag = be != null ? be.saveWithFullMetadata() : null;
                    guarded.add(new GuardedBlock(samplePos.immutable(), state, beTag));
                }
            }
        }
        StickSnapshotFeature.LOGGER.debug(
                "[server][mirror-debug] captured source guard around snapshotPos={} entries={}",
                snapshot.getPos(), guarded.size());
        return guarded;
    }

    private static void logGuardedAreaMutations(ServerLevel level, List<GuardedBlock> guarded, String playerName) {
        int mutatedCount = 0;
        for (GuardedBlock guard : guarded) {
            if (!level.hasChunkAt(guard.pos())) {
                continue;
            }
            BlockState currentState = level.getBlockState(guard.pos());
            BlockEntity currentBe = level.getBlockEntity(guard.pos());
            CompoundTag currentBeTag = currentBe != null ? currentBe.saveWithFullMetadata() : null;

            boolean stateChanged = !currentState.equals(guard.state());
            boolean beChanged = !Objects.equals(currentBeTag, guard.beTag());
            if (!stateChanged && !beChanged) {
                continue;
            }

            mutatedCount++;
            StickSnapshotFeature.LOGGER.debug(
                    "[server][mirror-debug] detected source mutation player={} pos={} stateChanged={} beChanged={} beforeState={} afterState={}",
                    playerName, guard.pos(), stateChanged, beChanged, currentState, guard.state());
        }

        if (mutatedCount > 0) {
            StickSnapshotFeature.LOGGER.debug(
                    "[server][mirror-debug] source guard observed mutations player={} mutatedCount={}",
                    playerName, mutatedCount);
        }
    }

    private static CapturedPackets runVirtualUse(ServerLevel level, ServerPlayer realPlayer,
            BlockState snapshotState, BlockHitResult hitResult, Vec3 sandboxHit) {
        try {
            // Keep fake UUID tied to real player for mod-side security checks that key off player identity.
            UUID fakeId = UUID
                    .nameUUIDFromBytes(("sticksnapshot:" + realPlayer.getUUID()).getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(fakeId, "stick_snapshot_virtual");
            try (ReplayGuard.Scope ignored = ReplayGuard.begin(realPlayer, fakeId, "virtual-use")) {
                FakePlayer fakePlayer = new ReplayGuardedFakePlayer(level, profile);
                if (fakePlayer.containerMenu != fakePlayer.inventoryMenu) {
                    fakePlayer.closeContainer();
                }

                ServerGamePacketListenerImpl oldConnection = fakePlayer.connection;
                CapturingConnection captureConnection = new CapturingConnection();
                new ServerGamePacketListenerImpl(level.getServer(), captureConnection, fakePlayer);

                fakePlayer.setPos(sandboxHit.x, sandboxHit.y, sandboxHit.z);
                fakePlayer.setYRot(realPlayer.getYRot());
                fakePlayer.setXRot(realPlayer.getXRot());
                fakePlayer.setShiftKeyDown(realPlayer.isShiftKeyDown());

                ItemStack oldMainHand = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND);
                fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                try {
                    InteractionResult result = snapshotState.use(level, fakePlayer, InteractionHand.MAIN_HAND, hitResult);
                    AbstractContainerMenu fakeMenu = fakePlayer.containerMenu != fakePlayer.inventoryMenu
                            ? fakePlayer.containerMenu
                            : null;
                    StickSnapshotFeature.LOGGER.debug("[server] virtual use result={} fakeMenuOpened={}",
                            result, fakeMenu != null);

                    List<Packet<?>> packets = captureConnection.snapshot();
                    logCapturedPackets(realPlayer, packets);
                    if (fakeMenu == null || packets.isEmpty()) {
                        StickSnapshotFeature.LOGGER.debug(
                                "[server][mirror-debug] no mirrorable packets, player={} fakeMenuOpened={} packetCount={}",
                                realPlayer.getScoreboardName(), fakeMenu != null, packets.size());
                        return null;
                    }

                    int sourceContainerId = detectContainerId(packets);
                    return new CapturedPackets(packets, sourceContainerId);
                } finally {
                    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, oldMainHand);
                    if (fakePlayer.containerMenu != fakePlayer.inventoryMenu) {
                        fakePlayer.closeContainer();
                    }
                    fakePlayer.connection = oldConnection;
                }
            }
        } catch (Exception ex) {
            StickSnapshotFeature.LOGGER.debug("[server] virtual use failed: {}", ex.toString());
            return null;
        }
    }

            private static void mirrorCapturedPacketsToRealPlayer(ServerPlayer realPlayer, ServerLevel level,
                CapturedPackets captured, Component title, BlockSnapshot snapshot) {
        List<Packet<?>> packets = captured.packets;
        int sourceContainerId = captured.sourceContainerId;
        boolean hasForgePlayPayload = hasForgePlayPayload(packets);
                BlockPos clientVirtualPos = getClientVirtualPos(realPlayer);

        int targetContainerId = sourceContainerId;
        if (!hasForgePlayPayload && sourceContainerId >= 0) {
            targetContainerId = nextMirrorContainerId();
        }

        int mirrored = 0;
        for (Packet<?> packet : packets) {
            if (packet instanceof ClientboundCustomPayloadPacket customPayload) {
                MirrorForgeOpenPacket forgeOpenPacket = decodeForgeOpenPacket(level, customPayload, snapshot,
                        clientVirtualPos);
                if (forgeOpenPacket != null) {
                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> realPlayer), forgeOpenPacket);
                    StickSnapshotFeature.LOGGER.debug(
                            "[server][mirror-debug] forwarding forge-open via mod channel player={} windowId={} menuTypeId={} virtualPos={} stateId={} title={}",
                            realPlayer.getScoreboardName(), forgeOpenPacket.windowId(), forgeOpenPacket.menuTypeId(),
                            clientVirtualPos, forgeOpenPacket.snapshotStateId(), forgeOpenPacket.title().getString());
                    mirrored++;
                    continue;
                }
            }

            if (!hasForgePlayPayload && packet instanceof ClientboundOpenScreenPacket openPacket) {
                int mappedWindowId = remapContainerId(openPacket.getContainerId(), sourceContainerId, targetContainerId);
                MirrorForgeOpenPacket vanillaMirrorOpen = createVanillaMirrorOpenPacket(openPacket, snapshot,
                        clientVirtualPos, mappedWindowId);
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> realPlayer), vanillaMirrorOpen);
                StickSnapshotFeature.LOGGER.debug(
                        "[server][mirror-debug] forwarding vanilla-open via mod channel player={} windowId={} menuTypeId={} virtualPos={} stateId={} title={}",
                        realPlayer.getScoreboardName(), vanillaMirrorOpen.windowId(), vanillaMirrorOpen.menuTypeId(),
                        clientVirtualPos, vanillaMirrorOpen.snapshotStateId(), vanillaMirrorOpen.title().getString());
                mirrored++;
                continue;
            }

            Packet<?> packetToSend = remapMenuPacketContainerId(packet, sourceContainerId, targetContainerId);
            if (packetToSend != null) {
                StickSnapshotFeature.LOGGER.debug("[server][mirror-debug] forwarding packet player={} mapped={} -> {}",
                        realPlayer.getScoreboardName(), describePacket(packet),
                        describePacket(packetToSend));
                realPlayer.connection.send(packetToSend);
                mirrored++;
            }
        }

        if (mirrored == 0) {
            return;
        }

        StickSnapshotFeature.LOGGER.debug(
                "[server] mirrored captured packets to player={} sourceContainerId={} targetContainerId={} packetCount={} forgePlayPayload={} title={}",
                realPlayer.getScoreboardName(), sourceContainerId, targetContainerId, mirrored, hasForgePlayPayload,
                title.getString());
    }

    private static boolean hasForgePlayPayload(List<Packet<?>> packets) {
        for (Packet<?> packet : packets) {
            if (packet instanceof ClientboundCustomPayloadPacket customPayload
                    && "fml:play".equals(customPayload.getIdentifier().toString())) {
                return true;
            }
        }
        return false;
    }

    private static void logCapturedPackets(ServerPlayer player, List<Packet<?>> packets) {
        if (packets.isEmpty()) {
            StickSnapshotFeature.LOGGER.debug("[server][mirror-debug] captured no packets for player={}",
                    player.getScoreboardName());
            return;
        }
        StickSnapshotFeature.LOGGER.debug("[server][mirror-debug] captured packet count={} player={}",
                packets.size(), player.getScoreboardName());
        for (int i = 0; i < packets.size(); i++) {
            StickSnapshotFeature.LOGGER.debug("[server][mirror-debug] captured[{}] {}",
                    i, describePacket(packets.get(i)));
        }
    }

    private static String describePacket(Packet<?> packet) {
        if (packet == null) {
            return "null";
        }
        String base = packet.getClass().getSimpleName();
        if (packet instanceof ClientboundOpenScreenPacket openPacket) {
            return base + "{containerId=" + openPacket.getContainerId() + ",menuType=" + openPacket.getType() +
                    ",title=" + openPacket.getTitle().getString() + "}";
        }
        if (packet instanceof ClientboundContainerSetSlotPacket setSlotPacket) {
            return base + "{containerId=" + setSlotPacket.getContainerId() + ",stateId=" +
                    setSlotPacket.getStateId() + ",slot=" + setSlotPacket.getSlot() + ",item=" +
                    setSlotPacket.getItem() + "}";
        }
        if (packet instanceof ClientboundContainerSetContentPacket setContentPacket) {
            return base + "{containerId=" + setContentPacket.getContainerId() + ",stateId=" +
                    setContentPacket.getStateId() + ",items=" + setContentPacket.getItems().size() + "}";
        }
        if (packet instanceof ClientboundContainerSetDataPacket setDataPacket) {
            return base + "{containerId=" + setDataPacket.getContainerId() + ",id=" + setDataPacket.getId() +
                    ",value=" + setDataPacket.getValue() + "}";
        }
        if (packet instanceof ClientboundContainerClosePacket closePacket) {
            return base + "{containerId=" + closePacket.getContainerId() + "}";
        }
        if (packet instanceof ClientboundHorseScreenOpenPacket horsePacket) {
            return base + "{containerId=" + horsePacket.getContainerId() + ",size=" + horsePacket.getSize() +
                    ",entityId=" + horsePacket.getEntityId() + "}";
        }
        if (packet instanceof ClientboundMerchantOffersPacket offersPacket) {
            return base + "{containerId=" + offersPacket.getContainerId() + ",offers=" +
                    offersPacket.getOffers().size() + ",level=" + offersPacket.getVillagerLevel() + "}";
        }
        if (packet instanceof ClientboundCustomPayloadPacket customPayload) {
            FriendlyByteBuf data = customPayload.getData();
            int readable = data.readableBytes();
            byte[] head = new byte[Math.min(24, readable)];
            data.readBytes(head);
            return base + "{channel=" + customPayload.getIdentifier() + ",bytes=" + readable +
                    ",head=" + toHex(head) + "}";
        }
        return base;
    }

    private static String toHex(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int value = b & 0xFF;
            if (value < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(value));
        }
        return sb.toString();
    }

    private static Packet<?> remapMenuPacketContainerId(Packet<?> packet, int sourceContainerId, int targetContainerId) {
        if (packet instanceof ClientboundOpenScreenPacket openPacket) {
            int newContainerId = remapContainerId(openPacket.getContainerId(), sourceContainerId, targetContainerId);
            return new ClientboundOpenScreenPacket(newContainerId, openPacket.getType(), openPacket.getTitle());
        }
        if (packet instanceof ClientboundContainerSetSlotPacket setSlotPacket) {
            int original = setSlotPacket.getContainerId();
            int newContainerId = original < 0 ? original : remapContainerId(original, sourceContainerId, targetContainerId);
            return new ClientboundContainerSetSlotPacket(newContainerId, setSlotPacket.getStateId(),
                    setSlotPacket.getSlot(), setSlotPacket.getItem());
        }
        if (packet instanceof ClientboundContainerSetContentPacket setContentPacket) {
            int newContainerId = remapContainerId(setContentPacket.getContainerId(), sourceContainerId, targetContainerId);
            NonNullList<ItemStack> copied = NonNullList.withSize(setContentPacket.getItems().size(), ItemStack.EMPTY);
            for (int i = 0; i < setContentPacket.getItems().size(); i++) {
                copied.set(i, setContentPacket.getItems().get(i).copy());
            }
            return new ClientboundContainerSetContentPacket(newContainerId, setContentPacket.getStateId(), copied,
                    setContentPacket.getCarriedItem());
        }
        if (packet instanceof ClientboundContainerSetDataPacket setDataPacket) {
            int newContainerId = remapContainerId(setDataPacket.getContainerId(), sourceContainerId, targetContainerId);
            return new ClientboundContainerSetDataPacket(newContainerId, setDataPacket.getId(), setDataPacket.getValue());
        }
        if (packet instanceof ClientboundContainerClosePacket closePacket) {
            int newContainerId = remapContainerId(closePacket.getContainerId(), sourceContainerId, targetContainerId);
            return new ClientboundContainerClosePacket(newContainerId);
        }
        if (packet instanceof ClientboundHorseScreenOpenPacket horsePacket) {
            int newContainerId = remapContainerId(horsePacket.getContainerId(), sourceContainerId, targetContainerId);
            return new ClientboundHorseScreenOpenPacket(newContainerId, horsePacket.getSize(), horsePacket.getEntityId());
        }
        if (packet instanceof ClientboundMerchantOffersPacket offersPacket) {
            int newContainerId = remapContainerId(offersPacket.getContainerId(), sourceContainerId, targetContainerId);
            return new ClientboundMerchantOffersPacket(newContainerId, offersPacket.getOffers(),
                    offersPacket.getVillagerLevel(), offersPacket.getVillagerXp(), offersPacket.showProgress(),
                    offersPacket.canRestock());
        }
        if (packet instanceof ClientboundCustomPayloadPacket) {
            return packet;
        }

        // Pass through other packet types unchanged so mod-specific sync packets are not dropped.
        return packet;
    }

    @Nullable
        private static MirrorForgeOpenPacket decodeForgeOpenPacket(ServerLevel level,
            ClientboundCustomPayloadPacket customPayload, BlockSnapshot snapshot, BlockPos clientVirtualPos) {
        if (!"fml:play".equals(customPayload.getIdentifier().toString())) {
            return null;
        }

        FriendlyByteBuf payload = customPayload.getData();
        if (!payload.isReadable()) {
            return null;
        }

        int discriminator = payload.readVarInt();
        if (discriminator != 1) {
            return null;
        }

        int menuTypeId = payload.readVarInt();
        int windowId = payload.readVarInt();
        Component title = payload.readComponent();
        byte[] extraData = payload.readByteArray(32600);
        BlockPos menuSourcePos = readFirstBlockPos(extraData);
        byte[] rewrittenExtraData = rewriteFirstBlockPos(extraData, clientVirtualPos);
        StickSnapshotFeature.LOGGER.debug(
            "[server][mirror-debug] forge-open extraData rewritten windowId={} snapshotPos={} menuSourcePos={} virtualPos={} rawBytes={} rewrittenBytes={}",
            windowId, snapshot.getPos(), menuSourcePos, clientVirtualPos, extraData.length, rewrittenExtraData.length);

        BlockState contextState = Block.stateById(snapshot.getStateId());
        CompoundTag contextBeTag = snapshot.getBlockEntityTag();
        BlockPos contextPos = snapshot.getPos();
        if (menuSourcePos != null && level.hasChunkAt(menuSourcePos)) {
            contextPos = menuSourcePos;
            contextState = level.getBlockState(menuSourcePos);
            BlockEntity contextBe = level.getBlockEntity(menuSourcePos);
            contextBeTag = contextBe != null ? contextBe.saveWithFullMetadata() : null;
            StickSnapshotFeature.LOGGER.debug(
                    "[server][mirror-debug] forge-open context from menuSourcePos={} state={} hasBeTag={}",
                    menuSourcePos, contextState, contextBeTag != null);
        } else {
            StickSnapshotFeature.LOGGER.debug(
                    "[server][mirror-debug] forge-open fallback to snapshot context snapshotPos={} menuSourcePos={} chunkLoaded={}",
                    snapshot.getPos(), menuSourcePos, menuSourcePos != null && level.hasChunkAt(menuSourcePos));
        }

        CompoundTag normalizedSnapshotBeTag = normalizeBlockEntityTagForPos(
            contextBeTag, contextPos, clientVirtualPos, "client-virtual");
        return new MirrorForgeOpenPacket(menuTypeId, windowId, title, rewrittenExtraData,
            Block.getId(contextState), normalizedSnapshotBeTag);
    }

        private static BlockPos getClientVirtualPos(ServerPlayer player) {
        int y = player.serverLevel().getMinBuildHeight() + 1;
        return new BlockPos(player.getBlockX(), y, player.getBlockZ());
        }

    private static byte[] rewriteFirstBlockPos(byte[] extraData, BlockPos sourcePos) {
        if (extraData.length < Long.BYTES) {
            return extraData;
        }

        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(extraData));
        long originalPos = input.readLong();
        byte[] remaining = new byte[input.readableBytes()];
        input.readBytes(remaining);

        FriendlyByteBuf output = new FriendlyByteBuf(Unpooled.buffer(extraData.length));
        output.writeLong(sourcePos.asLong());
        output.writeBytes(remaining);

        byte[] rewritten = new byte[output.readableBytes()];
        output.readBytes(rewritten);
        StickSnapshotFeature.LOGGER.debug("[server][mirror-debug] forge-open remap blockpos {} -> {}", BlockPos.of(originalPos),
                sourcePos);
        return rewritten;
    }

    @Nullable
    private static BlockPos readFirstBlockPos(byte[] extraData) {
        if (extraData.length < Long.BYTES) {
            return null;
        }
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(extraData));
        return BlockPos.of(input.readLong());
    }

    private static MirrorForgeOpenPacket createVanillaMirrorOpenPacket(ClientboundOpenScreenPacket openPacket,
            BlockSnapshot snapshot, BlockPos clientVirtualPos, int mappedWindowId) {
        int menuTypeId = BuiltInRegistries.MENU.getId(openPacket.getType());
        byte[] extraData = encodeVirtualPos(clientVirtualPos);
        BlockState contextState = Block.stateById(snapshot.getStateId());
        CompoundTag normalizedSnapshotBeTag = normalizeBlockEntityTagForPos(
                snapshot.getBlockEntityTag(), snapshot.getPos(), clientVirtualPos, "client-virtual-vanilla");
        return new MirrorForgeOpenPacket(menuTypeId, mappedWindowId, openPacket.getTitle(), extraData,
                Block.getId(contextState), normalizedSnapshotBeTag);
    }

    private static byte[] encodeVirtualPos(BlockPos pos) {
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer(Long.BYTES));
        data.writeLong(pos.asLong());
        byte[] out = new byte[data.readableBytes()];
        data.readBytes(out);
        return out;
    }

    private static int remapContainerId(int id, int sourceContainerId, int targetContainerId) {
        if (sourceContainerId < 0) {
            return id;
        }
        return id == sourceContainerId ? targetContainerId : id;
    }

    @Nullable
    private static CompoundTag normalizeBlockEntityTagForPos(@Nullable CompoundTag rawTag, BlockPos sourcePos,
            BlockPos targetPos, String stage) {
        if (rawTag == null) {
            return null;
        }

        CompoundTag normalized = rawTag.copy();
        int dx = targetPos.getX() - sourcePos.getX();
        int dy = targetPos.getY() - sourcePos.getY();
        int dz = targetPos.getZ() - sourcePos.getZ();
        int remappedPosCount = remapEmbeddedPositions(normalized, dx, dy, dz);
        int beforeX = normalized.contains("x") ? normalized.getInt("x") : Integer.MIN_VALUE;
        int beforeY = normalized.contains("y") ? normalized.getInt("y") : Integer.MIN_VALUE;
        int beforeZ = normalized.contains("z") ? normalized.getInt("z") : Integer.MIN_VALUE;

        normalized.putInt("x", targetPos.getX());
        normalized.putInt("y", targetPos.getY());
        normalized.putInt("z", targetPos.getZ());

        StickSnapshotFeature.LOGGER.debug(
                "[server][mirror-debug] normalized block-entity tag stage={} sourcePos={} targetPos={} delta=({}, {}, {}) remappedPosFields={} tagPos=({}, {}, {}) -> ({}, {}, {})",
                stage, sourcePos, targetPos,
                dx, dy, dz, remappedPosCount,
                beforeX, beforeY, beforeZ,
                targetPos.getX(), targetPos.getY(), targetPos.getZ());
        return normalized;
    }

    private static int remapEmbeddedPositions(CompoundTag tag, int dx, int dy, int dz) {
        int remapped = 0;

        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            tag.putInt("x", tag.getInt("x") + dx);
            tag.putInt("y", tag.getInt("y") + dy);
            tag.putInt("z", tag.getInt("z") + dz);
            remapped++;
        }

        if (tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
            tag.putInt("X", tag.getInt("X") + dx);
            tag.putInt("Y", tag.getInt("Y") + dy);
            tag.putInt("Z", tag.getInt("Z") + dz);
            remapped++;
        }

        for (String key : tag.getAllKeys()) {
            if (tag.get(key) instanceof CompoundTag nested) {
                remapped += remapEmbeddedPositions(nested, dx, dy, dz);
            } else if (tag.get(key) instanceof ListTag listTag) {
                remapped += remapEmbeddedPositionsInList(listTag, dx, dy, dz);
            }
        }

        return remapped;
    }

    private static int remapEmbeddedPositionsInList(ListTag listTag, int dx, int dy, int dz) {
        int remapped = 0;
        for (int i = 0; i < listTag.size(); i++) {
            if (listTag.get(i) instanceof CompoundTag nested) {
                remapped += remapEmbeddedPositions(nested, dx, dy, dz);
            } else if (listTag.get(i) instanceof ListTag nestedList) {
                remapped += remapEmbeddedPositionsInList(nestedList, dx, dy, dz);
            }
        }
        return remapped;
    }

    private static void collectEmbeddedPositions(CompoundTag tag, Set<BlockPos> positions) {
        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            positions.add(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
        }
        if (tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
            positions.add(new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
        }

        for (String key : tag.getAllKeys()) {
            if (tag.get(key) instanceof CompoundTag nested) {
                collectEmbeddedPositions(nested, positions);
            } else if (tag.get(key) instanceof ListTag listTag) {
                collectEmbeddedPositionsInList(listTag, positions);
            }
        }
    }

    private static void collectEmbeddedPositionsInList(ListTag listTag, Set<BlockPos> positions) {
        for (int i = 0; i < listTag.size(); i++) {
            if (listTag.get(i) instanceof CompoundTag nested) {
                collectEmbeddedPositions(nested, positions);
            } else if (listTag.get(i) instanceof ListTag nestedList) {
                collectEmbeddedPositionsInList(nestedList, positions);
            }
        }
    }

    private static int detectContainerId(List<Packet<?>> packets) {
        for (Packet<?> packet : packets) {
            if (packet instanceof ClientboundOpenScreenPacket openPacket) {
                return openPacket.getContainerId();
            }
            if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
                return contentPacket.getContainerId();
            }
            if (packet instanceof ClientboundContainerSetSlotPacket slotPacket && slotPacket.getContainerId() >= 0) {
                return slotPacket.getContainerId();
            }
            if (packet instanceof ClientboundContainerSetDataPacket dataPacket) {
                return dataPacket.getContainerId();
            }
            if (packet instanceof ClientboundContainerClosePacket closePacket) {
                return closePacket.getContainerId();
            }
            if (packet instanceof ClientboundHorseScreenOpenPacket horsePacket) {
                return horsePacket.getContainerId();
            }
            if (packet instanceof ClientboundMerchantOffersPacket offersPacket) {
                return offersPacket.getContainerId();
            }
        }
        return -1;
    }

    private static class CapturedPackets {
        private final List<Packet<?>> packets;
        private final int sourceContainerId;

        private CapturedPackets(List<Packet<?>> packets, int sourceContainerId) {
            this.packets = packets;
            this.sourceContainerId = sourceContainerId;
        }
    }

    private record GuardedBlock(BlockPos pos, BlockState state, @Nullable CompoundTag beTag) {
    }

    private record SandboxInjectedBlock(BlockPos pos, BlockState originalState, @Nullable CompoundTag originalBeTag) {
    }

    private static class CapturingConnection extends Connection {
        private final List<Packet<?>> capturedPackets = new ArrayList<>();

        private CapturingConnection() {
            super(PacketFlow.CLIENTBOUND);
        }

        @Override
        public void send(Packet<?> packet) {
            capturedPackets.add(packet);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener sendListener) {
            capturedPackets.add(packet);
            if (sendListener != null) {
                sendListener.onSuccess();
            }
        }

        private List<Packet<?>> snapshot() {
            return new ArrayList<>(capturedPackets);
        }
    }

    private static int nextMirrorContainerId() {
        int id = MIRROR_CONTAINER_COUNTER.incrementAndGet();
        if (id > 1000) {
            MIRROR_CONTAINER_COUNTER.set(100);
            return 101;
        }
        return id;
    }
}
