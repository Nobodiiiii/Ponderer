package com.nododiiiii.ponderer.projector.client;

/**
 * Marks client-side Ponder playback that is being advanced only to render a
 * projector hologram. Instructions that would touch real client UI or play
 * real-world side effects must no-op while this context is active.
 */
public final class ProjectorRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<FrameTime> FRAME_TIME = new ThreadLocal<>();

    private ProjectorRenderContext() {
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static boolean hasFrameTime() {
        return FRAME_TIME.get() != null;
    }

    public static int frameTick() {
        FrameTime frameTime = FRAME_TIME.get();
        return frameTime == null ? 0 : frameTime.localTick();
    }

    public static float partialTick() {
        FrameTime frameTime = FRAME_TIME.get();
        return frameTime == null ? 0.0F : frameTime.partialTick();
    }

    public static float renderTime() {
        FrameTime frameTime = FRAME_TIME.get();
        return frameTime == null ? 0.0F : frameTime.localTick() + frameTime.partialTick();
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

    public static void runWithFrameTime(int localTick, float partialTick, Runnable action) {
        FrameTime previous = FRAME_TIME.get();
        DEPTH.set(DEPTH.get() + 1);
        FRAME_TIME.set(new FrameTime(Math.max(0, localTick), sanitizePartialTick(partialTick)));
        try {
            action.run();
        } finally {
            if (previous == null) {
                FRAME_TIME.remove();
            } else {
                FRAME_TIME.set(previous);
            }
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

    private static float sanitizePartialTick(float partialTick) {
        if (Float.isNaN(partialTick) || partialTick < 0.0F) {
            return 0.0F;
        }
        return Math.min(partialTick, 0.999F);
    }

    private record FrameTime(int localTick, float partialTick) {
    }
}
