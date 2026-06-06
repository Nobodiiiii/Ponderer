package com.nododiiiii.ponderer.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Basic multi-round agent pipeline:
 * planner -> registry resolver -> JSON writer -> deterministic validator -> repair loop.
 */
public final class AiSceneAgentPipeline {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .setLenient()
        .create();
    private static final Gson GSON_PRETTY = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    private static final int DEFAULT_REPAIR_ATTEMPTS = 2;

    private AiSceneAgentPipeline() {
    }

    public record Request(List<Path> structurePaths,
                          String carrierItemId,
                          String userPrompt,
                          List<String> referenceUrls,
                          @Nullable String existingJson,
                          boolean buildTutorial,
                          boolean includeImages) {
    }

    public record Result(String runId,
                         DslScene scene,
                         String outline,
                         String registryMapping,
                         String finalJson,
                         List<AiSceneValidator.Issue> validationIssues,
                         int repairAttempts,
                         List<String> drafts) {
    }

    record PreparedContext(List<StructureDescriber.StructureInfo> structures,
                           List<String> structureNames,
                           List<String> allBlockTypes,
                           String structureDescription,
                           List<LlmProvider.ContentBlock> webContent) {
    }

    @FunctionalInterface
    interface RegistryMappingResolver {
        String build(List<String> requiredElements, List<String> structureBlockIds);
    }

    public static Result run(Request request, LlmProvider llm, AiRuntimeConfig config,
                             Consumer<String> onStatus) {
        return run(request, llm, config, DEFAULT_REPAIR_ATTEMPTS, onStatus);
    }

    public static Result run(Request request, LlmProvider llm, AiRuntimeConfig config,
                             Consumer<String> onStatus, BiConsumer<String, String> diagnostics) {
        return run(request, llm, config, DEFAULT_REPAIR_ATTEMPTS, onStatus, diagnostics);
    }

    static Result run(Request request, LlmProvider llm, AiRuntimeConfig config, int maxRepairAttempts,
                      Consumer<String> onStatus) {
        return run(request, llm, config, maxRepairAttempts, onStatus, RegistryMapper::buildMappingForDisplayNames);
    }

    static Result run(Request request, LlmProvider llm, AiRuntimeConfig config, int maxRepairAttempts,
                      Consumer<String> onStatus, BiConsumer<String, String> diagnostics) {
        return run(request, llm, config, maxRepairAttempts, onStatus,
            RegistryMapper::buildMappingForDisplayNames, diagnostics);
    }

    static Result run(Request request, LlmProvider llm, AiRuntimeConfig config, int maxRepairAttempts,
                      Consumer<String> onStatus, RegistryMappingResolver registryMappingResolver) {
        return run(request, llm, config, maxRepairAttempts, onStatus, registryMappingResolver, (name, content) -> {
        });
    }

    static Result run(Request request, LlmProvider llm, AiRuntimeConfig config, int maxRepairAttempts,
                      Consumer<String> onStatus, RegistryMappingResolver registryMappingResolver,
                      BiConsumer<String, String> diagnostics) {
        String runId = UUID.randomUUID().toString();
        PreparedContext context = prepareContext(request, onStatus, diagnostics);

        onStatus.accept("Planning scene");
        String outline = llm.generate(
            AiSceneGenerator.buildOutlineSystemPrompt(request.buildTutorial()),
            outlineContent(request, context),
            config.baseUrl(),
            config.apiKey(),
            config.model()
        ).join();
        diagnostics.accept("last_outline.log", outline);

        List<String> requiredElements = AiSceneGenerator.parseRequiredElements(outline);
        String registryMapping = registryMappingResolver.build(requiredElements, context.allBlockTypes());
        diagnostics.accept("last_registry_mapping.log", registryMapping);

        onStatus.accept("Writing scene JSON");
        List<String> drafts = new ArrayList<>();
        Draft draft = generateDraft(llm, config, request, context, outline, registryMapping, null, 1, diagnostics);
        drafts.add(draft.rawJson());

        int repairs = 0;
        List<AiSceneValidator.Issue> issues = validateDraft(draft, context);
        while (!issues.isEmpty() && repairs < maxRepairAttempts) {
            repairs++;
            onStatus.accept("Repairing scene JSON (" + repairs + "/" + maxRepairAttempts + ")");
            String repairInstruction = buildRepairInstruction(draft.rawJson(), issues);
            draft = generateDraft(llm, config, request, context, outline, registryMapping, repairInstruction,
                repairs + 1, diagnostics);
            drafts.add(draft.rawJson());
            issues = validateDraft(draft, context);
        }

        if (!issues.isEmpty() || draft.scene() == null) {
            diagnostics.accept("last_validation_error.log", issues.stream()
                .map(AiSceneValidator.Issue::format)
                .collect(Collectors.joining("\n")));
            throw new RuntimeException("AI agent could not produce a valid scene:\n"
                + issues.stream().map(AiSceneValidator.Issue::format).collect(Collectors.joining("\n")));
        }

        AiSceneGenerator.autoAddKeyFrames(draft.scene());
        String finalJson = GSON_PRETTY.toJson(draft.scene());
        diagnostics.accept("last_extracted_json_attempt_final.log", finalJson);
        return new Result(runId, draft.scene(), outline, registryMapping, finalJson, issues, repairs, List.copyOf(drafts));
    }

    private static PreparedContext prepareContext(Request request, Consumer<String> onStatus,
                                                  BiConsumer<String, String> diagnostics) {
        try {
            List<StructureDescriber.StructureInfo> structures = new ArrayList<>();
            List<String> structureNames = new ArrayList<>();
            Set<String> blockTypes = new LinkedHashSet<>();

            if (request.structurePaths() == null || request.structurePaths().isEmpty()) {
                try (InputStream in = SceneStore.openBuiltinStructure("basic")) {
                    if (in != null) {
                        StructureDescriber.StructureInfo info = StructureDescriber.describe(in);
                        structures.add(info);
                        structureNames.add("ponderer:basic");
                        blockTypes.addAll(info.blockTypes());
                    }
                }
            } else {
                for (Path path : request.structurePaths()) {
                    StructureDescriber.StructureInfo info = StructureDescriber.describe(path);
                    structures.add(info);
                    structureNames.add(toStructureId(path));
                    blockTypes.addAll(info.blockTypes());
                }
            }

            String structureDescription = buildStructureDescription(structures, structureNames);
            List<LlmProvider.ContentBlock> webContent = fetchReferenceContent(request, onStatus, diagnostics);
            return new PreparedContext(
                List.copyOf(structures),
                List.copyOf(structureNames),
                List.copyOf(blockTypes),
                structureDescription,
                List.copyOf(webContent)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare AI agent context: " + e.getMessage(), e);
        }
    }

    private static String buildStructureDescription(List<StructureDescriber.StructureInfo> structures,
                                                    List<String> structureNames) {
        StringBuilder out = new StringBuilder();
        out.append("=== Structure Information ===\n");
        out.append("Structure pool:\n");
        for (int i = 0; i < structureNames.size(); i++) {
            out.append("  ").append(i + 1).append(". ").append(structureNames.get(i)).append("\n");
        }
        out.append("\n");
        for (int i = 0; i < structures.size(); i++) {
            out.append("--- Structure: ").append(structureNames.get(i)).append(" ---\n");
            out.append(structures.get(i).textDescription()).append("\n");
        }
        return out.toString();
    }

    private static List<LlmProvider.ContentBlock> fetchReferenceContent(Request request, Consumer<String> onStatus,
                                                                       BiConsumer<String, String> diagnostics) {
        List<LlmProvider.ContentBlock> content = new ArrayList<>();
        if (request.referenceUrls() == null) {
            return content;
        }
        boolean hasUrls = request.referenceUrls().stream().anyMatch(url -> url != null && !url.isBlank());
        if (hasUrls) {
            onStatus.accept("Fetching reference pages");
        }
        StringBuilder webLog = new StringBuilder();
        for (String url : request.referenceUrls()) {
            if (url == null || url.isBlank()) {
                continue;
            }
            try {
                WebPageFetcher.WebPageContent page = WebPageFetcher.fetch(url.trim(), request.includeImages());
                content.add(new LlmProvider.ContentBlock.Text(
                    "=== Reference web page: " + url.trim() + " ===\n" + page.text()));
                webLog.append("=== ").append(url.trim()).append(" ===\n");
                webLog.append("Text length: ").append(page.text().length())
                    .append(" chars, Images sent: ").append(page.images().size()).append("\n\n");
                webLog.append(page.text()).append("\n\n");
                if (request.includeImages()) {
                    for (WebPageFetcher.ImageData image : page.images()) {
                        content.add(new LlmProvider.ContentBlock.Image(image.base64(), image.mediaType()));
                        webLog.append("[Image: ").append(image.mediaType())
                            .append(", base64 length: ").append(image.base64().length()).append("]\n");
                    }
                } else {
                    webLog.append("[Images skipped: includeImages=false]\n");
                }
                webLog.append("\n");
            } catch (Exception e) {
                content.add(new LlmProvider.ContentBlock.Text(
                    "(Failed to fetch " + url.trim() + ": " + e.getMessage() + ")"));
                webLog.append("=== FAILED: ").append(url.trim()).append(" ===\n")
                    .append(e.getMessage()).append("\n\n");
            }
        }
        if (!webLog.isEmpty()) {
            diagnostics.accept("last_web_content.log", webLog.toString());
        }
        return content;
    }

    private static List<LlmProvider.ContentBlock> outlineContent(Request request, PreparedContext context) {
        List<LlmProvider.ContentBlock> content = new ArrayList<>();
        content.add(new LlmProvider.ContentBlock.Text(context.structureDescription()));
        content.addAll(context.webContent());
        if (request.existingJson() != null && !request.existingJson().isBlank()) {
            content.add(new LlmProvider.ContentBlock.Text(
                "=== Current scene JSON (adjust based on instruction below) ===\n" + request.existingJson()));
        }
        content.add(new LlmProvider.ContentBlock.Text(
            "=== User instruction ===\n" + request.userPrompt()
                + "\n\nTarget item ID: " + request.carrierItemId()
                + "\nStructures: " + String.join(", ", context.structureNames())));
        return content;
    }

    private static Draft generateDraft(LlmProvider llm, AiRuntimeConfig config, Request request,
                                       PreparedContext context, String outline, String registryMapping,
                                       @Nullable String repairInstruction, int attempt,
                                       BiConsumer<String, String> diagnostics) {
        List<LlmProvider.ContentBlock> content = new ArrayList<>();
        content.add(new LlmProvider.ContentBlock.Text(context.structureDescription()));
        content.add(new LlmProvider.ContentBlock.Text(registryMapping));
        content.addAll(context.webContent());
        if (request.existingJson() != null && !request.existingJson().isBlank()) {
            content.add(new LlmProvider.ContentBlock.Text(
                "=== Current scene JSON (modify based on instruction below) ===\n" + request.existingJson()));
        }
        content.add(new LlmProvider.ContentBlock.Text("=== Scene design outline (follow this plan) ===\n" + outline));
        if (repairInstruction != null && !repairInstruction.isBlank()) {
            content.add(new LlmProvider.ContentBlock.Text(repairInstruction));
        }
        content.add(new LlmProvider.ContentBlock.Text(
            "=== User instruction ===\n" + request.userPrompt()
                + "\n\nTarget item ID: " + request.carrierItemId()
                + "\nStructures: " + String.join(", ", context.structureNames())));

        String response = llm.generate(
            AiSceneGenerator.buildSystemPrompt(request.buildTutorial()),
            content,
            config.baseUrl(),
            config.apiKey(),
            config.model()
        ).join();
        diagnostics.accept("last_json_response_attempt_" + attempt + ".log", response);
        String json = LlmJsonSupport.cleanJson(LlmJsonSupport.extractJson(response));
        diagnostics.accept("last_extracted_json_attempt_" + attempt + ".log", json);
        DslScene scene = null;
        Exception parseError = null;
        try {
            scene = GSON.fromJson(json, DslScene.class);
        } catch (Exception e) {
            parseError = e;
        }
        return new Draft(json, scene, parseError);
    }

    private static List<AiSceneValidator.Issue> validateDraft(Draft draft, PreparedContext context) {
        List<AiSceneValidator.Issue> issues = new ArrayList<>();
        if (draft.parseError() != null) {
            issues.add(new AiSceneValidator.Issue("$", "JSON parse error: " + draft.parseError().getMessage()));
            return issues;
        }
        issues.addAll(AiSceneValidator.validate(draft.scene(), context.structures(), context.structureNames()));
        return issues;
    }

    private static String buildRepairInstruction(String previousJson, List<AiSceneValidator.Issue> issues) {
        String formattedIssues = issues.stream()
            .map(AiSceneValidator.Issue::format)
            .collect(Collectors.joining("\n"));
        return """
            === VALIDATOR REPAIR REQUEST ===
            Your previous JSON did not pass deterministic validation.
            Keep the same scene plan and intent. Fix only the JSON fields needed to resolve these errors.
            Output one complete valid DslScene JSON object, with no markdown fences or explanation.

            Validation errors:
            %s

            Previous JSON:
            %s
            """.formatted(formattedIssues, previousJson);
    }

    private static String toStructureId(Path path) {
        Path structureDir = SceneStore.getStructureDir();
        Path absolute = path.toAbsolutePath().normalize();
        Path root = structureDir.toAbsolutePath().normalize();
        if (absolute.startsWith(root)) {
            String relative = root.relativize(absolute).toString().replace('\\', '/');
            if (relative.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                relative = relative.substring(0, relative.length() - 4);
            }
            ResourceLocation parsed = ResourceLocation.tryParse(relative.contains(":") ? relative : "ponderer:" + relative);
            if (parsed != null) {
                return parsed.toString();
            }
        }

        String name = path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            name = name.substring(0, name.length() - 4);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(name.contains(":") ? name : "ponderer:" + name);
        if (parsed != null) {
            return parsed.toString();
        }
        List<String> segments = SafePaths.splitValidatedRelativePath(name);
        return segments == null || segments.isEmpty() ? "ponderer:generated" : "ponderer:" + String.join("/", segments);
    }

    private record Draft(String rawJson, @Nullable DslScene scene, @Nullable Exception parseError) {
    }
}
