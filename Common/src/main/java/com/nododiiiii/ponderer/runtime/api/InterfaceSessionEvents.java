package com.nododiiiii.ponderer.runtime.api;

import java.util.concurrent.CopyOnWriteArrayList;

/** Runtime events contain no editing state; tools subscribe only while installed. */
public final class InterfaceSessionEvents {
    private static final CopyOnWriteArrayList<Runnable> SLOTS = new CopyOnWriteArrayList<>();
    private InterfaceSessionEvents() {}
    public static AutoCloseable onSlotsChanged(Runnable listener) {
        SLOTS.add(listener);
        return () -> SLOTS.remove(listener);
    }
    public static void slotsChanged() { SLOTS.forEach(Runnable::run); }
}
