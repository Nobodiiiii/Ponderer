package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.FriendlyByteBuf;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record PermissionListRequestPayload() implements CustomPacketPayload {

    public static final Type<PermissionListRequestPayload> TYPE =
            new Type<>(new ResourceLocation(Ponderer.MODID, "permission_list_request"));
    public static final StreamCodec<FriendlyByteBuf, PermissionListRequestPayload> CODEC =
            StreamCodec.of(PermissionListRequestPayload::encode, PermissionListRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, PermissionListRequestPayload payload) {
    }

    private static PermissionListRequestPayload decode(FriendlyByteBuf buf) {
        return new PermissionListRequestPayload();
    }

    public static void handle(PermissionListRequestPayload payload, @Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        PermissionListResponsePayload.sendSnapshot(player, "", "", false);
    }
}
