package com.nododiiiii.ponderer.forge.sticksnapshot.snapshot;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReplayGuard {
    private static final ThreadLocal<ReplayContext> THREAD_CONTEXT = new ThreadLocal<>();
    private static final Map<UUID, UUID> PLAYER_TO_SESSION = new ConcurrentHashMap<>();
    private static final Map<UUID, ReplaySessionState> SESSION_STATES = new ConcurrentHashMap<>();

    private ReplayGuard() {
    }

    public static Scope begin(ServerPlayer realPlayer, @Nullable UUID fakePlayerId, String reason) {
        UUID sessionId = UUID.randomUUID();
        ReplayContext context = new ReplayContext(sessionId, realPlayer.getUUID(), fakePlayerId, reason);
        ReplaySessionState state = new ReplaySessionState(context);

        THREAD_CONTEXT.set(context);
        SESSION_STATES.put(sessionId, state);
        PLAYER_TO_SESSION.put(realPlayer.getUUID(), sessionId);
        if (fakePlayerId != null) {
            PLAYER_TO_SESSION.put(fakePlayerId, sessionId);
        }

        StickSnapshotFeature.LOGGER.debug(
                "[replay-guard] begin session={} realPlayer={} fakePlayer={} reason={}",
                sessionId, realPlayer.getScoreboardName(), fakePlayerId, reason);
        return new Scope(sessionId, true);
    }

    public static Scope openFromCapture(@Nullable CapturedContext captured, String source) {
        if (captured == null || !isSessionActive(captured.sessionId())) {
            return Scope.noop();
        }

        ReplayContext context = new ReplayContext(
                captured.sessionId(),
                captured.realPlayerId(),
                captured.fakePlayerId(),
                captured.reason() + " -> " + source);
        THREAD_CONTEXT.set(context);
        return new Scope(captured.sessionId(), false);
    }

    @Nullable
    public static CapturedContext captureCurrentContext() {
        ReplayContext context = THREAD_CONTEXT.get();
        if (context == null) {
            return null;
        }
        return new CapturedContext(context.sessionId, context.realPlayerId, context.fakePlayerId, context.reason);
    }

    public static boolean isActive() {
        return THREAD_CONTEXT.get() != null;
    }

    public static boolean isReplayPlayer(UUID playerId) {
        ReplayContext context = THREAD_CONTEXT.get();
        if (context != null && context.matches(playerId)) {
            return true;
        }

        UUID sessionId = PLAYER_TO_SESSION.get(playerId);
        return sessionId != null && isSessionActive(sessionId);
    }

    public static boolean shouldBlockPlayer(ServerPlayer player) {
        return isReplayPlayer(player.getUUID());
    }

    public static boolean isSessionActive(UUID sessionId) {
        ReplaySessionState state = SESSION_STATES.get(sessionId);
        return state != null && state.active.get();
    }

    public static void auditBlocked(String layer, String action, @Nullable String actor) {
        ReplayContext context = THREAD_CONTEXT.get();
        UUID sessionId = context != null ? context.sessionId : null;
        StickSnapshotFeature.LOGGER.debug(
                "[replay-guard] blocked layer={} action={} actor={} session={}",
                layer, action, actor, sessionId);
    }

    public static void auditDroppedAsync(String source, UUID sessionId) {
        StickSnapshotFeature.LOGGER.debug(
                "[replay-guard] drop async task source={} session={} because replay context already ended",
                source, sessionId);
    }

    private static void endSession(UUID sessionId) {
        ReplaySessionState state = SESSION_STATES.remove(sessionId);
        if (state == null) {
            return;
        }

        state.active.set(false);
        PLAYER_TO_SESSION.remove(state.context.realPlayerId, sessionId);
        if (state.context.fakePlayerId != null) {
            PLAYER_TO_SESSION.remove(state.context.fakePlayerId, sessionId);
        }
    }

    public static final class Scope implements AutoCloseable {
        @Nullable
        private final UUID sessionId;
        private final boolean owner;
        private boolean closed;

        private Scope(@Nullable UUID sessionId, boolean owner) {
            this.sessionId = sessionId;
            this.owner = owner;
        }

        private static Scope noop() {
            Scope scope = new Scope(null, false);
            scope.closed = true;
            return scope;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            THREAD_CONTEXT.remove();
            if (owner && sessionId != null) {
                endSession(sessionId);
            }
        }
    }

    public record CapturedContext(UUID sessionId, UUID realPlayerId, @Nullable UUID fakePlayerId, String reason) {
    }

    private static final class ReplayContext {
        private final UUID sessionId;
        private final UUID realPlayerId;
        @Nullable
        private final UUID fakePlayerId;
        private final String reason;

        private ReplayContext(UUID sessionId, UUID realPlayerId, @Nullable UUID fakePlayerId, String reason) {
            this.sessionId = sessionId;
            this.realPlayerId = realPlayerId;
            this.fakePlayerId = fakePlayerId;
            this.reason = reason;
        }

        private boolean matches(UUID playerId) {
            return realPlayerId.equals(playerId) || (fakePlayerId != null && fakePlayerId.equals(playerId));
        }
    }

    private static final class ReplaySessionState {
        private final ReplayContext context;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private ReplaySessionState(ReplayContext context) {
            this.context = context;
        }
    }
}
