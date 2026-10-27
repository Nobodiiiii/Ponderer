package com.nododiiiii.ponderer.application;

import com.nododiiiii.ponderer.application.api.SaveOutcome;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.workspace.api.SceneRepository;

/** Synchronous save use case. Callers retain control of the existing scheduled UI/index refresh. */
public final class SceneApplicationService {
    private final SceneRepository scenes;
    public SceneApplicationService(SceneRepository scenes) { this.scenes = scenes; }
    public SaveOutcome save(DslScene scene) {
        var result = scenes.saveSceneToLocalDetailed(scene);
        if (!result.isSuccess()) return new SaveOutcome(result, SceneRefreshCoordinator.version(), null);
        try {
            SceneRefreshCoordinator.loadCatalog();
            return new SaveOutcome(result, SceneRefreshCoordinator.version(), null);
        } catch (RuntimeException error) {
            return new SaveOutcome(result, SceneRefreshCoordinator.version(), error);
        }
    }
}
