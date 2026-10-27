package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record UploadResponsePayload(String sceneId, @Nullable String pack, String status) implements CustomPacketPayload {

    public static final Type<UploadResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "upload_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UploadResponsePayload> CODEC =
            StreamCodec.of(UploadResponsePayload::encode, UploadResponsePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, UploadResponsePayload payload) {
        buf.writeUtf(payload.sceneId());
        writeOptionalUtf(buf, payload.pack());
        buf.writeUtf(payload.status());
    }

    private static UploadResponsePayload decode(RegistryFriendlyByteBuf buf) {
        return new UploadResponsePayload(buf.readUtf(), readOptionalUtf(buf), buf.readUtf());
    }

    private static void writeOptionalUtf(RegistryFriendlyByteBuf buf, @Nullable String value) {
        boolean present = value != null && !value.isBlank();
        buf.writeBoolean(present);
        if (present) {
            buf.writeUtf(value);
        }
    }

    @Nullable
    private static String readOptionalUtf(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
