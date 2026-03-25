package com.nododiiiii.ponderer.forge.sticksnapshot.client;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ModNetworking;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ReplaySnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.SaveSnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

public final class ForgeShowInterfaceClient {

    private ForgeShowInterfaceClient() {
    }

    public static void showInterfaceStep(DslScene.DslStep step, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        BlockPos pos = parseBlockPos(step.blockPos);
        if (pos == null) {
            StickSnapshotFeature.LOGGER.warn("show_interface skipped: missing blockPos context");
            return;
        }

        BlockState state = mc.level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (step.block != null) {
            ResourceLocation expected = ResourceLocation.tryParse(step.block);
            if (expected != null && !expected.equals(blockId)) {
                StickSnapshotFeature.LOGGER.debug(
                    "show_interface context block mismatch at {} expected={} actual={}",
                    pos, expected, blockId);
            }
        }

        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        Direction face = parseDirection(step.direction);
        Vec3 hit = parseHit(step.point, pos);

        BlockSnapshot snapshot = new BlockSnapshot(
            Block.getId(state),
            blockId,
            blockEntity != null ? blockEntity.saveWithFullMetadata() : null,
            mc.level.dimension().location(),
            pos.immutable(),
            face,
            hit,
            Boolean.TRUE.equals(step.whileSneaking));

        ClientInputHandler.prepareMirrorReplay(Math.max(1, durationTicks));
        ModNetworking.CHANNEL.sendToServer(new SaveSnapshotPacket(snapshot));
        ModNetworking.CHANNEL.sendToServer(new ReplaySnapshotPacket());
    }

    @Nullable
    private static BlockPos parseBlockPos(@Nullable List<Integer> pos) {
        if (pos == null || pos.size() < 3) {
            return null;
        }
        return new BlockPos(pos.get(0), pos.get(1), pos.get(2));
    }

    private static Direction parseDirection(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Direction.UP;
        }
        try {
            return Direction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return Direction.UP;
        }
    }

    private static Vec3 parseHit(@Nullable List<Double> point, BlockPos pos) {
        if (point == null || point.size() < 3) {
            return Vec3.atCenterOf(pos);
        }
        return new Vec3(point.get(0), point.get(1), point.get(2));
    }
}