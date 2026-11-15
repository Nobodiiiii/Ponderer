package com.nododiiiii.ponderer.exporter;

import com.nododiiiii.ponderer.scene.*;
import java.util.Map;
import java.util.LinkedHashMap;

/** The Lib modes share document relocation; only native Java exports assemble a host. */
final class LibExportSources {
    private LibExportSources() {}
    static boolean usesLib(JavaModuleScanResult.TargetProject target) { return target.mode != JavaModuleExportRequest.Mode.JAVA_SUPPORT; }
    static String extensionPackage(JavaModuleScanResult.TargetProject target) { return target.basePackage + ".ponderext"; }
    static String jsonPath(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan) {
        return "/assets/" + target.modId + "/ponderer/scenes/" + plan.className.toLowerCase(java.util.Locale.ROOT) + ".json";
    }
    static DslScene document(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan) {
        DslScene result = SceneCodec.copy(plan.sourceScene);
        result.pack = null;
        result.editable = false;
        Map<String, String> relocated = new LinkedHashMap<>();
        for (var asset : plan.structures) relocated.put(asset.sourceId, asset.targetResourceId);
        result.structures = result.structures == null ? java.util.List.of() : result.structures.stream().map(id -> relocate(id, relocated)).toList();
        result.structure = relocate(result.structure, relocated);
        var tags = new java.util.LinkedHashSet<String>();
        for (String id : plan.tags) tags.add(plan.customTags.containsKey(id) ? target.modId + ":" + id.substring(id.indexOf(':') + 1) : id);
        tags.add(target.modId + ":ponderer_exported");
        result.tags = java.util.List.copyOf(tags);
        result.customTags = new LinkedHashMap<>();
        plan.customTags.forEach((id, definition) -> result.customTags.put(target.modId + ":" + id.substring(id.indexOf(':') + 1), definition));
        if (result.scenes != null) for (int i = 0; i < result.scenes.size(); i++) {
            var segment = result.scenes.get(i);
            if (segment.steps == null) continue;
            for (var step : segment.steps) if (step != null) step.structure = relocate(step.structure, relocated);
        }
        return result;
    }
    private static String relocate(String id, Map<String, String> relocated) {
        if (id == null || id.isBlank()) return id;
        return relocated.getOrDefault(id.contains(":") ? id : "ponderer:" + id, id);
    }
    static String members(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan) {
        if (target.mode == JavaModuleExportRequest.Mode.JSON_LIB) throw new IllegalArgumentException("JSON content has no generated Java members");
        JavaValueEmitter emitter = new JavaValueEmitter();
        String value = emitter.value(document(target, plan));
        String load = emitter.statements() + "        return " + value + ";\n";
        return """
                private static final com.nododiiiii.ponderer.runtime.api.RuntimeEngine RUNTIME = createRuntime();
                private static com.nododiiiii.ponderer.scene.DslScene DOCUMENT;
                private static final com.nododiiiii.ponderer.runtime.api.NbtSceneFilter.Registration BINDINGS = new com.nododiiiii.ponderer.runtime.api.NbtSceneFilter.Registration();
                private static com.nododiiiii.ponderer.runtime.api.RuntimeEngine createRuntime() {
                    var engine = com.nododiiiii.ponderer.runtime.api.RuntimeEngine.create(%s,
                        new com.nododiiiii.ponderer.runtime.spi.AssetStructureSource(%s),
                        com.nododiiiii.ponderer.runtime.spi.IngredientResolver.NONE,
                        com.nododiiiii.ponderer.runtime.spi.RegistrationContribution.NONE, () -> false);
                    %s.PonderExtensions.install(engine);
                    return engine;
                }
                private static com.nododiiiii.ponderer.scene.DslScene loadDocument() {
            %s    }
                private static boolean reload() {
                    DOCUMENT = loadDocument();
                    RUNTIME.replaceCatalog(() -> java.util.List.of(com.nododiiiii.ponderer.scene.SceneSnapshot.of(DOCUMENT)));
                    var diagnostics = com.nododiiiii.ponderer.runtime.api.SceneRequirements.diagnostics(DOCUMENT, RUNTIME.steps());
                    if (!diagnostics.isEmpty()) { com.mojang.logging.LogUtils.getLogger().error("Scene unavailable: {}", diagnostics); return false; }
                    return true;
                }
                public static void registerSharedText(net.createmod.ponder.api.registration.SharedTextRegistrationHelper helper) {
                    RUNTIME.plugin().registerSharedText(helper);
                }
                public static void close() { BINDINGS.close(); RUNTIME.close(); }

            """.formatted(quote(target.modId), quote(target.modId), extensionPackage(target), load);
    }
    static String extension(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;

            /** Handwritten extension point. The exporter creates this file once and never overwrites it. */
            public final class PonderExtensions {
                private PonderExtensions() {}
                public static void install(RuntimeEngine engine) {
                    // Register compiled custom steps and anchored Java fragments here.
                    // Player enhancements are not requested by generated content.
                }
            }
            """.formatted(extensionPackage(target));
    }
    static String quote(String value) { return new com.google.gson.Gson().toJson(value); }
}
