package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.workspace.api.PonderPackInfo;
import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.nododiiiii.ponderer.workspace.api.*;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Transitional product facade. Removed once all consumers use owned capabilities (stage 6). */
public final class SceneStore {
    private SceneStore() {}

    public static DslScene copyScene(@javax.annotation.Nullable DslScene scene) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().copyScene(scene);
    }

    public static Path getSceneDir() {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getSceneDir();
    }

    public static Path getStructureDir() {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getStructureDir();
    }

    public static Path getReadonlyCacheRoot() {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getReadonlyCacheRoot();
    }

    public static Path getPacksRoot() {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getPacksRoot();
    }

    public static Path getReadonlyCachePackDir(String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getReadonlyCachePackDir(packName);
    }

    public static Path getReadonlyCachePackSceneDir(String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getReadonlyCachePackSceneDir(packName);
    }

    public static Path getReadonlyCachePackStructureDir(String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getReadonlyCachePackStructureDir(packName);
    }

    public static Path getPackDir(String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getPackDir(packName);
    }

    public static Path getPackSceneDir(String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getPackSceneDir(packName);
    }

    public static Path getPackStructureDir(String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getPackStructureDir(packName);
    }

    public static Path getServerPackSceneDir(MinecraftServer server, String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getServerPackSceneDir(server, packName);
    }

    public static Path getServerPackStructureDir(MinecraftServer server, String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getServerPackStructureDir(server, packName);
    }

    public static Path getStructurePath(String path) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getStructurePath(path);
    }

    public static Path getStructurePath(String path) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getStructurePath(path);
    }

    public static Path resolveStructurePath(String path, @javax.annotation.Nullable String packName) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().resolveStructurePath(path, packName);
    }

    public static Path getServerSceneDir(MinecraftServer server) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getServerSceneDir(server);
    }

    public static Path getServerStructureDir(MinecraftServer server) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().getServerStructureDir(server);
    }

    public static Path resolveServerScenePath(MinecraftServer server, ResourceLocation id, @javax.annotation.Nullable String pack) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().resolveServerScenePath(server, id, pack);
    }

    public static Path resolveServerStructurePath(MinecraftServer server, ResourceLocation id,
            @javax.annotation.Nullable String pack) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().resolveServerStructurePath(server, id, pack);
    }

    public static Path resolveLocalSyncStructurePath(ResourceLocation id, @javax.annotation.Nullable String pack) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().resolveLocalSyncStructurePath(id, pack);
    }

    public static String displaySceneKey(String id, @javax.annotation.Nullable String pack) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().displaySceneKey(id, pack);
    }

    public static boolean saveToServer(MinecraftServer server, String sceneId, @javax.annotation.Nullable String pack, String json) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().saveToServer(server, sceneId, pack, json);
    }

    public static boolean saveStructureToServer(MinecraftServer server, String structureId, @javax.annotation.Nullable String pack,
            byte[] structureBytes) {
        return com.nododiiiii.ponderer.application.Workspaces.files().structures().saveStructureToServer(server, structureId, pack, structureBytes);
    }

    public static List<SyncFileRef> collectServerScriptRefs(MinecraftServer server) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().collectServerScriptRefs(server);
    }

    public static List<SyncFileRef> collectServerStructureRefs(MinecraftServer server) {
        return com.nododiiiii.ponderer.application.Workspaces.files().structures().collectServerStructureRefs(server);
    }

    static SyncFileRef toServerStructureRef(Path root, Path file, String ext, @javax.annotation.Nullable String explicitPack) {
        return com.nododiiiii.ponderer.application.Workspaces.files().structures().toServerStructureRef(root, file, ext, explicitPack);
    }

    public static Path resolveLocalScenePath(DslScene scene) {
        return com.nododiiiii.ponderer.application.Workspaces.files().layout().resolveLocalScenePath(scene);
    }

    public static Path findLocalSceneFile(String sceneId) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().findLocalSceneFile(sceneId);
    }

    public static Path findLocalSceneFile(String sceneId) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().findLocalSceneFile(sceneId);
    }

    public static synchronized LocalSaveResult saveSceneToLocalDetailed(DslScene scene) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().saveSceneToLocalDetailed(scene);
    }

    public static boolean saveSceneToLocal(DslScene scene) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().saveSceneToLocal(scene);
    }

    public static String serializeLocalScene(DslScene scene) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().serializeLocalScene(scene);
    }

    public static boolean deleteSceneLocal(String sceneId) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().deleteSceneLocal(sceneId);
    }

    public static boolean deleteSceneByKey(String sceneKey) {
        return com.nododiiiii.ponderer.application.Workspaces.files().scenes().deleteSceneByKey(sceneKey);
    }

    public static void extractDefaultsIfNeeded() {
        com.nododiiiii.ponderer.application.Workspaces.files().structures().extractDefaultsIfNeeded();
    }

    public static boolean ensureBuiltinStructure(String path) {
        return com.nododiiiii.ponderer.application.Workspaces.files().structures().ensureBuiltinStructure(path);
    }

    public static InputStream openBuiltinStructure(String path) {
        return com.nododiiiii.ponderer.application.Workspaces.files().structures().openBuiltinStructure(path);
    }

    public static boolean isBuiltinStructureName(String name) {
        return com.nododiiiii.ponderer.application.Workspaces.files().structures().isBuiltinStructureName(name);
    }

    public static int reloadFromDisk() {
        return com.nododiiiii.ponderer.application.SceneRefreshCoordinator.loadCatalog();
    }

    public static void sanitizeScene(DslScene scene) {
        com.nododiiiii.ponderer.application.Workspaces.files().scenes().sanitizeScene(scene);
    }

    public static PackExportResult packScenesAndStructuresDetailed(String name, String version, String author) {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().packScenesAndStructuresDetailed(name, version, author);
    }

    public static boolean packScenesAndStructures(String name, String version, String author) {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().packScenesAndStructures(name, version, author);
    }

    public static PackExportResult packSelectedScenesAndStructuresDetailed(String name, String version, String author, Set<String> selectedSceneIds) {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().packSelectedScenesAndStructuresDetailed(name, version, author, selectedSceneIds);
    }

    public static boolean packSelectedScenesAndStructures(String name, String version, String author, Set<String> selectedSceneIds) {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().packSelectedScenesAndStructures(name, version, author, selectedSceneIds);
    }

    public static List<PonderPackInfo> scanAvailableSourcePacks() {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().scanAvailableSourcePacks();
    }

    public static PackImportResult importPackFromResourcePack(Path zipPath) {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().importPackFromResourcePack(zipPath);
    }

    public static void collectStructureReferences(@javax.annotation.Nullable DslScene scene, Set<String> structures) {
        com.nododiiiii.ponderer.application.Workspaces.files().packs().collectStructureReferences(scene, structures);
    }

    public static PackUpdateInfo loadPonderPackFromResourcePack(Path zipPath, boolean forceOverwrite) throws IOException {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().loadPonderPackFromResourcePack(zipPath, forceOverwrite);
    }

    public static AutoLoadResult autoLoadPonderPacks() {
        return com.nododiiiii.ponderer.application.Workspaces.files().packs().autoLoadPonderPacks();
    }

    public static void removeOrphanedPacks(List<String> displayNames) {
        com.nododiiiii.ponderer.application.Workspaces.files().packs().removeOrphanedPacks(displayNames);
    }
}
