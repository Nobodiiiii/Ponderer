package com.nododiiiii.ponderer.application;

import com.nododiiiii.ponderer.runtime.api.SceneItemBindings;
import net.createmod.ponder.foundation.PonderIndex;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.feedback.ClientFeedback;
import com.nododiiiii.ponderer.feedback.FeedbackLevel;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The product owns catalog publication and Ponder refresh order. Features subscribe to invalidation. */
public final class SceneRefreshCoordinator {
    private static final CopyOnWriteArrayList<Runnable> invalidations = new CopyOnWriteArrayList<>();
    private static long version;
    private SceneRefreshCoordinator() {}

    /** Initial bootstrap only, before Ponder registers its plugins. Writes use refresh(). */
    public static int loadCatalog() {
        var scenes = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().scenes().loadScenes();
        com.nododiiiii.ponderer.application.api.RuntimeAccess.engine().catalog().setScenes(scenes);
        version++;
        return scenes.size();
    }
    public static long version() { return version; }
    /** Publish a complete workspace view and finish the native index before reporting success. */
    public static int refresh() {
        var scenes = com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().scenes().loadScenes();
        publish(scenes, true);
        return scenes.size();
    }
    public static int refreshAndReport() {
        try { return refresh(); }
        catch (RuntimeException failure) {
            reportFailure(failure);
            return -1;
        }
    }
    public static void reportFailure(RuntimeException failure) {
        LogUtils.getLogger().error("Unable to refresh the Ponder catalog; /ponderer reload can retry", failure);
        ClientFeedback.result(Component.translatable("ponderer.refresh.failed", failure.getMessage()), FeedbackLevel.ERROR);
    }
    public static AutoCloseable subscribe(Runnable listener) {
        invalidations.add(listener);
        return () -> invalidations.remove(listener);
    }
    public static void reloadPonderIndex() {
        publish(RuntimeAccess.engine().catalog().getScenes(), false);
    }
    private static void publish(List<DslScene> scenes, boolean advanceVersion) {
        var catalog = RuntimeAccess.engine().catalog();
        var previous = catalog.getScenes();
        catalog.setScenes(scenes);
        try {
            PonderIndex.reload();
            if (advanceVersion) version++;
        } catch (RuntimeException failure) {
            catalog.setScenes(previous);
            try { PonderIndex.reload(); }
            catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
            throw failure;
        } finally {
            for (Runnable listener : invalidations) {
                try { listener.run(); }
                catch (RuntimeException failure) { LogUtils.getLogger().warn("Ponder catalog listener failed", failure); }
            }
        }
    }
    public static void onItemTagsUpdated() {
        if (com.nododiiiii.ponderer.application.api.RuntimeAccess.engine().catalog().getScenes().stream().anyMatch(SceneItemBindings::hasTagBindings)) {
            try { reloadPonderIndex(); }
            catch (RuntimeException failure) { reportFailure(failure); }
        }
    }
}
