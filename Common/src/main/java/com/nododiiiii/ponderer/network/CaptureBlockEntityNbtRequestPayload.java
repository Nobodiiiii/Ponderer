package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.RaycastHelper;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.UploadPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public record CaptureBlockEntityNbtRequestPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<CaptureBlockEntityNbtRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "capture_block_entity_nbt_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureBlockEntityNbtRequestPayload> CODEC =
            StreamCodec.of(CaptureBlockEntityNbtRequestPayload::encode, CaptureBlockEntityNbtRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, CaptureBlockEntityNbtRequestPayload payload) {
        buf.writeBlockPos(payload.pos());
    }

    private static CaptureBlockEntityNbtRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new CaptureBlockEntityNbtRequestPayload(buf.readBlockPos());
    }

    public static void handle(CaptureBlockEntityNbtRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }

        CompoundTag nbt = null;
        if (UploadPermissions.canUpload(player)
                && isAuthorizedBlockCapture(player, payload.pos())
                && player.serverLevel().hasChunkAt(payload.pos())) {
            BlockEntity blockEntity = player.serverLevel().getBlockEntity(payload.pos());
            if (blockEntity != null) {
                nbt = blockEntity.saveWithoutMetadata(player.serverLevel().registryAccess());
            }
        }

        PondererServices.NETWORK.sendToPlayer(player, new CaptureBlockEntityNbtResponsePayload(payload.pos(), nbt));
    }

    private static boolean isAuthorizedBlockCapture(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > 36.0D) {
            return false;
        }
        BlockHitResult hit = RaycastHelper.rayTraceRange(player.serverLevel(), player, 6.0D);
        return hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && pos.equals(hit.getBlockPos());
    }
}
