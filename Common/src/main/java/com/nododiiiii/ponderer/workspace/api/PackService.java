package com.nododiiiii.ponderer.workspace.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface PackService {
    List<PonderPackInfo> scanAvailableSourcePacks();
    WorkspaceResults.PackImportResult importPackFromResourcePack(Path zipPath);
    WorkspaceResults.PackExportResult packSelectedScenesAndStructuresDetailed(
        String name, String version, String author, Set<String> selectedSceneIds);
    WorkspaceResults.AutoLoadResult autoLoadPonderPacks();
}
