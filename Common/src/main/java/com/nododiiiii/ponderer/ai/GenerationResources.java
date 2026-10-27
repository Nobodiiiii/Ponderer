package com.nododiiiii.ponderer.ai;

import java.nio.file.Path;
import java.io.InputStream;

public interface GenerationResources {
    Path structureRoot();
    default Path promptDirectory() { return Path.of(System.getProperty("java.io.tmpdir"), "ponderer-test-config/ponderer/prompts"); }
    Path resolve(String relative, String pack);
    InputStream builtin(String path);

    static GenerationResources bundled() {
        return new GenerationResources() {
            public Path structureRoot() { return Path.of(".").toAbsolutePath(); }
            public Path resolve(String relative, String pack) { return null; }
            public InputStream builtin(String path) {
                return GenerationResources.class.getResourceAsStream("/data/ponderer/default_structures/" + path + ".nbt");
            }
        };
    }
}
