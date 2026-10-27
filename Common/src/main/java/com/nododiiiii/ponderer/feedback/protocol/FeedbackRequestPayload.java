package com.nododiiiii.ponderer.feedback.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Request identity without changing the individual operation codecs. Only allowlisted requests are accepted. */
public record FeedbackRequestPayload(UUID requestId, CustomPacketPayload payload) implements CustomPacketPayload {
    public static final Type<FeedbackRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ponderer", "feedback_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackRequestPayload> CODEC = StreamCodec.of(
        (buf, request) -> { buf.writeUUID(request.requestId()); FeedbackPackets.encodeRequest(buf, request.payload()); },
        buf -> new FeedbackRequestPayload(buf.readUUID(), FeedbackPackets.decodeRequest(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

}
