package com.nododiiiii.ponderer.workspace.api;

import com.nododiiiii.ponderer.scene.DslScene;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Owned workspace capability; operations never publish playback or UI state. */
public interface PackService {
    PackExportResult packScenesAndStructuresDetailed(String name, String version, String author);

    boolean packScenesAndStructures(String name, String version, String author);

    PackExportResult packSelectedScenesAndStructuresDetailed(String name, String version, String author, Set<String> selectedSceneIds);

    boolean packSelectedScenesAndStructures(String name, String version, String author, Set<String> selectedSceneIds);

    List<PonderPackInfo> scanAvailableSourcePacks();

    PackImportResult importPackFromResourcePack(Path zipPath);

    void collectStructureReferences(@javax.annotation.Nullable DslScene scene, Set<String> structures);

    PackUpdateInfo loadPonderPackFromResourcePack(Path zipPath, boolean forceOverwrite) throws IOException;

    AutoLoadResult autoLoadPonderPacks();

    void removeOrphanedPacks(List<String> displayNames);
}
