package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncRequestPayload(String mode) implements CustomPacketPayload {

    public static final Type<SyncRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "sync_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRequestPayload> CODEC =
            StreamCodec.of(SyncRequestPayload::encode, SyncRequestPayload::decode);

    public SyncRequestPayload() { this("check"); }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, SyncRequestPayload payload) {
        buf.writeUtf(payload.mode());
    }

    private static SyncRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new SyncRequestPayload(buf.readUtf());
    }


}
