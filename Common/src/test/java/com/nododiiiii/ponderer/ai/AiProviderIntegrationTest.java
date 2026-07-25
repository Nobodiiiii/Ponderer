package com.nododiiiii.ponderer.ai;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AiProviderIntegrationTest {

    @Test
    @Timeout(150)
    void reachesConfiguredProviderWhenExplicitlyEnabled() throws Exception {
        Assumptions.assumeTrue(
            "true".equalsIgnoreCase(System.getenv("PONDERER_AI_INTEGRATION")),
            "Set PONDERER_AI_INTEGRATION=true to run the real provider check");
        AiRuntimeConfig config = ClaudeCodeAiConfig.loadDefault(64).orElseThrow();
        Assumptions.assumeTrue(config.hasApiKey(), "No Claude Code API credential is configured");

        AiCancellationScope cancellation = new AiCancellationScope();
        String response = AiProviderFactory.create(config)
            .generate(
                "Reply with the single word OK.",
                List.of(new LlmProvider.ContentBlock.Text("Connectivity check")),
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                cancellation)
            .get(120, TimeUnit.SECONDS);

        assertFalse(response.isBlank());
    }
}
