package com.nododiiiii.ponderer.workspace.api;

import com.nododiiiii.ponderer.scene.DslScene;
import java.util.List;

/** Storage capability; saving never publishes a runtime catalog. */
public interface SceneRepository {
    List<DslScene> loadScenes();
    WorkspaceResults.LocalSaveResult saveSceneToLocalDetailed(DslScene scene);
    boolean deleteSceneByKey(String sceneKey);
}
