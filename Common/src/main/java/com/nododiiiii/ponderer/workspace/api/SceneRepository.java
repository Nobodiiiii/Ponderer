package com.nododiiiii.ponderer.workspace.api;

import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import java.util.List;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Owned workspace capability; operations never publish playback or UI state. */
public interface SceneRepository {
    DslScene copyScene(@javax.annotation.Nullable DslScene scene);

    String displaySceneKey(String id, @javax.annotation.Nullable String pack);

    boolean saveToServer(MinecraftServer server, String sceneId, @javax.annotation.Nullable String pack, String json);

    List<SyncFileRef> collectServerScriptRefs(MinecraftServer server);

    Path findLocalSceneFile(String sceneId);

    Path findLocalSceneFile(String sceneId, @javax.annotation.Nullable String pack);

    LocalSaveResult saveSceneToLocalDetailed(DslScene scene);

    boolean saveSceneToLocal(DslScene scene);

    String serializeLocalScene(DslScene scene);

    boolean deleteSceneLocal(String sceneId);

    boolean deleteSceneByKey(String sceneKey);

    List<DslScene> loadScenes();

    void sanitizeScene(DslScene scene);
}
