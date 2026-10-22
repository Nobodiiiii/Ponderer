package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.ponder.DslScene;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable source material plus mutable per-run tool usage state.
 */
public final class AgentContext {

    public record StructureDocument(String id, StructureDescriber.StructureSnapshot snapshot) {
    }

    public record ReferenceDocument(String id, String url, @Nullable String text,
                                    List<WebPageFetcher.ImageData> images, @Nullable String error) {
        public boolean available() {
            return text != null;
        }
    }

    public record ExistingSceneDocument(@Nullable JsonObject json, @Nullable DslScene scene,
                                        @Nullable String error) {
        public boolean available() {
            return json != null && scene != null;
        }

        public boolean provided() {
            return available() || error != null;
        }
    }

    private final List<StructureDocument> structures;
    private final List<ReferenceDocument> references;
    private final ExistingSceneDocument existingScene;
    private final String carrierItemId;
    private final String userPrompt;
    private final boolean buildTutorial;
    private final boolean includeImages;
    private final AiSceneAgentPipeline.Request.EditMode editMode;
    private final AiDraftStore drafts;
    AiDraftStore drafts() { return drafts; }

    private final RunState runState = new RunState();

    public AgentContext(List<StructureDocument> structures, List<ReferenceDocument> references,
                        ExistingSceneDocument existingScene, String carrierItemId, String userPrompt,
                        boolean buildTutorial, boolean includeImages) {
        this(structures, references, existingScene, carrierItemId, userPrompt, buildTutorial, includeImages,
            AiSceneAgentPipeline.Request.EditMode.SCOPED);
    }

    public AgentContext(List<StructureDocument> structures, List<ReferenceDocument> references,
                        ExistingSceneDocument existingScene, String carrierItemId, String userPrompt,
                        boolean buildTutorial, boolean includeImages, AiSceneAgentPipeline.Request.EditMode editMode) {
        this.structures = List.copyOf(structures);
        this.references = List.copyOf(references);
        this.existingScene = existingScene;
        this.carrierItemId = carrierItemId;
        this.userPrompt = userPrompt;
        this.buildTutorial = buildTutorial;
        this.includeImages = includeImages;
        this.editMode = editMode;
        this.drafts = new AiDraftStore(existingScene.available() ? existingScene.json() : new JsonObject());
    }

    public List<StructureDocument> structures() {
        return structures;
    }

    public List<ReferenceDocument> references() {
        return references;
    }

    public ExistingSceneDocument existingScene() {
        return existingScene;
    }

    public String carrierItemId() {
        return carrierItemId;
    }

    public String userPrompt() {
        return userPrompt;
    }

    public AiSceneAgentPipeline.Request.EditMode editMode() { return editMode; }

    public boolean buildTutorial() {
        return buildTutorial;
    }

    public boolean includeImages() {
        return includeImages;
    }

    RunState runState() {
        return runState;
    }

    public static final class RunState {
        private List<String> clarifications = List.of();
        void clarifications(List<String> values) { clarifications = List.copyOf(values); }
        List<String> clarifications() { return clarifications; }
        private final java.util.LinkedHashSet<String> evidence = new java.util.LinkedHashSet<>();
        void evidence(String name, JsonObject arguments) {
            if (java.util.Set.of("review_scene", "submit_scene", "finish_task", "apply_scene_patch").contains(name)) return;
            String key = name + ":" + arguments;
            if (key.length() <= 2048) evidence.add(key);
            while (evidence.size() > 16) evidence.remove(evidence.iterator().next());
        }
        List<String> evidence() { return List.copyOf(evidence); }
        private boolean queriedStructure;
        private boolean searchedReferences;
        private boolean inspectedExistingScene;
        private final java.util.Set<String> inspectedSegments = new java.util.HashSet<>();
        void markSegmentInspected(String id) { inspectedSegments.add(id); }
        boolean inspectedSegment(String id) { return inspectedSegments.contains(id); }
        private JsonObject editScope;
        private int validationAttempts;
        private String stopStatus;
        private String stopReason;
        void finish(String status, String reason) { stopStatus = status; stopReason = reason; }
        String stopStatus() { return stopStatus; }
        String stopReason() { return stopReason; }

        JsonObject editScope() { return editScope; }
        void setEditScope(JsonObject scope) { editScope = scope.deepCopy(); }
        private List<AiSceneValidator.Issue> lastErrors = List.of();
        private List<AiSceneQualityLinter.Warning> lastWarnings = List.of();

        void markStructureQueried() {
            queriedStructure = true;
        }

        void markReferencesSearched() {
            searchedReferences = true;
        }

        void markExistingSceneInspected() {
            inspectedExistingScene = true;
        }

        public boolean queriedStructure() {
            return queriedStructure;
        }

        public boolean searchedReferences() {
            return searchedReferences;
        }

        public boolean inspectedExistingScene() {
            return inspectedExistingScene;
        }

        void recordReview(List<AiSceneValidator.Issue> errors, List<AiSceneQualityLinter.Warning> warnings) {
            validationAttempts++;
            lastErrors = List.copyOf(errors);
            lastWarnings = List.copyOf(warnings);
        }

        public int validationAttempts() {
            return validationAttempts;
        }

        public List<AiSceneValidator.Issue> lastErrors() {
            return lastErrors;
        }

        public List<AiSceneQualityLinter.Warning> lastWarnings() {
            return lastWarnings;
        }
    }
}
