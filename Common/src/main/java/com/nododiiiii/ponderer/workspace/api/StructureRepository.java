package com.nododiiiii.ponderer.workspace.api;

import net.minecraft.server.MinecraftServer;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.*;

/** Owned workspace capability; operations never publish playback or UI state. */
public interface StructureRepository {
    boolean saveStructureToServer(MinecraftServer server, String structureId, @javax.annotation.Nullable String pack,
            byte[] structureBytes);

    List<SyncFileRef> collectServerStructureRefs(MinecraftServer server);

    SyncFileRef toServerStructureRef(Path root, Path file, String ext, @javax.annotation.Nullable String explicitPack);

    void extractDefaultsIfNeeded();

    boolean ensureBuiltinStructure(String path);

    InputStream openBuiltinStructure(String path);

    boolean isBuiltinStructureName(String name);
}
