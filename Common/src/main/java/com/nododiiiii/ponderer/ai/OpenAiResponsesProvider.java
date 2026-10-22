package com.nododiiiii.ponderer.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Adapter for responses; both public APIs share one wire implementation. */
public class OpenAiResponsesProvider implements LlmProvider {
    private final int maxTokens;
    private final String reasoningEffort;
    private final int requestTimeout;


    public OpenAiResponsesProvider(int maxTokens) { this(maxTokens, ""); }
    public OpenAiResponsesProvider(int maxTokens, String effort) { this(maxTokens, effort, AiDefaults.REQUEST_TIMEOUT_SECONDS); }
    public OpenAiResponsesProvider(int maxTokens, String effort, int timeout) {
        this.maxTokens = maxTokens; this.reasoningEffort = effort; this.requestTimeout = timeout;
    }

    @Override public boolean supportsNativeTools(AiRuntimeConfig config) { return true; }
    @Override public CompletableFuture<Reply> exchange(Exchange request, AiCancellationScope cancellation) {
        return AiWireTransport.exchange("responses", request, cancellation);
    }

    @Override public CompletableFuture<String> generate(String system, List<ContentBlock> content, String url, String key, String model) {
        return generate(system, content, url, key, model, new AiCancellationScope());
    }

    @Override public CompletableFuture<String> generate(String system, List<ContentBlock> content, String url, String key, String model, AiCancellationScope cancellation) {
        AiRuntimeConfig config = new AiRuntimeConfig("openai", "responses", url, key, model, maxTokens, "direct", reasoningEffort, requestTimeout);
        return exchange(new Exchange(system, List.of(new Message("user", content, List.of(), "", "", null)), List.of(), config, false, maxTokens), cancellation)
            .thenApply(Reply::completeText);
    }
}
