package com.nododiiiii.ponderer.neoforge.sticksnapshot.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class ItemSnapshot {
    private final ResourceLocation itemId;
    @Nullable
    private final CompoundTag itemTag;
    private final ResourceLocation dimensionId;
    private final boolean sneaking;
    private final float yaw;
    private final float pitch;

    public ItemSnapshot(ResourceLocation itemId, @Nullable CompoundTag itemTag, ResourceLocation dimensionId,
                        boolean sneaking, float yaw, float pitch) {
        this.itemId = itemId;
        this.itemTag = itemTag;
        this.dimensionId = dimensionId;
        this.sneaking = sneaking;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    @Nullable
    public CompoundTag getItemTag() {
        return itemTag;
    }

    public ResourceLocation getDimensionId() {
        return dimensionId;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public BlockPos getReferencePos() {
        return BlockPos.ZERO;
    }

    public void writeToBuf(RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(itemId);
        buf.writeBoolean(itemTag != null);
        if (itemTag != null) {
            buf.writeNbt(itemTag);
        }
        buf.writeResourceLocation(dimensionId);
        buf.writeBoolean(sneaking);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static ItemSnapshot fromBuf(RegistryFriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        CompoundTag itemTag = buf.readBoolean() ? buf.readNbt() : null;
        ResourceLocation dimensionId = buf.readResourceLocation();
        boolean sneaking = buf.readBoolean();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        return new ItemSnapshot(itemId, itemTag, dimensionId, sneaking, yaw, pitch);
    }
}
