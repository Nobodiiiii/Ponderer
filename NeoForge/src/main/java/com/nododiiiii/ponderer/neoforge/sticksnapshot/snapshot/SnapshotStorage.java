package com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class SnapshotStorage {
    private static final String ROOT = "StickSnapshotData";
    private static final String SNAPSHOT = "Snapshot";

    private SnapshotStorage() {
    }

    public static void save(ServerPlayer player, BlockSnapshot snapshot) {
        ReplayStorageGuard.writePlayerPersistentData(player, ROOT + "." + SNAPSHOT, () -> {
            CompoundTag root = player.getPersistentData();
            root.put(SNAPSHOT, toTag(snapshot));
            root.putBoolean(ROOT, true);
        });
    }

    public static BlockSnapshot load(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT) || !root.contains(SNAPSHOT)) {
            return null;
        }
        return fromTag(root.getCompound(SNAPSHOT));
    }

    public static CompoundTag toTag(BlockSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stateId", snapshot.getStateId());
        tag.putString("blockId", snapshot.getBlockId().toString());
        if (snapshot.getBlockEntityTag() != null) {
            tag.put("blockEntityTag", snapshot.getBlockEntityTag().copy());
        }
        tag.putString("dimensionId", snapshot.getDimensionId().toString());
        tag.putLong("pos", snapshot.getPos().asLong());
        tag.putInt("face", snapshot.getFace().ordinal());
        tag.putDouble("hitX", snapshot.getHitLocation().x);
        tag.putDouble("hitY", snapshot.getHitLocation().y);
        tag.putDouble("hitZ", snapshot.getHitLocation().z);
        tag.putBoolean("inside", snapshot.isInside());
        return tag;
    }

    public static BlockSnapshot fromTag(CompoundTag tag) {
        int stateId = tag.getInt("stateId");
        ResourceLocation blockId = ResourceLocation.parse(tag.getString("blockId"));
        CompoundTag blockEntityTag = tag.contains("blockEntityTag") ? tag.getCompound("blockEntityTag") : null;
        ResourceLocation dimensionId = ResourceLocation.parse(tag.getString("dimensionId"));
        return new BlockSnapshot(
                stateId,
                blockId,
                blockEntityTag,
                dimensionId,
                net.minecraft.core.BlockPos.of(tag.getLong("pos")),
                net.minecraft.core.Direction.values()[tag.getInt("face")],
                new net.minecraft.world.phys.Vec3(tag.getDouble("hitX"), tag.getDouble("hitY"), tag.getDouble("hitZ")),
                tag.getBoolean("inside")
        );
    }
}
