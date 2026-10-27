package com.nododiiiii.ponderer.application;

import com.nododiiiii.ponderer.application.api.SaveOutcome;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.workspace.api.SceneRepository;

/** Client-thread save use case. Disk success and the complete native refresh have separate results. */
public final class SceneApplicationService {
    private final SceneRepository scenes;
    public SceneApplicationService(SceneRepository scenes) { this.scenes = scenes; }
    public SaveOutcome save(DslScene scene) {
        var result = scenes.saveSceneToLocalDetailed(scene);
        if (!result.isSuccess()) return new SaveOutcome(result, SceneRefreshCoordinator.version(), null);
        try {
            SceneRefreshCoordinator.refresh();
            return new SaveOutcome(result, SceneRefreshCoordinator.version(), null);
        } catch (RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().error("Scene saved, but Ponder refresh failed; /ponderer reload can retry", error);
            com.nododiiiii.ponderer.feedback.ClientFeedback.chat(net.minecraft.network.chat.Component.translatable(
                "ponderer.ui.save_error.refresh_after_save", error.getMessage()));
            return new SaveOutcome(result, SceneRefreshCoordinator.version(), error);
        }
    }
}
