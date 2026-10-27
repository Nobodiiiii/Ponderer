package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.ai.*;
import com.nododiiiii.ponderer.Config;
import java.nio.file.Path;
import java.io.InputStream;

public final class ProductGeneration {
    private ProductGeneration() {}
    public static AiSceneAgentPipeline pipeline() {
        return new AiSceneAgentPipeline(new GenerationResources() {
            public Path promptDirectory() { return com.nododiiiii.ponderer.platform.PondererServices.PLATFORM.getConfigDir().resolve("ponderer/prompts"); }
            public Path structureRoot() { return com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().getStructureDir(); }
            public Path resolve(String relative, String pack) { return com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().layout().resolveStructurePath(relative, pack); }
            public InputStream builtin(String path) { return com.nododiiiii.ponderer.workspace.api.WorkspaceAccess.get().structures().openBuiltinStructure(path); }
        }, () -> Config.AI_MAX_AGENT_TURNS.get());
    }
}
