package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.Config;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Host accounting. Unknown usage is explicitly estimated rather than reported as zero. */
final class AiRunBudget {
    private final long started = System.nanoTime();
    private final int seconds = setting(() -> Config.AI_MAX_RUN_SECONDS.get(), 900);
    private final long total = setting(() -> Config.AI_MAX_RUN_TOKENS.get(), 524288);
    private final int context = setting(() -> Config.AI_CONTEXT_TOKENS.get(), 131072);
    private final int stallLimit = setting(() -> Config.AI_NO_PROGRESS_TURNS.get(), 4);
    private long input, output, cached;
    private int stalls, retries, failures, compactions;
    private boolean estimated;
    private double inputCalibration = 1.0;
    private String lastDraft = "";
    private final Set<String> facts = new HashSet<>();

    int seconds() { return seconds; }
    int contextTokens() { return context; }
    long remaining() { return Math.max(0, total - input - output); }
    long remainingMillis() { return Math.max(0, seconds * 1000L - (System.nanoTime() - started) / 1_000_000); }

    int outputAllowance(long requestTokens, int requested) {
        if (context - requestTokens < 512) throw new AiRunStoppedException("context_exhausted", "AI request exceeds the configured context capacity after compaction; reduce input size or adjust the model capacity.");
        long available = Math.min(remaining() - requestTokens, context - requestTokens);
        if (available < 512) throw new AiRunStoppedException("budget_exhausted", "AI token budget exhausted; the draft is retained for recovery.");
        return (int) Math.min(requested, available);
    }

    void account(LlmProvider.Reply reply, long requestEstimate) {
        var usage = reply.usage();
        if (usage.inputTokens() > 0 && requestEstimate > 0) {
            // Only increase the safety margin; a later request may contain different languages/images.
            inputCalibration = Math.max(inputCalibration, Math.min(16, usage.inputTokens() * 1.15 / requestEstimate * inputCalibration));
        }
        input += usage.inputTokens() < 0 ? requestEstimate : usage.inputTokens();
        output += usage.outputTokens() < 0 ? Math.max(1, tokens(reply.protocolText())) : usage.outputTokens();
        cached += Math.max(0, usage.cachedTokens());
        estimated |= usage.inputTokens() < 0 || usage.outputTokens() < 0;
    }

    void failedAttempt(long requestEstimate, int reservedOutput, boolean retrying) {
        // A lost HTTP reply may still have been billed. Account for the reserved ceiling.
        input += requestEstimate; output += reservedOutput; estimated = true; failures++;
        if (retrying) retries++;
    }

    void compacted() { compactions++; }

    void progress(AgentContext host, List<AiSceneAgentPipeline.ToolResult> results) {
        boolean progress = !lastDraft.equals(host.drafts().scene().toString());
        lastDraft = host.drafts().scene().toString();
        for (var result : results) {
            JsonObject value = result.result();
            if (!value.has("ok") || value.get("ok").getAsBoolean()) {
                if (!value.has("errors") || value.getAsJsonArray("errors").isEmpty()) {
                    progress |= facts.add(result.name() + ":" + value);
                }
            }
        }
        stalls = progress ? 0 : stalls + 1;
        if (stalls >= stallLimit) throw new AiRunStoppedException("no_progress", "AI stopped after repeated turns without new evidence or draft changes. The draft is retained.");
    }

    JsonObject summary() {
        JsonObject value = new JsonObject();
        value.addProperty("input_tokens", input); value.addProperty("output_tokens", output);
        value.addProperty("cached_tokens", cached); value.addProperty("usage_contains_estimates", estimated);
        value.addProperty("remaining_tokens", remaining()); value.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
        value.addProperty("max_run_seconds", seconds); value.addProperty("context_tokens", context);
        value.addProperty("no_progress_turns", stalls); value.addProperty("retries", retries); value.addProperty("compactions", compactions);
        value.addProperty("transport_failures", failures);
        value.addProperty("context_accounting", "text_heuristic_with_usage_safety_margin_plus_image_allowance");
        value.addProperty("input_safety_margin", inputCalibration);
        return value;
    }

    static long estimate(String system, List<LlmProvider.Message> messages, List<LlmProvider.ToolDefinition> tools) {
        long characters = tokens(system); long images = 0;
        for (var tool : tools) characters += tokens(tool.name()) + tokens(tool.description()) + tokens(tool.schema().toString()) + 32;
        for (var message : messages) {
            if (message.replay() != null) characters += tokens(message.replay().toString());
            else {
                for (var block : message.content()) {
                    if (block instanceof LlmProvider.ContentBlock.Text text) characters += tokens(text.text());
                    else images += 4096;
                }
                for (var call : message.calls()) characters += tokens(call.arguments().toString()) + tokens(call.name()) + 32;
            }
            characters += 32;
        }
        return characters + images;
    }

    long estimateRequest(String system, List<LlmProvider.Message> messages, List<LlmProvider.ToolDefinition> tools) {
        return (long) Math.ceil(estimate(system, messages, tools) * inputCalibration);
    }

    private static long tokens(String text) {
        long ascii = 0, unicode = 0;
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset); offset += Character.charCount(point);
            if (point < 128) ascii++; else unicode += point > 0xFFFF ? 4 : 2;
        }
        return (ascii + 2) / 3 + unicode;
    }

    private static int setting(java.util.function.IntSupplier value, int fallback) {
        try { return value.getAsInt(); } catch (RuntimeException | LinkageError unavailable) { return fallback; }
    }
}
