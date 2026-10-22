package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.Config;

/**
 * Resolves Ponderer's AI config from the selected source.
 */
public final class AiRuntimeConfigResolver {

    private AiRuntimeConfigResolver() {
    }

    public static AiRuntimeConfig resolve() {
        int maxTokens = Config.AI_MAX_TOKENS.get();
        String source = Config.AI_CONFIG_SOURCE.get().trim();
        AiRuntimeConfig config;
        if ("codex".equalsIgnoreCase(source)) {
            config = CodexAiConfig.loadDefault(maxTokens).orElseGet(() -> emptyConfig(maxTokens, "codex-empty"));
        } else if ("claude_code".equalsIgnoreCase(source)) {
            config = ClaudeCodeAiConfig.loadDefault(maxTokens).orElseGet(() -> emptyConfig(maxTokens, "claude-code-empty"));
        } else {
            config = customConfig(maxTokens);
        }

        String effort = Config.AI_REASONING_EFFORT.get().trim();
        config = config.withRequestTimeout(Config.AI_REQUEST_TIMEOUT_SECONDS.get()).withToolCalling(Config.AI_TOOL_CALLING.get());
        return effort.isEmpty() ? config : config.withReasoningEffort(effort);
    }

    private static AiRuntimeConfig customConfig(int maxTokens) {
        String wireApi = Config.getEffectiveWireApi();
        return new AiRuntimeConfig(
            "anthropic".equals(wireApi) ? "anthropic" : "openai",
            wireApi,
            Config.getEffectiveBaseUrl(),
            Config.AI_API_KEY.get().trim(),
            Config.getEffectiveModel(),
            maxTokens,
            "ponderer"
        );
    }

    private static AiRuntimeConfig emptyConfig(int maxTokens, String source) {
        String wireApi = Config.getEffectiveWireApi();
        return new AiRuntimeConfig(
            "anthropic".equals(wireApi) ? "anthropic" : "openai",
            wireApi,
            Config.getEffectiveBaseUrl(),
            "",
            Config.getEffectiveModel(),
            maxTokens,
            source
        );
    }
}
