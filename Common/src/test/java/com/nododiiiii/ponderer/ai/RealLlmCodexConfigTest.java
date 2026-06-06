package com.nododiiiii.ponderer.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealLlmCodexConfigTest {

    private static final String PROBE = "PONDERER_LLM_OK";

    @Test
    @EnabledIfSystemProperty(named = "ponderer.realLlm", matches = "true")
    void codexConfigCanCallRealLlm() throws Exception {
        AiRuntimeConfig config = CodexAiConfig.loadDefault(64).orElseThrow();
        assertTrue(config.hasApiKey(), "Codex config/auth must provide an API key");
        assertFalse(config.baseUrl().isBlank(), "Codex config must provide a base URL");
        assertFalse(config.model().isBlank(), "Codex config must provide a model");

        LlmProvider provider = AiProviderFactory.create(config);
        String response = provider.generate(
                "You are a connectivity probe. Reply with exactly " + PROBE + " and no other text.",
                List.of(new LlmProvider.ContentBlock.Text("Return exactly " + PROBE)),
                config.baseUrl(),
                config.apiKey(),
                config.model())
            .get(90, TimeUnit.SECONDS);

        assertTrue(response.trim().contains(PROBE),
            () -> "Expected probe marker in real LLM response, got: " + response);
    }
}
