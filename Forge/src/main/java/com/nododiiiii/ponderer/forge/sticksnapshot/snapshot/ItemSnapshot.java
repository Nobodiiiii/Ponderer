package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Captures a held-item snapshot for show_interface step replay.
 * Mirrors the role of {@link BlockSnapshot} but targets item-driven GUI opens
 * (e.g. tetra's holosphere workbench, books, item menus).
 */
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

    /** Caller may override this for visual context; currently informational only. */
    public BlockPos getReferencePos() {
        return BlockPos.ZERO;
    }

    public void writeToBuf(FriendlyByteBuf buf) {
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

    public static ItemSnapshot fromBuf(FriendlyByteBuf buf) {
        ResourceLocation itemId = buf.readResourceLocation();
        CompoundTag itemTag = buf.readBoolean() ? buf.readNbt() : null;
        ResourceLocation dimensionId = buf.readResourceLocation();
        boolean sneaking = buf.readBoolean();
        float yaw = buf.readFloat();
        float pitch = buf.readFloat();
        return new ItemSnapshot(itemId, itemTag, dimensionId, sneaking, yaw, pitch);
    }
}
