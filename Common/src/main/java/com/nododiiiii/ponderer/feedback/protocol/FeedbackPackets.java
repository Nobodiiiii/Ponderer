package com.nododiiiii.ponderer.feedback.protocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.List;

/** Direction-specific allowlists prevent nesting envelopes or dispatching a response as a server request. */
public final class FeedbackPackets {
    public record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        void encode(RegistryFriendlyByteBuf buf, CustomPacketPayload payload) {
            @SuppressWarnings("unchecked") T typed = (T) payload;
            codec.encode(buf, typed);
        }
    }
    private record Registry(List<Entry<?>> requests, List<Entry<?>> responses) {}
    private static volatile Registry registry = new Registry(List.of(), List.of());

    /** Install the host's complete, ordered protocol table before registering network channels. */
    public static void install(List<Entry<?>> requests, List<Entry<?>> responses) {
        registry = new Registry(List.copyOf(requests), List.copyOf(responses));
    }

    private FeedbackPackets() {}

    public static boolean isResponse(CustomPacketPayload payload) {
        return registry.responses().stream().anyMatch(entry -> entry.type().equals(payload.type()));
    }

    public static boolean isRequest(CustomPacketPayload payload) {
        return registry.requests().stream().anyMatch(entry -> entry.type().equals(payload.type()));
    }

    private static void encode(RegistryFriendlyByteBuf buf, CustomPacketPayload payload, List<Entry<?>> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry<?> entry = entries.get(i);
            if (!entry.type().equals(payload.type())) continue;
            buf.writeVarInt(i);
            entry.encode(buf, payload);
            return;
        }
        throw new IllegalArgumentException("Unsupported feedback payload: " + payload.type().id());
    }

    private static CustomPacketPayload decode(RegistryFriendlyByteBuf buf, List<Entry<?>> entries) {
        int index = buf.readVarInt();
        if (index < 0 || index >= entries.size()) throw new IllegalArgumentException("Invalid feedback payload index: " + index);
        return entries.get(index).codec().decode(buf);
    }

    public static void encodeRequest(RegistryFriendlyByteBuf buf, CustomPacketPayload payload) { encode(buf, payload, registry.requests()); }
    public static CustomPacketPayload decodeRequest(RegistryFriendlyByteBuf buf) { return decode(buf, registry.requests()); }
    public static void encodeResponse(RegistryFriendlyByteBuf buf, CustomPacketPayload payload) { encode(buf, payload, registry.responses()); }
    public static CustomPacketPayload decodeResponse(RegistryFriendlyByteBuf buf) { return decode(buf, registry.responses()); }

}
