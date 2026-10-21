package com.nododiiiii.ponderer.ai;

import java.net.URI;
import java.util.Locale;

/** Protocol selection without assuming that every compatible gateway supports OpenAI extensions. */
final class AiApiSupport {
    private AiApiSupport() {
    }

    static boolean isOfficialOpenAi(String baseUrl) {
        return "api.openai.com".equalsIgnoreCase(URI.create(baseUrl).getHost());
    }

    static String chatTokenParameter(String baseUrl, String model) {
        String id = model.toLowerCase(Locale.ROOT);
        // Gateways also host these model families; their reasoning budgets require the newer field.
        boolean reasoningModel = id.startsWith("gpt-5") || id.startsWith("gpt-6")
            || id.matches("o[1-9](?:[-.].*)?");
        return isOfficialOpenAi(baseUrl) || reasoningModel ? "max_completion_tokens" : "max_tokens";
    }

    static RuntimeException outputLimitExceeded() {
        return new IllegalStateException("AI output was cut off by the token limit. Increase ai.maxTokens "
            + "within your model's output limit or lower reasoning effort; the budget includes reasoning tokens.");
    }
}
