package com.nododiiiii.ponderer.runtime.api;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/** Optional input owners can suspend camera animation without runtime loading tools. */
public final class PlaybackInputLocks {
    private static final CopyOnWriteArrayList<BooleanSupplier> LOCKS = new CopyOnWriteArrayList<>();
    private PlaybackInputLocks() {}
    public static AutoCloseable register(BooleanSupplier lock) {
        LOCKS.add(lock);
        return () -> LOCKS.remove(lock);
    }
    public static boolean isLocked() { return LOCKS.stream().anyMatch(BooleanSupplier::getAsBoolean); }
}
