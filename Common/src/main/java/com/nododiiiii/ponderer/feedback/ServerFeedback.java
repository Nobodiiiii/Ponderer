package com.nododiiiii.ponderer.feedback;

import com.nododiiiii.ponderer.network.FeedbackMessagePayload;
import com.nododiiiii.ponderer.network.FeedbackResponsePayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Scoped to synchronous server-thread handlers; no client classes are loaded here. */
public final class ServerFeedback {
    private record Context(UUID requestId, ServerPlayer player) {}
    private static final ThreadLocal<Context> context = new ThreadLocal<>();

    private ServerFeedback() {}

    public static void handle(UUID id, ServerPlayer player, Runnable action) {
        Context previous = context.get();
        context.set(new Context(id, player));
        try {
            action.run();
        } finally {
            context.remove();
            PondererServices.NETWORK.sendToPlayer(player, new FeedbackResponsePayload(id, null, true));
            if (previous != null) context.set(previous);
        }
    }

    public static CustomPacketPayload response(ServerPlayer player, CustomPacketPayload payload) {
        Context current = context.get();
        if (current == null || current.player() != player || !com.nododiiiii.ponderer.network.FeedbackPackets.isResponse(payload)) return payload;
        return new FeedbackResponsePayload(current.requestId(), payload, false);
    }

    public static void send(ServerPlayer player, Component message, FeedbackLevel level) {
        PondererServices.NETWORK.sendToPlayer(player, new FeedbackMessagePayload(message, level));
    }
}
