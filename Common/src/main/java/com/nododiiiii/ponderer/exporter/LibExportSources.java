package com.nododiiiii.ponderer.exporter;

import com.nododiiiii.ponderer.scene.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** The Lib modes share document relocation; only native Java exports assemble a host. */
final class LibExportSources {
    private LibExportSources() {}
    static boolean usesLib(JavaModuleScanResult.TargetProject target) { return target.mode != JavaModuleExportRequest.Mode.JAVA_SUPPORT; }
    static String documentId(JavaModuleScanResult.TargetProject target, String id) {
        return target.mode == JavaModuleExportRequest.Mode.JAVA_LIB
            ? target.modId + ":" + id.substring(id.indexOf(':') + 1) : id;
    }
    static String extensionPackage(JavaModuleScanResult.TargetProject target) { return target.basePackage + ".ponderext"; }
    static String jsonPath(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan) {
        return "/assets/" + target.modId + "/ponderer/scenes/" + plan.className.toLowerCase(java.util.Locale.ROOT) + ".json";
    }
    static DslScene document(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan) {
        DslScene result = SceneCodec.copy(plan.sourceScene);
        result.internalId = plan.sourceScene.identity();
        result.exported = true;
        result.pack = null;
        result.editable = target.mode == JavaModuleExportRequest.Mode.JAVA_LIB;
        result.id = documentId(target, result.id);
        result.runtimeSource = target.mode == JavaModuleExportRequest.Mode.JAVA_LIB ? result.id : null;
        Map<String, String> relocated = new LinkedHashMap<>();
        for (var asset : plan.structures) relocated.put(asset.sourceId, asset.targetResourceId);
        result.structures = result.structures == null ? java.util.List.of() : result.structures.stream().map(id -> relocate(id, relocated)).toList();
        result.structure = relocate(result.structure, relocated);
        var tags = new java.util.LinkedHashSet<String>();
        for (String id : plan.tags) tags.add(plan.customTags.containsKey(id) ? target.modId + ":" + id.substring(id.indexOf(':') + 1) : id);
        tags.add(target.modId + ":ponderer_exported");
        result.tags = java.util.List.copyOf(tags);
        result.customTags = new LinkedHashMap<>();
        plan.customTags.forEach((id, definition) -> {
            var copy = new DslScene.CustomTag();
            copy.internalId = definition.internalId == null ? id : definition.internalId;
            copy.title = definition.title;
            copy.description = definition.description;
            copy.icon = definition.icon;
            result.customTags.put(target.modId + ":" + id.substring(id.indexOf(':') + 1), copy);
        });
        if (result.scenes != null) for (int i = 0; i < result.scenes.size(); i++) {
            var segment = result.scenes.get(i);
            segment.structure = relocate(segment.structure, relocated);
            segment.steps = exportedSteps(segment.steps);
            if (segment.steps == null) continue;
            for (var step : segment.steps) if (step != null) step.structure = relocate(step.structure, relocated);
        }
        return result;
    }
    private static List<DslScene.DslStep> exportedSteps(List<DslScene.DslStep> steps) {
        if (steps == null) return null;
        var result = new ArrayList<DslScene.DslStep>();
        for (var step : steps) {
            if (step != null) {
                if ("shared_text".equalsIgnoreCase(step.type)) continue;
                step.steps = exportedSteps(step.steps);
            }
            result.add(step);
        }
        return result;
    }
    private static String relocate(String id, Map<String, String> relocated) {
        if (id == null || id.isBlank()) return id;
        return relocated.getOrDefault(id.contains(":") ? id : "ponderer:" + id, id);
    }
    static String members(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan, DslScene document) {
        if (target.mode == JavaModuleExportRequest.Mode.JSON_LIB) throw new IllegalArgumentException("JSON content has no generated Java members");
        return """
                private static final RuntimeEngine RUNTIME = createRuntime();
                private static final EditableScenes.Registration SOURCE =
                    EditableScenes.register(RUNTIME, %s::loadDocument);
                private static DslScene DOCUMENT;
                private static RuntimeEngine createRuntime() {
                    var engine = RuntimeEngine.create(%s,
                        new AssetStructureSource(%s), IngredientResolver.NONE,
                        RegistrationContribution.NONE, () -> true);
                    %s.PonderExtensions.install(engine);
                    return engine;
                }
            %s
                private static boolean reload() {
                    try { DOCUMENT = SOURCE.reload(); return true; }
                    catch (RuntimeException error) {
                        com.mojang.logging.LogUtils.getLogger().error("Java scene unavailable", error);
                        return false;
                    }
                }
                public static void prepare() { reload(); }
                public static void close() { RUNTIME.close(); }

            """.formatted(plan.className, quote(target.modId), quote(target.modId), extensionPackage(target), JavaSceneSources.members(document));
    }
    static String extension(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;

            /** Handwritten extension point. The exporter creates this file once and never overwrites it. */
            public final class PonderExtensions {
                private PonderExtensions() {}
                public static void install(RuntimeEngine engine) {
                    // Register compiled custom steps and scene-boundary Java fragments here.
                    // Player enhancements are not requested by generated content.
                }
            }
            """.formatted(extensionPackage(target));
    }
    static String quote(String value) { return new com.google.gson.Gson().toJson(value); }
}
