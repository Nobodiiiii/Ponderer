package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JavaModuleExportService {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    private static final Set<String> SUPPORTED_STEP_TYPES = Set.of(
        "show_structure",
        "show_extra_structure",
        "idle",
        "text",
        "create_entity",
        "create_item_entity",
        "rotate_camera_y",
        "highlight_section",
        "show_controls",
        "encapsulate_bounds",
        "play_sound",
        "set_block",
        "destroy_block",
        "replace_blocks",
        "hide_section",
        "show_section_and_merge",
        "rotate_section",
        "move_section",
        "toggle_redstone_power",
        "modify_block_entity_nbt",
        "indicate_redstone",
        "indicate_success",
        "clear_entities",
        "clear_item_entities",
        "modify_entities_nbt",
        "modify_item_entities_nbt",
        "next_scene"
    );

    private static final Set<String> WARNING_STEP_TYPES = Set.of(
        "show_interface",
        "click_interface",
        "change_interface_slot",
        "zoom_scene"
    );

    private static final Set<String> IGNORED_STEP_TYPES = Set.of("shared_text");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;", Pattern.MULTILINE);
    private static final Pattern MOD_ANNOTATION_PATTERN = Pattern.compile("@Mod\\s*\\(");
    private static final String GENERATED_DIR = ".ponderer-export";
    private static final String MANIFEST_NAME = "manifest.json";
    private static final String REPORT_NAME = "last-report.json";
    private static final String ATTRIBUTION_TAG_PATH = "ponderer_exported";
    private static final String ATTRIBUTION_TITLE_EN_US = "The Ponderer...";
    private static final String ATTRIBUTION_TITLE_ZH_CN = "思索者...";
    private static final String ATTRIBUTION_DESCRIPTION_EN_US =
        "This mod's Ponder scenes were created and exported with The Ponderer. Learn more: https://modrinth.com/mod/the-ponderer";
    private static final String ATTRIBUTION_DESCRIPTION_ZH_CN =
        "以上思索由模组思索者（Ponderer）制作并导出，了解更多：https://modrinth.com/mod/the-ponderer";

    private JavaModuleExportService() {
    }

    public static JavaModuleScanResult scan(JavaModuleExportRequest request) {
        JavaModuleScanResult result = new JavaModuleScanResult();
        if (request == null || request.targetRoot() == null) {
            addFinding(result.fatalFindings, "fatal", null, null, null, null, "Target mod root is required.");
            return result;
        }

        JavaModuleScanResult.TargetProject targetProject = detectTargetProject(request.targetRoot(), result.fatalFindings);
        result.targetProject = targetProject;
        if (targetProject == null) {
            return result;
        }

        List<DslScene> scenes = collectRequestedScenes(request, result.fatalFindings);
        if (scenes.isEmpty()) {
            addFinding(result.fatalFindings, "fatal", null, null, null, null, "No exportable scenes were found.");
            return result;
        }

        for (DslScene scene : scenes) {
            JavaModuleScanResult.ScenePlan plan = scanScene(targetProject, scene, result);
            result.scenePlans.add(plan);
        }

        return result;
    }

    public static JavaModuleExportResult export(JavaModuleExportRequest request, JavaModuleScanResult scanResult) {
        if (request == null || scanResult == null) {
            return JavaModuleExportResult.failure("Missing export request or scan result.");
        }
        if (scanResult.hasFatalFindings()) {
            return JavaModuleExportResult.failure(firstMessage(scanResult.fatalFindings, "Scan contains fatal problems."));
        }
        if (scanResult.targetProject == null) {
            return JavaModuleExportResult.failure("Target project information is unavailable.");
        }

        JavaModuleExportManifest manifest = readManifest(scanResult.targetProject.targetRoot);
        applyTargetDefaults(manifest, scanResult.targetProject);

        List<TextFile> textWrites = new ArrayList<>();
        List<BinaryFile> binaryWrites = new ArrayList<>();
        List<Path> deletions = new ArrayList<>();
        List<SceneExportOutcome> outcomes = new ArrayList<>();
        Map<String, Set<String>> staleManagedLangKeys = new LinkedHashMap<>();

        for (JavaModuleScanResult.ScenePlan scenePlan : scanResult.scenePlans) {
            JavaModuleExportManifest.SceneEntry existing = manifest.scenes.remove(scenePlan.sceneId);
            if (existing != null) {
                collectOwnedPaths(scanResult.targetProject.targetRoot, existing.javaFiles, deletions);
                collectOwnedPaths(scanResult.targetProject.targetRoot, existing.resourceFiles, deletions);
                collectManagedLangKeys(existing, staleManagedLangKeys);
            }

            SceneExportOutcome outcome = new SceneExportOutcome();
            outcome.sceneId = scenePlan.sceneId;
            outcome.sceneKey = scenePlan.sceneKey;
            outcome.className = scenePlan.className;
            outcome.replaced = existing != null;
            outcome.messages.addAll(scenePlan.messages);
            outcome.blankSegments = (int) scenePlan.segments.stream().filter(seg -> seg.blankSegment).count();
            outcome.exportedSegments = scenePlan.segments.size();
            outcome.omittedSteps = scenePlan.segments.stream().mapToInt(seg -> seg.omittedStepTypes.size()).sum();

            if (scenePlan.skippedScene) {
                outcome.status = SceneExportOutcome.Status.SKIPPED_SCENE;
                outcomes.add(outcome);
                continue;
            }

            RenderedScene rendered = renderScene(scanResult.targetProject, scenePlan, outcome);
            textWrites.add(rendered.sceneJava());
            binaryWrites.addAll(rendered.structureFiles());
            manifest.scenes.put(scenePlan.sceneId, rendered.manifestEntry());
            outcome.ownedFiles.add(rendered.sceneJava().relativePath());
            rendered.structureFiles().forEach(file -> outcome.ownedFiles.add(file.relativePath()));
            outcomes.add(outcome);
        }

        List<TextFile> globalWrites = buildGlobalWrites(scanResult.targetProject, manifest, staleManagedLangKeys);
        textWrites.addAll(globalWrites);

        Path exportDir = scanResult.targetProject.targetRoot.resolve(GENERATED_DIR).normalize();
        Path manifestPath = exportDir.resolve(MANIFEST_NAME).normalize();
        Path reportPath = exportDir.resolve(REPORT_NAME).normalize();
        textWrites.add(new TextFile(relativeToRoot(scanResult.targetProject.targetRoot, manifestPath), GSON.toJson(manifest)));

        ExportReport report = new ExportReport();
        report.loader = scanResult.targetProject.loader;
        report.modId = scanResult.targetProject.modId;
        report.basePackage = scanResult.targetProject.basePackage;
        report.targetRoot = scanResult.targetProject.targetRoot.toString();
        report.fatalFindings = scanResult.fatalFindings;
        report.sceneUnsupportedFindings = scanResult.sceneUnsupportedFindings;
        report.warningFindings = scanResult.warningFindings;
        report.ignoredFindings = scanResult.ignoredFindings;
        report.sceneOutcomes = outcomes;

        try {
            ensureInsideRoot(scanResult.targetProject.targetRoot, exportDir);
            Files.createDirectories(exportDir);

            for (Path deletion : deletions.stream().distinct().toList()) {
                deleteIfExists(scanResult.targetProject.targetRoot, deletion);
            }

            if ("fabric".equals(scanResult.targetProject.loader) && scanResult.targetProject.metadataPath != null) {
                updateFabricMetadata(scanResult.targetProject);
            }

            for (TextFile write : textWrites) {
                Path target = resolveTargetPath(scanResult.targetProject.targetRoot, write.relativePath());
                Files.createDirectories(target.getParent());
                Files.writeString(target, write.content(), StandardCharsets.UTF_8);
            }

            for (BinaryFile write : binaryWrites) {
                Path target = resolveTargetPath(scanResult.targetProject.targetRoot, write.relativePath());
                Files.createDirectories(target.getParent());
                Files.write(target, write.bytes());
            }

            Files.writeString(reportPath, GSON.toJson(report), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return JavaModuleExportResult.failure("Failed to export Java module: " + e.getMessage());
        }

        JavaModuleExportResult result = new JavaModuleExportResult();
        result.success = true;
        result.reportPath = reportPath;
        result.sceneOutcomes.addAll(outcomes);
        result.addedCount = (int) outcomes.stream().filter(outcome -> !outcome.replaced && outcome.status != SceneExportOutcome.Status.SKIPPED_SCENE).count();
        result.replacedCount = (int) outcomes.stream().filter(outcome -> outcome.replaced && outcome.status != SceneExportOutcome.Status.SKIPPED_SCENE).count();
        result.partialCount = (int) outcomes.stream().filter(outcome -> outcome.status == SceneExportOutcome.Status.PARTIAL).count();
        result.blankCount = (int) outcomes.stream().filter(outcome -> outcome.status == SceneExportOutcome.Status.BLANK).count();
        result.skippedCount = (int) outcomes.stream().filter(outcome -> outcome.status == SceneExportOutcome.Status.SKIPPED_SCENE).count();
        return result;
    }

    @Nullable
    private static JavaModuleScanResult.TargetProject detectTargetProject(Path targetRoot,
                                                                          List<JavaModuleScanResult.Finding> fatals) {
        Path normalizedRoot = targetRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            addFinding(fatals, "fatal", null, null, null, null, "Target root is not a directory: " + normalizedRoot);
            return null;
        }

        Path javaRoot = normalizedRoot.resolve("src/main/java").normalize();
        Path resourcesRoot = normalizedRoot.resolve("src/main/resources").normalize();
        if (!Files.isDirectory(javaRoot)) {
            addFinding(fatals, "fatal", null, null, null, null, "Missing src/main/java in target module.");
        }
        if (!Files.isDirectory(resourcesRoot)) {
            addFinding(fatals, "fatal", null, null, null, null, "Missing src/main/resources in target module.");
        }
        if (!fatals.isEmpty()) {
            return null;
        }

        Path fabricMetadata = resourcesRoot.resolve("fabric.mod.json").normalize();
        Path forgeMetadata = normalizedRoot.resolve("src/main/templates/META-INF/mods.toml").normalize();
        if (!Files.isRegularFile(forgeMetadata)) {
            forgeMetadata = resourcesRoot.resolve("META-INF/mods.toml").normalize();
        }

        boolean hasFabric = Files.isRegularFile(fabricMetadata);
        boolean hasForge = Files.isRegularFile(forgeMetadata);
        if (hasFabric == hasForge) {
            addFinding(fatals, "fatal", null, null, null, null,
                hasFabric
                    ? "Target root contains both Fabric and Forge metadata. Select a single loader module root."
                    : "Unable to detect Fabric or Forge metadata in the target module.");
            return null;
        }

        Properties properties = loadGradleProperties(normalizedRoot);
        String loader = hasFabric ? "fabric" : "forge";
        String modId = hasFabric ? parseFabricModId(fabricMetadata, properties) : parseForgeModId(forgeMetadata, properties);
        if (modId == null || modId.isBlank()) {
            addFinding(fatals, "fatal", null, null, null, null, "Unable to detect the target mod id.");
            return null;
        }

        String basePackage = hasFabric
            ? parseFabricBasePackage(fabricMetadata, properties)
            : parseForgeBasePackage(javaRoot);
        if (basePackage == null || basePackage.isBlank()) {
            basePackage = findFirstPackage(javaRoot);
        }
        if (basePackage == null || basePackage.isBlank()) {
            addFinding(fatals, "fatal", null, null, null, null, "Unable to detect the target base package.");
            return null;
        }

        if (hasFabric && (!Files.exists(fabricMetadata) || !Files.isWritable(fabricMetadata))) {
            addFinding(fatals, "fatal", null, null, null, null, "fabric.mod.json is not writable.");
            return null;
        }

        JavaModuleScanResult.TargetProject project = new JavaModuleScanResult.TargetProject();
        project.loader = loader;
        project.modId = modId;
        project.basePackage = basePackage;
        project.generatedPackage = basePackage + ".ponder.generated";
        project.scenePackage = project.generatedPackage + ".scenes";
        project.targetRoot = normalizedRoot;
        project.javaRoot = javaRoot;
        project.resourcesRoot = resourcesRoot;
        project.metadataPath = hasFabric ? fabricMetadata : forgeMetadata;
        return project;
    }

    private static List<DslScene> collectRequestedScenes(JavaModuleExportRequest request,
                                                         List<JavaModuleScanResult.Finding> fatals) {
        List<DslScene> runtimeScenes = SceneRuntime.getScenes();
        if (runtimeScenes.isEmpty()) {
            addFinding(fatals, "fatal", null, null, null, null, "No scenes are currently loaded.");
            return List.of();
        }

        if (request.exportAllLocalScenes()) {
            return runtimeScenes.stream()
                .filter(JavaModuleExportService::isLocalEditableScene)
                .collect(Collectors.toCollection(ArrayList::new));
        }

        Set<String> selectedKeys = request.selectedSceneKeys();
        return runtimeScenes.stream()
            .filter(scene -> scene != null && scene.id != null && selectedKeys.contains(scene.sceneKey()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean isLocalEditableScene(DslScene scene) {
        if (scene == null || scene.id == null || scene.id.isBlank()) {
            return false;
        }
        Path path = SceneStore.resolveLocalScenePath(scene);
        return path != null && Files.exists(path);
    }

    private static JavaModuleScanResult.ScenePlan scanScene(JavaModuleScanResult.TargetProject target,
                                                            DslScene scene,
                                                            JavaModuleScanResult result) {
        JavaModuleScanResult.ScenePlan plan = new JavaModuleScanResult.ScenePlan();
        plan.sourceScene = scene;
        plan.sceneId = scene.id;
        plan.sceneKey = scene.sceneKey();
        plan.className = classNameFor(scene.id);
        plan.storyboardBasePath = storyboardBasePath(scene.id);

        for (String itemId : safeList(scene.items)) {
            ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
            if (itemLoc != null) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(Items.AIR);
                if (item != Items.AIR) {
                    plan.componentItems.add(itemLoc.toString());
                }
            }
        }
        if (plan.componentItems.isEmpty()) {
            plan.skippedScene = true;
            plan.messages.add("Scene has no valid components to register.");
            addFinding(result.sceneUnsupportedFindings, "scene-level unsupported", scene.id, scene.sceneKey(),
                null, null, "Scene has no valid components to register.");
            return plan;
        }

        for (String tagId : safeList(scene.tags)) {
            ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
            if (tagLoc != null) {
                plan.tags.add(tagLoc.toString());
            }
        }

        collectSceneMetaWarnings(scene, result, plan);

        List<DslScene.SceneSegment> segments = normalizeSegments(scene);
        List<StructureResolution> segmentSchematics = resolveSegmentSchematics(target, scene, segments, result, plan);
        if (segmentSchematics.stream().noneMatch(Objects::nonNull)) {
            plan.skippedScene = true;
            plan.messages.add("Scene has no resolvable structure references.");
            addFinding(result.sceneUnsupportedFindings, "scene-level unsupported", scene.id, scene.sceneKey(),
                null, null, "Scene has no resolvable structure references.");
            return plan;
        }

        int supportedStepCount = 0;
        int unsupportedOnlyStepCount = 0;
        for (int index = 0; index < segments.size(); index++) {
            DslScene.SceneSegment segment = segments.get(index);
            JavaModuleScanResult.SegmentPlan segmentPlan = new JavaModuleScanResult.SegmentPlan();
            segmentPlan.sourceSegment = segment;
            segmentPlan.methodName = "storyboard$" + index;
            segmentPlan.titlePath = storyboardPath(scene, segment, index, segments.size());
            segmentPlan.storyboardPath = segmentPlan.titlePath;
            segmentPlan.schematic = index < segmentSchematics.size() ? toStructureAsset(segmentSchematics.get(index)) : null;
            if (segmentPlan.schematic != null) {
                plan.structures.add(segmentPlan.schematic);
            }

            for (DslScene.DslStep step : safeSteps(segment.steps)) {
                if (step == null || step.type == null) {
                    continue;
                }
                String stepType = step.type.toLowerCase(Locale.ROOT);
                if ("next_scene".equals(stepType)) {
                    continue;
                }
                if (IGNORED_STEP_TYPES.contains(stepType)) {
                    plan.hadIgnoredSharedText = true;
                    addFinding(result.ignoredFindings, "ignored-by-design", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "shared_text is intentionally omitted from Java module export.");
                    continue;
                }
                if (WARNING_STEP_TYPES.contains(stepType)) {
                    plan.partial = true;
                    plan.hadOmittedSteps = true;
                    segmentPlan.omittedStepTypes.add(stepType);
                    unsupportedOnlyStepCount++;
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Unsupported step will be omitted: " + stepType);
                    continue;
                }
                if (!SUPPORTED_STEP_TYPES.contains(stepType)) {
                    plan.partial = true;
                    plan.hadOmittedSteps = true;
                    segmentPlan.omittedStepTypes.add(stepType);
                    unsupportedOnlyStepCount++;
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Unknown step will be omitted: " + stepType);
                    continue;
                }
                if (!isGeneratableStep(step)) {
                    plan.partial = true;
                    plan.hadOmittedSteps = true;
                    segmentPlan.omittedStepTypes.add(stepType);
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Step is missing required data and will be omitted: " + stepType);
                    continue;
                }

                JavaModuleScanResult.GeneratedStep generatedStep = new JavaModuleScanResult.GeneratedStep();
                generatedStep.sourceStep = step;
                if ("show_controls".equals(stepType) && step.item != null && !step.item.isBlank()
                    && !isItemStackSpec(step.item, step.nbt)) {
                    generatedStep.omitControlItem = true;
                    plan.partial = true;
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Non-item show_controls ingredient will be omitted.");
                }
                if ("show_extra_structure".equals(stepType)) {
                    StructureResolution extraResolution = resolveStructureReference(target, scene, step.structure);
                    if (extraResolution == null) {
                        plan.partial = true;
                        plan.hadOmittedSteps = true;
                        segmentPlan.omittedStepTypes.add(stepType);
                        addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                            stepType, "show_extra_structure structure could not be resolved: " + step.structure);
                        continue;
                    }
                    plan.structures.add(toStructureAsset(extraResolution));
                    generatedStep.extraStructureResourceId = target.modId
                        + ":ponder/generated/" + extraResolution.sourceId.getNamespace()
                        + "/" + extraResolution.sourceId.getPath() + ".nbt";
                }
                segmentPlan.steps.add(generatedStep);
                supportedStepCount++;
            }

            segmentPlan.blankSegment = segmentPlan.steps.isEmpty();
            if (segmentPlan.blankSegment) {
                plan.partial = plan.partial || !segmentPlan.omittedStepTypes.isEmpty();
            }
            plan.segments.add(segmentPlan);
        }

        plan.hadSupportedSteps = supportedStepCount > 0;
        if (!plan.hadSupportedSteps && unsupportedOnlyStepCount > 0) {
            plan.skippedScene = true;
            plan.messages.add("Scene only contains unsupported or omitted content.");
            addFinding(result.sceneUnsupportedFindings, "scene-level unsupported", scene.id, scene.sceneKey(),
                null, null, "Scene only contains unsupported or omitted content.");
            return plan;
        }

        plan.blank = plan.segments.stream().allMatch(segment -> segment.blankSegment);
        if (plan.blank && !plan.partial) {
            plan.messages.add("Scene exports as a blank placeholder.");
        }

        dedupeStructures(plan);
        return plan;
    }

    private static void collectSceneMetaWarnings(DslScene scene,
                                                 JavaModuleScanResult result,
                                                 JavaModuleScanResult.ScenePlan plan) {
        if (scene.nbtFilter != null && !scene.nbtFilter.isBlank()) {
            plan.partial = true;
            plan.hadMetaDrops = true;
            addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), null, null,
                "Scene NBT filter will be omitted.");
        }
        if (scene.triggerMode != null && !scene.triggerMode.isBlank()) {
            plan.partial = true;
            plan.hadMetaDrops = true;
            addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), null, null,
                "Scene trigger settings will be omitted.");
        }
        if (scene.triggerStructure != null || scene.triggerStructureRange != null
            || scene.triggerCoord1 != null || scene.triggerCoord2 != null) {
            plan.partial = true;
            plan.hadMetaDrops = true;
            addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), null, null,
                "Scene trigger coordinates/structure settings will be omitted.");
        }
        if (scene.hintStyle != null || scene.onlyFirstTime != null || scene.hintFrequency != null
            || scene.hintAuto != null || scene.hintTitle != null || scene.hintSubtitle != null
            || scene.hintTitleText != null || scene.hintSubtitleText != null) {
            plan.partial = true;
            plan.hadMetaDrops = true;
            addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), null, null,
                "Scene hint settings will be omitted.");
        }
    }

    private static List<StructureResolution> resolveSegmentSchematics(JavaModuleScanResult.TargetProject target,
                                                                      DslScene scene,
                                                                      List<DslScene.SceneSegment> segments,
                                                                      JavaModuleScanResult result,
                                                                      JavaModuleScanResult.ScenePlan plan) {
        List<StructureResolution> resolved = new ArrayList<>();
        StructureResolution current = resolveDefaultStructure(target, scene);
        for (int i = 0; i < segments.size(); i++) {
            DslScene.SceneSegment segment = segments.get(i);
            String explicit = extractExplicitStructureRef(segment);
            if (explicit != null) {
                StructureResolution next = resolveStructureReference(target, scene, explicit);
                if (next != null) {
                    current = next;
                } else {
                    plan.partial = true;
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        "show_structure", "Missing structure reference will be ignored: " + explicit);
                }
            }
            resolved.add(current);
        }
        return resolved;
    }

    @Nullable
    private static StructureResolution resolveDefaultStructure(JavaModuleScanResult.TargetProject target, DslScene scene) {
        List<String> pool = structurePool(scene);
        for (String ref : pool) {
            StructureResolution resolution = resolveStructureReference(target, scene, ref);
            if (resolution != null) {
                return resolution;
            }
        }
        return null;
    }

    @Nullable
    private static StructureResolution resolveStructureReference(JavaModuleScanResult.TargetProject target,
                                                                 DslScene scene,
                                                                 String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        List<String> pool = structurePool(scene);
        Integer parsedIndex = tryParseInt(ref.trim());
        if (parsedIndex != null) {
            int index = -1;
            if (parsedIndex >= 1 && parsedIndex <= pool.size()) {
                index = parsedIndex - 1;
            } else if (parsedIndex >= 0 && parsedIndex < pool.size()) {
                index = parsedIndex;
            }
            if (index >= 0) {
                return resolveStructureReference(target, scene, pool.get(index));
            }
            return null;
        }

        ResourceLocation sourceId = parseStructureLocation(ref.trim());
        if (sourceId == null) {
            return null;
        }
        Path sourcePath = findStructureSourcePath(sourceId, scene.pack);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            return null;
        }

        StructureResolution resolution = new StructureResolution();
        resolution.sourceId = sourceId;
        resolution.sourcePath = sourcePath.toAbsolutePath().normalize();
        resolution.targetResourceId = target.modId + ":generated/" + sourceId.getNamespace() + "/" + sourceId.getPath();
        resolution.targetRelativePath = "src/main/resources/assets/" + target.modId + "/ponder/generated/"
            + sourceId.getNamespace() + "/" + sourceId.getPath() + ".nbt";
        return resolution;
    }

    private static List<String> structurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    private static String extractExplicitStructureRef(DslScene.SceneSegment segment) {
        for (DslScene.DslStep step : safeSteps(segment.steps)) {
            if (step == null || step.type == null) {
                continue;
            }
            if ("show_structure".equalsIgnoreCase(step.type) && step.structure != null && !step.structure.isBlank()) {
                return step.structure.trim();
            }
        }
        return null;
    }

    private static void dedupeStructures(JavaModuleScanResult.ScenePlan plan) {
        Map<String, JavaModuleScanResult.StructureAsset> deduped = new LinkedHashMap<>();
        for (JavaModuleScanResult.StructureAsset asset : plan.structures) {
            if (asset != null) {
                deduped.put(asset.targetRelativePath, asset);
            }
        }
        plan.structures = new ArrayList<>(deduped.values());
    }

    private static JavaModuleScanResult.StructureAsset toStructureAsset(@Nullable StructureResolution resolution) {
        if (resolution == null) {
            return null;
        }
        JavaModuleScanResult.StructureAsset asset = new JavaModuleScanResult.StructureAsset();
        asset.sourceId = resolution.sourceId.toString();
        asset.sourcePath = resolution.sourcePath;
        asset.targetResourceId = resolution.targetResourceId;
        asset.targetRelativePath = resolution.targetRelativePath;
        return asset;
    }

    private static boolean isGeneratableStep(DslScene.DslStep step) {
        String type = step.type == null ? "" : step.type.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "show_structure", "idle", "text", "show_controls", "rotate_camera_y", "next_scene" -> true;
            case "show_extra_structure" -> hasPos(step.blockPos) && isNonBlank(step.structure);
            case "create_entity" -> isNonBlank(step.entity);
            case "create_item_entity" -> isNonBlank(step.item);
            case "highlight_section", "destroy_block", "indicate_redstone", "indicate_success",
                "toggle_redstone_power", "modify_block_entity_nbt", "hide_section",
                "show_section_and_merge" -> hasPos(step.blockPos);
            case "encapsulate_bounds" -> step.bounds != null && step.bounds.size() >= 3;
            case "play_sound" -> isNonBlank(step.sound);
            case "replace_blocks", "set_block" -> hasPos(step.blockPos) && isNonBlank(step.block);
            case "rotate_section", "move_section" -> isNonBlank(step.linkId) || hasPos(step.blockPos);
            case "clear_entities", "clear_item_entities", "modify_entities_nbt", "modify_item_entities_nbt" ->
                Boolean.TRUE.equals(step.fullScene) || hasPos(step.blockPos);
            default -> true;
        };
    }

    private static RenderedScene renderScene(JavaModuleScanResult.TargetProject target,
                                             JavaModuleScanResult.ScenePlan scenePlan,
                                             SceneExportOutcome outcome) {
        String sceneSource = buildSceneSource(target, scenePlan);
        String sceneRelativePath = "src/main/java/" + packageToPath(target.scenePackage) + "/" + scenePlan.className + ".java";
        Map<String, Map<String, String>> langEntries = buildSceneLangEntries(target, scenePlan);

        List<BinaryFile> structureFiles = new ArrayList<>();
        for (JavaModuleScanResult.StructureAsset asset : scenePlan.structures) {
            if (asset == null || asset.sourcePath == null) {
                continue;
            }
            try {
                structureFiles.add(new BinaryFile(asset.targetRelativePath, Files.readAllBytes(asset.sourcePath)));
            } catch (IOException ignored) {
            }
        }

        JavaModuleExportManifest.SceneEntry entry = new JavaModuleExportManifest.SceneEntry();
        entry.sceneId = scenePlan.sceneId;
        entry.sceneKey = scenePlan.sceneKey;
        entry.className = target.scenePackage + "." + scenePlan.className;
        entry.status = scenePlan.blank ? SceneExportOutcome.Status.BLANK.name() : scenePlan.partial
            ? SceneExportOutcome.Status.PARTIAL.name()
            : SceneExportOutcome.Status.COMPLETE.name();
        entry.javaFiles.add(sceneRelativePath);
        scenePlan.structures.forEach(asset -> entry.resourceFiles.add(asset.targetRelativePath));
        entry.langEntries = copyLangEntries(langEntries);
        entry.contentHash = hashSceneContent(sceneSource, structureFiles, entry.langEntries);

        outcome.status = scenePlan.blank ? SceneExportOutcome.Status.BLANK
            : scenePlan.partial ? SceneExportOutcome.Status.PARTIAL
            : SceneExportOutcome.Status.COMPLETE;

        return new RenderedScene(entry, new TextFile(sceneRelativePath, sceneSource), structureFiles);
    }

    private static List<TextFile> buildGlobalWrites(JavaModuleScanResult.TargetProject target,
                                                    JavaModuleExportManifest manifest,
                                                    Map<String, Set<String>> staleManagedLangKeys) {
        List<TextFile> writes = new ArrayList<>();
        String generatedPackagePath = packageToPath(target.generatedPackage);
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderPlugin.java",
            buildPluginSource(target)));
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderIndex.java",
            buildIndexSource(target, manifest)));
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderAttribution.java",
            buildAttributionSource(target)));
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderSupport.java",
            buildSupportSource(target)));
        writes.addAll(buildLangWrites(target, manifest, staleManagedLangKeys));
        if ("fabric".equals(target.loader)) {
            writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderFabricClient.java",
                buildFabricClientSource(target)));
        } else {
            writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderForgeClient.java",
                buildForgeClientSource(target)));
        }
        return writes;
    }

    private static List<TextFile> buildLangWrites(JavaModuleScanResult.TargetProject target,
                                                  JavaModuleExportManifest manifest,
                                                  Map<String, Set<String>> staleManagedLangKeys) {
        Map<String, Map<String, String>> managedEntries = collectManagedLangEntries(manifest);
        mergeLangEntries(managedEntries, buildAttributionLangEntries(target.modId));
        Set<String> locales = new LinkedHashSet<>();
        locales.addAll(managedEntries.keySet());
        locales.addAll(staleManagedLangKeys.keySet());

        List<TextFile> writes = new ArrayList<>();
        for (String locale : locales.stream().sorted().toList()) {
            Path langPath = target.resourcesRoot.resolve("assets/" + target.modId + "/lang/" + locale + ".json").normalize();
            Map<String, String> mergedEntries = readLangFile(langPath);
            Set<String> managedKeys = new LinkedHashSet<>();
            managedKeys.addAll(managedEntries.getOrDefault(locale, Map.of()).keySet());
            managedKeys.addAll(staleManagedLangKeys.getOrDefault(locale, Set.of()));
            managedKeys.forEach(mergedEntries::remove);
            mergedEntries.putAll(managedEntries.getOrDefault(locale, Map.of()));
            writes.add(new TextFile(relativeToRoot(target.targetRoot, langPath), GSON.toJson(mergedEntries)));
        }
        return writes;
    }

    private static void mergeLangEntries(Map<String, Map<String, String>> target,
                                         Map<String, Map<String, String>> additions) {
        additions.forEach((locale, values) -> {
            if (locale == null || locale.isBlank() || values == null || values.isEmpty()) {
                return;
            }
            target.computeIfAbsent(locale, unused -> new LinkedHashMap<>()).putAll(values);
        });
    }

    private static Map<String, Map<String, String>> collectManagedLangEntries(JavaModuleExportManifest manifest) {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        if (manifest.scenes == null) {
            return merged;
        }
        manifest.scenes.values().stream()
            .sorted(Comparator.comparing(entry -> entry.sceneId == null ? "" : entry.sceneId))
            .forEach(entry -> {
                if (entry.langEntries == null) {
                    return;
                }
                entry.langEntries.forEach((locale, values) -> {
                    if (locale == null || locale.isBlank() || values == null || values.isEmpty()) {
                        return;
                    }
                    Map<String, String> bucket = merged.computeIfAbsent(locale, unused -> new LinkedHashMap<>());
                    values.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(langEntry -> bucket.put(langEntry.getKey(), langEntry.getValue()));
                });
            });
        return merged;
    }

    private static void collectManagedLangKeys(JavaModuleExportManifest.SceneEntry entry,
                                               Map<String, Set<String>> target) {
        if (entry == null || entry.langEntries == null) {
            return;
        }
        entry.langEntries.forEach((locale, values) -> {
            if (locale == null || locale.isBlank() || values == null || values.isEmpty()) {
                return;
            }
            target.computeIfAbsent(locale, unused -> new LinkedHashSet<>()).addAll(values.keySet());
        });
    }

    private static Map<String, Map<String, String>> buildSceneLangEntries(JavaModuleScanResult.TargetProject target,
                                                                          JavaModuleScanResult.ScenePlan scenePlan) {
        Map<String, Map<String, String>> langEntries = new LinkedHashMap<>();
        for (int segmentIndex = 0; segmentIndex < scenePlan.segments.size(); segmentIndex++) {
            JavaModuleScanResult.SegmentPlan segment = scenePlan.segments.get(segmentIndex);
            if (segment.schematic == null) {
                continue;
            }

            String titleKey = specificLangKey(target.modId, segment.titlePath, "header");
            exportSegmentTitleTranslations(scenePlan.sourceScene, segment.sourceSegment, segment.titlePath,
                scenePlan.segments.size(), segmentIndex)
                .forEach((locale, value) ->
                    langEntries.computeIfAbsent(locale, unused -> new LinkedHashMap<>()).put(titleKey, value));

            int textIndex = 1;
            for (JavaModuleScanResult.GeneratedStep generatedStep : segment.steps) {
                DslScene.DslStep step = generatedStep.sourceStep;
                if (step == null || !"text".equalsIgnoreCase(step.type)) {
                    continue;
                }
                String textKey = specificLangKey(target.modId, segment.titlePath, "text_" + textIndex);
                addLangEntries(langEntries, textKey, step.text, "");
                textIndex++;
            }
        }
        return langEntries;
    }

    private static Map<String, Map<String, String>> buildAttributionLangEntries(String modId) {
        Map<String, Map<String, String>> langEntries = new LinkedHashMap<>();
        String titleKey = tagTitleLangKey(modId, ATTRIBUTION_TAG_PATH);
        String descriptionKey = tagDescriptionLangKey(modId, ATTRIBUTION_TAG_PATH);

        langEntries.computeIfAbsent("en_us", unused -> new LinkedHashMap<>()).put(titleKey, ATTRIBUTION_TITLE_EN_US);
        langEntries.get("en_us").put(descriptionKey, ATTRIBUTION_DESCRIPTION_EN_US);

        langEntries.computeIfAbsent("zh_cn", unused -> new LinkedHashMap<>()).put(titleKey, ATTRIBUTION_TITLE_ZH_CN);
        langEntries.get("zh_cn").put(descriptionKey, ATTRIBUTION_DESCRIPTION_ZH_CN);
        return langEntries;
    }

    private static Map<String, String> exportSegmentTitleTranslations(DslScene scene,
                                                                      DslScene.SceneSegment segment,
                                                                      String titlePath,
                                                                      int total,
                                                                      int index) {
        if (segment != null && segment.title != null && !segment.title.isEmpty()) {
            return exportTranslations(segment.title, fallbackSegmentTitle(scene, segment, titlePath, total, index));
        }
        if (scene != null && scene.title != null && !scene.title.isEmpty()) {
            Map<String, String> base = exportTranslations(scene.title, resolveExportText(scene.title, titlePath));
            if (total <= 1) {
                return base;
            }
            Map<String, String> suffixed = new LinkedHashMap<>();
            base.forEach((locale, value) -> suffixed.put(locale, value + " #" + (index + 1)));
            return suffixed;
        }
        return exportTranslations(null, fallbackSegmentTitle(scene, segment, titlePath, total, index));
    }

    private static void addLangEntries(Map<String, Map<String, String>> target,
                                       String langKey,
                                       @Nullable LocalizedText text,
                                       String fallback) {
        Map<String, String> translations = exportTranslations(text, fallback);
        translations.forEach((locale, value) ->
            target.computeIfAbsent(locale, unused -> new LinkedHashMap<>()).put(langKey, value));
    }

    private static Map<String, String> exportTranslations(@Nullable LocalizedText text, String fallback) {
        LinkedHashMap<String, String> translations = new LinkedHashMap<>();
        if (text != null && !text.isPlain()) {
            text.getAllTranslations().forEach((locale, value) -> {
                if ("_plain".equals(locale)) {
                    return;
                }
                String normalizedLocale = normalizeLangCode(locale);
                if (normalizedLocale != null) {
                    translations.put(normalizedLocale, value == null ? "" : value);
                }
            });
        }

        String defaultText = resolveExportText(text, fallback);
        translations.putIfAbsent("en_us", defaultText);
        if (translations.isEmpty()) {
            translations.put("en_us", defaultText);
        }
        return translations;
    }

    @Nullable
    private static String normalizeLangCode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isBlank() ? null : normalized;
    }

    private static String specificLangKey(String modId, String scenePath, String key) {
        return modId + ".ponder." + scenePath + "." + key;
    }

    private static String generatedAttributionTagId(String modId) {
        return modId + ":" + ATTRIBUTION_TAG_PATH;
    }

    private static String tagTitleLangKey(String modId, String tagPath) {
        return modId + ".ponder.tag." + tagPath;
    }

    private static String tagDescriptionLangKey(String modId, String tagPath) {
        return tagTitleLangKey(modId, tagPath) + ".description";
    }

    private static Map<String, String> readLangFile(Path langPath) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(langPath)) {
            return values;
        }
        try (Reader reader = Files.newBufferedReader(langPath, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isJsonPrimitive())
                .forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
        } catch (Exception ignored) {
        }
        return values;
    }

    private static Map<String, Map<String, String>> copyLangEntries(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        source.forEach((locale, values) -> copy.put(locale, new LinkedHashMap<>(values)));
        return copy;
    }

    private static Map<String, String> sortStringMap(Map<String, String> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> right,
                LinkedHashMap::new));
    }

    private static String buildPluginSource(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import net.createmod.ponder.api.registration.PonderPlugin;
            import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
            import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
            import net.minecraft.resources.ResourceLocation;

            public final class GeneratedPonderPlugin implements PonderPlugin {
                @Override
                public String getModId() {
                    return "%s";
                }

                @Override
                public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                    GeneratedPonderIndex.register(helper);
                }

                @Override
                public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
                    GeneratedPonderAttribution.registerTag(helper);
                    GeneratedPonderIndex.registerTags(helper);
                }
            }
            """.formatted(target.generatedPackage, escapeJava(target.modId));
    }

    private static String buildIndexSource(JavaModuleScanResult.TargetProject target,
                                           JavaModuleExportManifest manifest) {
        StringBuilder imports = new StringBuilder();
        StringBuilder sceneBody = new StringBuilder();
        StringBuilder tagBody = new StringBuilder();
        for (JavaModuleExportManifest.SceneEntry entry : manifest.scenes.values()) {
            if (entry.className == null || entry.className.isBlank()) {
                continue;
            }
            imports.append("import ").append(entry.className).append(";\n");
            String simpleName = entry.className.substring(entry.className.lastIndexOf('.') + 1);
            sceneBody.append("        ").append(simpleName).append(".register(helper);\n");
            tagBody.append("        ").append(simpleName).append(".registerTags(helper);\n");
        }

        return """
            package %s;

            %s
            import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
            import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
            import net.minecraft.resources.ResourceLocation;

            public final class GeneratedPonderIndex {
                private GeneratedPonderIndex() {
                }

                public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
            %s    }

                public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
            %s    }
            }
            """.formatted(target.generatedPackage, imports, sceneBody, tagBody);
    }

    private static String buildFabricClientSource(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import net.createmod.ponder.foundation.PonderIndex;
            import net.fabricmc.api.ClientModInitializer;

            public final class GeneratedPonderFabricClient implements ClientModInitializer {
                @Override
                public void onInitializeClient() {
                    PonderIndex.addPlugin(new GeneratedPonderPlugin());
                }
            }
            """.formatted(target.generatedPackage);
    }

    private static String buildForgeClientSource(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import net.createmod.ponder.foundation.PonderIndex;
            import net.minecraftforge.api.distmarker.Dist;
            import net.minecraftforge.eventbus.api.SubscribeEvent;
            import net.minecraftforge.fml.common.Mod;
            import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

            @Mod.EventBusSubscriber(modid = "%s", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
            public final class GeneratedPonderForgeClient {
                private GeneratedPonderForgeClient() {
                }

                @SubscribeEvent
                public static void onClientSetup(FMLClientSetupEvent event) {
                    event.enqueueWork(() -> PonderIndex.addPlugin(new GeneratedPonderPlugin()));
                }
            }
            """.formatted(target.generatedPackage, escapeJava(target.modId));
    }

    private static String buildAttributionSource(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
            import net.minecraft.resources.ResourceLocation;
            import net.minecraft.world.item.Items;

            public final class GeneratedPonderAttribution {
                private static final ResourceLocation TAG = new ResourceLocation(%s, %s);

                private GeneratedPonderAttribution() {
                }

                public static ResourceLocation tag() {
                    return TAG;
                }

                public static void registerTag(PonderTagRegistrationHelper<ResourceLocation> helper) {
                    helper.registerTag(TAG)
                        .title(%s)
                        .description(%s)
                        .item(Items.WRITABLE_BOOK)
                        .register();
                }
            }
            """.formatted(
            target.generatedPackage,
            stringExpr(target.modId),
            stringExpr(ATTRIBUTION_TAG_PATH),
            stringExpr(ATTRIBUTION_TITLE_EN_US),
            stringExpr(ATTRIBUTION_DESCRIPTION_EN_US));
    }

    private static String buildSceneSource(JavaModuleScanResult.TargetProject target,
                                           JavaModuleScanResult.ScenePlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(target.scenePackage).append(";\n\n");
        sb.append("import ").append(target.generatedPackage).append(".GeneratedPonderAttribution;\n");
        sb.append("import ").append(target.generatedPackage).append(".GeneratedPonderSupport;\n");
        sb.append("import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;\n");
        sb.append("import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;\n");
        sb.append("import net.createmod.ponder.api.scene.SceneBuilder;\n");
        sb.append("import net.createmod.ponder.api.scene.SceneBuildingUtil;\n");
        sb.append("import net.minecraft.core.BlockPos;\n");
        sb.append("import net.minecraft.resources.ResourceLocation;\n");
        sb.append("import net.minecraft.world.phys.Vec3;\n");
        sb.append("import java.util.Map;\n\n");
        sb.append("public final class ").append(plan.className).append(" {\n");
        sb.append("    private ").append(plan.className).append("() {\n");
        sb.append("    }\n\n");
        sb.append("    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {\n");
        sb.append("        ResourceLocation[] tags = ")
            .append(resourceLocationArrayExpr(plan.tags, generatedAttributionTagId(target.modId))).append(";\n");
        sb.append("        var multi = helper.forComponents(java.util.List.of(")
            .append(plan.componentItems.stream().map(JavaModuleExportService::resourceLocationExpr).collect(Collectors.joining(", ")))
            .append("));\n");
        for (JavaModuleScanResult.SegmentPlan segment : plan.segments) {
            if (segment.schematic == null) {
                continue;
            }
            sb.append("        multi.addStoryBoard(")
                .append(resourceLocationExpr(segment.schematic.targetResourceId))
                .append(", ")
                .append(plan.className).append("::").append(segment.methodName)
                .append(", tags);\n");
        }
        sb.append("    }\n\n");
        sb.append("    public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {\n");
        sb.append("        ResourceLocation tag = GeneratedPonderAttribution.tag();\n");
        for (String componentItem : plan.componentItems) {
            sb.append("        helper.addTagToComponent(")
                .append(resourceLocationExpr(componentItem))
                .append(", tag);\n");
        }
        sb.append("    }\n\n");

        for (JavaModuleScanResult.SegmentPlan segment : plan.segments) {
            sb.append("    private static void ").append(segment.methodName)
                .append("(SceneBuilder scene, SceneBuildingUtil util) {\n");
            sb.append("        scene.title(")
                .append(stringExpr(segment.titlePath))
                .append(", ")
                .append(localizedTextExpr(segment.sourceSegment.title, fallbackSegmentTitle(plan.sourceScene, segment.sourceSegment, segment.titlePath, plan.segments.size(), plan.segments.indexOf(segment))))
                .append(");\n");
            sb.append("        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();\n");

            boolean firstIsShowStructure = !segment.steps.isEmpty()
                && "show_structure".equalsIgnoreCase(segment.steps.get(0).sourceStep.type);
            if (!firstIsShowStructure) {
                sb.append("        GeneratedPonderSupport.showStructure(scene, context, null, null, null, null);\n");
                sb.append("        scene.idle(20);\n");
            }

            if (segment.blankSegment) {
                if (!segment.omittedStepTypes.isEmpty()) {
                    sb.append("        // Unsupported steps omitted: ")
                        .append(String.join(", ", segment.omittedStepTypes)).append("\n");
                }
                sb.append("        scene.idle(1);\n");
                sb.append("    }\n\n");
                continue;
            }

            for (JavaModuleScanResult.GeneratedStep generatedStep : segment.steps) {
                DslScene.DslStep step = generatedStep.sourceStep;
                if (Boolean.TRUE.equals(step.attachKeyFrame)) {
                    sb.append("        scene.addKeyframe();\n");
                }
                appendStepSource(sb, generatedStep);
            }
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static void appendStepSource(StringBuilder sb, JavaModuleScanResult.GeneratedStep generatedStep) {
        DslScene.DslStep step = generatedStep.sourceStep;
        String type = step.type.toLowerCase(Locale.ROOT);
        switch (type) {
            case "show_structure" -> sb.append("        GeneratedPonderSupport.showStructure(scene, context, ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(floatExpr(step.scale)).append(", ")
                .append(floatExpr(step.rotation)).append(");\n");
            case "show_extra_structure" -> {
                String resId = generatedStep.extraStructureResourceId;
                int rotationDegrees = step.rotation == null ? 0 : Math.round(step.rotation);
                sb.append("        GeneratedPonderSupport.showExtraStructure(scene, context, ")
                    .append(resourceLocationExpr(resId)).append(", ")
                    .append(blockPosExpr(step.blockPos)).append(", ")
                    .append(rotationDegrees).append(", ")
                    .append(Boolean.TRUE.equals(step.replaceAir)).append(", ")
                    .append(boolExpr(step.immediateDisplay)).append(", ")
                    .append(boolExpr(step.spawnParticles)).append(", ")
                    .append(stringExpr(step.entranceAnimation)).append(", ")
                    .append(intExpr(step.entranceDuration)).append(", ")
                    .append(intExpr(step.entranceInterval)).append(", ")
                    .append(boolExpr(step.smartDisplay)).append(", ")
                    .append(stringExpr(step.linkId)).append(", ")
                    .append(stringExpr(step.direction)).append(");\n");
            }
            case "idle" -> sb.append("        scene.idle(").append(step.durationOrDefault(20)).append(");\n");
            case "text" -> sb.append("        GeneratedPonderSupport.showText(scene, ")
                .append(localizedTextExpr(step.text, ""))
                .append(", ")
                .append(vecExpr(step.point))
                .append(", ")
                .append(step.durationOrDefault(60))
                .append(", ")
                .append(stringExpr(step.color))
                .append(", ")
                .append(Boolean.TRUE.equals(step.placeNearTarget))
                .append(");\n");
            case "create_entity" -> sb.append("        GeneratedPonderSupport.createEntity(scene, ")
                .append(stringExpr(step.entity)).append(", ")
                .append(vecExprOrDefault(step.pos != null ? step.pos : step.point, 2.5, 1.5, 2.5)).append(", ")
                .append(vecExpr(step.lookAt)).append(", ")
                .append(floatExpr(step.yaw)).append(", ")
                .append(floatExpr(step.pitch)).append(", ")
                .append(stringExpr(step.nbt)).append(");\n");
            case "create_item_entity" -> sb.append("        GeneratedPonderSupport.createItemEntity(scene, ")
                .append(stringExpr(step.item)).append(", ")
                .append(step.count == null ? 1 : Math.max(1, step.count)).append(", ")
                .append(vecExprOrDefault(step.pos != null ? step.pos : step.point, 2.5, 1.5, 2.5)).append(", ")
                .append(vecExprOrDefault(step.motion, 2.5, 1.5, 2.5)).append(", ")
                .append(stringExpr(step.nbt)).append(");\n");
            case "rotate_camera_y" -> sb.append("        GeneratedPonderSupport.rotateCameraY(scene, ")
                .append(step.degrees == null ? "90f" : floatLiteral(step.degrees)).append(", ")
                .append(step.durationOrDefault(20)).append(");\n");
            case "highlight_section" -> sb.append("        GeneratedPonderSupport.highlightSection(scene, ")
                .append(stringExpr(step.color)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(step.durationOrDefault(40)).append(");\n");
            case "show_controls" -> sb.append("        GeneratedPonderSupport.showControls(scene, ")
                .append(vecExprOrDefault(step.point, 2.5, 1.5, 2.5)).append(", ")
                .append(stringExpr(step.direction)).append(", ")
                .append(step.durationOrDefault(60)).append(", ")
                .append(stringExpr(step.action)).append(", ")
                .append(generatedStep.omitControlItem ? "null" : stringExpr(step.item)).append(", ")
                .append(generatedStep.omitControlItem ? "null" : stringExpr(step.nbt)).append(", ")
                .append(Boolean.TRUE.equals(step.whileSneaking)).append(", ")
                .append(Boolean.TRUE.equals(step.whileCTRL)).append(");\n");
            case "encapsulate_bounds" -> sb.append("        GeneratedPonderSupport.encapsulateBounds(scene, ")
                .append(boundsExpr(step.bounds)).append(");\n");
            case "play_sound" -> sb.append("        GeneratedPonderSupport.playSound(scene, ")
                .append(stringExpr(step.sound)).append(", ")
                .append(step.soundVolume == null ? "1.0f" : floatLiteral(step.soundVolume)).append(", ")
                .append(step.pitch == null ? "1.0f" : floatLiteral(step.pitch)).append(", ")
                .append(stringExpr(step.source)).append(");\n");
            case "set_block" -> sb.append("        GeneratedPonderSupport.setBlock(scene, context, ")
                .append(stringExpr(step.block)).append(", ")
                .append(mapExpr(step.blockProperties)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(boolExpr(step.immediateDisplay)).append(", ")
                .append(boolExpr(step.spawnParticles)).append(", ")
                .append(stringExpr(step.entranceAnimation)).append(", ")
                .append(intExpr(step.entranceDuration)).append(", ")
                .append(intExpr(step.entranceInterval)).append(", ")
                .append(boolExpr(step.smartDisplay)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(stringExpr(step.direction)).append(");\n");
            case "destroy_block" -> sb.append("        GeneratedPonderSupport.destroyBlock(scene, context, ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(boolExpr(step.destroyParticles)).append(");\n");
            case "replace_blocks" -> sb.append("        GeneratedPonderSupport.replaceBlocks(scene, context, ")
                .append(stringExpr(step.block)).append(", ")
                .append(mapExpr(step.blockProperties)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(boolExpr(step.spawnParticles)).append(");\n");
            case "hide_section" -> sb.append("        GeneratedPonderSupport.hideSection(scene, context, ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(step.durationOrDefault(20)).append(", ")
                .append(stringExpr(step.direction)).append(");\n");
            case "show_section_and_merge" -> sb.append("        GeneratedPonderSupport.showSectionAndMerge(scene, context, ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(step.durationOrDefault(20)).append(", ")
                .append(stringExpr(step.direction)).append(", ")
                .append(stringExpr(step.entranceAnimation)).append(", ")
                .append(intExpr(step.entranceDuration)).append(", ")
                .append(intExpr(step.entranceInterval)).append(", ")
                .append(boolExpr(step.smartDisplay)).append(");\n");
            case "rotate_section" -> {
                double rotY = step.rotY == null ? (step.degrees == null ? 0.0 : step.degrees) : step.rotY;
                sb.append("        GeneratedPonderSupport.rotateSection(scene, context, ")
                    .append(stringExpr(step.linkId)).append(", ")
                    .append(blockPosExpr(step.blockPos)).append(", ")
                    .append(blockPosExpr(step.blockPos2)).append(", ")
                    .append(step.rotX == null ? "0.0" : doubleLiteral(step.rotX)).append(", ")
                    .append(doubleLiteral(rotY)).append(", ")
                    .append(step.rotZ == null ? "0.0" : doubleLiteral(step.rotZ)).append(", ")
                    .append(step.durationOrDefault(20)).append(");\n");
            }
            case "move_section" -> sb.append("        GeneratedPonderSupport.moveSection(scene, context, ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(vecExprOrDefault(step.offset, 0.0, 0.0, 0.0)).append(", ")
                .append(step.durationOrDefault(20)).append(");\n");
            case "toggle_redstone_power" -> sb.append("        GeneratedPonderSupport.toggleRedstonePower(scene, ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            case "modify_block_entity_nbt" -> sb.append("        GeneratedPonderSupport.modifyBlockEntity(scene, ")
                .append(mapExpr(step.blockProperties)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(boolExpr(step.reDrawBlocks)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            case "indicate_redstone" -> sb.append("        GeneratedPonderSupport.indicateRedstone(scene, ")
                .append(blockPosExpr(step.blockPos)).append(");\n");
            case "indicate_success" -> sb.append("        GeneratedPonderSupport.indicateSuccess(scene, ")
                .append(blockPosExpr(step.blockPos)).append(");\n");
            case "clear_entities" -> sb.append("        GeneratedPonderSupport.clearEntities(scene, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.entity)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            case "clear_item_entities" -> sb.append("        GeneratedPonderSupport.clearItemEntities(scene, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.item)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            case "modify_entities_nbt" -> sb.append("        GeneratedPonderSupport.modifyEntitiesNbt(scene, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.entity)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            case "modify_item_entities_nbt" -> sb.append("        GeneratedPonderSupport.modifyItemEntitiesNbt(scene, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.item)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            default -> sb.append("        // Unsupported step omitted: ").append(type).append("\n");
        }
    }

    private static String buildSupportSource(JavaModuleScanResult.TargetProject target) {
        return """
            package %s;

            import com.mojang.logging.LogUtils;
            import net.createmod.catnip.math.Pointing;
            import net.createmod.ponder.api.PonderPalette;
            import net.createmod.ponder.api.element.ElementLink;
            import net.createmod.ponder.api.element.InputElementBuilder;
            import net.createmod.ponder.api.element.TextElementBuilder;
            import net.createmod.ponder.api.element.WorldSectionElement;
            import net.createmod.ponder.api.scene.SceneBuilder;
            import net.createmod.ponder.api.scene.Selection;
            import net.createmod.ponder.foundation.instruction.DisplayWorldSectionInstruction;
            import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
            import net.minecraft.client.Minecraft;
            import net.minecraft.client.resources.sounds.SimpleSoundInstance;
            import net.minecraft.client.resources.sounds.SoundInstance;
            import net.minecraft.commands.arguments.EntityAnchorArgument;
            import net.minecraft.core.BlockPos;
            import net.minecraft.core.Direction;
            import net.minecraft.core.registries.BuiltInRegistries;
            import net.minecraft.nbt.CompoundTag;
            import net.minecraft.nbt.ListTag;
            import net.minecraft.nbt.NbtAccounter;
            import net.minecraft.nbt.NbtIo;
            import net.minecraft.nbt.Tag;
            import net.minecraft.nbt.TagParser;
            import net.minecraft.resources.ResourceLocation;
            import net.minecraft.sounds.SoundEvent;
            import net.minecraft.sounds.SoundSource;
            import net.minecraft.world.entity.Entity;
            import net.minecraft.world.entity.EntityType;
            import net.minecraft.world.entity.Mob;
            import net.minecraft.world.entity.item.ItemEntity;
            import net.minecraft.world.item.Item;
            import net.minecraft.world.item.ItemStack;
            import net.minecraft.world.level.Level;
            import net.minecraft.world.level.block.Block;
            import net.minecraft.world.level.block.Blocks;
            import net.minecraft.world.level.block.Rotation;
            import net.minecraft.world.level.block.entity.BlockEntity;
            import net.minecraft.world.level.block.state.BlockState;
            import net.minecraft.world.level.block.state.properties.Property;
            import net.minecraft.world.phys.Vec3;
            import org.slf4j.Logger;

            import java.io.BufferedInputStream;
            import java.io.DataInputStream;
            import java.io.InputStream;
            import java.util.ArrayList;
            import java.util.Comparator;
            import java.util.HashMap;
            import java.util.HashSet;
            import java.util.List;
            import java.util.Locale;
            import java.util.Map;
            import java.util.Set;
            import java.util.function.ToIntFunction;
            import java.util.zip.GZIPInputStream;

            public final class GeneratedPonderSupport {
                private static final Logger LOGGER = LogUtils.getLogger();

                public static final class Context {
                    final Map<String, ElementLink<WorldSectionElement>> sectionLinks = new HashMap<>();
                    final Set<Long> visibleBlockKeys = new HashSet<>();
                    final Set<Long> hiddenBlockKeys = new HashSet<>();
                    boolean allBlocksVisible;
                }

                private GeneratedPonderSupport() {
                }

                public static void showStructure(SceneBuilder scene, Context context, BlockPos pos1, BlockPos pos2,
                                                 Float scale, Float rotation) {
                    Selection selection;
                    boolean everywhere;
                    if (pos1 != null) {
                        selection = pos2 == null
                            ? scene.getScene().getSceneBuildingUtil().select().position(pos1)
                            : scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
                        everywhere = false;
                    } else {
                        selection = scene.getScene().getSceneBuildingUtil().select().everywhere();
                        everywhere = true;
                    }
                    scene.world().showSection(selection, Direction.UP);
                    if (everywhere) {
                        context.allBlocksVisible = true;
                        context.visibleBlockKeys.clear();
                        context.hiddenBlockKeys.clear();
                    } else {
                        updateVisibleRange(context, pos1, pos2, true);
                    }
                    if (scale != null) {
                        scene.scaleSceneView(scale);
                    }
                    float rotationOffset = rotation == null ? 0f : rotation;
                    if (rotationOffset != 0f) {
                        scene.addInstruction(ps -> {
                            var yRotation = ps.getTransform().yRotation;
                            float target = yRotation.getChaseTarget() + rotationOffset;
                            yRotation.startWithValue(target);
                        });
                    }
                }

                public static void showText(SceneBuilder scene, String text, Vec3 point, int duration,
                                            String color, boolean placeNearTarget) {
                    TextElementBuilder builder = scene.overlay()
                        .showText(duration)
                        .text(text == null ? "" : text);
                    if (point != null) {
                        builder.pointAt(point);
                    }
                    PonderPalette palette = parsePalette(color);
                    if (palette != null) {
                        builder.colored(palette);
                    }
                    if (placeNearTarget) {
                        builder.placeNearTarget();
                    }
                }

                public static void createEntity(SceneBuilder scene, String entityId, Vec3 pos, Vec3 lookAt,
                                                Float yaw, Float pitch, String nbt) {
                    ResourceLocation loc = entityId == null ? null : ResourceLocation.tryParse(entityId);
                    if (loc == null) {
                        return;
                    }
                    scene.world().createEntity((Level level) -> {
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(loc).orElse(null);
                        if (type == null) {
                            return null;
                        }
                        Entity entity = type.create(level);
                        if (entity == null) {
                            return null;
                        }
                        entity.setPosRaw(pos.x, pos.y, pos.z);
                        entity.setOldPosAndRot();
                        Vec3 targetLook = lookAt == null ? pos.add(0, 0, -1) : lookAt;
                        entity.lookAt(EntityAnchorArgument.Anchor.FEET, targetLook);
                        if (yaw != null) {
                            entity.setYRot(yaw);
                            entity.setYHeadRot(yaw);
                            entity.setYBodyRot(yaw);
                        }
                        if (pitch != null) {
                            entity.setXRot(pitch);
                        }
                        if (entity instanceof Mob mob) {
                            mob.setNoAi(true);
                        }
                        entity.setNoGravity(true);
                        entity.setDeltaMovement(Vec3.ZERO);
                        if (nbt != null && !nbt.isBlank()) {
                            try {
                                CompoundTag patch = TagParser.parseTag(nbt);
                                CompoundTag data = new CompoundTag();
                                entity.saveWithoutId(data);
                                data.merge(patch);
                                entity.load(data);
                            } catch (Exception ignored) {
                            }
                        }
                        return entity;
                    });
                }

                public static void createItemEntity(SceneBuilder scene, String itemId, int count, Vec3 pos,
                                                    Vec3 motion, String nbt) {
                    ResourceLocation loc = itemId == null ? null : ResourceLocation.tryParse(itemId);
                    Item item = loc == null ? null : BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
                    if (item == null) {
                        return;
                    }
                    CompoundTag patch = null;
                    if (nbt != null && !nbt.isBlank()) {
                        try {
                            patch = TagParser.parseTag(nbt);
                        } catch (Exception ignored) {
                        }
                    }
                    CompoundTag finalPatch = patch;
                    scene.world().createEntity((Level level) -> {
                        ItemStack stack = new ItemStack(item, Math.max(1, count));
                        if (finalPatch != null) {
                            stack = applyPatchToItemStack(stack, finalPatch);
                        }
                        ItemEntity entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                        entity.setDeltaMovement(motion);
                        if (finalPatch != null && isLikelyEntityPatch(finalPatch)) {
                            mergeEntityNbt(entity, finalPatch);
                        }
                        return entity;
                    });
                }

                public static void rotateCameraY(SceneBuilder scene, float degrees, int duration) {
                    scene.addInstruction(ponderScene -> {
                        var yRotation = ponderScene.getTransform().yRotation;
                        float target = yRotation.getChaseTarget() + degrees;
                        if (duration == 0) {
                            yRotation.startWithValue(target);
                        } else {
                            yRotation.chaseTimed(target, duration);
                        }
                    });
                    if (duration > 0) {
                        scene.idle(duration);
                    }
                }

                public static void highlightSection(SceneBuilder scene, String color, BlockPos pos1, BlockPos pos2,
                                                    int duration) {
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    PonderPalette palette = parsePalette(color);
                    if (palette == null) {
                        palette = PonderPalette.BLUE;
                    }
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    scene.overlay().showOutline(palette, new Object(), selection, duration);
                }

                public static void showControls(SceneBuilder scene, Vec3 point, String direction, int duration,
                                                String action, String itemSpec, String nbt,
                                                boolean whileSneaking, boolean whileCtrl) {
                    InputElementBuilder builder = scene.overlay().showControls(point, parsePointing(direction), duration);
                    String normalizedAction = action == null ? "" : action.toLowerCase(Locale.ROOT);
                    switch (normalizedAction) {
                        case "left" -> builder.leftClick();
                        case "right" -> builder.rightClick();
                        case "scroll" -> builder.scroll();
                        default -> {
                        }
                    }
                    if (itemSpec != null && !itemSpec.isBlank()) {
                        ItemStack stack = parseItemStackSpec(itemSpec, nbt);
                        if (stack != null) {
                            builder.withItem(stack);
                        }
                    }
                    if (whileSneaking) {
                        builder.whileSneaking();
                    }
                    if (whileCtrl) {
                        builder.whileCTRL();
                    }
                }

                public static void encapsulateBounds(SceneBuilder scene, BlockPos size) {
                    scene.addInstruction(ps -> ps.getWorld().getBounds().encapsulate(size));
                }

                public static void playSound(SceneBuilder scene, String soundId, float volume, float pitch, String source) {
                    ResourceLocation loc = soundId == null ? null : ResourceLocation.tryParse(soundId);
                    if (loc == null) {
                        return;
                    }
                    SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(loc).orElse(null);
                    if (sound == null) {
                        return;
                    }
                    SoundSource soundSource = parseSoundSource(source);
                    scene.addInstruction(ps -> {
                        if (Minecraft.getInstance().player == null) {
                            return;
                        }
                        var instance = new SimpleSoundInstance(
                            sound,
                            soundSource,
                            volume,
                            pitch,
                            SoundInstance.createUnseededRandom(),
                            Minecraft.getInstance().player.blockPosition());
                        Minecraft.getInstance().getSoundManager().play(instance);
                    });
                }

                public static void setBlock(SceneBuilder scene, Context context, String blockId,
                                            Map<String, String> blockProperties, BlockPos pos1, BlockPos pos2,
                                            String nbt, Boolean immediateDisplay, Boolean spawnParticles,
                                            String entranceAnimation, Integer entranceDuration, Integer entranceInterval,
                                            Boolean smartDisplay, String linkId, String direction) {
                    ResourceLocation loc = blockId == null ? null : ResourceLocation.tryParse(blockId);
                    Block block = loc == null ? null : BuiltInRegistries.BLOCK.getOptional(loc).orElse(null);
                    if (block == null || pos1 == null) {
                        return;
                    }
                    BlockState state = applyBlockProperties(block.defaultBlockState(), blockProperties);
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    boolean immediate = !Boolean.FALSE.equals(immediateDisplay);
                    boolean particles = immediate && !Boolean.FALSE.equals(spawnParticles);
                    String normalizedAnimation = normalizeEntranceAnimation(entranceAnimation);
                    if (normalizedAnimation != null && !"none".equals(normalizedAnimation)) {
                        applyAnimatedSetBlock(scene, context, state, pos1, targetPos2, nbt,
                            normalizedAnimation, entranceDuration, entranceInterval, smartDisplay, linkId, direction);
                        return;
                    }
                    ensureSceneCanShowRange(scene, pos1, targetPos2, immediate);
                    updateVisibleRange(context, pos1, targetPos2, immediate);
                    if (!pos1.equals(targetPos2)) {
                        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                        scene.world().setBlocks(selection, state, particles);
                        applySetBlockNbtPatch(scene, nbt, selection);
                    } else {
                        scene.world().setBlock(pos1, state, particles);
                        applySetBlockNbtPatch(scene, nbt, scene.getScene().getSceneBuildingUtil().select().position(pos1));
                    }
                }

                public static void destroyBlock(SceneBuilder scene, Context context, BlockPos pos, Boolean destroyParticles) {
                    if (pos == null) {
                        return;
                    }
                    boolean particles = !Boolean.FALSE.equals(destroyParticles);
                    if (particles) {
                        scene.world().destroyBlock(pos);
                    } else {
                        scene.world().setBlock(pos, Blocks.AIR.defaultBlockState(), false);
                    }
                    updateVisibleRange(context, pos, pos, false);
                }

                public static void replaceBlocks(SceneBuilder scene, Context context, String blockId,
                                                 Map<String, String> blockProperties, BlockPos pos1, BlockPos pos2,
                                                 Boolean spawnParticles) {
                    ResourceLocation loc = blockId == null ? null : ResourceLocation.tryParse(blockId);
                    Block block = loc == null ? null : BuiltInRegistries.BLOCK.getOptional(loc).orElse(null);
                    if (block == null || pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    ensureSceneCanShowRange(scene, pos1, targetPos2, true);
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    scene.world().replaceBlocks(selection, applyBlockProperties(block.defaultBlockState(), blockProperties),
                        !Boolean.FALSE.equals(spawnParticles));
                    updateVisibleRange(context, pos1, targetPos2, true);
                }

                public static void hideSection(SceneBuilder scene, Context context, BlockPos pos1, BlockPos pos2,
                                               int duration, String directionRaw) {
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    updateVisibleRange(context, pos1, targetPos2, false);
                    final Selection hiddenSelection = selection;
                    final List<ElementLink<WorldSectionElement>> existingSectionLinks =
                        new ArrayList<>(context.sectionLinks.values());
                    scene.addInstruction(ps -> {
                        if (ps.getBaseWorldSection().isEmpty()) {
                            Selection all = ps.getSceneBuildingUtil().select().everywhere();
                            ps.getBaseWorldSection().set(all);
                            ps.getBaseWorldSection().setVisible(true);
                            ps.getBaseWorldSection().setFade(1);
                            ps.getBaseWorldSection().queueRedraw();
                        }
                        for (ElementLink<WorldSectionElement> existing : existingSectionLinks) {
                            WorldSectionElement section = ps.resolve(existing);
                            if (section != null) {
                                section.erase(hiddenSelection);
                            }
                        }
                    });
                    Direction direction = parseDirection(directionRaw);
                    ElementLink<WorldSectionElement> link = scene.world().makeSectionIndependent(selection);
                    if (duration <= 0) {
                        scene.addInstruction(ps -> {
                            WorldSectionElement element = ps.resolve(link);
                            if (element != null) {
                                element.setVisible(false);
                                element.setFade(0);
                            }
                        });
                        return;
                    }
                    if (duration == 15) {
                        scene.world().hideIndependentSection(link, direction);
                        return;
                    }
                    scene.addInstruction(new FadeOutOfSceneInstruction<>(duration, direction, link));
                }

                public static void showSectionAndMerge(SceneBuilder scene, Context context, BlockPos pos1, BlockPos pos2,
                                                       String linkId, int duration, String directionRaw,
                                                       String entranceAnimation, Integer entranceDuration,
                                                       Integer entranceInterval, Boolean smartDisplay) {
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    String key = linkId == null ? "" : linkId.trim();
                    if (key.isEmpty()) {
                        key = autoLinkId(context);
                    }
                    Direction direction = parseDirection(directionRaw);
                    ElementLink<WorldSectionElement> existing = context.sectionLinks.get(key);
                    String normalizedAnimation = normalizeEntranceAnimation(entranceAnimation);
                    if ("none".equals(normalizedAnimation)) {
                        duration = 0;
                    }
                    if (normalizedAnimation != null && !"none".equals(normalizedAnimation)) {
                        int rowDuration = entranceDuration == null ? 20 : Math.max(0, entranceDuration);
                        int rowInterval = entranceInterval == null ? 1 : Math.max(0, entranceInterval);
                        applyAnimatedShowSectionAndMerge(scene, context, key, existing, pos1, targetPos2,
                            normalizedAnimation, direction, rowDuration, rowInterval, !Boolean.FALSE.equals(smartDisplay));
                        updateVisibleRange(context, pos1, targetPos2, true);
                        return;
                    }
                    if (existing == null) {
                        ElementLink<WorldSectionElement> created;
                        if (duration <= 0) {
                            created = scene.world().showIndependentSectionImmediately(selection);
                        } else if (duration == 15) {
                            created = scene.world().showIndependentSection(selection, direction);
                        } else {
                            DisplayWorldSectionInstruction instruction =
                                new DisplayWorldSectionInstruction(duration, direction, selection, null);
                            scene.addInstruction(instruction);
                            created = instruction.createLink(scene.getScene());
                        }
                        context.sectionLinks.put(key, created);
                        updateVisibleRange(context, pos1, targetPos2, true);
                        return;
                    }
                    if (duration <= 0) {
                        ElementLink<WorldSectionElement> target = existing;
                        scene.addInstruction(ps -> {
                            WorldSectionElement element = ps.resolve(target);
                            if (element != null) {
                                element.add(selection);
                                element.queueRedraw();
                            }
                        });
                        updateVisibleRange(context, pos1, targetPos2, true);
                        return;
                    }
                    if (duration == 15) {
                        scene.world().showSectionAndMerge(selection, direction, existing);
                        updateVisibleRange(context, pos1, targetPos2, true);
                        return;
                    }
                    scene.addInstruction(new DisplayWorldSectionInstruction(duration, direction, selection,
                        () -> scene.getScene().resolve(existing)));
                    updateVisibleRange(context, pos1, targetPos2, true);
                }

                public static void rotateSection(SceneBuilder scene, Context context, String linkId,
                                                 BlockPos pos1, BlockPos pos2,
                                                 double rotX, double rotY, double rotZ, int duration) {
                    ElementLink<WorldSectionElement> link = resolveSectionLink(scene, context, linkId, pos1, pos2);
                    if (link != null) {
                        scene.world().rotateSection(link, rotX, rotY, rotZ, duration);
                    }
                }

                public static void moveSection(SceneBuilder scene, Context context, String linkId,
                                               BlockPos pos1, BlockPos pos2, Vec3 offset, int duration) {
                    ElementLink<WorldSectionElement> link = resolveSectionLink(scene, context, linkId, pos1, pos2);
                    if (link != null) {
                        scene.world().moveSection(link, offset, duration);
                    }
                }

                public static void toggleRedstonePower(SceneBuilder scene, BlockPos pos1, BlockPos pos2) {
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    scene.world().toggleRedstonePower(scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2));
                }

                public static void modifyBlockEntity(SceneBuilder scene, Map<String, String> blockProperties, String nbt,
                                                     Boolean redraw, BlockPos pos1, BlockPos pos2) {
                    if (pos1 == null) {
                        return;
                    }
                    boolean hasProps = blockProperties != null && !blockProperties.isEmpty();
                    boolean hasNbt = nbt != null && !nbt.isBlank();
                    if (!hasProps && !hasNbt) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    if (hasProps) {
                        for (BlockPos cursor : BlockPos.betweenClosed(pos1, targetPos2)) {
                            BlockPos target = cursor.immutable();
                            scene.world().modifyBlock(target, state -> applyBlockProperties(state, blockProperties), false);
                        }
                    }
                    if (hasNbt) {
                        try {
                            CompoundTag patch = TagParser.parseTag(nbt);
                            scene.world().modifyBlockEntityNBT(selection, BlockEntity.class,
                                data -> data.merge(patch.copy()), Boolean.TRUE.equals(redraw));
                        } catch (Exception ignored) {
                        }
                    }
                }

                public static void indicateRedstone(SceneBuilder scene, BlockPos pos) {
                    if (pos != null) {
                        scene.effects().indicateRedstone(pos);
                    }
                }

                public static void indicateSuccess(SceneBuilder scene, BlockPos pos) {
                    if (pos != null) {
                        scene.effects().indicateSuccess(pos);
                    }
                }

                public static void clearEntities(SceneBuilder scene, boolean fullScene, String entityId,
                                                 BlockPos pos1, BlockPos pos2) {
                    ResourceLocation filter = entityId == null || entityId.isBlank() ? null : ResourceLocation.tryParse(entityId);
                    if (fullScene) {
                        scene.world().modifyEntities(Entity.class, entity -> {
                            if (entity instanceof ItemEntity) {
                                return;
                            }
                            if (filter == null || EntityType.getKey(entity.getType()).equals(filter)) {
                                entity.discard();
                            }
                        });
                        return;
                    }
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    scene.world().modifyEntitiesInside(Entity.class, selection, entity -> {
                        if (entity instanceof ItemEntity) {
                            return;
                        }
                        if (filter == null || EntityType.getKey(entity.getType()).equals(filter)) {
                            entity.discard();
                        }
                    });
                }

                public static void clearItemEntities(SceneBuilder scene, boolean fullScene, String itemId,
                                                     BlockPos pos1, BlockPos pos2) {
                    ResourceLocation filter = itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
                    if (fullScene) {
                        scene.world().modifyEntities(ItemEntity.class, entity -> {
                            if (filter == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filter)) {
                                entity.discard();
                            }
                        });
                        return;
                    }
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    scene.world().modifyEntitiesInside(ItemEntity.class, selection, entity -> {
                        if (filter == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filter)) {
                            entity.discard();
                        }
                    });
                }

                public static void modifyEntitiesNbt(SceneBuilder scene, boolean fullScene, String entityId, String nbt,
                                                     BlockPos pos1, BlockPos pos2) {
                    CompoundTag patch = parseEntityPatch(nbt);
                    if (patch == null) {
                        return;
                    }
                    ResourceLocation filter = entityId == null || entityId.isBlank() ? null : ResourceLocation.tryParse(entityId);
                    if (fullScene) {
                        scene.world().modifyEntities(Entity.class, entity -> {
                            if (entity instanceof ItemEntity) {
                                return;
                            }
                            if (filter == null || EntityType.getKey(entity.getType()).equals(filter)) {
                                mergeEntityNbt(entity, patch);
                            }
                        });
                        return;
                    }
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    scene.world().modifyEntitiesInside(Entity.class, selection, entity -> {
                        if (entity instanceof ItemEntity) {
                            return;
                        }
                        if (filter == null || EntityType.getKey(entity.getType()).equals(filter)) {
                            mergeEntityNbt(entity, patch);
                        }
                    });
                }

                public static void modifyItemEntitiesNbt(SceneBuilder scene, boolean fullScene, String itemId, String nbt,
                                                         BlockPos pos1, BlockPos pos2) {
                    CompoundTag patch = parseEntityPatch(nbt);
                    if (patch == null) {
                        return;
                    }
                    ResourceLocation filter = itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
                    if (fullScene) {
                        scene.world().modifyEntities(ItemEntity.class, entity -> {
                            if (filter == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filter)) {
                                entity.setItem(applyPatchToItemStack(entity.getItem(), patch));
                                if (isLikelyEntityPatch(patch)) {
                                    mergeEntityNbt(entity, patch);
                                }
                            }
                        });
                        return;
                    }
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    scene.world().modifyEntitiesInside(ItemEntity.class, selection, entity -> {
                        if (filter == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filter)) {
                            entity.setItem(applyPatchToItemStack(entity.getItem(), patch));
                            if (isLikelyEntityPatch(patch)) {
                                mergeEntityNbt(entity, patch);
                            }
                        }
                    });
                }

                private static CompoundTag parseEntityPatch(String nbt) {
                    if (nbt == null || nbt.isBlank()) {
                        return null;
                    }
                    try {
                        return TagParser.parseTag(nbt);
                    } catch (Exception ignored) {
                        return null;
                    }
                }

                private static void applyAnimatedSetBlock(SceneBuilder scene, Context context, BlockState state,
                                                          BlockPos pos1, BlockPos pos2, String nbt,
                                                          String entranceAnimation, Integer entranceDuration,
                                                          Integer entranceInterval, Boolean smartDisplay,
                                                          String linkId, String direction) {
                    ensureSceneCanShowRange(scene, pos1, pos2, false);
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
                    scene.world().setBlocks(selection, state, false);
                    applySetBlockNbtPatch(scene, nbt, selection);
                    String key = linkId == null ? "" : linkId.trim();
                    if (key.isEmpty()) {
                        key = autoLinkId(context);
                    }
                    Direction entryDirection = parseDirection(direction);
                    ElementLink<WorldSectionElement> existing = context.sectionLinks.get(key);
                    int rowDuration = entranceDuration == null ? 20 : Math.max(0, entranceDuration);
                    int rowInterval = entranceInterval == null ? 1 : Math.max(0, entranceInterval);
                    applyAnimatedShowSectionAndMerge(scene, context, key, existing, pos1, pos2,
                        entranceAnimation, entryDirection, rowDuration, rowInterval, !Boolean.FALSE.equals(smartDisplay));
                    updateVisibleRange(context, pos1, pos2, true);
                }

                private static void applySetBlockNbtPatch(SceneBuilder scene, String nbt, Selection selection) {
                    if (nbt == null || nbt.isBlank()) {
                        return;
                    }
                    try {
                        CompoundTag patch = TagParser.parseTag(nbt);
                        scene.world().modifyBlockEntityNBT(selection, BlockEntity.class, data -> data.merge(patch.copy()), true);
                    } catch (Exception ignored) {
                    }
                }

                private static void ensureSceneCanShowRange(SceneBuilder scene, BlockPos pos1, BlockPos pos2,
                                                            boolean forceVisibleNow) {
                    int maxY = Math.max(pos1.getY(), pos2.getY());
                    BlockPos requiredBounds = new BlockPos(0, maxY + 1, 0);
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
                    scene.addInstruction(ps -> {
                        ps.getWorld().getBounds().encapsulate(requiredBounds);
                        if (!forceVisibleNow) {
                            if (!ps.getBaseWorldSection().isEmpty()) {
                                ps.getBaseWorldSection().erase(selection);
                                ps.getBaseWorldSection().queueRedraw();
                            }
                            return;
                        }
                        if (ps.getBaseWorldSection().isEmpty()) {
                            Selection all = ps.getSceneBuildingUtil().select().everywhere();
                            ps.getBaseWorldSection().set(all);
                            ps.getBaseWorldSection().setVisible(true);
                            ps.getBaseWorldSection().setFade(1);
                        } else {
                            ps.getBaseWorldSection().add(selection);
                        }
                        ps.getBaseWorldSection().queueRedraw();
                    });
                }

                private static ElementLink<WorldSectionElement> resolveSectionLink(SceneBuilder scene, Context context,
                                                                                   String linkId, BlockPos pos1, BlockPos pos2) {
                    String normalized = linkId == null ? "" : linkId.trim();
                    if (!normalized.isEmpty()) {
                        ElementLink<WorldSectionElement> existing = context.sectionLinks.get(normalized);
                        if (existing != null) {
                            return existing;
                        }
                    }
                    if (pos1 == null) {
                        return null;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, targetPos2);
                    ElementLink<WorldSectionElement> created = scene.world().showIndependentSectionImmediately(selection);
                    String key = normalized.isEmpty() ? autoLinkId(context) : normalized;
                    context.sectionLinks.put(key, created);
                    updateVisibleRange(context, pos1, targetPos2, true);
                    return created;
                }

                private static void applyAnimatedShowSectionAndMerge(SceneBuilder scene, Context context, String linkId,
                                                                     ElementLink<WorldSectionElement> existing,
                                                                     BlockPos pos1, BlockPos pos2,
                                                                     String entranceAnimation,
                                                                     Direction entryDirection,
                                                                     int rowDuration, int rowInterval,
                                                                     boolean smartDisplay) {
                    List<List<BlockPos>> groups = orderedLayerGroups(pos1, pos2, entranceAnimation);
                    if (smartDisplay) {
                        groups = filterVisibleGroups(groups, context);
                    }
                    if (groups.isEmpty()) {
                        return;
                    }
                    ElementLink<WorldSectionElement> working = existing;
                    for (List<BlockPos> group : groups) {
                        if (group.isEmpty()) {
                            continue;
                        }
                        Selection groupSelection = selectionForGroup(scene, group);
                        if (working == null) {
                            DisplayWorldSectionInstruction instruction =
                                new DisplayWorldSectionInstruction(rowDuration, entryDirection, groupSelection, null);
                            scene.addInstruction(instruction);
                            working = instruction.createLink(scene.getScene());
                            context.sectionLinks.put(linkId, working);
                        } else {
                            ElementLink<WorldSectionElement> target = working;
                            scene.addInstruction(new DisplayWorldSectionInstruction(rowDuration, entryDirection, groupSelection,
                                () -> scene.getScene().resolve(target)));
                        }
                        scene.idle(rowInterval);
                    }
                }

                private static Selection selectionForGroup(SceneBuilder scene, List<BlockPos> group) {
                    BlockPos first = group.get(0);
                    int minX = first.getX();
                    int minY = first.getY();
                    int minZ = first.getZ();
                    int maxX = first.getX();
                    int maxY = first.getY();
                    int maxZ = first.getZ();
                    for (int i = 1; i < group.size(); i++) {
                        BlockPos pos = group.get(i);
                        minX = Math.min(minX, pos.getX());
                        minY = Math.min(minY, pos.getY());
                        minZ = Math.min(minZ, pos.getZ());
                        maxX = Math.max(maxX, pos.getX());
                        maxY = Math.max(maxY, pos.getY());
                        maxZ = Math.max(maxZ, pos.getZ());
                    }
                    return scene.getScene().getSceneBuildingUtil().select().fromTo(
                        new BlockPos(minX, minY, minZ),
                        new BlockPos(maxX, maxY, maxZ));
                }

                private static List<List<BlockPos>> orderedLayerGroups(BlockPos pos1, BlockPos pos2, String entranceAnimation) {
                    int minX = Math.min(pos1.getX(), pos2.getX());
                    int minY = Math.min(pos1.getY(), pos2.getY());
                    int minZ = Math.min(pos1.getZ(), pos2.getZ());
                    int maxX = Math.max(pos1.getX(), pos2.getX());
                    int maxY = Math.max(pos1.getY(), pos2.getY());
                    int maxZ = Math.max(pos1.getZ(), pos2.getZ());

                    List<BlockPos> positions = new ArrayList<>();
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int x = minX; x <= maxX; x++) {
                                positions.add(new BlockPos(x, y, z));
                            }
                        }
                    }

                    Comparator<BlockPos> tieBreaker = Comparator
                        .comparingInt((BlockPos p) -> p.getY())
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX);

                    Comparator<BlockPos> comparator = switch (entranceAnimation) {
                        case "down" -> Comparator.comparingInt((BlockPos p) -> p.getY()).reversed().thenComparing(tieBreaker);
                        case "up" -> Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparing(tieBreaker);
                        case "south" -> Comparator.comparingInt((BlockPos p) -> p.getZ()).thenComparing(tieBreaker);
                        case "north" -> Comparator.comparingInt((BlockPos p) -> p.getZ()).reversed().thenComparing(tieBreaker);
                        case "east" -> Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparing(tieBreaker);
                        case "west" -> Comparator.comparingInt((BlockPos p) -> p.getX()).reversed().thenComparing(tieBreaker);
                        default -> tieBreaker;
                    };
                    positions.sort(comparator);

                    java.util.function.ToIntFunction<BlockPos> layerKey = switch (entranceAnimation) {
                        case "simultaneous" -> p -> 0;
                        case "south", "north" -> BlockPos::getZ;
                        case "east", "west" -> BlockPos::getX;
                        default -> BlockPos::getY;
                    };

                    List<List<BlockPos>> groups = new ArrayList<>();
                    List<BlockPos> currentGroup = null;
                    int currentKey = Integer.MIN_VALUE;
                    for (BlockPos pos : positions) {
                        int key = layerKey.applyAsInt(pos);
                        if (currentGroup == null || key != currentKey) {
                            currentGroup = new ArrayList<>();
                            groups.add(currentGroup);
                            currentKey = key;
                        }
                        currentGroup.add(pos);
                    }
                    return groups;
                }

                private static List<List<BlockPos>> filterVisibleGroups(List<List<BlockPos>> groups, Context context) {
                    List<List<BlockPos>> filtered = new ArrayList<>();
                    for (List<BlockPos> group : groups) {
                        List<BlockPos> pending = new ArrayList<>();
                        for (BlockPos pos : group) {
                            if (!isBlockVisible(context, pos.asLong())) {
                                pending.add(pos);
                            }
                        }
                        if (!pending.isEmpty()) {
                            filtered.add(pending);
                        }
                    }
                    return filtered;
                }

                private static void updateVisibleRange(Context context, BlockPos pos1, BlockPos pos2, boolean visible) {
                    if (pos1 == null) {
                        return;
                    }
                    BlockPos targetPos2 = pos2 == null ? pos1 : pos2;
                    int minX = Math.min(pos1.getX(), targetPos2.getX());
                    int minY = Math.min(pos1.getY(), targetPos2.getY());
                    int minZ = Math.min(pos1.getZ(), targetPos2.getZ());
                    int maxX = Math.max(pos1.getX(), targetPos2.getX());
                    int maxY = Math.max(pos1.getY(), targetPos2.getY());
                    int maxZ = Math.max(pos1.getZ(), targetPos2.getZ());
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int x = minX; x <= maxX; x++) {
                                long key = BlockPos.asLong(x, y, z);
                                if (context.allBlocksVisible) {
                                    if (visible) {
                                        context.hiddenBlockKeys.remove(key);
                                    } else {
                                        context.hiddenBlockKeys.add(key);
                                    }
                                } else {
                                    if (visible) {
                                        context.visibleBlockKeys.add(key);
                                    } else {
                                        context.visibleBlockKeys.remove(key);
                                    }
                                }
                            }
                        }
                    }
                }

                private static boolean isBlockVisible(Context context, long key) {
                    if (context.allBlocksVisible) {
                        return !context.hiddenBlockKeys.contains(key);
                    }
                    return context.visibleBlockKeys.contains(key);
                }

                private static String autoLinkId(Context context) {
                    return "section_" + (context.sectionLinks.size() + 1);
                }

                private static String normalizeEntranceAnimation(String raw) {
                    if (raw == null || raw.isBlank()) {
                        return null;
                    }
                    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                        case "none", "(无)", "无" -> "none";
                        case "simultaneous", "同时" -> "simultaneous";
                        case "down", "从上到下", "上到下", "top_to_bottom", "top-down" -> "down";
                        case "up", "从下到上", "下到上", "bottom_to_top", "bottom-up" -> "up";
                        case "south", "从北到南", "北到南", "north_to_south", "north-south" -> "south";
                        case "north", "从南到北", "南到北", "south_to_north", "south-north" -> "north";
                        case "east", "从西到东", "西到东", "west_to_east", "west-east" -> "east";
                        case "west", "从东到西", "东到西", "east_to_west", "east-west" -> "west";
                        default -> null;
                    };
                }

                private static Direction parseDirection(String raw) {
                    if (raw == null || raw.isBlank()) {
                        return Direction.DOWN;
                    }
                    return switch (raw.toLowerCase(Locale.ROOT)) {
                        case "up" -> Direction.UP;
                        case "north" -> Direction.NORTH;
                        case "south" -> Direction.SOUTH;
                        case "west" -> Direction.WEST;
                        case "east" -> Direction.EAST;
                        default -> Direction.DOWN;
                    };
                }

                private static Pointing parsePointing(String raw) {
                    if (raw == null || raw.isBlank()) {
                        return Pointing.DOWN;
                    }
                    return switch (raw.toLowerCase(Locale.ROOT)) {
                        case "up" -> Pointing.UP;
                        case "left" -> Pointing.LEFT;
                        case "right" -> Pointing.RIGHT;
                        default -> Pointing.DOWN;
                    };
                }

                private static SoundSource parseSoundSource(String raw) {
                    if (raw == null || raw.isBlank()) {
                        return SoundSource.MASTER;
                    }
                    try {
                        return SoundSource.valueOf(raw.toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        return SoundSource.MASTER;
                    }
                }

                private static PonderPalette parsePalette(String raw) {
                    if (raw == null || raw.isBlank()) {
                        return null;
                    }
                    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                        case "white" -> PonderPalette.WHITE;
                        case "black" -> PonderPalette.BLACK;
                        case "red" -> PonderPalette.RED;
                        case "green" -> PonderPalette.GREEN;
                        case "blue" -> PonderPalette.BLUE;
                        case "input" -> PonderPalette.INPUT;
                        case "output" -> PonderPalette.OUTPUT;
                        case "slow" -> PonderPalette.SLOW;
                        case "medium" -> PonderPalette.MEDIUM;
                        case "fast" -> PonderPalette.FAST;
                        default -> null;
                    };
                }

                private static BlockState applyBlockProperties(BlockState state, Map<String, String> blockProperties) {
                    if (blockProperties == null || blockProperties.isEmpty()) {
                        return state;
                    }
                    var definition = state.getBlock().getStateDefinition();
                    BlockState result = state;
                    for (var entry : blockProperties.entrySet()) {
                        Property<?> property = definition.getProperty(entry.getKey());
                        if (property != null) {
                            result = setPropertyValue(result, property, entry.getValue());
                        }
                    }
                    return result;
                }

                @SuppressWarnings("unchecked")
                private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property, String value) {
                    return property.getValue(value)
                        .map(v -> state.setValue(property, v))
                        .orElse(state);
                }

                private static ItemStack parseItemStackSpec(String raw, String nbtOverride) {
                    if (raw == null || raw.isBlank()) {
                        return null;
                    }
                    String trimmed = raw.trim();
                    String itemIdPart = trimmed;
                    String nbtPart = null;
                    int brace = trimmed.indexOf('{');
                    if (brace >= 0) {
                        itemIdPart = trimmed.substring(0, brace).trim();
                        nbtPart = trimmed.substring(brace).trim();
                    }
                    ResourceLocation itemLoc = ResourceLocation.tryParse(itemIdPart);
                    if (itemLoc == null) {
                        return null;
                    }
                    Item item = BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(null);
                    if (item == null) {
                        return null;
                    }
                    ItemStack stack = new ItemStack(item);
                    String finalNbt = nbtOverride != null && !nbtOverride.isBlank() ? nbtOverride.trim() : nbtPart;
                    if (finalNbt != null && !finalNbt.isBlank() && !"{}".equals(finalNbt)) {
                        try {
                            CompoundTag tag = TagParser.parseTag(finalNbt);
                            if (!tag.isEmpty()) {
                                stack.setTag(tag);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    return stack;
                }

                private static ItemStack applyPatchToItemStack(ItemStack base, CompoundTag patch) {
                    ItemStack copy = base.copy();
                    CompoundTag itemPatch = patch;
                    if (patch.contains("Item", Tag.TAG_COMPOUND)) {
                        CompoundTag entityItem = patch.getCompound("Item");
                        if (entityItem.contains("tag", Tag.TAG_COMPOUND)) {
                            itemPatch = entityItem.getCompound("tag");
                        } else {
                            itemPatch = new CompoundTag();
                        }
                    }
                    if (!itemPatch.isEmpty()) {
                        copy.getOrCreateTag().merge(itemPatch.copy());
                    }
                    return copy;
                }

                private static boolean isLikelyEntityPatch(CompoundTag patch) {
                    return patch.contains("Item", Tag.TAG_COMPOUND)
                        || patch.contains("Age")
                        || patch.contains("PickupDelay")
                        || patch.contains("Health")
                        || patch.contains("Motion")
                        || patch.contains("Pos")
                        || patch.contains("Rotation")
                        || patch.contains("NoGravity")
                        || patch.contains("Glowing")
                        || patch.contains("Invulnerable")
                        || patch.contains("UUID")
                        || patch.contains("Tags");
                }

                private static void mergeEntityNbt(Entity entity, CompoundTag patch) {
                    CompoundTag data = new CompoundTag();
                    entity.saveWithoutId(data);
                    data.merge(patch.copy());
                    entity.load(data);
                }

                // ---- show_extra_structure ----------------------------------------------------

                private static final Set<String> EXTRA_SKIPPED_BLOCKS = Set.of(
                    "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void");
                private static final Set<String> EXTRA_POS_NBT_KEYS = Set.of("x", "y", "z");

                private static final class PlacedBlock {
                    final BlockPos pos;
                    final BlockState state;
                    final CompoundTag nbt;

                    PlacedBlock(BlockPos pos, BlockState state, CompoundTag nbt) {
                        this.pos = pos;
                        this.state = state;
                        this.nbt = nbt;
                    }
                }

                public static void showExtraStructure(SceneBuilder scene, Context context, ResourceLocation structureAssetId,
                                                      BlockPos base, int rotationDegrees, boolean replaceAir,
                                                      Boolean immediateDisplayFlag, Boolean spawnParticlesFlag,
                                                      String entranceAnimation, Integer entranceDuration,
                                                      Integer entranceInterval, Boolean smartDisplayFlag,
                                                      String linkIdRaw, String directionRaw) {
                    if (structureAssetId == null || base == null) {
                        return;
                    }
                    CompoundTag root;
                    try {
                        var resourceOpt = Minecraft.getInstance().getResourceManager().getResource(structureAssetId);
                        if (resourceOpt.isEmpty()) {
                            LOGGER.warn("show_extra_structure resource not found: {}", structureAssetId);
                            return;
                        }
                        try (InputStream is = resourceOpt.get().open()) {
                            root = NbtIo.read(
                                new DataInputStream(new BufferedInputStream(new GZIPInputStream(is))),
                                new NbtAccounter(0x20000000L));
                        }
                    } catch (Exception e) {
                        LOGGER.warn("show_extra_structure failed to read {}: {}", structureAssetId, e.getMessage());
                        return;
                    }

                    List<PlacedBlock> placed = planExtraStructure(root, base, rotationDegrees, !replaceAir);
                    if (placed.isEmpty()) {
                        return;
                    }

                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                    for (PlacedBlock b : placed) {
                        int x = b.pos.getX(), y = b.pos.getY(), z = b.pos.getZ();
                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (z < minZ) minZ = z;
                        if (x > maxX) maxX = x;
                        if (y > maxY) maxY = y;
                        if (z > maxZ) maxZ = z;
                    }
                    BlockPos minCorner = new BlockPos(minX, minY, minZ);
                    BlockPos maxCorner = new BlockPos(maxX, maxY, maxZ);

                    String anim = normalizeEntranceAnimation(entranceAnimation);
                    boolean simultaneous = "simultaneous".equals(anim);
                    boolean directional = anim != null && !"none".equals(anim) && !simultaneous;
                    boolean animatedReveal = simultaneous || directional;
                    boolean placeVisible = !animatedReveal && !Boolean.FALSE.equals(immediateDisplayFlag);
                    boolean particles = placeVisible && !Boolean.FALSE.equals(spawnParticlesFlag);

                    // Air-free strip decomposition shared between base-section ensure and the
                    // simultaneous reveal path.
                    List<List<BlockPos>> placedStrips = segmentExtraForAnimation(placed, "up");

                    ensureSceneCanShowExtra(scene, minCorner, maxCorner, placedStrips, placeVisible);

                    for (PlacedBlock b : placed) {
                        scene.world().setBlock(b.pos, b.state, particles);
                        if (b.nbt != null && !b.nbt.isEmpty()) {
                            CompoundTag patch = b.nbt;
                            Selection sel = scene.getScene().getSceneBuildingUtil().select().position(b.pos);
                            scene.world().modifyBlockEntityNBT(sel, BlockEntity.class, nbt -> nbt.merge(patch.copy()), true);
                        }
                    }
                    applyExtraPlacedVisibility(context, placed, placeVisible);

                    if (!animatedReveal) {
                        return;
                    }

                    String linkId = linkIdRaw == null ? "" : linkIdRaw.trim();
                    if (linkId.isEmpty()) {
                        linkId = autoLinkId(context);
                    }
                    Direction direction = parseDirection(directionRaw);
                    int rowDuration = entranceDuration == null ? 20 : Math.max(0, entranceDuration);
                    int rowInterval = entranceInterval == null ? 1 : Math.max(0, entranceInterval);
                    boolean smartDisplay = !Boolean.FALSE.equals(smartDisplayFlag);

                    List<List<BlockPos>> revealGroups;
                    int revealInterval;
                    if (simultaneous) {
                        revealGroups = placedStrips;
                        revealInterval = 0;
                    } else {
                        revealGroups = segmentExtraForAnimation(placed, anim);
                        revealInterval = rowInterval;
                    }
                    if (smartDisplay) {
                        revealGroups = filterVisibleGroups(revealGroups, context);
                    }
                    if (revealGroups.isEmpty()) {
                        return;
                    }
                    ElementLink<WorldSectionElement> working = context.sectionLinks.get(linkId);
                    for (List<BlockPos> group : revealGroups) {
                        if (group.isEmpty()) {
                            continue;
                        }
                        Selection groupSelection = selectionForGroup(scene, group);
                        if (working == null) {
                            DisplayWorldSectionInstruction inst =
                                new DisplayWorldSectionInstruction(rowDuration, direction, groupSelection, null);
                            scene.addInstruction(inst);
                            working = inst.createLink(scene.getScene());
                            context.sectionLinks.put(linkId, working);
                        } else {
                            ElementLink<WorldSectionElement> target = working;
                            scene.addInstruction(new DisplayWorldSectionInstruction(rowDuration, direction, groupSelection,
                                () -> scene.getScene().resolve(target)));
                        }
                        if (revealInterval > 0) {
                            scene.idle(revealInterval);
                        }
                    }
                    applyExtraPlacedVisibility(context, placed, true);
                }

                private static void ensureSceneCanShowExtra(SceneBuilder scene, BlockPos minCorner, BlockPos maxCorner,
                                                            List<List<BlockPos>> placedStrips, boolean forceVisibleNow) {
                    List<int[]> stripBounds = new ArrayList<>(placedStrips.size());
                    for (List<BlockPos> strip : placedStrips) {
                        if (strip.isEmpty()) {
                            continue;
                        }
                        BlockPos first = strip.get(0);
                        int sxMin = first.getX(), syMin = first.getY(), szMin = first.getZ();
                        int sxMax = sxMin, syMax = syMin, szMax = szMin;
                        for (int i = 1; i < strip.size(); i++) {
                            BlockPos p = strip.get(i);
                            if (p.getX() < sxMin) sxMin = p.getX();
                            if (p.getX() > sxMax) sxMax = p.getX();
                            if (p.getY() < syMin) syMin = p.getY();
                            if (p.getY() > syMax) syMax = p.getY();
                            if (p.getZ() < szMin) szMin = p.getZ();
                            if (p.getZ() > szMax) szMax = p.getZ();
                        }
                        stripBounds.add(new int[]{sxMin, syMin, szMin, sxMax, syMax, szMax});
                    }
                    BlockPos minCornerCaptured = minCorner;
                    BlockPos maxCornerCaptured = maxCorner;
                    scene.addInstruction(ps -> {
                        ps.getWorld().getBounds().encapsulate(minCornerCaptured);
                        ps.getWorld().getBounds().encapsulate(maxCornerCaptured);
                        if (!forceVisibleNow) {
                            if (!ps.getBaseWorldSection().isEmpty()) {
                                for (int[] b : stripBounds) {
                                    Selection sel = ps.getSceneBuildingUtil().select().fromTo(
                                        b[0], b[1], b[2], b[3], b[4], b[5]);
                                    ps.getBaseWorldSection().erase(sel);
                                }
                                ps.getBaseWorldSection().queueRedraw();
                            }
                            return;
                        }
                        if (ps.getBaseWorldSection().isEmpty()) {
                            Selection all = ps.getSceneBuildingUtil().select().everywhere();
                            ps.getBaseWorldSection().set(all);
                            ps.getBaseWorldSection().setVisible(true);
                            ps.getBaseWorldSection().setFade(1);
                        } else {
                            for (int[] b : stripBounds) {
                                Selection sel = ps.getSceneBuildingUtil().select().fromTo(
                                    b[0], b[1], b[2], b[3], b[4], b[5]);
                                ps.getBaseWorldSection().add(sel);
                            }
                        }
                        ps.getBaseWorldSection().queueRedraw();
                    });
                }

                private static void applyExtraPlacedVisibility(Context context, List<PlacedBlock> placed, boolean visible) {
                    for (PlacedBlock b : placed) {
                        long key = b.pos.asLong();
                        if (context.allBlocksVisible) {
                            if (visible) {
                                context.hiddenBlockKeys.remove(key);
                            } else {
                                context.hiddenBlockKeys.add(key);
                            }
                        } else {
                            if (visible) {
                                context.visibleBlockKeys.add(key);
                            } else {
                                context.visibleBlockKeys.remove(key);
                            }
                        }
                    }
                }

                private static Rotation toExtraRotation(int degrees) {
                    int normalized = ((degrees %% 360) + 360) %% 360;
                    return switch (normalized) {
                        case 90 -> Rotation.CLOCKWISE_90;
                        case 180 -> Rotation.CLOCKWISE_180;
                        case 270 -> Rotation.COUNTERCLOCKWISE_90;
                        default -> Rotation.NONE;
                    };
                }

                private static List<PlacedBlock> planExtraStructure(CompoundTag root, BlockPos base, int rotationDegrees, boolean skipAir) {
                    Rotation rotation = toExtraRotation(rotationDegrees);
                    ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
                    BlockState[] palette = new BlockState[paletteTag.size()];
                    for (int i = 0; i < paletteTag.size(); i++) {
                        palette[i] = parsePaletteEntry(paletteTag.getCompound(i));
                    }
                    ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);

                    List<BlockPos> rotatedPositions = new ArrayList<>();
                    List<BlockState> rotatedStates = new ArrayList<>();
                    List<CompoundTag> blockNbts = new ArrayList<>();

                    if (skipAir) {
                        for (int i = 0; i < blocks.size(); i++) {
                            CompoundTag entry = blocks.getCompound(i);
                            BlockPos src = readExtraEntryPos(entry);
                            if (src == null) continue;
                            BlockState state = resolveExtraEntryState(entry, palette);
                            if (state == null || isExtraSkippedBlock(state)) continue;
                            rotatedPositions.add(src.rotate(rotation));
                            rotatedStates.add(state.rotate(rotation));
                            blockNbts.add(readExtraBlockEntityPatch(entry));
                        }
                    } else {
                        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
                        if (sizeTag.size() < 3) {
                            return List.of();
                        }
                        int sizeX = sizeTag.getInt(0);
                        int sizeY = sizeTag.getInt(1);
                        int sizeZ = sizeTag.getInt(2);

                        Map<Long, CompoundTag> entryByPos = new HashMap<>(blocks.size());
                        for (int i = 0; i < blocks.size(); i++) {
                            CompoundTag entry = blocks.getCompound(i);
                            BlockPos p = readExtraEntryPos(entry);
                            if (p == null) continue;
                            entryByPos.put(p.asLong(), entry);
                        }

                        BlockState airState = Blocks.AIR.defaultBlockState();
                        for (int x = 0; x < sizeX; x++) {
                            for (int y = 0; y < sizeY; y++) {
                                for (int z = 0; z < sizeZ; z++) {
                                    BlockPos src = new BlockPos(x, y, z);
                                    CompoundTag entry = entryByPos.get(src.asLong());
                                    BlockState state;
                                    CompoundTag patch = null;
                                    if (entry == null) {
                                        state = airState;
                                    } else {
                                        BlockState resolved = resolveExtraEntryState(entry, palette);
                                        if (resolved == null || isExtraSkippedBlock(resolved)) {
                                            state = airState;
                                        } else {
                                            state = resolved;
                                            patch = readExtraBlockEntityPatch(entry);
                                        }
                                    }
                                    rotatedPositions.add(src.rotate(rotation));
                                    rotatedStates.add(state.rotate(rotation));
                                    blockNbts.add(patch);
                                }
                            }
                        }
                    }

                    if (rotatedPositions.isEmpty()) {
                        return List.of();
                    }
                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                    for (BlockPos p : rotatedPositions) {
                        if (p.getX() < minX) minX = p.getX();
                        if (p.getY() < minY) minY = p.getY();
                        if (p.getZ() < minZ) minZ = p.getZ();
                    }
                    int offsetX = base.getX() - minX;
                    int offsetY = base.getY() - minY;
                    int offsetZ = base.getZ() - minZ;
                    List<PlacedBlock> result = new ArrayList<>(rotatedPositions.size());
                    for (int i = 0; i < rotatedPositions.size(); i++) {
                        BlockPos rp = rotatedPositions.get(i);
                        BlockPos world = new BlockPos(
                            rp.getX() + offsetX,
                            rp.getY() + offsetY,
                            rp.getZ() + offsetZ);
                        result.add(new PlacedBlock(world, rotatedStates.get(i), blockNbts.get(i)));
                    }
                    return result;
                }

                private static BlockPos readExtraEntryPos(CompoundTag entry) {
                    ListTag pos = entry.getList("pos", Tag.TAG_INT);
                    if (pos.size() < 3) return null;
                    return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
                }

                private static BlockState resolveExtraEntryState(CompoundTag entry, BlockState[] palette) {
                    int stateIdx = entry.getInt("state");
                    if (stateIdx < 0 || stateIdx >= palette.length) return null;
                    return palette[stateIdx];
                }

                private static boolean isExtraSkippedBlock(BlockState state) {
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    return key != null && EXTRA_SKIPPED_BLOCKS.contains(key.toString());
                }

                private static CompoundTag readExtraBlockEntityPatch(CompoundTag entry) {
                    if (!entry.contains("nbt", Tag.TAG_COMPOUND)) return null;
                    CompoundTag raw = entry.getCompound("nbt").copy();
                    for (String absoluteKey : EXTRA_POS_NBT_KEYS) {
                        raw.remove(absoluteKey);
                    }
                    return raw.isEmpty() ? null : raw;
                }

                private static BlockState parsePaletteEntry(CompoundTag entry) {
                    String name = entry.getString("Name");
                    ResourceLocation id = ResourceLocation.tryParse(name);
                    if (id == null) {
                        return null;
                    }
                    Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                    if (block == null) {
                        return null;
                    }
                    BlockState state = block.defaultBlockState();
                    if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
                        CompoundTag props = entry.getCompound("Properties");
                        var def = block.getStateDefinition();
                        for (String key : props.getAllKeys()) {
                            Property<?> prop = def.getProperty(key);
                            if (prop != null) {
                                state = applyExtraProperty(state, prop, props.getString(key));
                            }
                        }
                    }
                    return state;
                }

                private static <T extends Comparable<T>> BlockState applyExtraProperty(BlockState state,
                                                                                       Property<T> prop, String value) {
                    return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
                }

                private static List<List<BlockPos>> segmentExtraForAnimation(List<PlacedBlock> blocks, String anim) {
                    if (blocks.isEmpty()) {
                        return List.of();
                    }
                    String a = anim == null ? "" : anim;
                    ToIntFunction<BlockPos> layerKey;
                    ToIntFunction<BlockPos> rowKey;
                    ToIntFunction<BlockPos> stripKey;
                    boolean reverseLayer;
                    switch (a) {
                        case "down" -> { layerKey = BlockPos::getY; rowKey = BlockPos::getZ; stripKey = BlockPos::getX; reverseLayer = true; }
                        case "up" -> { layerKey = BlockPos::getY; rowKey = BlockPos::getZ; stripKey = BlockPos::getX; reverseLayer = false; }
                        case "south" -> { layerKey = BlockPos::getZ; rowKey = BlockPos::getY; stripKey = BlockPos::getX; reverseLayer = false; }
                        case "north" -> { layerKey = BlockPos::getZ; rowKey = BlockPos::getY; stripKey = BlockPos::getX; reverseLayer = true; }
                        case "east" -> { layerKey = BlockPos::getX; rowKey = BlockPos::getY; stripKey = BlockPos::getZ; reverseLayer = false; }
                        case "west" -> { layerKey = BlockPos::getX; rowKey = BlockPos::getY; stripKey = BlockPos::getZ; reverseLayer = true; }
                        case "simultaneous" -> { layerKey = p -> 0; rowKey = BlockPos::getY; stripKey = BlockPos::getX; reverseLayer = false; }
                        default -> { layerKey = BlockPos::getY; rowKey = BlockPos::getZ; stripKey = BlockPos::getX; reverseLayer = false; }
                    }
                    List<BlockPos> positions = new ArrayList<>(blocks.size());
                    for (PlacedBlock b : blocks) {
                        positions.add(b.pos);
                    }
                    Comparator<BlockPos> cmp = Comparator
                        .comparingInt(layerKey)
                        .thenComparingInt(rowKey)
                        .thenComparingInt(stripKey);
                    if (reverseLayer) {
                        cmp = Comparator.comparingInt(layerKey).reversed()
                            .thenComparingInt(rowKey)
                            .thenComparingInt(stripKey);
                    }
                    positions.sort(cmp);
                    List<List<BlockPos>> groups = new ArrayList<>();
                    List<BlockPos> currentStrip = null;
                    int curLayer = Integer.MIN_VALUE;
                    int curRow = Integer.MIN_VALUE;
                    int curStrip = Integer.MIN_VALUE;
                    for (BlockPos p : positions) {
                        int lk = layerKey.applyAsInt(p);
                        int rk = rowKey.applyAsInt(p);
                        int sk = stripKey.applyAsInt(p);
                        boolean breakStrip = currentStrip == null
                            || lk != curLayer
                            || rk != curRow
                            || sk != curStrip + 1;
                        if (breakStrip) {
                            currentStrip = new ArrayList<>();
                            groups.add(currentStrip);
                            curLayer = lk;
                            curRow = rk;
                        }
                        currentStrip.add(p);
                        curStrip = sk;
                    }
                    return groups;
                }
            }
            """.formatted(target.generatedPackage);
    }

    private static void updateFabricMetadata(JavaModuleScanResult.TargetProject targetProject) throws IOException {
        String clientEntry = targetProject.generatedPackage + ".GeneratedPonderFabricClient";
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(targetProject.metadataPath, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonObject entrypoints = root.has("entrypoints") && root.get("entrypoints").isJsonObject()
            ? root.getAsJsonObject("entrypoints")
            : new JsonObject();
        JsonArray clientArray = entrypoints.has("client") && entrypoints.get("client").isJsonArray()
            ? entrypoints.getAsJsonArray("client")
            : new JsonArray();

        boolean exists = false;
        for (JsonElement element : clientArray) {
            if (element.isJsonPrimitive() && clientEntry.equals(element.getAsString())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            clientArray.add(clientEntry);
        }

        entrypoints.add("client", clientArray);
        root.add("entrypoints", entrypoints);
        Files.writeString(targetProject.metadataPath, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static JavaModuleExportManifest readManifest(Path targetRoot) {
        Path manifestPath = targetRoot.resolve(GENERATED_DIR).resolve(MANIFEST_NAME).normalize();
        if (!Files.isRegularFile(manifestPath)) {
            return new JavaModuleExportManifest();
        }
        try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            JavaModuleExportManifest manifest = GSON.fromJson(reader, JavaModuleExportManifest.class);
            return manifest == null ? new JavaModuleExportManifest() : manifest;
        } catch (Exception ignored) {
            return new JavaModuleExportManifest();
        }
    }

    private static void applyTargetDefaults(JavaModuleExportManifest manifest,
                                            JavaModuleScanResult.TargetProject targetProject) {
        manifest.loader = targetProject.loader;
        manifest.modId = targetProject.modId;
        manifest.basePackage = targetProject.basePackage;
        manifest.generatedPackage = targetProject.generatedPackage;
        if (manifest.scenes == null) {
            manifest.scenes = new LinkedHashMap<>();
        }
        manifest.scenes.values().forEach(entry -> {
            if (entry.javaFiles == null) {
                entry.javaFiles = new ArrayList<>();
            }
            if (entry.resourceFiles == null) {
                entry.resourceFiles = new ArrayList<>();
            }
            if (entry.langEntries == null) {
                entry.langEntries = new LinkedHashMap<>();
            }
        });
    }

    private static void collectOwnedPaths(Path root, List<String> relativePaths, List<Path> deletions) {
        if (relativePaths == null) {
            return;
        }
        for (String relativePath : relativePaths) {
            try {
                deletions.add(resolveTargetPath(root, relativePath));
            } catch (Exception ignored) {
            }
        }
    }

    private static Path resolveTargetPath(Path root, String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        ensureInsideRoot(root, target);
        return target;
    }

    private static void ensureInsideRoot(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Refusing to access path outside target root: " + normalizedPath);
        }
    }

    private static void deleteIfExists(Path root, Path path) throws IOException {
        ensureInsideRoot(root, path);
        Files.deleteIfExists(path);
    }

    private static String relativeToRoot(Path root, Path path) {
        ensureInsideRoot(root, path);
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    @Nullable
    private static String parseFabricModId(Path fabricMetadata, Properties properties) {
        try (Reader reader = Files.newBufferedReader(fabricMetadata, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("id")) {
                return null;
            }
            return resolvePlaceholders(root.get("id").getAsString(), properties);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String parseForgeModId(Path modsToml, Properties properties) {
        try {
            String content = Files.readString(modsToml, StandardCharsets.UTF_8);
            Matcher matcher = MOD_ID_PATTERN.matcher(content);
            if (!matcher.find()) {
                return null;
            }
            return resolvePlaceholders(matcher.group(1), properties);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String parseFabricBasePackage(Path fabricMetadata, Properties properties) {
        try (Reader reader = Files.newBufferedReader(fabricMetadata, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("entrypoints") || !root.get("entrypoints").isJsonObject()) {
                return null;
            }
            JsonObject entrypoints = root.getAsJsonObject("entrypoints");
            String className = firstEntrypoint(entrypoints, "client");
            if (className == null) {
                className = firstEntrypoint(entrypoints, "main");
            }
            if (className == null) {
                return null;
            }
            className = resolvePlaceholders(className, properties);
            int lastDot = className.lastIndexOf('.');
            return lastDot > 0 ? className.substring(0, lastDot) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String firstEntrypoint(JsonObject entrypoints, String key) {
        if (!entrypoints.has(key) || !entrypoints.get(key).isJsonArray()) {
            return null;
        }
        JsonArray array = entrypoints.getAsJsonArray(key);
        if (array.isEmpty()) {
            return null;
        }
        JsonElement first = array.get(0);
        return first.isJsonPrimitive() ? first.getAsString() : null;
    }

    @Nullable
    private static String parseForgeBasePackage(Path javaRoot) {
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (!MOD_ANNOTATION_PATTERN.matcher(content).find()) {
                    continue;
                }
                Matcher matcher = PACKAGE_PATTERN.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static String findFirstPackage(Path javaRoot) {
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = PACKAGE_PATTERN.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Properties loadGradleProperties(Path targetRoot) {
        for (Path candidate : List.of(
            targetRoot.resolve("gradle.properties"),
            targetRoot.getParent() == null ? null : targetRoot.getParent().resolve("gradle.properties"),
            targetRoot.getParent() == null || targetRoot.getParent().getParent() == null
                ? null
                : targetRoot.getParent().getParent().resolve("gradle.properties"))) {
            if (candidate == null || !Files.isRegularFile(candidate)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                Properties properties = new Properties();
                properties.load(reader);
                return properties;
            } catch (Exception ignored) {
            }
        }
        return new Properties();
    }

    private static String resolvePlaceholders(String raw, Properties properties) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(raw);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = properties.getProperty(key, matcher.group(0));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @Nullable
    private static ResourceLocation parseStructureLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.contains(":")) {
            return ResourceLocation.tryParse(raw);
        }
        return new ResourceLocation("ponder", raw);
    }

    @Nullable
    private static Path findStructureSourcePath(ResourceLocation id, @Nullable String pack) {
        if ("ponderer".equals(id.getNamespace())) {
            if (pack != null && !pack.isBlank()) {
                Path packPath = SceneStore.resolveLocalSyncStructurePath(id, pack);
                if (packPath != null && Files.exists(packPath)) {
                    return packPath;
                }
            }
            Path local = SceneStore.getStructurePath(id);
            if (local != null && Files.exists(local)) {
                return local;
            }
            return null;
        }

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }
        Path generated = server.getWorldPath(LevelResource.ROOT).resolve("generated").resolve(id.getNamespace())
            .resolve("structures").resolve(id.getPath() + ".nbt").normalize();
        return Files.exists(generated) ? generated : null;
    }

    private static List<DslScene.SceneSegment> normalizeSegments(DslScene scene) {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes;
        }
        DslScene.SceneSegment segment = new DslScene.SceneSegment();
        segment.steps = List.of();
        return List.of(segment);
    }

    private static List<DslScene.DslStep> safeSteps(@Nullable List<DslScene.DslStep> steps) {
        return steps == null ? List.of() : steps;
    }

    private static List<String> safeList(@Nullable List<String> list) {
        return list == null ? List.of() : list;
    }

    private static boolean hasPos(@Nullable List<Integer> pos) {
        return pos != null && pos.size() >= 3;
    }

    private static boolean isNonBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isItemStackSpec(String raw, @Nullable String nbtOverride) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        String itemIdPart = trimmed;
        if (trimmed.contains("{")) {
            itemIdPart = trimmed.substring(0, trimmed.indexOf('{')).trim();
        }
        ResourceLocation itemLoc = ResourceLocation.tryParse(itemIdPart);
        if (itemLoc == null) {
            return false;
        }
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(Items.AIR);
        return item != Items.AIR;
    }

    @Nullable
    private static Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String classNameFor(String sceneId) {
        String sanitized = storyboardBasePath(sceneId).replace('/', '_').replace('-', '_').replace('.', '_');
        sanitized = Arrays.stream(sanitized.split("_+"))
            .filter(part -> !part.isBlank())
            .map(JavaModuleExportService::capitalize)
            .collect(Collectors.joining());
        String digest = shortDigest(sceneId);
        return "Generated" + (sanitized.isBlank() ? "Scene" : sanitized) + "_" + digest;
    }

    private static String capitalize(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        if (raw.length() == 1) {
            return raw.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    private static String storyboardBasePath(String sceneId) {
        ResourceLocation id = ResourceLocation.tryParse(sceneId);
        return id == null ? "scene" : id.getPath();
    }

    private static String storyboardPath(DslScene scene, DslScene.SceneSegment segment, int index, int total) {
        String basePath = storyboardBasePath(scene.id);
        if (total <= 1) {
            return basePath;
        }
        String suffix = segment.id != null && !segment.id.isBlank() ? segment.id : String.valueOf(index + 1);
        return basePath + "_" + suffix;
    }

    private static String fallbackSegmentTitle(DslScene scene, DslScene.SceneSegment segment, String titlePath,
                                               int total, int index) {
        if (segment != null && segment.title != null && !resolveExportText(segment.title, "").isBlank()) {
            return resolveExportText(segment.title, "");
        }
        if (scene.title != null && !resolveExportText(scene.title, "").isBlank()) {
            String sceneTitle = resolveExportText(scene.title, "");
            return total > 1 ? sceneTitle + " #" + (index + 1) : sceneTitle;
        }
        return titlePath;
    }

    @Nullable
    private static String segmentId(@Nullable DslScene.SceneSegment segment) {
        if (segment == null || segment.id == null || segment.id.isBlank()) {
            return null;
        }
        return segment.id;
    }

    private static void addFinding(List<JavaModuleScanResult.Finding> target, String category,
                                   @Nullable String sceneId, @Nullable String sceneKey,
                                   @Nullable String segmentId, @Nullable String stepType,
                                   String message) {
        JavaModuleScanResult.Finding finding = new JavaModuleScanResult.Finding();
        finding.category = category;
        finding.sceneId = sceneId;
        finding.sceneKey = sceneKey;
        finding.segmentId = segmentId;
        finding.stepType = stepType;
        finding.message = message;
        target.add(finding);
    }

    private static String firstMessage(List<JavaModuleScanResult.Finding> findings, String fallback) {
        return findings.isEmpty() ? fallback : findings.get(0).message;
    }

    private static String resourceLocationArrayExpr(List<String> ids) {
        return resourceLocationArrayExpr(ids, null);
    }

    private static String resourceLocationArrayExpr(List<String> ids, @Nullable String extraId) {
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        if (ids != null) {
            combined.addAll(ids);
        }
        if (extraId != null && !extraId.isBlank()) {
            combined.add(extraId);
        }
        if (combined.isEmpty()) {
            return "new ResourceLocation[0]";
        }
        return "new ResourceLocation[]{" + combined.stream().map(JavaModuleExportService::resourceLocationExpr).collect(Collectors.joining(", ")) + "}";
    }

    private static String resourceLocationExpr(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return "new ResourceLocation(\"minecraft\", \"air\")";
        }
        return "new ResourceLocation(" + stringExpr(loc.getNamespace()) + ", " + stringExpr(loc.getPath()) + ")";
    }

    private static String localizedTextExpr(@Nullable LocalizedText text, String fallback) {
        return stringExpr(resolveExportText(text, fallback));
    }

    private static String resolveExportText(@Nullable LocalizedText text, String fallback) {
        if (text == null || text.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String resolved = text.resolve("en_us");
        if (resolved != null) {
            return resolved;
        }
        return fallback == null ? "" : fallback;
    }

    private static String stringExpr(@Nullable String value) {
        return value == null ? "null" : "\"" + escapeJava(value) + "\"";
    }

    private static String boolExpr(@Nullable Boolean value) {
        return value == null ? "null" : Boolean.toString(value);
    }

    private static String intExpr(@Nullable Integer value) {
        return value == null ? "null" : value.toString();
    }

    private static String floatExpr(@Nullable Float value) {
        return value == null ? "null" : floatLiteral(value);
    }

    private static String floatLiteral(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "0.0f";
        }
        return trimNumeric(value) + "f";
    }

    private static String doubleLiteral(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0.0";
        }
        return trimNumeric(value);
    }

    private static String trimNumeric(double value) {
        String raw = Double.toString(value);
        if (raw.endsWith(".0")) {
            return raw.substring(0, raw.length() - 2) + ".0";
        }
        return raw;
    }

    private static String vecExpr(@Nullable List<Double> values) {
        if (values == null || values.size() < 3) {
            return "null";
        }
        return "new Vec3(" + doubleLiteral(values.get(0)) + ", " + doubleLiteral(values.get(1)) + ", " + doubleLiteral(values.get(2)) + ")";
    }

    private static String vecExprOrDefault(@Nullable List<Double> values, double x, double y, double z) {
        if (values == null || values.size() < 3) {
            return "new Vec3(" + doubleLiteral(x) + ", " + doubleLiteral(y) + ", " + doubleLiteral(z) + ")";
        }
        return vecExpr(values);
    }

    private static String blockPosExpr(@Nullable List<Integer> values) {
        if (values == null || values.size() < 3) {
            return "null";
        }
        return "new BlockPos(" + values.get(0) + ", " + values.get(1) + ", " + values.get(2) + ")";
    }

    private static String boundsExpr(@Nullable List<Integer> values) {
        if (values == null || values.size() < 3) {
            return "new BlockPos(0, 0, 0)";
        }
        return "new BlockPos(" + values.get(0) + ", " + values.get(1) + ", " + values.get(2) + ")";
    }

    private static String mapExpr(@Nullable Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "null";
        }
        String entries = map.entrySet().stream()
            .map(entry -> "Map.entry(" + stringExpr(entry.getKey()) + ", " + stringExpr(entry.getValue()) + ")")
            .collect(Collectors.joining(", "));
        return "Map.ofEntries(" + entries + ")";
    }

    private static String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }

    private static String hashSceneContent(String source,
                                           List<BinaryFile> binaries,
                                           Map<String, Map<String, String>> langEntries) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source.getBytes(StandardCharsets.UTF_8));
            for (BinaryFile binary : binaries) {
                digest.update(binary.relativePath().getBytes(StandardCharsets.UTF_8));
                digest.update(binary.bytes());
            }
            if (langEntries != null && !langEntries.isEmpty()) {
                digest.update(GSON.toJson(sortNestedStringMap(langEntries)).getBytes(StandardCharsets.UTF_8));
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return shortDigest(source);
        }
    }

    private static Map<String, Map<String, String>> sortNestedStringMap(Map<String, Map<String, String>> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> sortStringMap(entry.getValue()),
                (left, right) -> right,
                LinkedHashMap::new));
    }

    private static String shortDigest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4 && i < bytes.length; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String escapeJava(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    private record TextFile(String relativePath, String content) {
    }

    private record BinaryFile(String relativePath, byte[] bytes) {
    }

    private record RenderedScene(JavaModuleExportManifest.SceneEntry manifestEntry,
                                 TextFile sceneJava,
                                 List<BinaryFile> structureFiles) {
    }

    private static final class StructureResolution {
        ResourceLocation sourceId;
        Path sourcePath;
        String targetResourceId;
        String targetRelativePath;
    }

    private static final class ExportReport {
        String loader;
        String modId;
        String basePackage;
        String targetRoot;
        List<JavaModuleScanResult.Finding> fatalFindings;
        List<JavaModuleScanResult.Finding> sceneUnsupportedFindings;
        List<JavaModuleScanResult.Finding> warningFindings;
        List<JavaModuleScanResult.Finding> ignoredFindings;
        List<SceneExportOutcome> sceneOutcomes;
    }
}
