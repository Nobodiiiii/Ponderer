package com.nododiiiii.ponderer.ponder;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaModuleScanResult {

    public static class TargetProject {
        public String loader;
        public String modId;
        public String basePackage;
        public String generatedPackage;
        public String scenePackage;
        public Path targetRoot;
        public Path javaRoot;
        public Path resourcesRoot;
        @Nullable
        public Path metadataPath;
    }

    public static class Finding {
        public String category;
        @Nullable
        public String sceneId;
        @Nullable
        public String sceneKey;
        @Nullable
        public String segmentId;
        @Nullable
        public String stepType;
        public String message;
    }

    public transient TargetProject targetProject;
    public transient List<ScenePlan> scenePlans = new ArrayList<>();

    public List<Finding> fatalFindings = new ArrayList<>();
    public List<Finding> sceneUnsupportedFindings = new ArrayList<>();
    public List<Finding> warningFindings = new ArrayList<>();
    public List<Finding> ignoredFindings = new ArrayList<>();

    public boolean hasFatalFindings() {
        return !fatalFindings.isEmpty();
    }

    public boolean hasSkippableFindings() {
        return !sceneUnsupportedFindings.isEmpty() || !warningFindings.isEmpty();
    }

    public int selectedSceneCount() {
        return scenePlans.size();
    }

    static class ScenePlan {
        transient DslScene sourceScene;
        String sceneId;
        String sceneKey;
        String className;
        String storyboardBasePath;
        List<String> componentItems = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        List<StructureAsset> structures = new ArrayList<>();
        List<SegmentPlan> segments = new ArrayList<>();
        boolean skippedScene;
        boolean partial;
        boolean blank;
        boolean hadSupportedSteps;
        boolean hadIgnoredSharedText;
        boolean hadMetaDrops;
        boolean hadOmittedSteps;
        List<String> messages = new ArrayList<>();
    }

    static class SegmentPlan {
        transient DslScene.SceneSegment sourceSegment;
        String methodName;
        String storyboardPath;
        String titlePath;
        @Nullable
        StructureAsset schematic;
        List<GeneratedStep> steps = new ArrayList<>();
        List<String> omittedStepTypes = new ArrayList<>();
        boolean blankSegment;
    }

    static class GeneratedStep {
        transient DslScene.DslStep sourceStep;
        boolean omitControlItem;
    }

    static class StructureAsset {
        String sourceId;
        Path sourcePath;
        String targetResourceId;
        String targetRelativePath;
    }
}
