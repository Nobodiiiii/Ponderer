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
        if ("codex".equalsIgnoreCase(source)) {
            return CodexAiConfig.loadDefault(maxTokens).orElseGet(() -> emptyConfig(maxTokens, "codex-empty"));
        }
        if ("claude_code".equalsIgnoreCase(source)) {
            return ClaudeCodeAiConfig.loadDefault(maxTokens).orElseGet(() -> emptyConfig(maxTokens, "claude-code-empty"));
        }

        return customConfig(maxTokens);
    }

    private static AiRuntimeConfig customConfig(int maxTokens) {
        return new AiRuntimeConfig(
            Config.AI_PROVIDER.get().trim(),
            "anthropic".equalsIgnoreCase(Config.AI_PROVIDER.get()) ? "anthropic" : "chat",
            Config.getEffectiveBaseUrl(),
            Config.AI_API_KEY.get().trim(),
            Config.getEffectiveModel(),
            maxTokens,
            "ponderer"
        );
    }

    private static AiRuntimeConfig emptyConfig(int maxTokens, String source) {
        return new AiRuntimeConfig(
            Config.AI_PROVIDER.get().trim(),
            "anthropic".equalsIgnoreCase(Config.AI_PROVIDER.get()) ? "anthropic" : "chat",
            Config.getEffectiveBaseUrl(),
            "",
            Config.getEffectiveModel(),
            maxTokens,
            source
        );
    }
}
