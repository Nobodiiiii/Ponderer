package com.nododiiiii.ponderer.ai;

import java.util.Locale;

/**
 * Resolved runtime settings for one LLM call chain.
 */
public record AiRuntimeConfig(
    String provider,
    String wireApi,
    String baseUrl,
    String apiKey,
    String model,
    int maxTokens,
    String source,
    String reasoningEffort,
    int requestTimeoutSeconds,
    String toolCalling
) {
    public AiRuntimeConfig {
        toolCalling = toolCalling == null ? "auto" : toolCalling;
        reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim().toLowerCase(Locale.ROOT);
    }

    public AiRuntimeConfig(String provider, String wireApi, String baseUrl, String apiKey, String model,
                           int maxTokens, String source, String reasoningEffort, int requestTimeoutSeconds) {
        this(provider, wireApi, baseUrl, apiKey, model, maxTokens, source, reasoningEffort, requestTimeoutSeconds, "auto");
    }

    public AiRuntimeConfig withToolCalling(String mode) {
        return new AiRuntimeConfig(provider, wireApi, baseUrl, apiKey, model, maxTokens, source, reasoningEffort, requestTimeoutSeconds, mode);
    }

    public AiRuntimeConfig(String provider, String wireApi, String baseUrl, String apiKey, String model,
                           int maxTokens, String source) {
        this(provider, wireApi, baseUrl, apiKey, model, maxTokens, source, "");
    }

    public AiRuntimeConfig(String provider, String wireApi, String baseUrl, String apiKey, String model,
                           int maxTokens, String source, String reasoningEffort) {
        this(provider, wireApi, baseUrl, apiKey, model, maxTokens, source, reasoningEffort,
            AiDefaults.REQUEST_TIMEOUT_SECONDS);
    }

    public AiRuntimeConfig withReasoningEffort(String effort) {
        return new AiRuntimeConfig(provider, wireApi, baseUrl, apiKey, model, maxTokens, source, effort,
            requestTimeoutSeconds, toolCalling);
    }

    public AiRuntimeConfig withRequestTimeout(int seconds) {
        return new AiRuntimeConfig(provider, wireApi, baseUrl, apiKey, model, maxTokens, source,
            reasoningEffort, seconds, toolCalling);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isAnthropic() {
        return "anthropic".equalsIgnoreCase(provider) || "anthropic".equalsIgnoreCase(wireApi);
    }

    public boolean isResponsesApi() {
        return "responses".equalsIgnoreCase(wireApi);
    }
}
