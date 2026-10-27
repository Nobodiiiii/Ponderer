package com.nododiiiii.ponderer.application.api;

import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import java.util.function.Supplier;

/** Product consumers see the runtime contract, while bootstrap alone constructs its implementation. */
public final class RuntimeAccess {
    private static Supplier<RuntimeEngine> source;
    private RuntimeAccess() {}
    public static void install(Supplier<RuntimeEngine> engine) { source = java.util.Objects.requireNonNull(engine); }
    public static RuntimeEngine engine() {
        if (source == null) throw new IllegalStateException("Runtime host has not been installed");
        return source.get();
    }
}
