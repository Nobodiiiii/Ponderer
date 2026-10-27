package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RemoteDeleteRequestPayload(String kind, String id, @Nullable String pack) implements CustomPacketPayload {

    public static final Type<RemoteDeleteRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_delete_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteDeleteRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemoteDeleteRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
    }

    public static RemoteDeleteRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new RemoteDeleteRequestPayload(buf.readUtf(), buf.readUtf(), readOptionalUtf(buf));
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
