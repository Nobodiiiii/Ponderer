package com.nododiiiii.ponderer.application;

import com.nododiiiii.ponderer.runtime.core.SceneItemBindings;
import net.createmod.ponder.foundation.PonderIndex;
import java.util.concurrent.CopyOnWriteArrayList;

/** The product owns catalog publication and Ponder refresh order. Features subscribe to invalidation. */
public final class SceneRefreshCoordinator {
    private static final CopyOnWriteArrayList<Runnable> invalidations = new CopyOnWriteArrayList<>();
    private static long version;
    private SceneRefreshCoordinator() {}

    public static int loadCatalog() {
        var scenes = Workspaces.files().scenes().loadScenes();
        com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().setScenes(scenes);
        version++;
        return scenes.size();
    }
    public static long version() { return version; }
    public static AutoCloseable subscribe(Runnable listener) {
        invalidations.add(listener);
        return () -> invalidations.remove(listener);
    }
    public static void reloadPonderIndex() {
        try {
            PonderIndex.reload();
        } finally {
            for (Runnable listener : invalidations) listener.run();
        }
    }
    public static void onItemTagsUpdated() {
        if (com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().getScenes().stream().anyMatch(SceneItemBindings::hasTagBindings)) {
            reloadPonderIndex();
        }
    }
}
