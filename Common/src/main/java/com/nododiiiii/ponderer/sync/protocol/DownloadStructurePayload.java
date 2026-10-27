package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record DownloadStructurePayload(String sourceId) implements CustomPacketPayload {

    public static final Type<DownloadStructurePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "download_structure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DownloadStructurePayload> CODEC =
            StreamCodec.of(DownloadStructurePayload::encode, DownloadStructurePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, DownloadStructurePayload payload) {
        buf.writeUtf(payload.sourceId());
    }

    private static DownloadStructurePayload decode(RegistryFriendlyByteBuf buf) {
        return new DownloadStructurePayload(buf.readUtf());
    }

}
