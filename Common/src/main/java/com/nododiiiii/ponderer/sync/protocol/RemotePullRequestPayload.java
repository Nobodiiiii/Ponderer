package com.nododiiiii.ponderer.sync.protocol;

import com.nododiiiii.ponderer.Ponderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;


public record RemotePullRequestPayload(String kind, String id, @Nullable String pack, boolean includeDependencies) implements CustomPacketPayload {

    public static final Type<RemotePullRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Ponderer.MODID, "remote_pull_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemotePullRequestPayload> CODEC =
        StreamCodec.of((buf, payload) -> payload.encode(buf), RemotePullRequestPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(kind());
        buf.writeUtf(id());
        writeOptionalUtf(buf, pack());
        buf.writeBoolean(includeDependencies());
    }

    public static RemotePullRequestPayload decode(RegistryFriendlyByteBuf buf) {
        return new RemotePullRequestPayload(buf.readUtf(), buf.readUtf(), readOptionalUtf(buf), buf.readBoolean());
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
