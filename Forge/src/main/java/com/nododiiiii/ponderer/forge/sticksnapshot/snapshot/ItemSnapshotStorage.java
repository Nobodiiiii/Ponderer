package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ItemSnapshotStorage {
    private static final String ROOT = "StickSnapshotData";
    private static final String SNAPSHOT = "ItemSnapshot";

    private ItemSnapshotStorage() {
    }

    public static void save(ServerPlayer player, ItemSnapshot snapshot) {
        ReplayStorageGuard.writePlayerPersistentData(player, ROOT + "." + SNAPSHOT, () -> {
            CompoundTag root = player.getPersistentData();
            root.put(SNAPSHOT, toTag(snapshot));
            root.putBoolean(ROOT, true);
        });
    }

    public static ItemSnapshot load(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT) || !root.contains(SNAPSHOT)) {
            return null;
        }
        return fromTag(root.getCompound(SNAPSHOT));
    }

    public static CompoundTag toTag(ItemSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putString("itemId", snapshot.getItemId().toString());
        if (snapshot.getItemTag() != null) {
            tag.put("itemTag", snapshot.getItemTag().copy());
        }
        tag.putString("dimensionId", snapshot.getDimensionId().toString());
        tag.putBoolean("sneaking", snapshot.isSneaking());
        tag.putFloat("yaw", snapshot.getYaw());
        tag.putFloat("pitch", snapshot.getPitch());
        return tag;
    }

    public static ItemSnapshot fromTag(CompoundTag tag) {
        ResourceLocation itemId = new ResourceLocation(tag.getString("itemId"));
        CompoundTag itemTag = tag.contains("itemTag") ? tag.getCompound("itemTag") : null;
        ResourceLocation dimensionId = new ResourceLocation(tag.getString("dimensionId"));
        boolean sneaking = tag.getBoolean("sneaking");
        float yaw = tag.getFloat("yaw");
        float pitch = tag.getFloat("pitch");
        return new ItemSnapshot(itemId, itemTag, dimensionId, sneaking, yaw, pitch);
    }
}
