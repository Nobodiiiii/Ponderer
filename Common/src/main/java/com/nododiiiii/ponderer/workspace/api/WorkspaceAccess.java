package com.nododiiiii.ponderer.workspace.api;

import java.util.function.Supplier;

/** Product binding, explicitly installed by the host; local storage has no global host dependency. */
public final class WorkspaceAccess {
    private static Supplier<? extends Workspace> source;
    private WorkspaceAccess() {}
    public static void install(Supplier<? extends Workspace> workspace) { source = java.util.Objects.requireNonNull(workspace); }
    public static Workspace get() {
        if (source == null) throw new IllegalStateException("Workspace host has not been installed");
        return source.get();
    }
}
