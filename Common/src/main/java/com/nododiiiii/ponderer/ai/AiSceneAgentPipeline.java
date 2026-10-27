package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.support.structure.StructureDescriber;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Bounded agent orchestration; context projection and tool scheduling have separate owners.
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

    private final GenerationResources resources;
    private final java.util.function.IntSupplier maxTurns;

    public AiSceneAgentPipeline(GenerationResources resources, java.util.function.IntSupplier maxTurns) {
        this.resources = java.util.Objects.requireNonNull(resources);
        this.maxTurns = java.util.Objects.requireNonNull(maxTurns);
    }


    public record Request(List<Path> structurePaths,
                          String carrierItemId,
                          String userPrompt,
                          List<String> referenceUrls,
                          @Nullable String existingJson,
                          boolean buildTutorial,
                          boolean includeImages,
                          @Nullable JsonObject resumeDraft,
                          @Nullable JsonObject resumeScope,
                          EditMode editMode,
                          @Nullable JsonObject resumeMemory) {
        public enum EditMode { SCOPED, REWRITE }
        public Request {
            structurePaths = structurePaths == null ? List.of() : List.copyOf(structurePaths);
            referenceUrls = referenceUrls == null ? List.of() : List.copyOf(referenceUrls);
            resumeDraft = resumeDraft == null ? null : resumeDraft.deepCopy();
            resumeScope = resumeScope == null ? null : resumeScope.deepCopy();
            resumeMemory = resumeMemory == null ? null : resumeMemory.deepCopy();
            editMode = editMode == null ? EditMode.SCOPED : editMode;
        }
        public Request(List<Path> paths, String carrier, String prompt, List<String> urls, String existing,
                       boolean building, boolean images, JsonObject draft, JsonObject scope, EditMode mode) {
            this(paths, carrier, prompt, urls, existing, building, images, draft, scope, mode, null);
        }
        public Request(List<Path> paths, String carrier, String prompt, List<String> urls, String existing,
                       boolean building, boolean images, JsonObject draft, JsonObject scope) {
            this(paths, carrier, prompt, urls, existing, building, images, draft, scope, EditMode.SCOPED);
        }
        public Request(List<Path> paths, String carrier, String prompt, List<String> urls, String existing,
                       boolean building, boolean images) {
            this(paths, carrier, prompt, urls, existing, building, images, null, null, EditMode.SCOPED);
        }
    }

    public record ToolCall(String id, String name, JsonObject arguments) {
    }

    public record ToolResult(String id, String name, JsonObject result) {
    }

    public record TraceEntry(int turn, String rawResponse, List<ToolCall> calls,
                             List<ToolResult> results, @Nullable String protocolError) {
    }

    public record Result(String runId,
                         com.nododiiiii.ponderer.scene.SceneSnapshot candidate,
                         String finalJson,
                         int turnCount,
                         int toolCallCount,
                         int validationAttempts,
                         List<TraceEntry> trace) {
        public DslScene scene() { return candidate.document(); }
    }

    public Result run(Request request, LlmProvider llm, AiRuntimeConfig config,
                             Consumer<String> onStatus) {
        return run(UUID.randomUUID().toString(), request, llm, config,
            maxTurns.getAsInt(), onStatus, (name, content) -> {
            }, new AiCancellationScope());
    }

    public Result run(Request request, LlmProvider llm, AiRuntimeConfig config,
                             Consumer<String> onStatus, BiConsumer<String, String> diagnostics) {
        return run(UUID.randomUUID().toString(), request, llm, config,
            maxTurns.getAsInt(), onStatus, diagnostics, new AiCancellationScope());
    }

    public Result run(String runId, Request request, LlmProvider llm, AiRuntimeConfig config,
                      int maxTurns, Consumer<String> onStatus,
                      BiConsumer<String, String> diagnostics, AiCancellationScope cancellation) {
        return run(runId, request, llm, config, maxTurns, onStatus, diagnostics, cancellation, new AiRunControl());
    }

    public Result run(String runId, Request request, LlmProvider llm, AiRuntimeConfig config,
                      int maxTurns, Consumer<String> onStatus, BiConsumer<String, String> diagnostics,
                      AiCancellationScope cancellation, AiRunControl control) {
        AiRunEvents events = new AiRunEvents(runId, 0, event -> diagnostics.accept(
            "event_" + event.get("sequence").getAsLong() + ".json", event.toString()));
        return run(runId, request, llm, config, maxTurns, onStatus, diagnostics, cancellation, control, events);
    }

    public Result run(String runId, Request request, LlmProvider llm, AiRuntimeConfig config,
                      int maxTurns, Consumer<String> onStatus, BiConsumer<String, String> diagnostics,
                      AiCancellationScope cancellation, AiRunControl control, AiRunEvents events) {
        events.onProgress(onStatus);
        cancellation.throwIfCancelled();
        AiRunBudget budget = new AiRunBudget();
        cancellation.expireAfter(budget.seconds());
        AgentContext context = null;
        AiToolExecutor toolExecutor = null;
        int lastTurn = 0, toolCallCount = 0;
        List<TraceEntry> trace = new ArrayList<>();
        try {
            int boundedTurns = Math.max(2, Math.min(AiDefaults.AGENT_TURN_LIMIT, maxTurns));
            AgentToolRegistry toolRegistry = new AgentToolRegistry();
            context = prepareContext(request, events::progress, diagnostics, cancellation);
            if (request.resumeDraft() != null) context.drafts().replace(request.resumeDraft());
            if (request.resumeScope() != null) context.runState().setEditScope(request.resumeScope());
            if (request.resumeMemory() != null && !request.resumeMemory().isEmpty()) context.runState().taskMemory().restore(request.resumeMemory());
            toolExecutor = new AiToolExecutor(toolRegistry, context, cancellation, events);
            boolean nativeTools = "native".equals(config.toolCalling())
                || (!"json".equals(config.toolCalling()) && llm.supportsNativeTools(config));
            String manifest = buildManifest(context);
            String systemPrompt = AiPromptAssembler.build(resources.promptDirectory(), request.buildTutorial(), context.existingScene().available(),
                nativeTools ? "Function tools are declared in the request. Use their schemas directly." : toolRegistry.promptSpecification());
            systemPrompt += nativeTools ? "\nTransport for this run: native function tools.\n" : "\nTransport for this run: JSON compatibility protocol.\n";
            diagnostics.accept("effective_system_prompt.txt", systemPrompt);
            diagnostics.accept("manifest.json", manifest);
            List<LlmProvider.ToolDefinition> definitions = toolRegistry.all().stream()
                .map(tool -> new LlmProvider.ToolDefinition(tool.name(), tool.description(), tool.inputSchema())).toList();
            AiConversation conversation = new AiConversation(systemPrompt, manifest, definitions, budget, context, events,
                request.resumeDraft() != null || request.resumeMemory() != null);
            List<LlmProvider.Message> history = conversation.messages();
            String lastAction = "context preparation", lastProtocolError = "";
            diagnostics.accept("checkpoint.json", AiRunFiles.checkpoint(request, context, 0, budget));
            for (int turn = 1; turn <= boundedTurns; turn++) {
                lastTurn = turn;
                cancellation.throwIfCancelled();
                events.progress("Agent turn " + turn + "/" + boundedTurns);
                JsonObject turnData = new JsonObject(); turnData.addProperty("max_turns", boundedTurns);
                events.emit("turn_started", turn, turnData);
                context.runState().clarifications(control.instructions());
                conversation.beginTurn(turn, control.drain());
                diagnostics.accept("checkpoint.json", AiRunFiles.checkpoint(request, context, turn, budget));
                LlmProvider.Reply reply;
                boolean contextRetried = false;
                while (true) {
                    try {
                        reply = exchangeWithRetry(llm, systemPrompt, conversation.request(turn, boundedTurns, nativeTools),
                            definitions, config, nativeTools, budget, diagnostics, turn, cancellation, events);
                        break;
                    } catch (RuntimeException error) {
                        cancellation.throwIfCancelled();
                        Throwable cause = error;
                        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                        if (!(cause instanceof AiHttpException http) || !http.contextOverflow()) throw error;
                        if (contextRetried || !conversation.compact(true, turn))
                            throw new AiRunStoppedException("context_exhausted", "The service rejected the context after recovery; reduce input size or set the model context capacity correctly. Draft retained.");
                        contextRetried = true;
                        budget.contextRetried();
                        events.emit("context_retry", turn, new JsonObject());
                        diagnostics.accept("checkpoint.json", AiRunFiles.checkpoint(request, context, turn, budget));
                    }
                }
                cancellation.throwIfCancelled();
                history.add(reply.message());
                String response = reply.protocolText();
                diagnostics.accept("turn_" + turn + "_usage.json", GSON.toJson(reply.usage()));
                AiToolCalls.Parsed parsed = AiToolCalls.parse(reply, toolRegistry);
                if (parsed.error() != null) {
                    lastAction = "invalid model response on turn " + turn; lastProtocolError = parsed.error();
                    JsonObject error = protocolError(parsed.error());
                    for (var call : reply.message().calls()) history.add(LlmProvider.Message.tool(call.id(), call.name(), error, List.of()));
                    history.add(LlmProvider.Message.user(error.toString()));
                    trace.add(new TraceEntry(turn, response, List.of(), List.of(), parsed.error()));
                    JsonObject invalidTurn = new JsonObject(); invalidTurn.addProperty("protocol_error", parsed.error());
                    events.emit("turn_finished", turn, invalidTurn);
                    writeTrace(diagnostics, runId, trace);
                    budget.progress(context, List.of());
                    diagnostics.accept("checkpoint.json", AiRunFiles.checkpoint(request, context, turn, budget));
                    events.progress("Fixing scene draft");
                    continue;
                }
                if (parsed.editScope() != null) context.runState().setEditScope(parsed.editScope());
                lastAction = parsed.calls().stream().map(call -> call.name() + "[" + call.id() + "]").collect(Collectors.joining(", "));
                List<ToolResult> results = new ArrayList<>();
                List<LlmProvider.ContentBlock> images = new ArrayList<>();
                DslScene submittedScene = null;
                JsonObject beforeDraft = context.drafts().descriptor();
                for (AiToolExecutor.Completed completed : toolExecutor.execute(parsed.calls(), turn)) {
                    cancellation.throwIfCancelled();
                    ToolCall call = completed.call();
                    AgentTool.ToolExecution execution = completed.execution();
                    ToolResult result = new ToolResult(call.id(), call.name(), execution.result()); results.add(result);
                    if (!execution.result().has("ok") || execution.result().get("ok").getAsBoolean()) context.runState().evidence(call.name(), call.arguments());
                    JsonObject timing = new JsonObject(); timing.addProperty("tool", call.name());
                    timing.addProperty("call_id", call.id()); timing.addProperty("elapsed_ms", completed.elapsedMillis());
                    diagnostics.accept("turn_" + turn + "_tool_" + toolCallCount + ".json", timing.toString());
                    history.add(LlmProvider.Message.tool(call.id(), call.name(), execution.result(), List.of()));
                    images.addAll(execution.images());
                    if (execution.submittedScene() != null) submittedScene = execution.submittedScene();
                    toolCallCount++;
                    diagnostics.accept("checkpoint.json", AiRunFiles.checkpoint(request, context, turn, budget));
                    cancellation.throwIfCancelled();
                }
                if (!beforeDraft.equals(context.drafts().descriptor())) events.emit("draft_changed", turn, context.drafts().descriptor());
                if (submittedScene != null && !control.settle()) {
                    // A user addition accepted during the request must receive another model turn.
                    submittedScene = null;
                    ToolResult previous = results.get(0);
                    JsonObject deferred = previous.result().deepCopy(); deferred.addProperty("accepted", false);
                    deferred.addProperty("ok", false); deferred.addProperty("code", "USER_INPUT_PENDING");
                    deferred.addProperty("error", "New user instructions are queued; apply them before submitting again.");
                    results.set(0, new ToolResult(previous.id(), previous.name(), deferred));
                    history.set(history.size() - 1, LlmProvider.Message.tool(previous.id(), previous.name(), deferred, List.of()));
                    events.emit("submission_deferred", turn, deferred);
                }
                if (!images.isEmpty()) history.add(new LlmProvider.Message("user", images, List.of(), "", "", null));
                trace.add(new TraceEntry(turn, response, parsed.calls(), List.copyOf(results), null));
                JsonObject finishedTurn = new JsonObject(); finishedTurn.add("calls", GSON.toJsonTree(parsed.calls()));
                finishedTurn.add("results", GSON.toJsonTree(results));
                events.emit("turn_finished", turn, finishedTurn);
                writeTrace(diagnostics, runId, trace);
                if (context.runState().stopStatus() != null) {
                    if (control.settle()) throw new AiRunStoppedException(context.runState().stopStatus(), context.runState().stopReason());
                    context.runState().finish(null, null);
                }
                if (submittedScene != null) {
                    String finalJson = GSON_PRETTY.toJson(submittedScene);
                    diagnostics.accept("last_extracted_json_attempt_final.log", finalJson);
                    return new Result(runId, com.nododiiiii.ponderer.scene.SceneSnapshot.of(submittedScene), finalJson, turn, toolCallCount, context.runState().validationAttempts(), List.copyOf(trace));
                }
                budget.progress(context, results);
                if (!context.runState().lastErrors().isEmpty()) events.progress("Fixing scene draft");
            }
            throw exhaustedException(boundedTurns, toolCallCount, lastAction, lastProtocolError, context);
        } finally {
            control.close();
            if (toolExecutor != null) toolExecutor.close();
            cancellation.closeDeadline();
            JsonObject metrics = budget.summary(); metrics.addProperty("turns", lastTurn); metrics.addProperty("tool_calls", toolCallCount);
            diagnostics.accept("metrics.json", GSON_PRETTY.toJson(metrics));
            if (context != null) {
                context.runState().clarifications(control.instructions());
                diagnostics.accept("checkpoint.json", AiRunFiles.checkpoint(request, context, lastTurn, budget));
                diagnostics.accept("diff.json", AiRunFiles.diff(context.existingScene().available() ? context.existingScene().json() : new JsonObject(), context.drafts().scene()));
            }
        }
    }

    private static LlmProvider.Reply exchangeWithRetry(LlmProvider llm, String system, List<LlmProvider.Message> messages,
            List<LlmProvider.ToolDefinition> tools, AiRuntimeConfig config, boolean nativeTools, AiRunBudget budget,
            BiConsumer<String, String> diagnostics, int turn, AiCancellationScope cancellation, AiRunEvents events) {
        long estimate = budget.estimateRequest(system, messages, tools);
        for (int attempt = 0; ; attempt++) {
            cancellation.throwIfCancelled();
            int allowance = budget.outputAllowance(estimate, config.maxTokens());
            int timeout = (int) Math.max(1, Math.min(config.requestTimeoutSeconds(), budget.remainingMillis() / 1000));
            long requestStarted = System.nanoTime();
            JsonObject requestData = new JsonObject(); requestData.addProperty("attempt", attempt + 1);
            requestData.addProperty("estimated_input_tokens", estimate); requestData.addProperty("max_output_tokens", allowance);
            events.emit("request_started", turn, requestData);
            int requestAttempt = attempt + 1;
            try {
                var reply = cancellation.track(llm.exchange(new LlmProvider.Exchange(system, messages, tools,
                    config.withRequestTimeout(timeout), nativeTools, allowance,
                    update -> {
                        cancellation.throwIfCancelled();
                        events.output(turn, requestAttempt, update.assistantText(), nativeTools);
                    }), cancellation)).join();
                String assistantText = reply.message().content().stream().filter(LlmProvider.ContentBlock.Text.class::isInstance)
                    .map(block -> ((LlmProvider.ContentBlock.Text) block).text()).collect(Collectors.joining("\n"));
                events.output(turn, requestAttempt, assistantText, nativeTools);
                JsonObject timing = new JsonObject(); timing.addProperty("elapsed_ms", (System.nanoTime() - requestStarted) / 1_000_000);
                timing.addProperty("attempt", attempt + 1); diagnostics.accept("turn_" + turn + "_request.json", timing.toString());
                timing.add("usage", GSON.toJsonTree(reply.usage())); timing.addProperty("finish_reason", reply.finishReason());
                events.emit("response_received", turn, timing);
                budget.account(reply, estimate);
                if (reply.outputLimited()) throw new AiRunStoppedException("output_limit", AiApiSupport.outputLimitExceeded().getMessage());
                return reply;
            } catch (RuntimeException error) {
                cancellation.throwIfCancelled();
                Throwable cause = error;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                boolean retryable = cause instanceof AiHttpException http ? http.retryable()
                    : cause instanceof java.io.IOException;
                JsonObject failure = new JsonObject(); failure.addProperty("attempt", attempt + 1);
                failure.addProperty("retryable", retryable);
                failure.addProperty("context_overflow", cause instanceof AiHttpException http && http.contextOverflow());
                events.emit("request_failed", turn, failure);
                if (retryable) budget.failedAttempt(estimate, allowance, attempt < 2);
                if (!retryable || attempt >= 2) throw error;
                long delaySeconds = cause instanceof AiHttpException http ? Math.max(1L << attempt, http.retryAfterSeconds) : 1L << attempt;
                if (delaySeconds >= (budget.remainingMillis() + 999) / 1000) throw new AiRunStoppedException("budget_exhausted", "Retry-After exceeds the remaining AI time budget; draft retained.");
                diagnostics.accept("turn_" + turn + "_retry_" + (attempt + 1) + ".json", "{\"attempt\":" + (attempt + 1) + ",\"reason\":\"transient_transport_error\"}");
                JsonObject retry = new JsonObject(); retry.addProperty("attempt", attempt + 2); retry.addProperty("delay_seconds", delaySeconds);
                events.emit("request_retry", turn, retry);
                java.util.concurrent.CompletableFuture<Void> delay = new java.util.concurrent.CompletableFuture<>();
                java.util.concurrent.CompletableFuture.delayedExecutor(delaySeconds, java.util.concurrent.TimeUnit.SECONDS).execute(() -> delay.complete(null));
                cancellation.track(delay).join();
            }
        }
    }

    private AgentContext prepareContext(Request request, Consumer<String> onStatus,
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
                com.nododiiiii.ponderer.scene.StructureReferences.collect(existingScene.scene(), references);
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
                try (InputStream in = resources.builtin("basic")) {
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
                request.includeImages(), request.editMode()
            );
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare AI agent context: " + safeMessage(e), e);
        }
    }

    private AgentContext.StructureDocument inspectExistingStructure(String reference,
                                                                           @Nullable String pack) throws Exception {
        ResourceLocation id = ResourceLocation.tryParse(reference.contains(":") ? reference : "ponderer:" + reference);
        if (id == null) {
            throw new IllegalArgumentException("Invalid existing scene structure: " + reference);
        }
        String relative = "ponderer".equals(id.getNamespace())
            ? id.getPath() : id.getNamespace() + "/" + id.getPath();
        Path path = resources.resolve(relative, pack);
        if (path != null && Files.isRegularFile(path)) {
            return new AgentContext.StructureDocument(id.toString(), StructureDescriber.inspect(path));
        }
        if ("ponderer".equals(id.getNamespace())) {
            try (InputStream in = resources.builtin(id.getPath())) {
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
        manifest.addProperty("edit_mode", context.editMode().name().toLowerCase(Locale.ROOT));
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

    private static JsonObject protocolError(String message) {
        JsonObject error = new JsonObject();
        JsonObject detail = new JsonObject();
        detail.addProperty("message", message);
        detail.addProperty("retryable", true);
        error.add("protocol_error", detail);
        return error;
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
        return new AiRunStoppedException("budget_exhausted", message.toString());
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

    static String statusForTool(String name) {
        return switch (name) {
            case "query_structure" -> "Querying structure";
            case "lookup_registry", "inspect_registry_entries" -> "Resolving registry entries";
            case "get_step_schema" -> "Loading scene step schema";
            case "read_reference" -> "Reading reference material";
            case "finish_task" -> "Retaining scene draft";
            case "search_references" -> "Searching reference material";
            case "inspect_existing_scene" -> "Inspecting existing scene";
            case "get_scene_draft" -> "Inspecting scene draft";
            case "apply_scene_patch" -> "Applying scoped scene patch";
            case "update_task_state" -> "Retaining scene draft";
            case "review_scene" -> "Reviewing scene draft";
            case "submit_scene" -> "Validating scene submission";
            default -> "Running agent tool";
        };
    }

    private String toStructureId(Path path) {
        Path structureDir = resources.structureRoot();
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

}
