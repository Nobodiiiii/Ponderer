package com.nododiiiii.ponderer.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.UUID;

public record FeedbackResponsePayload(UUID requestId, @Nullable CustomPacketPayload payload, boolean complete) implements CustomPacketPayload {
    public static final Type<FeedbackResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ponderer", "feedback_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackResponsePayload> CODEC = StreamCodec.of(
        (buf, response) -> {
            buf.writeUUID(response.requestId());
            buf.writeBoolean(response.complete());
            buf.writeBoolean(response.payload() != null);
            if (response.payload() != null) FeedbackPackets.encodeResponse(buf, response.payload());
        }, buf -> {
            UUID id = buf.readUUID();
            boolean complete = buf.readBoolean();
            return new FeedbackResponsePayload(id, buf.readBoolean() ? FeedbackPackets.decodeResponse(buf) : null, complete);
        });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
