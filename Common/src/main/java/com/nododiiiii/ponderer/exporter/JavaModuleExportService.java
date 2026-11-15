package com.nododiiiii.ponderer.exporter;

import com.nododiiiii.ponderer.product.catalog.CustomPonderTags;
import com.nododiiiii.ponderer.product.catalog.PondererAttribution;
import com.nododiiiii.ponderer.scene.step.ParticleEffectSpec;
import com.nododiiiii.ponderer.runtime.api.SceneItemBindings;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import static com.nododiiiii.ponderer.product.catalog.PondererAttribution.ATTRIBUTION_TAG_PATH;
import static com.nododiiiii.ponderer.product.catalog.PondererAttribution.ATTRIBUTION_TITLE_EN_US;
import static com.nododiiiii.ponderer.product.catalog.PondererAttribution.ATTRIBUTION_TITLE_ZH_CN;
import static com.nododiiiii.ponderer.product.catalog.PondererAttribution.ATTRIBUTION_DESCRIPTION_EN_US;
import static com.nododiiiii.ponderer.product.catalog.PondererAttribution.ATTRIBUTION_DESCRIPTION_ZH_CN;

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
        "spawn_particles",
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
        "set_interface_slots",
        "set_interface_data",
        "set_interface_context",
        "interface_action",
        "hide_interface",
        "await_interface",
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
    private static final String LICENSE_RESOURCE = "/META-INF/licenses/ponderer/LICENSE";

    private final StructureResourceSource resources;

    public JavaModuleExportService(StructureResourceSource resources) {
        this.resources = java.util.Objects.requireNonNull(resources);
    }

    public JavaModuleScanResult scan(JavaModuleExportRequest request) {
        JavaModuleScanResult result = new JavaModuleScanResult();
        if (request == null || request.targetRoot() == null) {
            addFinding(result.fatalFindings, "fatal", null, null, null, null, "Target mod root is required.");
            return result;
        }

        JavaModuleScanResult.TargetProject targetProject = detectTargetProject(request.targetRoot(), request.mode(), result.fatalFindings);
        result.targetProject = targetProject;
        if (targetProject != null) targetProject.mode = request.mode();
        if (targetProject == null) {
            return result;
        }

        if (request.supportOnly()) {
            if (request.mode() != JavaModuleExportRequest.Mode.JAVA_SUPPORT) addFinding(result.fatalFindings, "selection", null, null, null, null, "Select scenes for a Lib export.");
            return result;
        }

        List<DslScene> sourceScenes = request.scenes().stream()
            .map(com.nododiiiii.ponderer.scene.SceneSnapshot::document).toList();
        List<DslScene> scenes = collectRequestedScenes(request, sourceScenes, result.fatalFindings);
        if (scenes.isEmpty()) {
            addFinding(result.fatalFindings, "fatal", null, null, null, null, "No exportable scenes were found.");
            return result;
        }

        for (DslScene scene : scenes) {
            JavaModuleScanResult.ScenePlan plan = scanScene(targetProject, scene, sourceScenes, result);
            result.scenePlans.add(plan);
        }

        return result;
    }

    public JavaModuleExportResult export(JavaModuleExportRequest request, JavaModuleScanResult scanResult) {
        if (request == null || scanResult == null) {
            return JavaModuleExportResult.failure("Missing export request or scan result.");
        }
        if (scanResult.hasFatalFindings()) {
            return JavaModuleExportResult.failure(firstMessage(scanResult.fatalFindings, "Scan contains fatal problems."));
        }
        if (scanResult.targetProject == null) {
            return JavaModuleExportResult.failure("Target project information is unavailable.");
        }

        if (request.supportOnly()) {
            return exportSupportOnly(scanResult.targetProject);
        }

        JavaModuleExportManifest manifest = readManifest(scanResult.targetProject.targetRoot);
        String previousMode = manifest.mode;
        boolean migratingJson = request.mode() == JavaModuleExportRequest.Mode.JSON_LIB
            && manifest.scenes.values().stream().anyMatch(entry -> entry.resourceId == null || entry.javaFiles != null && !entry.javaFiles.isEmpty());
        if ((migratingJson || manifest.mode != null && !manifest.mode.equals(request.mode().name()))
            && !scanResult.scenePlans.stream().map(plan -> plan.sceneId).collect(java.util.stream.Collectors.toSet()).containsAll(manifest.scenes.keySet()))
            return JavaModuleExportResult.failure("Changing export mode or migrating to resource-only JSON requires selecting every previously exported scene.");
        if (manifest.generatedSources == null) manifest.generatedSources = new LinkedHashMap<>();
        Map<String, String> previousSources = new LinkedHashMap<>(manifest.generatedSources);
        manifest.ownsClientEntrypoint |= previousSources.containsKey("src/main/java/" + packageToPath(scanResult.targetProject.generatedPackage) + "/GeneratedPonderFabricClient.java");
        manifest.mode = request.mode().name();
        applyTargetDefaults(manifest, scanResult.targetProject);

        List<TextFile> textWrites = new ArrayList<>();
        try {
            textWrites.add(buildLicenseWrite());
        } catch (IOException e) {
            return JavaModuleExportResult.failure("Failed to read Ponderer license: " + e.getMessage());
        }
        List<BinaryFile> binaryWrites = new ArrayList<>();
        if (!LibExportSources.usesLib(scanResult.targetProject))
        for (String iconPath : List.of(PondererAttribution.ICON_TEXTURE_PATH, PondererAttribution.ICON_MODEL_RESOURCE_PATH)) {
            try (InputStream icon = JavaModuleExportService.class.getResourceAsStream("/assets/ponderer/" + iconPath)) {
                if (icon == null) {
                    return JavaModuleExportResult.failure("Missing bundled Ponderer attribution resource: " + iconPath);
                }
                byte[] bytes = icon.readAllBytes();
                if (iconPath.endsWith(".json")) {
                    bytes = new String(bytes, StandardCharsets.UTF_8)
                        .replace("ponderer:", scanResult.targetProject.modId + ":").getBytes(StandardCharsets.UTF_8);
                }
                binaryWrites.add(new BinaryFile("src/main/resources/assets/" + scanResult.targetProject.modId
                    + "/" + iconPath, bytes));
            } catch (IOException e) {
                return JavaModuleExportResult.failure("Failed to read Ponderer attribution resource: " + e.getMessage());
            }
        }
        List<Path> deletions = new ArrayList<>();
        if (LibExportSources.usesLib(scanResult.targetProject)) {
            // Older Java + Lib exports copied these shared assets. Remove only unchanged copies.
            for (String iconPath : List.of(PondererAttribution.ICON_TEXTURE_PATH, PondererAttribution.ICON_MODEL_RESOURCE_PATH)) {
                Path exportedIcon = scanResult.targetProject.resourcesRoot.resolve(
                    "assets/" + scanResult.targetProject.modId + "/" + iconPath);
                ensureInsideRoot(scanResult.targetProject.targetRoot, exportedIcon);
                if (!Files.isRegularFile(exportedIcon)) continue;
                try (InputStream icon = JavaModuleExportService.class.getResourceAsStream("/assets/ponderer/" + iconPath)) {
                    if (icon == null) continue;
                    byte[] expected = icon.readAllBytes();
                    if (iconPath.endsWith(".json")) expected = new String(expected, StandardCharsets.UTF_8)
                        .replace("ponderer:", scanResult.targetProject.modId + ":").getBytes(StandardCharsets.UTF_8);
                    if (Arrays.equals(expected, Files.readAllBytes(exportedIcon))) deletions.add(exportedIcon);
                } catch (IOException error) {
                    return JavaModuleExportResult.failure("Cannot inspect attribution asset " + exportedIcon + ": " + error.getMessage());
                }
            }
        }
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
            outcome.className = request.mode() == JavaModuleExportRequest.Mode.JSON_LIB ? null : scenePlan.className;
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

            RenderedScene rendered;
            try { rendered = renderScene(scanResult.targetProject, scenePlan, outcome); }
            catch (RuntimeException error) { return JavaModuleExportResult.failure("Cannot render " + scenePlan.sceneId + ": " + error.getMessage()); }
            textWrites.add(rendered.sceneFile());
            binaryWrites.addAll(rendered.structureFiles());
            manifest.scenes.put(scenePlan.sceneId, rendered.manifestEntry());
            outcome.ownedFiles.add(rendered.sceneFile().relativePath());
            rendered.structureFiles().forEach(file -> outcome.ownedFiles.add(file.relativePath()));
            outcomes.add(outcome);
        }

        Set<String> currentSceneIds = outcomes.stream()
            .filter(outcome -> outcome.status != SceneExportOutcome.Status.SKIPPED_SCENE)
            .map(outcome -> outcome.sceneId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        try { textWrites.addAll(buildGlobalWrites(scanResult.targetProject, manifest, currentSceneIds, staleManagedLangKeys)); }
        catch (RuntimeException error) { return JavaModuleExportResult.failure("Cannot prepare export resources: " + error.getMessage()); }

        Path exportDir = scanResult.targetProject.targetRoot.resolve(GENERATED_DIR).normalize();
        Path manifestPath = exportDir.resolve(MANIFEST_NAME).normalize();
        Path reportPath = exportDir.resolve(REPORT_NAME).normalize();
        if (!LibExportSources.usesLib(scanResult.targetProject) && previousMode != null
            && !previousMode.equals(JavaModuleExportRequest.Mode.JAVA_SUPPORT.name()))
            deletions.add(scanResult.targetProject.resourcesRoot.resolve("META-INF/pondererlib.features/ponderer_export.properties"));
        if (request.mode() == JavaModuleExportRequest.Mode.JSON_LIB) {
            previousSources.keySet().stream().filter(path -> path.endsWith(".java"))
                .forEach(path -> deletions.add(resolveTargetPath(scanResult.targetProject.targetRoot, path)));
        } else if (JavaModuleExportRequest.Mode.JSON_LIB.name().equals(previousMode)) {
            deletions.add(scanResult.targetProject.resourcesRoot.resolve("assets/" + scanResult.targetProject.modId + "/ponderer/content/ponderer_export.json"));
        }
        if (LibExportSources.usesLib(scanResult.targetProject)) {
            String support = "src/main/java/" + packageToPath(scanResult.targetProject.generatedPackage) + "/GeneratedPonderSupport.java";
            if (previousSources.containsKey(support)) deletions.add(resolveTargetPath(scanResult.targetProject.targetRoot, support));
        }
        if (request.mode() == JavaModuleExportRequest.Mode.JSON_LIB
            || previousMode != null && !previousMode.equals(JavaModuleExportRequest.Mode.JAVA_SUPPORT.name()) && !LibExportSources.usesLib(scanResult.targetProject)) {
            Path extension = resolveTargetPath(scanResult.targetProject.targetRoot, "src/main/java/"
                + packageToPath(LibExportSources.extensionPackage(scanResult.targetProject)) + "/PonderExtensions.java");
            try {
                if (Files.exists(extension)) {
                    if (!Files.readString(extension, StandardCharsets.UTF_8).equals(LibExportSources.extension(scanResult.targetProject)))
                        return JavaModuleExportResult.failure("Handwritten Lib extension preserved: " + extension + ". Register it with ResourceSceneContents.registerExtension and reference its ID in the content manifest, or move it before changing mode.");
                    deletions.add(extension);
                }
            } catch (IOException error) { return JavaModuleExportResult.failure("Cannot inspect " + extension + ": " + error.getMessage()); }
        }
        for (Path deletion : deletions) {
            String relative = relativeToRoot(scanResult.targetProject.targetRoot, deletion);
            String expected = previousSources.get(relative);
            if (relative.endsWith(".java") && expected != null) try {
                if (Files.exists(deletion) && !Files.readString(deletion, StandardCharsets.UTF_8).equals(expected))
                    return JavaModuleExportResult.failure("Handwritten changes preserved; export refused to delete " + deletion);
            } catch (IOException error) { return JavaModuleExportResult.failure("Cannot inspect " + deletion + ": " + error.getMessage()); }
            manifest.generatedSources.remove(relative);
        }
        for (TextFile write : textWrites) {
            if (!write.relativePath().endsWith(".java") || write.relativePath().endsWith("/ponderext/PonderExtensions.java")) continue;
            Path path = resolveTargetPath(scanResult.targetProject.targetRoot, write.relativePath());
            try {
                if (Files.exists(path)) {
                    String current = Files.readString(path, StandardCharsets.UTF_8);
                    String previous = previousSources.get(write.relativePath());
                    if (previous != null && !current.equals(previous)) return JavaModuleExportResult.failure("Handwritten changes preserved; export refused to overwrite " + path);
                    if (previous == null && !current.contains("Auto-generated by The Ponderer") && !path.getFileName().toString().equals("GeneratedPonderSupport.java"))
                        return JavaModuleExportResult.failure("Unmanaged Java source preserved: " + path);
                }
            } catch (IOException error) { return JavaModuleExportResult.failure("Cannot inspect " + path + ": " + error.getMessage()); }
            manifest.generatedSources.put(write.relativePath(), write.content());
        }
        try {
            TextFile metadata = buildMetadataWrite(scanResult.targetProject, manifest);
            if (metadata != null) textWrites.add(metadata);
        } catch (IOException error) { return JavaModuleExportResult.failure("Cannot update mod metadata: " + error.getMessage()); }
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

    private JavaModuleExportResult exportSupportOnly(JavaModuleScanResult.TargetProject targetProject) {
        String relativePath = "src/main/java/" + packageToPath(targetProject.generatedPackage) + "/GeneratedPonderSupport.java";
        Path targetPath;
        try {
            targetPath = resolveTargetPath(targetProject.targetRoot, relativePath);
            ensureInsideRoot(targetProject.targetRoot, targetPath);
            Files.createDirectories(targetPath.getParent());
            boolean replaced = Files.exists(targetPath);
            JavaModuleExportManifest manifest = readManifest(targetProject.targetRoot);
            if (manifest.generatedSources == null) manifest.generatedSources = new LinkedHashMap<>();
            String previous = manifest.generatedSources.get(relativePath);
            if (replaced) {
                String current = Files.readString(targetPath, StandardCharsets.UTF_8);
                if (previous != null && !previous.equals(current)) return JavaModuleExportResult.failure("Handwritten changes preserved; export refused to overwrite " + targetPath);
                if (previous == null && !current.contains("Auto-generated by The Ponderer")) return JavaModuleExportResult.failure("Unmanaged Java source preserved: " + targetPath);
            }
            String source = buildSupportSource(targetProject);
            TextFile license = buildLicenseWrite();
            Path licensePath = resolveTargetPath(targetProject.targetRoot, license.relativePath());
            Files.createDirectories(licensePath.getParent());
            Files.writeString(licensePath, license.content(), StandardCharsets.UTF_8);
            Files.writeString(targetPath, source, StandardCharsets.UTF_8);
            manifest.generatedSources.put(relativePath, source);
            Path manifestPath = targetProject.targetRoot.resolve(GENERATED_DIR).resolve(MANIFEST_NAME);
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath, GSON.toJson(manifest), StandardCharsets.UTF_8);
            JavaModuleExportResult result = new JavaModuleExportResult();
            result.success = true;
            result.supportOnly = true;
            result.supportFilePath = targetPath;
            if (replaced) {
                result.replacedCount = 1;
            } else {
                result.addedCount = 1;
            }
            return result;
        } catch (Exception e) {
            return JavaModuleExportResult.failure("Failed to write GeneratedPonderSupport.java: " + e.getMessage());
        }
    }

    @Nullable
    private JavaModuleScanResult.TargetProject detectTargetProject(Path targetRoot, JavaModuleExportRequest.Mode mode,
                                                                          List<JavaModuleScanResult.Finding> fatals) {
        Path normalizedRoot = targetRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            addFinding(fatals, "fatal", null, null, null, null, "Target root is not a directory: " + normalizedRoot);
            return null;
        }

        Path javaRoot = normalizedRoot.resolve("src/main/java").normalize();
        Path resourcesRoot = normalizedRoot.resolve("src/main/resources").normalize();
        if (mode != JavaModuleExportRequest.Mode.JSON_LIB && !Files.isDirectory(javaRoot)) {
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
        Path neoForgeMetadata = normalizedRoot.resolve("src/main/templates/META-INF/neoforge.mods.toml").normalize();
        if (!Files.isRegularFile(neoForgeMetadata)) {
            neoForgeMetadata = resourcesRoot.resolve("META-INF/neoforge.mods.toml").normalize();
        }

        boolean hasFabric = Files.isRegularFile(fabricMetadata);
        boolean hasForge = Files.isRegularFile(forgeMetadata);
        boolean hasNeoForge = Files.isRegularFile(neoForgeMetadata);
        int detectedLoaders = (hasFabric ? 1 : 0) + (hasForge ? 1 : 0) + (hasNeoForge ? 1 : 0);
        if (detectedLoaders != 1) {
            addFinding(fatals, "fatal", null, null, null, null,
                detectedLoaders > 1
                    ? "Target root contains metadata for multiple loaders. Select a single Fabric, Forge, or NeoForge module root."
                    : "Unable to detect Fabric, Forge, or NeoForge metadata in the target module.");
            return null;
        }

        Properties properties = loadGradleProperties(normalizedRoot);
        String loader = hasFabric ? "fabric" : hasNeoForge ? "neoforge" : "forge";
        Path metadataPath = hasFabric ? fabricMetadata : hasNeoForge ? neoForgeMetadata : forgeMetadata;
        String modId = hasFabric ? parseFabricModId(fabricMetadata, properties) : parseTomlModId(metadataPath, properties);
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
        if ((basePackage == null || basePackage.isBlank()) && mode == JavaModuleExportRequest.Mode.JSON_LIB) {
            basePackage = readManifest(normalizedRoot).basePackage;
            if (basePackage == null || basePackage.isBlank()) basePackage = modId.replace('-', '_');
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
        project.metadataPath = metadataPath;
        return project;
    }

    private List<DslScene> collectRequestedScenes(JavaModuleExportRequest request,
                                                         List<DslScene> runtimeScenes,
                                                         List<JavaModuleScanResult.Finding> fatals) {
        if (runtimeScenes.isEmpty()) {
            addFinding(fatals, "fatal", null, null, null, null, "No scenes are currently loaded.");
            return List.of();
        }

        Set<String> selectedKeys = request.selectedSceneKeys();
        return runtimeScenes.stream()
            .filter(scene -> scene != null && scene.id != null && selectedKeys.contains(scene.sceneKey()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private JavaModuleScanResult.ScenePlan scanScene(JavaModuleScanResult.TargetProject target,
                                                            DslScene scene,
                                                            List<DslScene> sourceScenes,
                                                            JavaModuleScanResult result) {
        JavaModuleScanResult.ScenePlan plan = new JavaModuleScanResult.ScenePlan();
        plan.sourceScene = scene;
        if (scene.formatVersion != com.nododiiiii.ponderer.runtime.api.SceneRequirements.FORMAT_VERSION)
            addFinding(result.fatalFindings, "format version", scene.id, scene.sceneKey(), null, null, "Unsupported scene format " + scene.formatVersion);
        plan.sceneId = scene.id;
        plan.sceneKey = scene.sceneKey();
        plan.className = classNameFor(scene.id);
        plan.storyboardBasePath = storyboardBasePath(scene.id);

        for (ResourceLocation itemLoc : SceneItemBindings.resolve(scene.items)) {
            plan.componentItems.add(itemLoc.toString());
        }
        if (plan.componentItems.isEmpty()) {
            plan.skippedScene = true;
            plan.messages.add("Scene has no valid components to register.");
            addFinding(result.sceneUnsupportedFindings, "scene-level unsupported", scene.id, scene.sceneKey(),
                null, null, "Scene has no valid components to register.");
            return plan;
        }

        for (String tagId : safeList(scene.tags)) {
            // Standalone exports register attribution under the target mod's namespace.
            if (PondererAttribution.isTag(tagId)) {
                continue;
            }
            ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
            if (tagLoc != null) {
                plan.tags.add(tagLoc.toString());
            }
        }
        plan.customTags = CustomPonderTags.copy(CustomPonderTags.forScene(scene, sourceScenes));

        if (!LibExportSources.usesLib(target)) collectSceneMetaWarnings(scene, result, plan);

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
                if (!LibExportSources.usesLib(target) && IGNORED_STEP_TYPES.contains(stepType)) {
                    plan.hadIgnoredSharedText = true;
                    addFinding(result.ignoredFindings, "ignored-by-design", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "shared_text is intentionally omitted from Java module export.");
                    continue;
                }
                if (!LibExportSources.usesLib(target) && WARNING_STEP_TYPES.contains(stepType)) {
                    plan.partial = true;
                    plan.hadOmittedSteps = true;
                    segmentPlan.omittedStepTypes.add(stepType);
                    unsupportedOnlyStepCount++;
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Unsupported step will be omitted: " + stepType);
                    continue;
                }
                if (!LibExportSources.usesLib(target) && !SUPPORTED_STEP_TYPES.contains(stepType)) {
                    plan.partial = true;
                    plan.hadOmittedSteps = true;
                    segmentPlan.omittedStepTypes.add(stepType);
                    unsupportedOnlyStepCount++;
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Unknown step will be omitted: " + stepType);
                    continue;
                }
                if ((!LibExportSources.usesLib(target) || SUPPORTED_STEP_TYPES.contains(stepType)) && !isGeneratableStep(step)) {
                    plan.partial = true;
                    plan.hadOmittedSteps = true;
                    segmentPlan.omittedStepTypes.add(stepType);
                    addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), segmentId(segment),
                        stepType, "Step is missing required data and will be omitted: " + stepType);
                    continue;
                }

                if (LibExportSources.usesLib(target) && !com.nododiiiii.ponderer.scene.SceneStepTypes.BUILTIN.contains(stepType))
                    addFinding(result.warningFindings, "extension required", scene.id, scene.sceneKey(), segmentId(segment), stepType,
                        "Compiled Java provider required for " + stepType + " at step " + step.id + "; register it in PonderExtensions.install.");
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

    private void collectSceneMetaWarnings(DslScene scene,
                                                 JavaModuleScanResult result,
                                                 JavaModuleScanResult.ScenePlan plan) {
        if (SceneItemBindings.hasTagBindings(scene)) {
            plan.partial = true;
            plan.hadMetaDrops = true;
            addFinding(result.warningFindings, "step/meta warnings", scene.id, scene.sceneKey(), null, null,
                "Item tag bindings export as the currently resolved item IDs.");
        }
        if (SceneItemBindings.hasNbtFilters(scene)) {
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

    private List<StructureResolution> resolveSegmentSchematics(JavaModuleScanResult.TargetProject target,
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
    private StructureResolution resolveDefaultStructure(JavaModuleScanResult.TargetProject target, DslScene scene) {
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
    private StructureResolution resolveStructureReference(JavaModuleScanResult.TargetProject target,
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

    private List<String> structurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    private String extractExplicitStructureRef(DslScene.SceneSegment segment) {
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

    private void dedupeStructures(JavaModuleScanResult.ScenePlan plan) {
        Map<String, JavaModuleScanResult.StructureAsset> deduped = new LinkedHashMap<>();
        for (JavaModuleScanResult.StructureAsset asset : plan.structures) {
            if (asset != null) {
                deduped.put(asset.targetRelativePath, asset);
            }
        }
        plan.structures = new ArrayList<>(deduped.values());
    }

    private JavaModuleScanResult.StructureAsset toStructureAsset(@Nullable StructureResolution resolution) {
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

    private boolean isGeneratableStep(DslScene.DslStep step) {
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
            case "spawn_particles" -> {
                try {
                    ParticleEffectSpec.validate(step);
                    yield true;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
            case "replace_blocks", "set_block" -> hasPos(step.blockPos) && isNonBlank(step.block);
            case "rotate_section", "move_section" -> isNonBlank(step.linkId) || hasPos(step.blockPos);
            case "clear_entities", "clear_item_entities", "modify_entities_nbt", "modify_item_entities_nbt" ->
                Boolean.TRUE.equals(step.fullScene) || hasPos(step.blockPos) || isNonBlank(step.linkId);
            default -> true;
        };
    }

    private RenderedScene renderScene(JavaModuleScanResult.TargetProject target,
                                             JavaModuleScanResult.ScenePlan scenePlan,
                                             SceneExportOutcome outcome) {
        boolean resourceOnly = target.mode == JavaModuleExportRequest.Mode.JSON_LIB;
        String sceneSource = resourceOnly ? com.nododiiiii.ponderer.scene.SceneCodec.pretty(LibExportSources.document(target, scenePlan)) : buildSceneSource(target, scenePlan);
        String sceneRelativePath = resourceOnly ? "src/main/resources" + LibExportSources.jsonPath(target, scenePlan)
            : "src/main/java/" + packageToPath(target.scenePackage) + "/" + scenePlan.className + ".java";
        Map<String, Map<String, String>> langEntries = buildSceneLangEntries(target, scenePlan);

        List<BinaryFile> structureFiles = new ArrayList<>();
        for (JavaModuleScanResult.StructureAsset asset : scenePlan.structures) {
            if (asset == null || asset.sourcePath == null) {
                continue;
            }
            try {
                structureFiles.add(new BinaryFile(asset.targetRelativePath, Files.readAllBytes(asset.sourcePath)));
            } catch (IOException error) {
                throw new UncheckedIOException("Cannot copy structure " + asset.sourceId, error);
            }
        }
        JavaModuleExportManifest.SceneEntry entry = new JavaModuleExportManifest.SceneEntry();
        entry.runtimeFeatures = com.nododiiiii.ponderer.scene.SceneRuntimeRequirements.features(scenePlan.sourceScene).stream().sorted().toList();
        entry.sceneId = scenePlan.sceneId;
        entry.sceneKey = scenePlan.sceneKey;
        entry.className = resourceOnly ? null : target.scenePackage + "." + scenePlan.className;
        if (resourceOnly) entry.resourceId = target.modId + ":ponderer/scenes/" + scenePlan.className.toLowerCase(Locale.ROOT) + ".json";
        entry.status = scenePlan.blank ? SceneExportOutcome.Status.BLANK.name() : scenePlan.partial
            ? SceneExportOutcome.Status.PARTIAL.name()
            : SceneExportOutcome.Status.COMPLETE.name();
        if (resourceOnly) entry.resourceFiles.add(sceneRelativePath);
        else entry.javaFiles.add(sceneRelativePath);
        structureFiles.forEach(asset -> entry.resourceFiles.add(asset.relativePath()));
        entry.langEntries = copyLangEntries(langEntries);
        entry.contentHash = hashSceneContent(sceneSource, structureFiles, entry.langEntries);

        outcome.status = scenePlan.blank ? SceneExportOutcome.Status.BLANK
            : scenePlan.partial ? SceneExportOutcome.Status.PARTIAL
            : SceneExportOutcome.Status.COMPLETE;

        return new RenderedScene(entry, new TextFile(sceneRelativePath, sceneSource), structureFiles);
    }

    private List<TextFile> buildGlobalWrites(JavaModuleScanResult.TargetProject target,
                                                    JavaModuleExportManifest manifest,
                                                    Set<String> currentSceneIds,
                                                    Map<String, Set<String>> staleManagedLangKeys) {
        List<TextFile> writes = new ArrayList<>();
        if (LibExportSources.usesLib(target)) {
            Set<String> features = new java.util.TreeSet<>(Set.of("core", "render_cache"));
            for (var entry : manifest.scenes.values()) {
                if (entry.runtimeFeatures != null && !entry.runtimeFeatures.isEmpty()) features.addAll(entry.runtimeFeatures);
                else features.addAll(Set.of("view", "text", "nbt_bindings", "interfaces")); // Older partial-export manifests.
            }
            writes.add(new TextFile("src/main/resources/META-INF/pondererlib.features/ponderer_export.properties",
                "# Generated startup requirements. Handwritten providers may declare extra features in META-INF/pondererlib.features.properties.\nfeatures="
                    + String.join(",", features) + "\n"));
        }
        if (target.mode == JavaModuleExportRequest.Mode.JSON_LIB) {
            var content = new com.nododiiiii.ponderer.scene.ResourceContentManifest();
            Path contentPath = target.resourcesRoot.resolve("assets/" + target.modId + "/ponderer/content/ponderer_export.json");
            if (Files.exists(contentPath)) {
                try (Reader reader = Files.newBufferedReader(contentPath, StandardCharsets.UTF_8)) {
                    var previous = GSON.fromJson(reader, com.nododiiiii.ponderer.scene.ResourceContentManifest.class);
                    if (previous == null || previous.formatVersion != 1 || previous.extensions == null)
                        throw new IllegalArgumentException("Unsupported content manifest " + contentPath);
                    content.extensions = List.copyOf(previous.extensions);
                } catch (IOException error) { throw new UncheckedIOException(error); }
            }
            content.scenes = manifest.scenes.values().stream().map(entry -> entry.resourceId).filter(Objects::nonNull).sorted().toList();
            content.attribution = true;
            writes.add(new TextFile("src/main/resources/assets/" + target.modId + "/ponderer/content/ponderer_export.json", GSON.toJson(content)));
            writes.add(new TextFile(".ponderer-export/DEPENDENCIES.md", "This resource-only export requires external pondererlib >= 0.1.0 and upstream Ponder.\nLib discovers assets/<namespace>/ponderer/content/*.json and registers scenes on client resource reload.\nNo generated Java or content entrypoint is required. Install the loader-specific pondererlib JAR separately in mods; do not embed it in the host.\nThe same assets tree works in a client resource pack.\nOptional compiled providers register with ResourceSceneContents.registerExtension; manifests select provider IDs in extensions.\n"));
            writes.addAll(buildLangWrites(target, manifest, currentSceneIds, staleManagedLangKeys));
            return writes;
        }
        String generatedPackagePath = packageToPath(target.generatedPackage);
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderPlugin.java",
            buildPluginSource(target)));
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderIndex.java",
            buildIndexSource(target, manifest)));
        writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderAttribution.java",
            buildAttributionSource(target)));
        if (!LibExportSources.usesLib(target)) writes.add(new TextFile("src/main/java/" + generatedPackagePath + "/GeneratedPonderSupport.java", buildSupportSource(target)));
        if (LibExportSources.usesLib(target)) {
            String extensionPath = "src/main/java/" + packageToPath(LibExportSources.extensionPackage(target)) + "/PonderExtensions.java";
            // Deliberately excluded from manifest ownership and source-overwrite checks.
            if (!Files.exists(target.targetRoot.resolve(extensionPath))) writes.add(new TextFile(extensionPath, LibExportSources.extension(target)));
            writes.add(new TextFile(".ponderer-export/DEPENDENCIES.md", "This export requires external pondererlib >= 0.1.0 and upstream Ponder.\nDeclare the loader-specific pondererlib artifact with Fabric modImplementation or NeoForge implementation for development; do not use include or jarJar for Lib.\nInstall the pondererlib JAR separately in mods; multiple hosts share one copy.\nHandwritten custom steps belong in " + LibExportSources.extensionPackage(target) + ".PonderExtensions.\n"));
        } else {
            writes.add(new TextFile(".ponderer-export/DEPENDENCIES.md", "This export uses upstream Ponder and generated Support without pondererlib.\nConfigure loader-specific Ponder/Flywheel dependencies in the host build.\nWhen embedding legacy NeoForge Ponder coordinates, coordinate JarJar identity with modern Create-provided ponder-neoforge.\n"));
        }
        writes.addAll(buildLangWrites(target, manifest, currentSceneIds, staleManagedLangKeys));
        switch (target.loader) {
            case "fabric" -> writes.add(new TextFile(
                "src/main/java/" + generatedPackagePath + "/GeneratedPonderFabricClient.java",
                buildFabricClientSource(target)));
            case "neoforge" -> writes.add(new TextFile(
                "src/main/java/" + generatedPackagePath + "/GeneratedPonderNeoForgeClient.java",
                buildNeoForgeClientSource(target)));
            case "forge" -> writes.add(new TextFile(
                "src/main/java/" + generatedPackagePath + "/GeneratedPonderForgeClient.java",
                buildForgeClientSource(target)));
            default -> throw new IllegalArgumentException("Unsupported target loader: " + target.loader);
        }
        return writes;
    }

    private List<TextFile> buildLangWrites(JavaModuleScanResult.TargetProject target,
                                                  JavaModuleExportManifest manifest,
                                                  Set<String> currentSceneIds,
                                                  Map<String, Set<String>> staleManagedLangKeys) {
        // Incremental: only touch lang keys belonging to this export's scenes (plus stale removals
        // and the always-on attribution). Other scenes' entries — even those tracked in the
        // manifest — are left alone so partial exports cannot rewrite untouched translations.
        Map<String, Map<String, String>> currentEntries = collectManagedLangEntriesForScenes(manifest, currentSceneIds);
        mergeLangEntries(currentEntries, buildAttributionLangEntries(target.modId));
        Set<String> locales = new LinkedHashSet<>();
        locales.addAll(currentEntries.keySet());
        locales.addAll(staleManagedLangKeys.keySet());

        List<TextFile> writes = new ArrayList<>();
        for (String locale : locales.stream().sorted().toList()) {
            Path langPath = target.resourcesRoot.resolve("assets/" + target.modId + "/lang/" + locale + ".json").normalize();
            Map<String, String> mergedEntries = readLangFile(langPath);
            Set<String> keysToRemove = new LinkedHashSet<>();
            keysToRemove.addAll(currentEntries.getOrDefault(locale, Map.of()).keySet());
            keysToRemove.addAll(staleManagedLangKeys.getOrDefault(locale, Set.of()));
            keysToRemove.forEach(mergedEntries::remove);
            mergedEntries.putAll(currentEntries.getOrDefault(locale, Map.of()));
            writes.add(new TextFile(relativeToRoot(target.targetRoot, langPath), GSON.toJson(mergedEntries)));
        }
        return writes;
    }

    private void mergeLangEntries(Map<String, Map<String, String>> target,
                                         Map<String, Map<String, String>> additions) {
        additions.forEach((locale, values) -> {
            if (locale == null || locale.isBlank() || values == null || values.isEmpty()) {
                return;
            }
            target.computeIfAbsent(locale, unused -> new LinkedHashMap<>()).putAll(values);
        });
    }

    private Map<String, Map<String, String>> collectManagedLangEntriesForScenes(JavaModuleExportManifest manifest,
                                                                                       Set<String> sceneIds) {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        if (manifest.scenes == null || sceneIds == null || sceneIds.isEmpty()) {
            return merged;
        }
        manifest.scenes.values().stream()
            .filter(entry -> entry.sceneId != null && sceneIds.contains(entry.sceneId))
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

    private void collectManagedLangKeys(JavaModuleExportManifest.SceneEntry entry,
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

    private Map<String, Map<String, String>> buildSceneLangEntries(JavaModuleScanResult.TargetProject target,
                                                                          JavaModuleScanResult.ScenePlan scenePlan) {
        Map<String, Map<String, String>> langEntries = new LinkedHashMap<>();
        scenePlan.customTags.forEach((id, definition) -> {
            ResourceLocation location = ResourceLocation.parse(exportedTagId(target, scenePlan, id));
            addLangEntries(langEntries, tagTitleLangKey(location.getNamespace(), location.getPath()), definition.title, id);
            addLangEntries(langEntries, tagDescriptionLangKey(location.getNamespace(), location.getPath()), definition.description, "");
        });
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

    private Map<String, Map<String, String>> buildAttributionLangEntries(String modId) {
        Map<String, Map<String, String>> langEntries = new LinkedHashMap<>();
        String titleKey = tagTitleLangKey(modId, ATTRIBUTION_TAG_PATH);
        String descriptionKey = tagDescriptionLangKey(modId, ATTRIBUTION_TAG_PATH);

        langEntries.computeIfAbsent("en_us", unused -> new LinkedHashMap<>()).put(titleKey, ATTRIBUTION_TITLE_EN_US);
        langEntries.get("en_us").put(descriptionKey, ATTRIBUTION_DESCRIPTION_EN_US);

        langEntries.computeIfAbsent("zh_cn", unused -> new LinkedHashMap<>()).put(titleKey, ATTRIBUTION_TITLE_ZH_CN);
        langEntries.get("zh_cn").put(descriptionKey, ATTRIBUTION_DESCRIPTION_ZH_CN);
        return langEntries;
    }

    private Map<String, String> exportSegmentTitleTranslations(DslScene scene,
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

    private void addLangEntries(Map<String, Map<String, String>> target,
                                       String langKey,
                                       @Nullable LocalizedText text,
                                       String fallback) {
        Map<String, String> translations = exportTranslations(text, fallback);
        translations.forEach((locale, value) ->
            target.computeIfAbsent(locale, unused -> new LinkedHashMap<>()).put(langKey, value));
    }

    private Map<String, String> exportTranslations(@Nullable LocalizedText text, String fallback) {
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
    private String normalizeLangCode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isBlank() ? null : normalized;
    }

    private String specificLangKey(String modId, String scenePath, String key) {
        return modId + ".ponder." + scenePath + "." + key;
    }

    private String generatedAttributionTagId(String modId) {
        return modId + ":" + ATTRIBUTION_TAG_PATH;
    }

    /** Generated custom categories belong to the destination mod; external categories keep their IDs. */
    private String exportedTagId(JavaModuleScanResult.TargetProject target,
                                         JavaModuleScanResult.ScenePlan plan, String id) {
        if (!plan.customTags.containsKey(id)) {
            return id;
        }
        return ResourceLocation.fromNamespaceAndPath(target.modId, ResourceLocation.parse(id).getPath()).toString();
    }

    private String tagTitleLangKey(String modId, String tagPath) {
        return modId + ".ponder.tag." + tagPath;
    }

    private String tagDescriptionLangKey(String modId, String tagPath) {
        return tagTitleLangKey(modId, tagPath) + ".description";
    }

    private Map<String, String> readLangFile(Path langPath) {
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

    private Map<String, Map<String, String>> copyLangEntries(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        source.forEach((locale, values) -> copy.put(locale, new LinkedHashMap<>(values)));
        return copy;
    }

    private Map<String, String> sortStringMap(Map<String, String> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> right,
                LinkedHashMap::new));
    }

    private static final String GENERATED_NOTICE_LINE_EN =
        "// Auto-generated by The Ponderer — do not edit; will be overwritten on next export.";
    private static final String GENERATED_NOTICE_LINE_ZH =
        "// 由思索者自动生成 — 请勿手动修改；下次导出时会被覆盖。";
    private static final String GENERATED_NOTICE_BLOCK =
        GENERATED_NOTICE_LINE_EN + "\n" + GENERATED_NOTICE_LINE_ZH + "\n";

    private String buildPluginSource(JavaModuleScanResult.TargetProject target) {
        return GENERATED_NOTICE_BLOCK + """
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
                public void registerSharedText(net.createmod.ponder.api.registration.SharedTextRegistrationHelper helper) {
                    GeneratedPonderIndex.registerSharedText(helper);
                }

                @Override
                public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
                    GeneratedPonderAttribution.registerTag(helper);
                    GeneratedPonderIndex.registerTags(helper);
                }
            }
            """.formatted(target.generatedPackage, escapeJava(target.modId));
    }

    private String buildIndexSource(JavaModuleScanResult.TargetProject target,
                                           JavaModuleExportManifest manifest) {
        StringBuilder imports = new StringBuilder();
        StringBuilder sceneBody = new StringBuilder();
        StringBuilder tagBody = new StringBuilder();
        StringBuilder sharedBody = new StringBuilder();
        StringBuilder closeBody = new StringBuilder();
        for (JavaModuleExportManifest.SceneEntry entry : manifest.scenes.values()) {
            if (entry.className == null || entry.className.isBlank()) {
                continue;
            }
            imports.append("import ").append(entry.className).append(";\n");
            String simpleName = entry.className.substring(entry.className.lastIndexOf('.') + 1);
            sceneBody.append("        ").append(simpleName).append(".register(helper);\n");
            tagBody.append("        ").append(simpleName).append(".registerTags(helper);\n");
            if (LibExportSources.usesLib(target)) sharedBody.append("        ").append(simpleName).append(".registerSharedText(helper);\n");
            if (LibExportSources.usesLib(target)) closeBody.append("        ").append(simpleName).append(".close();\n");
        }

        return GENERATED_NOTICE_BLOCK + """
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
                public static void registerSharedText(net.createmod.ponder.api.registration.SharedTextRegistrationHelper helper) {
            %s    }
                public static void close() {
            %s    }
            }
            """.formatted(target.generatedPackage, imports, sceneBody, tagBody, sharedBody, closeBody);
    }

    private String buildFabricClientSource(JavaModuleScanResult.TargetProject target) {
        return GENERATED_NOTICE_BLOCK + """
            package %s;

            import net.createmod.ponder.foundation.PonderIndex;
            import net.fabricmc.api.ClientModInitializer;

            public final class GeneratedPonderFabricClient implements ClientModInitializer {
                @Override
                public void onInitializeClient() {
            %s        PonderIndex.addPlugin(new GeneratedPonderPlugin());
                }
            }
            """.formatted(target.generatedPackage, LibExportSources.usesLib(target) ? ""
                : "        GeneratedPonderAttribution.iconModelLocation();\n");
    }

    private String buildForgeClientSource(JavaModuleScanResult.TargetProject target) {
        String modelRegistration = LibExportSources.usesLib(target) ? "" : """
                @SubscribeEvent
                public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
                    event.register(GeneratedPonderAttribution.iconModelLocation());
                }

            """;
        return GENERATED_NOTICE_BLOCK + """
            package %s;

            import net.createmod.ponder.foundation.PonderIndex;
            import net.minecraftforge.api.distmarker.Dist;
            import net.minecraftforge.eventbus.api.SubscribeEvent;
            import net.minecraftforge.fml.common.Mod;
            import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
            %s

            @Mod.EventBusSubscriber(modid = "%s", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
            public final class GeneratedPonderForgeClient {
                private GeneratedPonderForgeClient() {
                }

            %s    @SubscribeEvent
                public static void onClientSetup(FMLClientSetupEvent event) {
                    event.enqueueWork(() -> PonderIndex.addPlugin(new GeneratedPonderPlugin()));
                }
            }
            """.formatted(target.generatedPackage, LibExportSources.usesLib(target) ? ""
                : "import net.minecraftforge.client.event.ModelEvent;",
                escapeJava(target.modId), modelRegistration);
    }

    String buildNeoForgeClientSource(JavaModuleScanResult.TargetProject target) {
        String modelRegistration = LibExportSources.usesLib(target) ? "" : """
                @SubscribeEvent
                public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
                    event.register(ModelResourceLocation.standalone(GeneratedPonderAttribution.iconModelLocation()));
                }

            """;
        return GENERATED_NOTICE_BLOCK + """
            package %s;

            import net.createmod.ponder.foundation.PonderIndex;
            import net.neoforged.api.distmarker.Dist;
            import net.neoforged.bus.api.SubscribeEvent;
            import net.neoforged.fml.common.EventBusSubscriber;
            import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
            %s

            @EventBusSubscriber(modid = "%s", value = Dist.CLIENT)
            public final class GeneratedPonderNeoForgeClient {
                private GeneratedPonderNeoForgeClient() {
                }

            %s    @SubscribeEvent
                public static void onClientSetup(FMLClientSetupEvent event) {
                    event.enqueueWork(() -> PonderIndex.addPlugin(new GeneratedPonderPlugin()));
                }
            }
            """.formatted(target.generatedPackage, LibExportSources.usesLib(target) ? ""
                : "import net.neoforged.neoforge.client.event.ModelEvent;\nimport net.minecraft.client.resources.model.ModelResourceLocation;",
                escapeJava(target.modId), modelRegistration);
    }

    String buildAttributionSource(JavaModuleScanResult.TargetProject target) {
        if (LibExportSources.usesLib(target)) return GENERATED_NOTICE_BLOCK + """
            package %s;

            import com.nododiiiii.ponderer.runtime.api.PonderAttribution;
            import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
            import net.minecraft.resources.ResourceLocation;

            public final class GeneratedPonderAttribution {
                private GeneratedPonderAttribution() {}

                public static ResourceLocation tag() {
                    return PonderAttribution.tag(%s);
                }

                public static void registerTag(PonderTagRegistrationHelper<ResourceLocation> helper) {
                    PonderAttribution.register(%s, helper);
                }
            }
            """.formatted(target.generatedPackage, stringExpr(target.modId), stringExpr(target.modId));
        return GENERATED_NOTICE_BLOCK + """
            package %s;

            import com.mojang.blaze3d.systems.RenderSystem;
            import dev.engine_room.flywheel.lib.model.baked.PartialModel;
            import net.createmod.catnip.animation.AnimationTickHolder;
            import net.createmod.catnip.gui.element.GuiGameElement;
            import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
            import net.createmod.ponder.foundation.PonderIndex;
            import net.createmod.ponder.foundation.PonderTag;
            import net.createmod.ponder.foundation.registration.PonderTagRegistry;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.resources.ResourceLocation;
            import net.minecraft.world.item.ItemStack;

            public final class GeneratedPonderAttribution {
                private static final int ICON_ROTATION_TICKS = 20 * 30;
                private static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath(%s, %s);

                private static final class Icon {
                    private static final PartialModel MODEL = PartialModel.of(
                        ResourceLocation.fromNamespaceAndPath(TAG.getNamespace(), %s));
                }

                private GeneratedPonderAttribution() {
                }

                public static ResourceLocation tag() {
                    return TAG;
                }

                public static ResourceLocation iconModelLocation() {
                    return Icon.MODEL.modelLocation();
                }

                public static void registerTag(PonderTagRegistrationHelper<ResourceLocation> helper) {
                    helper.registerTag(TAG)
                        .title(%s)
                        .description(%s)
                        .register();
                    PonderTag tag = new PonderTag(TAG, null, ItemStack.EMPTY, ItemStack.EMPTY) {
                        @Override
                        public void render(GuiGraphics graphics, int x, int y) {
                            // UI time keeps the icon rotating while scene playback or the game is paused.
                            double ticks = AnimationTickHolder.getTicks(true) %% ICON_ROTATION_TICKS
                                + AnimationTickHolder.getPartialTicksUI();
                            GuiGameElement.of(Icon.MODEL)
                                .rotateBlock(30, 135 + ticks * 360 / ICON_ROTATION_TICKS, 0)
                                .scale(10)
                                .at(x + 3, y + 13, 100)
                                .render(graphics);
                            // Model batch cleanup disables depth testing; following native widgets expect it enabled.
                            RenderSystem.enableDepthTest();
                        }
                    };
                    ((PonderTagRegistry) PonderIndex.getTagAccess()).registerTag(tag);
                }
            }
            """.formatted(
            target.generatedPackage,
            stringExpr(target.modId),
            stringExpr(ATTRIBUTION_TAG_PATH),
            stringExpr(PondererAttribution.ICON_MODEL_PATH),
            stringExpr(ATTRIBUTION_TITLE_EN_US),
            stringExpr(ATTRIBUTION_DESCRIPTION_EN_US));
    }

    private String buildSceneSource(JavaModuleScanResult.TargetProject target,
                                           JavaModuleScanResult.ScenePlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(GENERATED_NOTICE_BLOCK);
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
        if (LibExportSources.usesLib(target)) sb.append(LibExportSources.members(target, plan));
        sb.append("    private ").append(plan.className).append("() {\n");
        sb.append("    }\n\n");
        sb.append("    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {\n");
        if (LibExportSources.usesLib(target)) sb.append("        if (!reload()) return;\n");
        sb.append("        ResourceLocation[] tags = ")
            .append(resourceLocationArrayExpr(plan.tags.stream().map(id -> exportedTagId(target, plan, id)).toList(),
                generatedAttributionTagId(target.modId))).append(";\n");
        sb.append("        var multi = helper.forComponents(java.util.List.of(")
            .append(plan.componentItems.stream().map(this::resourceLocationExpr).collect(Collectors.joining(", ")))
            .append("));\n");
        if (LibExportSources.usesLib(target)) {
            int componentStart = sb.lastIndexOf("        var multi =");
            sb.replace(componentStart, sb.length(), "        var multi = helper.forComponents(com.nododiiiii.ponderer.runtime.api.SceneItemBindings.resolve(DOCUMENT.items));\n");
        }
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
        if (LibExportSources.usesLib(target)) sb.append("        if (DOCUMENT == null) reload();\n        RUNTIME.plugin().registerTags(helper);\n");
        plan.customTags.forEach((id, definition) -> {
            String exportedId = exportedTagId(target, plan, id);
            sb.append("        if (net.createmod.ponder.foundation.PonderIndex.getTagAccess().getListedTags().stream()\n")
                .append("            .noneMatch(existing -> existing.getId().equals(").append(resourceLocationExpr(exportedId)).append("))) {\n")
                .append("            helper.registerTag(").append(resourceLocationExpr(exportedId)).append(")\n")
                .append("                .title(").append(stringExpr(resolveExportText(definition.title, id))).append(")\n")
                .append("                .description(").append(stringExpr(resolveExportText(definition.description, ""))).append(")\n")
                .append("                .item(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(")
                .append(resourceLocationExpr(BuiltInRegistries.ITEM.getKey(CustomPonderTags.iconItem(definition)).toString()))
                .append(").orElse(net.minecraft.world.item.Items.BOOK))\n")
                .append("                .addToIndex().register();\n")
                .append("        }\n");
        });
        sb.append("        ResourceLocation tag = GeneratedPonderAttribution.tag();\n");
        for (String componentItem : plan.componentItems) {
            sb.append("        helper.addTagToComponent(")
                .append(resourceLocationExpr(componentItem))
                .append(", tag);\n");
            for (String sceneTag : plan.tags) {
                sb.append("        helper.addTagToComponent(")
                    .append(resourceLocationExpr(componentItem))
                    .append(", ").append(resourceLocationExpr(exportedTagId(target, plan, sceneTag))).append(");\n");
            }
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
            if (LibExportSources.usesLib(target)) {
                sb.append("        var program = RUNTIME.program(").append(stringExpr(plan.sceneId)).append(", ")
                    .append(stringExpr(segment.sourceSegment.id == null ? Integer.toString(plan.segments.indexOf(segment)) : segment.sourceSegment.id))
                    .append(", scene, util);\n        GeneratedPonderSupport.Context context = program.context(null).actions();\n");
            } else sb.append("        GeneratedPonderSupport.Context context = new GeneratedPonderSupport.Context();\n");
            appendPreScanBounds(sb, segment.sourceSegment);

            boolean firstIsShowStructure = !segment.steps.isEmpty()
                && ("show_structure".equalsIgnoreCase(segment.steps.get(0).sourceStep.type) || LibExportSources.usesLib(target) && "show_interface".equalsIgnoreCase(segment.steps.get(0).sourceStep.type));
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
                appendStepSource(sb, generatedStep, target);
            }
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        String source = sb.toString();
        if (LibExportSources.usesLib(target)) source = source.replace("import " + target.generatedPackage + ".GeneratedPonderSupport;", "import com.nododiiiii.ponderer.runtime.api.PonderActions;").replace("GeneratedPonderSupport", "PonderActions");
        if (target.mode == JavaModuleExportRequest.Mode.JAVA_LIB) source = editableLibSceneSource(target, plan, source);
        return source;
    }

    /**
     * Mirror of DynamicPonderPlugin.preScanSegmentBounds: scan every step's coordinate-bearing
     * fields and emit a single preScanBounds() call so the generated storyboard can encapsulate
     * the full extent (including negative coords and points outside the schematic footprint)
     * before any instruction touches world.bounds.
     */
    private void appendPreScanBounds(StringBuilder sb, DslScene.SceneSegment segment) {
        if (segment == null || segment.steps == null || segment.steps.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (DslScene.DslStep step : segment.steps) {
            if (step == null) {
                continue;
            }
            if (step.blockPos != null && step.blockPos.size() >= 3) {
                int x = step.blockPos.get(0), y = step.blockPos.get(1), z = step.blockPos.get(2);
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                any = true;
            }
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                int x = step.blockPos2.get(0), y = step.blockPos2.get(1), z = step.blockPos2.get(2);
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                any = true;
            }
            if ("encapsulate_bounds".equalsIgnoreCase(step.type)
                && step.bounds != null && step.bounds.size() >= 3) {
                int bx = step.bounds.get(0), by = step.bounds.get(1), bz = step.bounds.get(2);
                if (0 < minX) minX = 0; if (bx > maxX) maxX = bx;
                if (0 < minY) minY = 0; if (by > maxY) maxY = by;
                if (0 < minZ) minZ = 0; if (bz > maxZ) maxZ = bz;
                any = true;
            }
        }
        if (!any) {
            return;
        }
        sb.append("        GeneratedPonderSupport.preScanBounds(scene, new BlockPos(")
            .append(minX).append(", ").append(minY).append(", ").append(minZ)
            .append("), new BlockPos(")
            .append(maxX).append(", ").append(maxY).append(", ").append(maxZ)
            .append("));\n");
    }

    private void appendStepSource(StringBuilder sb, JavaModuleScanResult.GeneratedStep generatedStep, JavaModuleScanResult.TargetProject target) {
        DslScene.DslStep step = generatedStep.sourceStep;
        String type = step.type.toLowerCase(Locale.ROOT);
        if (LibExportSources.usesLib(target)) {
            if (!SUPPORTED_STEP_TYPES.contains(type) || type.equals("text") || type.equals("show_controls") || type.equals("show_structure")) {
                DslScene.DslStep copy = GSON.fromJson(GSON.toJson(step), DslScene.DslStep.class);
                copy.attachKeyFrame = null;
                JavaValueEmitter emitter = new JavaValueEmitter();
                String value = emitter.value(copy);
                sb.append("        { // step ").append(stringExpr(step.id)).append('\n').append(emitter.statements())
                    .append("        program.step(").append(value).append(");\n        }\n");
                return;
            }
            sb.append("        program.beforeStep(").append(stringExpr(step.id)).append(");\n");
        }
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
            case "create_entity" -> sb.append("        GeneratedPonderSupport.createEntity(scene, context, ")
                .append(stringExpr(step.entity)).append(", ")
                .append(vecExprOrDefault(step.pos != null ? step.pos : step.point, 2.5, 1.5, 2.5)).append(", ")
                .append(vecExpr(step.lookAt)).append(", ")
                .append(floatExpr(step.yaw)).append(", ")
                .append(floatExpr(step.pitch)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(stringExpr(step.entranceAnimation)).append(", ")
                .append(intExpr(step.entranceDuration)).append(", ")
                .append(stringExpr(step.direction)).append(");\n");
            case "create_item_entity" -> sb.append("        GeneratedPonderSupport.createItemEntity(scene, context, ")
                .append(stringExpr(step.item)).append(", ")
                .append(step.count == null ? 1 : Math.max(1, step.count)).append(", ")
                .append(vecExprOrDefault(step.pos != null ? step.pos : step.point, 2.5, 1.5, 2.5)).append(", ")
                .append(vecExprOrDefault(step.motion, 2.5, 1.5, 2.5)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(stringExpr(step.linkId)).append(");\n");
            case "rotate_camera_y" -> sb.append("        GeneratedPonderSupport.rotateCameraY(scene, ")
                .append(step.degrees == null ? "90f" : floatLiteral(step.degrees)).append(", ")
                .append(step.degreesX == null ? "0f" : floatLiteral(step.degreesX)).append(", ")
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
                .append(blockPosExpr(step.blockPos2)).append(", ")
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
            case "spawn_particles" -> sb.append("        GeneratedPonderSupport.spawnParticles(scene, ")
                .append(stringExpr(step.particle)).append(", ")
                .append(vecExpr(step.pos)).append(", ")
                .append(stringExpr(ParticleEffectSpec.distribution(step))).append(", ")
                .append(vecExpr(ParticleEffectSpec.spread(step))).append(", ")
                .append(vecExpr(ParticleEffectSpec.motion(step))).append(", ")
                .append(ParticleEffectSpec.count(step)).append(", ")
                .append(ParticleEffectSpec.duration(step)).append(");\n");
            case "clear_entities" -> sb.append("        GeneratedPonderSupport.clearEntities(scene, context, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.entity)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(stringExpr(step.entranceAnimation)).append(", ")
                .append(intExpr(step.entranceDuration)).append(", ")
                .append(stringExpr(step.direction)).append(");\n");
            case "clear_item_entities" -> sb.append("        GeneratedPonderSupport.clearItemEntities(scene, context, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.item)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            case "modify_entities_nbt" -> sb.append("        GeneratedPonderSupport.modifyEntitiesNbt(scene, context, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.entity)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(", ")
                .append(vecExpr(step.offset)).append(", ")
                .append(intExpr(step.duration)).append(", ")
                .append(boolExpr(step.walkAnimation)).append(");\n");
            case "modify_item_entities_nbt" -> sb.append("        GeneratedPonderSupport.modifyItemEntitiesNbt(scene, context, ")
                .append(Boolean.TRUE.equals(step.fullScene)).append(", ")
                .append(stringExpr(step.item)).append(", ")
                .append(stringExpr(step.linkId)).append(", ")
                .append(stringExpr(step.nbt)).append(", ")
                .append(blockPosExpr(step.blockPos)).append(", ")
                .append(blockPosExpr(step.blockPos2)).append(");\n");
            default -> sb.append("        // Unsupported step omitted: ").append(type).append("\n");
        }
        if (LibExportSources.usesLib(target)) sb.append("        program.afterStep(").append(stringExpr(step.id)).append(");\n");
    }

    private String editableLibSceneSource(JavaModuleScanResult.TargetProject target, JavaModuleScanResult.ScenePlan plan, String nativeSource) {
        String prefix = nativeSource.substring(0, nativeSource.indexOf("public final class"));
        String tags = """
                public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
                    if (DOCUMENT == null && !reload()) return;
                    for (var entry : DOCUMENT.customTags.entrySet()) {
                        ResourceLocation id = ResourceLocation.parse(entry.getKey());
                        if (net.createmod.ponder.foundation.PonderIndex.getTagAccess().getListedTags().stream()
                            .anyMatch(existing -> existing.getId().equals(id))) continue;
                        var definition = entry.getValue();
                        ResourceLocation icon = ResourceLocation.tryParse(definition.icon == null ? "minecraft:book" : definition.icon);
                        helper.registerTag(id)
                            .title(definition.title == null ? id.getPath() : definition.title.resolve())
                            .description(definition.description == null ? "" : definition.description.resolve())
                            .item(icon == null ? net.minecraft.world.item.Items.BOOK : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(icon).orElse(net.minecraft.world.item.Items.BOOK))
                            .addToIndex().register();
                    }
                    RUNTIME.plugin().registerTags(helper);
                }
            """;
        return prefix + "public final class " + plan.className + " {\n" + LibExportSources.members(target, plan)
            + "    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {\n        if (reload()) RUNTIME.plugin().registerScenes(helper);\n    }\n"
            + tags + "}\n";
    }

    private TextFile buildLicenseWrite() throws IOException {
        try (InputStream in = JavaModuleExportService.class.getResourceAsStream(LICENSE_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: " + LICENSE_RESOURCE);
            }
            return new TextFile("src/main/resources" + LICENSE_RESOURCE,
                new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static final String SUPPORT_TEMPLATE_RESOURCE = "/ponderer/generated_ponder_support.java.template";
    private static volatile String supportTemplateCache;

    private String buildSupportSource(JavaModuleScanResult.TargetProject target) {
        return loadSupportTemplate().replace("${PACKAGE}", target.generatedPackage);
    }

    private String loadSupportTemplate() {
        String cached = supportTemplateCache;
        if (cached != null) {
            return cached;
        }
        try (InputStream in = com.nododiiiii.ponderer.runtime.api.PonderActions.class.getResourceAsStream(SUPPORT_TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing template resource: " + SUPPORT_TEMPLATE_RESOURCE);
            }
            String loaded = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            supportTemplateCache = loaded;
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load GeneratedPonderSupport template", e);
        }
    }

    private TextFile buildMetadataWrite(JavaModuleScanResult.TargetProject targetProject, JavaModuleExportManifest manifest) throws IOException {
        if (targetProject.metadataPath == null) return null;
        if ("neoforge".equals(targetProject.loader)) {
            String contents = Files.readString(targetProject.metadataPath, StandardCharsets.UTF_8);
            String dependency = "\n# Ponderer Lib export dependency\n[[dependencies." + targetProject.modId + "]]\nmodId=\"pondererlib\"\ntype=\"required\"\nversionRange=\"[0.1.0,)\"\nordering=\"AFTER\"\nside=\"CLIENT\"\n# End Ponderer Lib export dependency\n";
            if (LibExportSources.usesLib(targetProject)) {
                if (!contents.matches("(?s).*modId\\s*=\\s*\"pondererlib\".*")) {
                    contents += dependency;
                    manifest.ownsLibDependency = true;
                }
            } else if (manifest.ownsLibDependency) {
                contents = contents.replace(dependency, "");
                manifest.ownsLibDependency = false;
            }
            return new TextFile(relativeToRoot(targetProject.targetRoot, targetProject.metadataPath), contents);
        }
        if (!"fabric".equals(targetProject.loader)) return null;
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
        if (targetProject.mode == JavaModuleExportRequest.Mode.JSON_LIB && manifest.ownsClientEntrypoint) {
            JsonArray retained = new JsonArray();
            for (JsonElement element : clientArray) {
                String name = element.isJsonPrimitive() ? element.getAsString()
                    : element.isJsonObject() && element.getAsJsonObject().has("value") ? element.getAsJsonObject().get("value").getAsString() : null;
                if (!clientEntry.equals(name)) retained.add(element);
            }
            clientArray = retained;
            manifest.ownsClientEntrypoint = false;
        } else if (targetProject.mode != JavaModuleExportRequest.Mode.JSON_LIB && !exists) {
            clientArray.add(clientEntry);
            manifest.ownsClientEntrypoint = true;
        }

        if (!clientArray.isEmpty()) entrypoints.add("client", clientArray);
        else entrypoints.remove("client");
        if (!entrypoints.isEmpty()) root.add("entrypoints", entrypoints);
        else root.remove("entrypoints");
        if (LibExportSources.usesLib(targetProject)) {
            JsonObject dependencies = root.has("depends") && root.get("depends").isJsonObject() ? root.getAsJsonObject("depends") : new JsonObject();
            if (!dependencies.has("pondererlib")) {
                dependencies.addProperty("pondererlib", ">=0.1.0");
                manifest.ownsLibDependency = true;
            }
            root.add("depends", dependencies);
        } else if (manifest.ownsLibDependency && root.has("depends")) {
            JsonObject dependencies = root.getAsJsonObject("depends");
            if (dependencies.has("pondererlib") && dependencies.get("pondererlib").isJsonPrimitive()
                && ">=0.1.0".equals(dependencies.get("pondererlib").getAsString())) dependencies.remove("pondererlib");
            manifest.ownsLibDependency = false;
        }
        return new TextFile(relativeToRoot(targetProject.targetRoot, targetProject.metadataPath), GSON.toJson(root));
    }

    private JavaModuleExportManifest readManifest(Path targetRoot) {
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

    private void applyTargetDefaults(JavaModuleExportManifest manifest,
                                            JavaModuleScanResult.TargetProject targetProject) {
        manifest.version = 4;
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

    private void collectOwnedPaths(Path root, List<String> relativePaths, List<Path> deletions) {
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

    private Path resolveTargetPath(Path root, String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        ensureInsideRoot(root, target);
        return target;
    }

    private void ensureInsideRoot(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Refusing to access path outside target root: " + normalizedPath);
        }
    }

    private void deleteIfExists(Path root, Path path) throws IOException  {
        ensureInsideRoot(root, path);
        Files.deleteIfExists(path);
    }

    private String relativeToRoot(Path root, Path path) {
        ensureInsideRoot(root, path);
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    @Nullable
    private String parseFabricModId(Path fabricMetadata, Properties properties) {
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
    private String parseTomlModId(Path modsToml, Properties properties) {
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
    private String parseFabricBasePackage(Path fabricMetadata, Properties properties) {
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
    private String firstEntrypoint(JsonObject entrypoints, String key) {
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
    private String parseForgeBasePackage(Path javaRoot) {
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
    private String findFirstPackage(Path javaRoot) {
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

    private Properties loadGradleProperties(Path targetRoot) {
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

    private String resolvePlaceholders(String raw, Properties properties) {
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
    private ResourceLocation parseStructureLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.contains(":")) {
            return ResourceLocation.tryParse(raw);
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", raw);
    }

    @Nullable
    private Path findStructureSourcePath(ResourceLocation id, @Nullable String pack) {
        return resources.find(id, pack);
    }

    private List<DslScene.SceneSegment> normalizeSegments(DslScene scene) {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes;
        }
        DslScene.SceneSegment segment = new DslScene.SceneSegment();
        segment.steps = List.of();
        return List.of(segment);
    }

    private List<DslScene.DslStep> safeSteps(@Nullable List<DslScene.DslStep> steps) {
        return steps == null ? List.of() : steps;
    }

    private List<String> safeList(@Nullable List<String> list) {
        return list == null ? List.of() : list;
    }

    private boolean hasPos(@Nullable List<Integer> pos) {
        return pos != null && pos.size() >= 3;
    }

    private boolean isNonBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private boolean isItemStackSpec(String raw, @Nullable String nbtOverride) {
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
    private Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String classNameFor(String sceneId) {
        String sanitized = storyboardBasePath(sceneId).replace('/', '_').replace('-', '_').replace('.', '_');
        sanitized = Arrays.stream(sanitized.split("_+"))
            .filter(part -> !part.isBlank())
            .map(this::capitalize)
            .collect(Collectors.joining());
        String digest = shortDigest(sceneId);
        return "Generated" + (sanitized.isBlank() ? "Scene" : sanitized) + "_" + digest;
    }

    private String capitalize(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        if (raw.length() == 1) {
            return raw.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    private String storyboardBasePath(String sceneId) {
        ResourceLocation id = ResourceLocation.tryParse(sceneId);
        return id == null ? "scene" : id.getPath();
    }

    private String storyboardPath(DslScene scene, DslScene.SceneSegment segment, int index, int total) {
        String basePath = storyboardBasePath(scene.id);
        if (total <= 1) {
            return basePath;
        }
        String suffix = segment.id != null && !segment.id.isBlank() ? segment.id : String.valueOf(index + 1);
        return basePath + "_" + suffix;
    }

    private String fallbackSegmentTitle(DslScene scene, DslScene.SceneSegment segment, String titlePath,
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
    private String segmentId(@Nullable DslScene.SceneSegment segment) {
        if (segment == null || segment.id == null || segment.id.isBlank()) {
            return null;
        }
        return segment.id;
    }

    private void addFinding(List<JavaModuleScanResult.Finding> target, String category,
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

    private String firstMessage(List<JavaModuleScanResult.Finding> findings, String fallback) {
        return findings.isEmpty() ? fallback : findings.get(0).message;
    }

    private String resourceLocationArrayExpr(List<String> ids) {
        return resourceLocationArrayExpr(ids, null);
    }

    private String resourceLocationArrayExpr(List<String> ids, @Nullable String extraId) {
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
        return "new ResourceLocation[]{" + combined.stream().map(this::resourceLocationExpr).collect(Collectors.joining(", ")) + "}";
    }

    String resourceLocationExpr(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) {
            return "ResourceLocation.fromNamespaceAndPath(\"minecraft\", \"air\")";
        }
        return "ResourceLocation.fromNamespaceAndPath(" + stringExpr(loc.getNamespace()) + ", "
            + stringExpr(loc.getPath()) + ")";
    }

    private String localizedTextExpr(@Nullable LocalizedText text, String fallback) {
        return stringExpr(resolveExportText(text, fallback));
    }

    private String resolveExportText(@Nullable LocalizedText text, String fallback) {
        if (text == null || text.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String resolved = text.resolve("en_us");
        if (resolved != null) {
            return resolved;
        }
        return fallback == null ? "" : fallback;
    }

    private String stringExpr(@Nullable String value) {
        return value == null ? "null" : "\"" + escapeJava(value) + "\"";
    }

    private String boolExpr(@Nullable Boolean value) {
        return value == null ? "null" : Boolean.toString(value);
    }

    private String intExpr(@Nullable Integer value) {
        return value == null ? "null" : value.toString();
    }

    private String floatExpr(@Nullable Float value) {
        return value == null ? "null" : floatLiteral(value);
    }

    private String floatLiteral(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "0.0f";
        }
        return trimNumeric(value) + "f";
    }

    private String doubleLiteral(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0.0";
        }
        return trimNumeric(value);
    }

    private String trimNumeric(double value) {
        String raw = Double.toString(value);
        if (raw.endsWith(".0")) {
            return raw.substring(0, raw.length() - 2) + ".0";
        }
        return raw;
    }

    private String vecExpr(@Nullable List<Double> values) {
        if (values == null || values.size() < 3) {
            return "null";
        }
        return "new Vec3(" + doubleLiteral(values.get(0)) + ", " + doubleLiteral(values.get(1)) + ", " + doubleLiteral(values.get(2)) + ")";
    }

    private String vecExprOrDefault(@Nullable List<Double> values, double x, double y, double z) {
        if (values == null || values.size() < 3) {
            return "new Vec3(" + doubleLiteral(x) + ", " + doubleLiteral(y) + ", " + doubleLiteral(z) + ")";
        }
        return vecExpr(values);
    }

    private String blockPosExpr(@Nullable List<Integer> values) {
        if (values == null || values.size() < 3) {
            return "null";
        }
        return "new BlockPos(" + values.get(0) + ", " + values.get(1) + ", " + values.get(2) + ")";
    }

    private String boundsExpr(@Nullable List<Integer> values) {
        if (values == null || values.size() < 3) {
            return "new BlockPos(0, 0, 0)";
        }
        return "new BlockPos(" + values.get(0) + ", " + values.get(1) + ", " + values.get(2) + ")";
    }

    private String mapExpr(@Nullable Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "null";
        }
        String entries = map.entrySet().stream()
            .map(entry -> "Map.entry(" + stringExpr(entry.getKey()) + ", " + stringExpr(entry.getValue()) + ")")
            .collect(Collectors.joining(", "));
        return "Map.ofEntries(" + entries + ")";
    }

    private String packageToPath(String packageName) {
        return packageName.replace('.', '/');
    }

    private String hashSceneContent(String source,
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

    private Map<String, Map<String, String>> sortNestedStringMap(Map<String, Map<String, String>> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> sortStringMap(entry.getValue()),
                (left, right) -> right,
                LinkedHashMap::new));
    }

    private String shortDigest(String value) {
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

    private String escapeJava(String value) {
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
                                 TextFile sceneFile,
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
