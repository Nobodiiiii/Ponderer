package com.nododiiiii.ponderer.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over different LLM API providers.
 * Two implementations: Anthropic Messages API and OpenAI-compatible Chat Completions API.
 */
public interface LlmProvider {

    /**
     * A content block in the user message - either text or a base64 image.
     */
    sealed interface ContentBlock permits ContentBlock.Text, ContentBlock.Image {
        record Text(String text) implements ContentBlock {}
        record Image(String base64Data, String mediaType) implements ContentBlock {}
    }

    /**
     * Send a generation request to the LLM.
     *
     * @param systemPrompt the system prompt
     * @param userContent  list of content blocks (text and images)
     * @param baseUrl      API base URL
     * @param apiKey       API key
     * @param model        model name
     * @return future that resolves to the assistant's text response
     */
    CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                        String baseUrl, String apiKey, String model);
}
