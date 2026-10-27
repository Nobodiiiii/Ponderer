package com.nododiiiii.ponderer.support.lifecycle;

/** Run every cleanup even if one resource fails, reporting all failures afterwards. */
public final class Cleanup {
    private Cleanup() {}
    public static void all(Runnable... actions) {
        RuntimeException failure = null;
        for (Runnable action : actions) {
            try { action.run(); } catch (RuntimeException error) {
                if (failure == null) failure = new IllegalStateException("Resource cleanup failed");
                failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
