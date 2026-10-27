package com.nododiiiii.ponderer.workspace.api;

import com.nododiiiii.ponderer.scene.DslScene;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Owned workspace capability; operations never publish playback or UI state. */
public interface WorkspaceLayoutView {
    Path getSceneDir();

    Path getStructureDir();

    Path getReadonlyCacheRoot();

    Path getPacksRoot();

    Path getReadonlyCachePackDir(String packName);

    Path getReadonlyCachePackSceneDir(String packName);

    Path getReadonlyCachePackStructureDir(String packName);

    Path getPackDir(String packName);

    Path getPackSceneDir(String packName);

    Path getPackStructureDir(String packName);

    Path getServerPackSceneDir(MinecraftServer server, String packName);

    Path getServerPackStructureDir(MinecraftServer server, String packName);

    Path getStructurePath(String path);

    Path getStructurePath(ResourceLocation id);

    Path resolveStructurePath(String path, @javax.annotation.Nullable String packName);

    Path getServerSceneDir(MinecraftServer server);

    Path getServerStructureDir(MinecraftServer server);

    Path resolveServerScenePath(MinecraftServer server, ResourceLocation id, @javax.annotation.Nullable String pack);

    Path resolveServerStructurePath(MinecraftServer server, ResourceLocation id,
            @javax.annotation.Nullable String pack);

    Path resolveLocalSyncStructurePath(ResourceLocation id, @javax.annotation.Nullable String pack);

    Path resolveLocalScenePath(DslScene scene);
}
