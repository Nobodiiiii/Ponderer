package com.nododiiiii.ponderer.ai;

public final class AiProviderFactory {

    private AiProviderFactory() {
    }

    public static LlmProvider create(AiRuntimeConfig config) {
        if (config.isAnthropic()) {
            return new AnthropicProvider(config.maxTokens(), config.reasoningEffort(), config.requestTimeoutSeconds());
        }
        if (config.isResponsesApi()) {
            return new OpenAiResponsesProvider(config.maxTokens(), config.reasoningEffort(), config.requestTimeoutSeconds());
        }
        return new OpenAiCompatProvider(config.maxTokens(), config.reasoningEffort(), config.requestTimeoutSeconds());
    }
}
