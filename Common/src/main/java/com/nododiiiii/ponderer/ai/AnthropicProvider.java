package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.nododiiiii.ponderer.Config;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LLM provider for the Anthropic Messages API.
 * Endpoint: {base}/messages
 * Auth: x-api-key header
 * Response: text blocks in content (thinking blocks are skipped)
 */
public class AnthropicProvider implements LlmProvider {

    private final int maxTokens;
    private final String reasoningEffort;
    private final Duration requestTimeout;

    public AnthropicProvider() {
        this(Config.AI_MAX_TOKENS.get(), Config.AI_REASONING_EFFORT.get(), Config.AI_REQUEST_TIMEOUT_SECONDS.get());
    }

    public AnthropicProvider(int maxTokens) {
        this(maxTokens, "");
    }

    public AnthropicProvider(int maxTokens, String reasoningEffort) {
        this(maxTokens, reasoningEffort, AiDefaults.REQUEST_TIMEOUT_SECONDS);
    }

    public AnthropicProvider(int maxTokens, String reasoningEffort, int requestTimeoutSeconds) {
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    @Override
    public boolean supportsNativeTools(AiRuntimeConfig config) { return true; }

    @Override
    public CompletableFuture<Reply> exchange(Exchange request, AiCancellationScope cancellation) {
        return AiWireTransport.exchange("anthropic", request, cancellation);
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                               String baseUrl, String apiKey, String model) {
        return generateInternal(systemPrompt, userContent, baseUrl, apiKey, model, null);
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                               String baseUrl, String apiKey, String model,
                                               AiCancellationScope cancellation) {
        return generateInternal(systemPrompt, userContent, baseUrl, apiKey, model, cancellation);
    }

    private CompletableFuture<String> generateInternal(String systemPrompt, List<ContentBlock> userContent,
                                                       String baseUrl, String apiKey, String model,
                                                       AiCancellationScope cancellation) {
        if (cancellation != null) {
            cancellation.throwIfCancelled();
        }
        String requestedModel = model == null ? "" : model.trim();
        boolean oneMillionContext = requestedModel.toLowerCase(java.util.Locale.ROOT).endsWith("[1m]");
        String apiModel = oneMillionContext
            ? requestedModel.substring(0, requestedModel.length() - "[1m]".length()).trim()
            : requestedModel;
        JsonObject body = new JsonObject();
        body.addProperty("model", apiModel);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("system", systemPrompt);
        if (!reasoningEffort.isBlank()) {
            JsonObject outputConfig = new JsonObject();
            outputConfig.addProperty("effort", reasoningEffort);
            body.add("output_config", outputConfig);
        }

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        JsonArray content = new JsonArray();
        for (ContentBlock block : userContent) {
            if (block instanceof ContentBlock.Text t) {
                JsonObject textBlock = new JsonObject();
                textBlock.addProperty("type", "text");
                textBlock.addProperty("text", t.text());
                content.add(textBlock);
            } else if (block instanceof ContentBlock.Image img) {
                JsonObject imageBlock = new JsonObject();
                imageBlock.addProperty("type", "image");
                JsonObject source = new JsonObject();
                source.addProperty("type", "base64");
                source.addProperty("media_type", img.mediaType());
                source.addProperty("data", img.base64Data());
                imageBlock.add("source", source);
                content.add(imageBlock);
            }
        }
        userMsg.add("content", content);
        messages.add(userMsg);
        body.add("messages", messages);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/messages"))
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (oneMillionContext) {
            requestBuilder.header("anthropic-beta", "context-1m-2025-08-07");
        }
        if (apiKey.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            requestBuilder.header("Authorization", apiKey);
        } else {
            requestBuilder.header("x-api-key", apiKey);
        }
        HttpRequest request = requestBuilder.build();

        CompletableFuture<HttpResponse<String>> requestFuture =
            HttpClientFactory.get().sendAsync(request, HttpResponse.BodyHandlers.ofString());
        if (cancellation != null) {
            cancellation.track(requestFuture);
        }
        CompletableFuture<String> result = requestFuture
            .thenApply(response -> {
                String responseBody = response.body();
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Anthropic API error " + response.statusCode() + ": " + responseBody);
                }
                try {
                    if (responseBody == null || responseBody.isBlank()) {
                        throw new RuntimeException("Anthropic API returned empty response body");
                    }
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    String stopReason = json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
                        ? json.get("stop_reason").getAsString() : "unknown";
                    if ("max_tokens".equals(stopReason)) {
                        throw AiApiSupport.outputLimitExceeded();
                    }
                    if ("refusal".equals(stopReason)) {
                        throw new RuntimeException("Anthropic API refused the request");
                    }
                    JsonArray contentArr = json.getAsJsonArray("content");
                    if (contentArr == null || contentArr.isEmpty()) {
                        throw new RuntimeException("Anthropic API returned no content (stop_reason: " + stopReason
                            + "). Response: " + responseBody.substring(0, Math.min(500, responseBody.length())));
                    }
                    StringBuilder text = new StringBuilder();
                    for (JsonElement element : contentArr) {
                        if (!element.isJsonObject()) continue;
                        JsonObject block = element.getAsJsonObject();
                        if (block.has("type") && "text".equals(block.get("type").getAsString())
                            && block.has("text") && !block.get("text").isJsonNull()) {
                            text.append(block.get("text").getAsString());
                        }
                    }
                    if (text.toString().isBlank()) {
                        throw new RuntimeException("Anthropic API returned no text (stop_reason: " + stopReason + ")");
                    }
                    return text.toString();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse Anthropic response: " + e.getMessage()
                        + "\nResponse: " + responseBody.substring(0, Math.min(500, responseBody.length())), e);
                }
            });
        return cancellation == null ? result : cancellation.track(result);
    }
}
