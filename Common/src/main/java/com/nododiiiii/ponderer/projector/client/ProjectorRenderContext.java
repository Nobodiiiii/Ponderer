package com.nododiiiii.ponderer.projector.client;

/**
 * Marks client-side Ponder playback that is being advanced only to render a
 * projector hologram. Instructions that would touch real client UI or play
 * real-world side effects must no-op while this context is active.
 */
public final class ProjectorRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ProjectorRenderContext() {
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void run(Runnable action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            pop();
        }
    }

    public static <T> T supply(java.util.function.Supplier<T> action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return action.get();
        } finally {
            pop();
        }
    }

    private static void pop() {
        int nextDepth = DEPTH.get() - 1;
        if (nextDepth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(nextDepth);
        }
    }
}
