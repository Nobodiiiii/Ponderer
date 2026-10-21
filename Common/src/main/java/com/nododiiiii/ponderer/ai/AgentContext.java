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
    private final RunState runState = new RunState();

    public AgentContext(List<StructureDocument> structures, List<ReferenceDocument> references,
                        ExistingSceneDocument existingScene, String carrierItemId, String userPrompt,
                        boolean buildTutorial, boolean includeImages) {
        this.structures = List.copyOf(structures);
        this.references = List.copyOf(references);
        this.existingScene = existingScene;
        this.carrierItemId = carrierItemId;
        this.userPrompt = userPrompt;
        this.buildTutorial = buildTutorial;
        this.includeImages = includeImages;
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
        private boolean queriedStructure;
        private boolean searchedReferences;
        private boolean inspectedExistingScene;
        private JsonObject editScope;
        private int validationAttempts;

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
