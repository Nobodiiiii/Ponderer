package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.ai.*;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.SceneStore;
import java.nio.file.Path;
import java.io.InputStream;

public final class ProductGeneration {
    private ProductGeneration() {}
    public static AiSceneAgentPipeline pipeline() {
        return new AiSceneAgentPipeline(new GenerationResources() {
            public Path structureRoot() { return SceneStore.getStructureDir(); }
            public Path resolve(String relative, String pack) { return SceneStore.resolveStructurePath(relative, pack); }
            public InputStream builtin(String path) { return SceneStore.openBuiltinStructure(path); }
        }, () -> Config.AI_MAX_AGENT_TURNS.get());
    }
}
