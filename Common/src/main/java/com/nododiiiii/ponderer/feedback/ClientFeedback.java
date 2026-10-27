package com.nododiiiii.ponderer.feedback;

import com.nododiiiii.ponderer.application.network.FeedbackPacketHandlers;
import com.nododiiiii.ponderer.application.protocol.FeedbackRequestPayload;
import com.nododiiiii.ponderer.application.protocol.FeedbackResponsePayload;
import com.nododiiiii.ponderer.application.protocol.FeedbackPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-thread routing. UI requests retain their original screen and never fall back to world chat. */
public final class ClientFeedback {
    private record Notice(Component message, FeedbackLevel level) {}
    private static final class Request {
        Screen screen;
        final long started = System.currentTimeMillis();
        final ArrayList<Notice> notices = new ArrayList<>();
        Request(Screen screen) { this.screen = screen; }
    }
    private static final Map<UUID, Request> requests = new LinkedHashMap<>();
    private static Object connection;
    private static Request receiving;

    private ClientFeedback() {}

    public static CustomPacketPayload request(CustomPacketPayload packet) {
        if (!FeedbackPackets.isRequest(packet)) return packet;
        tick();
        UUID id = UUID.randomUUID();
        Screen screen = Minecraft.getInstance().screen;
        requests.put(id, new Request(screen instanceof FeedbackTarget ? screen : null));
        return new FeedbackRequestPayload(id, packet);
    }

    public static void receive(FeedbackResponsePayload response) {
        tick();
        Request request = requests.get(response.requestId());
        if (request == null) return;
        Request previous = receiving;
        receiving = request;
        try {
            if (response.payload() != null) FeedbackPacketHandlers.handleResponse(response.payload());
            if (response.complete()) {
                requests.remove(response.requestId());
                if (!request.notices.isEmpty()) {
                    Component combined = Component.empty();
                    FeedbackLevel level = FeedbackLevel.INFO;
                    for (Notice notice : request.notices) {
                        if (!combined.getString().isEmpty()) combined = combined.copy().append("\n");
                        combined = combined.copy().append(notice.message());
                        if (notice.level().ordinal() > level.ordinal()) level = notice.level();
                    }
                    deliver(request.screen, combined, level);
                }
            }
        } finally {
            receiving = previous;
        }
    }

    /** Null means a chat command, even if a different screen has opened since it was sent. */
    public static Screen responseScreen() {
        return receiving == null ? Minecraft.getInstance().screen : receiving.screen;
    }

    public static boolean hasRequest() { return receiving != null; }

    public static void continueOn(Screen successor) {
        if (receiving != null && successor instanceof FeedbackTarget) receiving.screen = successor;
    }

    public static void result(Component message, FeedbackLevel level) {
        if (message == null || message.getString().isBlank()) return;
        if (receiving != null) receiving.notices.add(new Notice(message, level));
        else deliver(Minecraft.getInstance().screen, message, level);
    }

    public static void deliver(Screen screen, Component message, FeedbackLevel level) {
        if (screen instanceof FeedbackTarget target) target.acceptFeedback(message, level);
        else chat(message);
    }

    /** Background notices and explicit chat-command output always retain a chat history. */
    public static void chat(Component message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(message, false);
    }

    public static void tick() {
        Object current = Minecraft.getInstance().getConnection();
        if (connection != current) {
            requests.clear();
            connection = current;
        }
        requests.values().removeIf(request -> System.currentTimeMillis() - request.started > 120_000);
    }
}
