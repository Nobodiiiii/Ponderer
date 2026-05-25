package com.nododiiiii.ponderer.ponder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaModuleExportManifest {
    public int version = 2;
    public String loader;
    public String modId;
    public String basePackage;
    public String generatedPackage;
    public Map<String, SceneEntry> scenes = new LinkedHashMap<>();

    public static class SceneEntry {
        public String sceneId;
        public String sceneKey;
        public String className;
        public String status;
        public String contentHash;
        public List<String> javaFiles = new ArrayList<>();
        public List<String> resourceFiles = new ArrayList<>();
        public Map<String, Map<String, String>> langEntries = new LinkedHashMap<>();
    }
}
