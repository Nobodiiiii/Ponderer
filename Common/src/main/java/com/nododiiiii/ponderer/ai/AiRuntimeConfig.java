package com.nododiiiii.ponderer.ai;

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
    String source
) {
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
