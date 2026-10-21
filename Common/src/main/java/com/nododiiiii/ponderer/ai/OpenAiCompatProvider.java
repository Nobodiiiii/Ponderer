package com.nododiiiii.ponderer.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Adapter for chat; both public APIs share one wire implementation. */
public class OpenAiCompatProvider implements LlmProvider {
    private final int maxTokens;
    private final String reasoningEffort;
    private final int requestTimeout;

    public OpenAiCompatProvider() {
        this(com.nododiiiii.ponderer.Config.AI_MAX_TOKENS.get(), com.nododiiiii.ponderer.Config.AI_REASONING_EFFORT.get(), com.nododiiiii.ponderer.Config.AI_REQUEST_TIMEOUT_SECONDS.get());
    }

    public OpenAiCompatProvider(int maxTokens) { this(maxTokens, ""); }
    public OpenAiCompatProvider(int maxTokens, String effort) { this(maxTokens, effort, AiDefaults.REQUEST_TIMEOUT_SECONDS); }
    public OpenAiCompatProvider(int maxTokens, String effort, int timeout) {
        this.maxTokens = maxTokens; this.reasoningEffort = effort; this.requestTimeout = timeout;
    }

    @Override public boolean supportsNativeTools(AiRuntimeConfig config) { return AiApiSupport.isOfficialOpenAi(config.baseUrl()); }
    @Override public CompletableFuture<Reply> exchange(Exchange request, AiCancellationScope cancellation) {
        return AiWireTransport.exchange("chat", request, cancellation);
    }

    @Override public CompletableFuture<String> generate(String system, List<ContentBlock> content, String url, String key, String model) {
        return generate(system, content, url, key, model, new AiCancellationScope());
    }

    @Override public CompletableFuture<String> generate(String system, List<ContentBlock> content, String url, String key, String model, AiCancellationScope cancellation) {
        AiRuntimeConfig config = new AiRuntimeConfig("openai", "chat", url, key, model, maxTokens, "direct", reasoningEffort, requestTimeout);
        return exchange(new Exchange(system, List.of(new Message("user", content, List.of(), "", "", null)), List.of(), config, false, maxTokens), cancellation)
            .thenApply(Reply::completeText);
    }
}
