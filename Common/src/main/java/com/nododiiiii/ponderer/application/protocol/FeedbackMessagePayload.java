package com.nododiiiii.ponderer.application.protocol;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FeedbackMessagePayload(Component message, FeedbackLevel level, boolean interaction) implements CustomPacketPayload {
    public static final Type<FeedbackMessagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ponderer", "feedback_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackMessagePayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            ComponentSerialization.STREAM_CODEC.encode(buf, payload.message());
            buf.writeEnum(payload.level());
            buf.writeBoolean(payload.interaction());
        }, buf -> new FeedbackMessagePayload(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readEnum(FeedbackLevel.class), buf.readBoolean()));
    public FeedbackMessagePayload(Component message, FeedbackLevel level) { this(message, level, false); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

}
