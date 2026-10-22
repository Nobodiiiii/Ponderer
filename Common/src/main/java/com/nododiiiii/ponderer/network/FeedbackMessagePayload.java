package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import com.nododiiiii.ponderer.feedback.WorldFeedback;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.nododiiiii.ponderer.network.compat.StreamCodec;
import com.nododiiiii.ponderer.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FeedbackMessagePayload(Component message, FeedbackLevel level, boolean interaction) implements CustomPacketPayload {
    public static final Type<FeedbackMessagePayload> TYPE = new Type<>(new ResourceLocation("ponderer", "feedback_message"));
    public static final StreamCodec<FriendlyByteBuf, FeedbackMessagePayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeComponent(payload.message());
            buf.writeEnum(payload.level());
            buf.writeBoolean(payload.interaction());
        }, buf -> new FeedbackMessagePayload(buf.readComponent(), buf.readEnum(FeedbackLevel.class), buf.readBoolean()));
    public FeedbackMessagePayload(Component message, FeedbackLevel level) { this(message, level, false); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(FeedbackMessagePayload payload) {
        if (payload.interaction()) WorldFeedback.interaction(payload.message().copy().withStyle(net.minecraft.ChatFormatting.RED));
        else ClientFeedback.result(payload.message(), payload.level());
    }
}
