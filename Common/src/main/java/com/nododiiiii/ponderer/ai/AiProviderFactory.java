package com.nododiiiii.ponderer.ai;

public final class AiProviderFactory {

    private AiProviderFactory() {
    }

    public static LlmProvider create(AiRuntimeConfig config) {
        if (config.isAnthropic()) {
            return new AnthropicProvider(config.maxTokens());
        }
        if (config.isResponsesApi()) {
            return new OpenAiResponsesProvider(config.maxTokens());
        }
        return new OpenAiCompatProvider(config.maxTokens());
    }
}
