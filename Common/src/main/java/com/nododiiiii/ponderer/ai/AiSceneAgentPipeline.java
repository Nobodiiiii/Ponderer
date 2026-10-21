package com.nododiiiii.ponderer.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Bounded JSON-protocol agent loop for AI-authored Ponder scenes.
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
    private static final int MAX_TOOL_CALLS_PER_TURN = 4;

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

    public record ToolCall(String id, String name, JsonObject arguments) {
    }

    public record ToolResult(String id, String name, JsonObject result) {
    }

    public record TraceEntry(int turn, String rawResponse, List<ToolCall> calls,
                             List<ToolResult> results, @Nullable String protocolError) {
    }

    public record Result(String runId,
                         DslScene scene,
                         String finalJson,
                         int turnCount,
                         int toolCallCount,
                         int validationAttempts,
                         List<TraceEntry> trace) {
    }

    public static Result run(Request request, LlmProvider llm, AiRuntimeConfig config,
                             Consumer<String> onStatus) {
        return run(UUID.randomUUID().toString(), request, llm, config,
            Config.AI_MAX_AGENT_TURNS.get(), onStatus, (name, content) -> {
            }, new AiCancellationScope());
    }

    public static Result run(Request request, LlmProvider llm, AiRuntimeConfig config,
                             Consumer<String> onStatus, BiConsumer<String, String> diagnostics) {
        return run(UUID.randomUUID().toString(), request, llm, config,
            Config.AI_MAX_AGENT_TURNS.get(), onStatus, diagnostics, new AiCancellationScope());
    }

    static Result run(String runId, Request request, LlmProvider llm, AiRuntimeConfig config,
                      int maxTurns, Consumer<String> onStatus,
                      BiConsumer<String, String> diagnostics, AiCancellationScope cancellation) {
        cancellation.throwIfCancelled();
        int boundedTurns = Math.max(2, Math.min(AiDefaults.AGENT_TURN_LIMIT, maxTurns));
        AgentToolRegistry toolRegistry = new AgentToolRegistry();
        AgentContext context = prepareContext(request, onStatus, diagnostics, cancellation);
        String manifest = buildManifest(context);
        String systemPrompt = AiSceneGenerator.buildAgentSystemPrompt(
            request.buildTutorial(), context.existingScene().available(), toolRegistry.promptSpecification());

        diagnostics.accept("effective_system_prompt.txt", systemPrompt);
        diagnostics.accept("manifest.json", manifest);
        List<TraceEntry> trace = new ArrayList<>();
        List<LlmProvider.ContentBlock> transcript = new ArrayList<>();
        Set<String> usedCallIds = new HashSet<>();
        int toolCallCount = 0;
        String lastAction = "context preparation";
        String lastProtocolError = "";

        for (int turn = 1; turn <= boundedTurns; turn++) {
            cancellation.throwIfCancelled();
            onStatus.accept("Agent turn " + turn + "/" + boundedTurns);
            List<LlmProvider.ContentBlock> content = buildTurnContent(
                manifest, transcript, turn, boundedTurns);
            String response = llm.generate(
                systemPrompt,
                content,
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                cancellation
            ).join();
            cancellation.throwIfCancelled();

            ParseOutcome parsed = parseToolCalls(response, toolRegistry);
            if (parsed.error() != null) {
                lastAction = "invalid model response on turn " + turn;
                lastProtocolError = parsed.error();
                JsonObject error = protocolError(parsed.error());
                transcript.add(new LlmProvider.ContentBlock.Text(
                    formatTranscriptTurn(turn, response, error)));
                trace.add(new TraceEntry(turn, response, List.of(), List.of(), parsed.error()));
                writeTrace(diagnostics, runId, trace);
                onStatus.accept("Fixing scene draft");
                continue;
            }
            if (parsed.editScope() != null) context.runState().setEditScope(parsed.editScope());
            String reusedId = parsed.calls().stream()
                .map(ToolCall::id)
                .filter(usedCallIds::contains)
                .findFirst()
                .orElse(null);
            if (reusedId != null) {
                String errorMessage = "Duplicate tool call id across turns: " + reusedId;
                lastAction = "invalid model response on turn " + turn;
                lastProtocolError = errorMessage;
                JsonObject error = protocolError(errorMessage);
                transcript.add(new LlmProvider.ContentBlock.Text(
                    formatTranscriptTurn(turn, response, error)));
                trace.add(new TraceEntry(turn, response, parsed.calls(), List.of(), errorMessage));
                writeTrace(diagnostics, runId, trace);
                onStatus.accept("Fixing scene draft");
                continue;
            }
            parsed.calls().forEach(call -> usedCallIds.add(call.id()));
            lastAction = parsed.calls().stream()
                .map(call -> call.name() + "[" + call.id() + "]")
                .collect(Collectors.joining(", "));

            List<ToolResult> results = new ArrayList<>();
            List<LlmProvider.ContentBlock> turnTranscript = new ArrayList<>();
            turnTranscript.add(new LlmProvider.ContentBlock.Text(
                "TURN " + turn + " ASSISTANT_RESPONSE:\n" + response));
            DslScene submittedScene = null;
            for (ToolCall call : parsed.calls()) {
                cancellation.throwIfCancelled();
                AgentTool tool = toolRegistry.get(call.name());
                onStatus.accept(statusForTool(call.name()));
                AgentTool.ToolExecution execution;
                try {
                    execution = tool.execute(context, call.arguments(), cancellation);
                } catch (CancellationException e) {
                    throw e;
                } catch (RuntimeException e) {
                    JsonObject failure = new JsonObject();
                    failure.addProperty("ok", false);
                    failure.addProperty("error", safeMessage(e));
                    execution = AgentTool.ToolExecution.result(failure);
                }
                cancellation.throwIfCancelled();
                ToolResult result = new ToolResult(call.id(), call.name(), execution.result());
                results.add(result);
                turnTranscript.add(new LlmProvider.ContentBlock.Text(
                    formatToolResult(turn, result)));
                turnTranscript.addAll(execution.images());
                if (execution.submittedScene() != null) {
                    submittedScene = execution.submittedScene();
                }
                toolCallCount++;
            }

            transcript.addAll(turnTranscript);
            trace.add(new TraceEntry(turn, response, parsed.calls(), List.copyOf(results), null));
            writeTrace(diagnostics, runId, trace);

            if (submittedScene != null) {
                cancellation.throwIfCancelled();
                String finalJson = GSON_PRETTY.toJson(submittedScene);
                diagnostics.accept("last_extracted_json_attempt_final.log", finalJson);
                return new Result(
                    runId,
                    submittedScene,
                    finalJson,
                    turn,
                    toolCallCount,
                    context.runState().validationAttempts(),
                    List.copyOf(trace)
                );
            }
            if (!context.runState().lastErrors().isEmpty()
                && parsed.calls().stream().anyMatch(call ->
                    "review_scene".equals(call.name()) || "submit_scene".equals(call.name()))) {
                onStatus.accept("Fixing scene draft");
            }
        }

        throw exhaustedException(
            boundedTurns, toolCallCount, lastAction, lastProtocolError, context);
    }

    private static AgentContext prepareContext(Request request, Consumer<String> onStatus,
                                               BiConsumer<String, String> diagnostics,
                                               AiCancellationScope cancellation) {
        try {
            AgentContext.ExistingSceneDocument existingScene = parseExistingScene(request.existingJson());
            if (existingScene.error() != null) {
                throw new IllegalArgumentException(existingScene.error());
            }
            List<AgentContext.StructureDocument> structures = new ArrayList<>();
            if (existingScene.available()) {
                Set<String> references = new LinkedHashSet<>();
                SceneStore.collectStructureReferences(existingScene.scene(), references);
                for (String reference : references) {
                    cancellation.throwIfCancelled();
                    structures.add(inspectExistingStructure(reference, existingScene.scene().pack));
                }
            }
            if (request.structurePaths() != null) {
                for (Path path : request.structurePaths()) {
                    cancellation.throwIfCancelled();
                    String id = toStructureId(path);
                    if (structures.stream().noneMatch(document -> document.id().equals(id))) {
                        structures.add(new AgentContext.StructureDocument(id, StructureDescriber.inspect(path)));
                    }
                }
            }
            if (structures.isEmpty()) {
                try (InputStream in = SceneStore.openBuiltinStructure("basic")) {
                    if (in != null) {
                        structures.add(new AgentContext.StructureDocument(
                            "ponderer:basic", StructureDescriber.inspect(in)));
                    }
                }
            }
            if (structures.isEmpty()) {
                throw new IllegalArgumentException("No readable structures were provided");
            }

            List<AgentContext.ReferenceDocument> references = fetchReferences(
                request, onStatus, diagnostics, cancellation);
            return new AgentContext(
                structures,
                references,
                existingScene,
                request.carrierItemId(),
                request.userPrompt(),
                request.buildTutorial(),
                request.includeImages()
            );
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare AI agent context: " + safeMessage(e), e);
        }
    }

    private static AgentContext.StructureDocument inspectExistingStructure(String reference,
                                                                           @Nullable String pack) throws Exception {
        ResourceLocation id = ResourceLocation.tryParse(reference.contains(":") ? reference : "ponderer:" + reference);
        if (id == null) {
            throw new IllegalArgumentException("Invalid existing scene structure: " + reference);
        }
        String relative = "ponderer".equals(id.getNamespace())
            ? id.getPath() : id.getNamespace() + "/" + id.getPath();
        Path path = SceneStore.resolveStructurePath(relative, pack);
        if (path != null && Files.isRegularFile(path)) {
            return new AgentContext.StructureDocument(id.toString(), StructureDescriber.inspect(path));
        }
        if ("ponderer".equals(id.getNamespace())) {
            try (InputStream in = SceneStore.openBuiltinStructure(id.getPath())) {
                if (in != null) {
                    return new AgentContext.StructureDocument(id.toString(), StructureDescriber.inspect(in));
                }
            }
        }
        throw new IllegalArgumentException("Cannot read existing scene structure: " + reference);
    }

    private static List<AgentContext.ReferenceDocument> fetchReferences(
        Request request, Consumer<String> onStatus, BiConsumer<String, String> diagnostics,
        AiCancellationScope cancellation) {
        List<AgentContext.ReferenceDocument> references = new ArrayList<>();
        if (request.referenceUrls() == null || request.referenceUrls().isEmpty()) {
            return references;
        }
        onStatus.accept("Fetching reference pages");
        StringBuilder webLog = new StringBuilder();
        int index = 0;
        for (String rawUrl : request.referenceUrls()) {
            if (rawUrl == null || rawUrl.isBlank()) {
                continue;
            }
            cancellation.throwIfCancelled();
            String id = "ref_" + (++index);
            String url = rawUrl.trim();
            try {
                WebPageFetcher.WebPageContent page = WebPageFetcher.fetch(
                    url, request.includeImages(), cancellation);
                references.add(new AgentContext.ReferenceDocument(
                    id, url, page.text(), List.copyOf(page.images()), null));
                webLog.append("=== ").append(id).append(" ").append(url).append(" ===\n")
                    .append("Text length: ").append(page.text().length())
                    .append(", Images: ").append(page.images().size()).append("\n\n")
                    .append(page.text()).append("\n\n");
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                String message = safeMessage(e);
                references.add(new AgentContext.ReferenceDocument(
                    id, url, null, List.of(), message));
                webLog.append("=== FAILED ").append(id).append(" ").append(url).append(" ===\n")
                    .append(message).append("\n\n");
            }
        }
        if (!webLog.isEmpty()) {
            diagnostics.accept("last_web_content.log", webLog.toString());
        }
        return List.copyOf(references);
    }

    private static AgentContext.ExistingSceneDocument parseExistingScene(@Nullable String existingJson) {
        if (existingJson == null || existingJson.isBlank()) {
            return new AgentContext.ExistingSceneDocument(null, null, null);
        }
        try {
            JsonObject json = JsonParser.parseString(existingJson).getAsJsonObject();
            DslScene scene = GSON.fromJson(json, DslScene.class);
            return new AgentContext.ExistingSceneDocument(json, scene, null);
        } catch (RuntimeException e) {
            return new AgentContext.ExistingSceneDocument(null, null,
                "Existing scene JSON could not be parsed: " + safeMessage(e));
        }
    }

    private static String buildManifest(AgentContext context) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("prompt_version", AiPromptAssembler.VERSION);
        manifest.addProperty("minecraft_version", net.minecraft.SharedConstants.getCurrentVersion().getName());
        try {
            var platform = com.nododiiiii.ponderer.platform.PondererServices.PLATFORM;
            manifest.addProperty("loader", platform.getPlatformName());
            manifest.add("mod_versions", GSON.toJsonTree(platform.loadedModVersions()));
        } catch (Throwable unavailable) { manifest.addProperty("loader", "unavailable"); }
        manifest.add("draft", context.drafts().descriptor());
        manifest.addProperty("carrier_item_id", context.carrierItemId());
        manifest.addProperty("user_instruction", context.userPrompt());
        manifest.addProperty("build_tutorial", context.buildTutorial());
        manifest.addProperty("include_images", context.includeImages());

        JsonArray structures = new JsonArray();
        for (AgentContext.StructureDocument document : context.structures()) {
            StructureDescriber.StructureInfo info = document.snapshot().info();
            JsonObject structure = new JsonObject();
            structure.addProperty("id", document.id());
            structure.add("size", GSON.toJsonTree(List.of(info.sizeX(), info.sizeY(), info.sizeZ())));
            structure.addProperty("block_count", document.snapshot().blocks().size());
            structure.add("block_types", GSON.toJsonTree(info.blockTypes()));
            structures.add(structure);
        }
        manifest.add("structures", structures);

        JsonArray references = new JsonArray();
        for (AgentContext.ReferenceDocument document : context.references()) {
            JsonObject reference = new JsonObject();
            reference.addProperty("id", document.id());
            reference.addProperty("url", document.url());
            reference.addProperty("available", document.available());
            if (document.available()) {
                reference.addProperty("text_length", document.text().length());
                reference.addProperty("image_count", document.images().size());
            } else {
                reference.addProperty("error", document.error());
            }
            references.add(reference);
        }
        manifest.add("references", references);
        manifest.add("existing_scene", existingSceneSummary(context.existingScene()));
        return GSON_PRETTY.toJson(manifest);
    }

    private static JsonObject existingSceneSummary(AgentContext.ExistingSceneDocument existing) {
        JsonObject summary = new JsonObject();
        summary.addProperty("available", existing.available());
        if (!existing.available()) {
            if (existing.error() != null) {
                summary.addProperty("error", existing.error());
            }
            return summary;
        }
        summary.addProperty("id", existing.scene().id);
        JsonArray topLevelFields = new JsonArray();
        for (String key : existing.json().keySet()) {
            if (!"scenes".equals(key)) {
                topLevelFields.add(key);
            }
        }
        summary.add("top_level_fields", topLevelFields);
        JsonArray segments = new JsonArray();
        int totalSteps = 0;
        if (existing.scene().scenes != null) {
            for (DslScene.SceneSegment segment : existing.scene().scenes) {
                JsonObject item = new JsonObject();
                item.addProperty("id", segment == null ? "" : segment.id);
                int stepCount = segment == null || segment.steps == null ? 0 : segment.steps.size();
                item.addProperty("step_count", stepCount);
                totalSteps += stepCount;
                segments.add(item);
            }
        }
        summary.addProperty("segment_count", segments.size());
        summary.addProperty("step_count", totalSteps);
        summary.add("segments", segments);
        return summary;
    }

    private static List<LlmProvider.ContentBlock> buildTurnContent(
        String manifest, List<LlmProvider.ContentBlock> transcript, int turn, int maxTurns) {
        List<LlmProvider.ContentBlock> content = new ArrayList<>();
        content.add(new LlmProvider.ContentBlock.Text(
            "=== Generation manifest ===\n" + manifest));
        if (!transcript.isEmpty()) {
            content.add(new LlmProvider.ContentBlock.Text(
                "=== Agent transcript (tool images follow their matching result) ==="));
            content.addAll(transcript);
        }
        content.add(new LlmProvider.ContentBlock.Text(
            "=== Turn budget ===\nTurn " + turn + " of " + maxTurns
                + ". Return only one JSON object containing tool_calls."
                + (turn == maxTurns ? " This is the final turn: submit_scene is required to finish."
                    : " Batch independent lookups and leave a turn for submit_scene.")));
        return content;
    }

    private static ParseOutcome parseToolCalls(String response, AgentToolRegistry registry) {
        try {
            String json = LlmJsonSupport.cleanJson(LlmJsonSupport.extractJson(response));
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return ParseOutcome.error("Response must be a JSON object");
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement callsElement = object.get("tool_calls");
            if (callsElement == null || !callsElement.isJsonArray()) {
                return ParseOutcome.error("Response must contain a tool_calls array");
            }
            JsonArray callsArray = callsElement.getAsJsonArray();
            if (callsArray.isEmpty()) {
                return ParseOutcome.error("tool_calls must not be empty");
            }
            if (callsArray.size() > MAX_TOOL_CALLS_PER_TURN) {
                return ParseOutcome.error("At most " + MAX_TOOL_CALLS_PER_TURN
                    + " tool calls are allowed per turn");
            }

            List<ToolCall> calls = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            boolean hasTerminal = false;
            for (JsonElement callElement : callsArray) {
                if (!callElement.isJsonObject()) {
                    return ParseOutcome.error("Each tool call must be an object");
                }
                JsonObject call = callElement.getAsJsonObject();
                String id = requiredProtocolString(call, "id");
                String name = requiredProtocolString(call, "name").toLowerCase(Locale.ROOT);
                if (!ids.add(id)) {
                    return ParseOutcome.error("Duplicate tool call id: " + id);
                }
                AgentTool tool = registry.get(name);
                if (tool == null) {
                    return ParseOutcome.error("Unknown tool: " + name);
                }
                JsonElement arguments = call.get("arguments");
                if (arguments == null || !arguments.isJsonObject()) {
                    return ParseOutcome.error("Tool arguments must be an object for " + name);
                }
                hasTerminal |= tool.terminal() || "apply_scene_patch".equals(name) || "review_scene".equals(name);
                calls.add(new ToolCall(id, name, arguments.getAsJsonObject().deepCopy()));
            }
            if (hasTerminal && calls.size() != 1) {
                return ParseOutcome.error("Draft mutations and submit_scene must be the only tool call in their turn");
            }
            return new ParseOutcome(List.copyOf(calls), null,
                object.has("edit_scope") && object.get("edit_scope").isJsonObject() ? object.getAsJsonObject("edit_scope") : null);
        } catch (RuntimeException e) {
            return ParseOutcome.error("Could not parse tool protocol JSON: " + safeMessage(e));
        }
    }

    private static String requiredProtocolString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.getAsString().trim();
    }

    private static JsonObject toolResultsJson(List<ToolResult> results) {
        JsonObject wrapper = new JsonObject();
        JsonArray array = new JsonArray();
        for (ToolResult result : results) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", result.id());
            entry.addProperty("name", result.name());
            entry.add("result", result.result());
            array.add(entry);
        }
        wrapper.add("tool_results", array);
        return wrapper;
    }

    private static JsonObject protocolError(String message) {
        JsonObject error = new JsonObject();
        JsonObject detail = new JsonObject();
        detail.addProperty("message", message);
        detail.addProperty("retryable", true);
        error.add("protocol_error", detail);
        return error;
    }

    private static String formatTranscriptTurn(int turn, String response, JsonObject result) {
        return "TURN " + turn + " ASSISTANT_RESPONSE:\n" + response
            + "\nTURN " + turn + " HOST_RESULT:\n" + GSON_PRETTY.toJson(result);
    }

    private static String formatToolResult(int turn, ToolResult result) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", result.id());
        entry.addProperty("name", result.name());
        entry.add("result", result.result());
        return "TURN " + turn + " TOOL_RESULT:\n" + GSON_PRETTY.toJson(entry);
    }

    private static RuntimeException exhaustedException(
        int maxTurns, int toolCallCount, String lastAction, String lastProtocolError,
        AgentContext context) {
        StringBuilder message = new StringBuilder(
            "AI agent exhausted " + maxTurns + " turns without submitting a valid scene");
        message.append("\nLast action: ").append(lastAction == null || lastAction.isBlank()
            ? "none" : lastAction);
        if (lastProtocolError != null && !lastProtocolError.isBlank()) {
            message.append("\nLast protocol error: ").append(lastProtocolError);
        }
        String lastErrors = context.runState().lastErrors().stream()
            .map(AiSceneValidator.Issue::format)
            .collect(Collectors.joining("\n"));
        if (!lastErrors.isBlank()) {
            message.append("\nLast validation errors:\n").append(lastErrors);
        } else if (!context.runState().lastWarnings().isEmpty()) {
            message.append("\nLast validation warnings:\n")
                .append(context.runState().lastWarnings().stream()
                    .map(warning -> warning.path() + ": " + warning.message())
                    .collect(Collectors.joining("\n")));
        }
        message.append("\nTurns allowed: ").append(maxTurns)
            .append("; tool calls executed: ").append(toolCallCount);
        return new RuntimeException(message.toString());
    }

    private static String formatTrace(List<TraceEntry> trace) {
        StringBuilder out = new StringBuilder();
        for (TraceEntry entry : trace) {
            out.append("=== TURN ").append(entry.turn()).append(" ===\n")
                .append("ASSISTANT_RESPONSE:\n").append(entry.rawResponse()).append("\n");
            if (entry.protocolError() != null) {
                out.append("PROTOCOL_ERROR:\n").append(entry.protocolError()).append("\n\n");
            } else {
                out.append("TOOL_CALLS:\n").append(GSON_PRETTY.toJson(entry.calls())).append("\n")
                    .append("TOOL_RESULTS:\n").append(GSON_PRETTY.toJson(entry.results())).append("\n\n");
            }
        }
        return out.toString();
    }

    private static void writeTrace(BiConsumer<String, String> diagnostics,
                                   String runId, List<TraceEntry> trace) {
        diagnostics.accept("last_agent_trace.log",
            "run=" + runId + "\n" + formatTrace(trace));
    }

    private static String statusForTool(String name) {
        return switch (name) {
            case "query_structure" -> "Querying structure";
            case "lookup_registry", "inspect_registry_entries" -> "Resolving registry entries";
            case "get_step_schema" -> "Loading scene step schema";
            case "search_references" -> "Searching reference material";
            case "inspect_existing_scene" -> "Inspecting existing scene";
            case "get_scene_draft" -> "Inspecting scene draft";
            case "apply_scene_patch" -> "Applying scoped scene patch";
            case "review_scene" -> "Reviewing scene draft";
            case "submit_scene" -> "Validating scene submission";
            default -> "Running agent tool";
        };
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
            ResourceLocation parsed = ResourceLocation.tryParse(
                relative.contains(":") ? relative : "ponderer:" + relative);
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
        return segments == null || segments.isEmpty()
            ? "ponderer:generated"
            : "ponderer:" + String.join("/", segments);
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record ParseOutcome(List<ToolCall> calls, @Nullable String error, @Nullable JsonObject editScope) {
        private static ParseOutcome error(String error) {
            return new ParseOutcome(List.of(), error, null);
        }
    }
}
