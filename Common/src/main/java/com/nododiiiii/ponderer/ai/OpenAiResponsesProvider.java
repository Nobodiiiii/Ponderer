package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provider for OpenAI Responses-compatible APIs.
 */
public class OpenAiResponsesProvider implements LlmProvider {

    private final int maxTokens;
    private final String reasoningEffort;
    private final Duration requestTimeout;

    public OpenAiResponsesProvider(int maxTokens) {
        this(maxTokens, "");
    }

    public OpenAiResponsesProvider(int maxTokens, String reasoningEffort) {
        this(maxTokens, reasoningEffort, AiDefaults.REQUEST_TIMEOUT_SECONDS);
    }

    public OpenAiResponsesProvider(int maxTokens, String reasoningEffort, int requestTimeoutSeconds) {
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
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
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("instructions", systemPrompt);
        body.addProperty("max_output_tokens", maxTokens);
        // Each turn already carries the transcript; no server-side conversation storage is needed.
        body.addProperty("store", false);
        if (!reasoningEffort.isBlank()) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", reasoningEffort);
            body.add("reasoning", reasoning);
        }

        JsonArray input = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        JsonArray content = new JsonArray();
        for (ContentBlock block : userContent) {
            if (block instanceof ContentBlock.Text text) {
                JsonObject part = new JsonObject();
                part.addProperty("type", "input_text");
                part.addProperty("text", text.text());
                content.add(part);
            } else if (block instanceof ContentBlock.Image image) {
                JsonObject part = new JsonObject();
                part.addProperty("type", "input_image");
                part.addProperty("image_url", "data:" + image.mediaType() + ";base64," + image.base64Data());
                content.add(part);
            }
        }
        userMessage.add("content", content);
        input.add(userMessage);
        body.add("input", input);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint(baseUrl)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        CompletableFuture<HttpResponse<String>> requestFuture =
            HttpClientFactory.get().sendAsync(request, HttpResponse.BodyHandlers.ofString());
        if (cancellation != null) {
            cancellation.track(requestFuture);
        }
        CompletableFuture<String> result = requestFuture
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("Responses API error " + response.statusCode() + ": " + response.body());
                }
                try {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String status = json.has("status") && !json.get("status").isJsonNull()
                        ? json.get("status").getAsString() : "completed";
                    if ("incomplete".equals(status)) {
                        JsonObject details = json.getAsJsonObject("incomplete_details");
                        if (details != null && details.has("reason")
                            && "max_output_tokens".equals(details.get("reason").getAsString())) {
                            throw AiApiSupport.outputLimitExceeded();
                        }
                        throw new RuntimeException("Responses API returned incomplete output: " + details);
                    }
                    if (!"completed".equals(status)) {
                        throw new RuntimeException("Responses API did not complete (status: " + status
                            + ", error: " + json.get("error") + ")");
                    }
                    if (json.has("output_text") && !json.get("output_text").isJsonNull()) {
                        String text = json.get("output_text").getAsString();
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                    String text = extractOutputText(json);
                    if (text.isBlank()) {
                        throw new RuntimeException("Responses API returned no output text");
                    }
                    return text;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    String bodyText = response.body();
                    throw new RuntimeException("Failed to parse Responses API response: " + e.getMessage()
                        + "\nResponse: " + bodyText.substring(0, Math.min(500, bodyText.length())), e);
                }
            });
        return cancellation == null ? result : cancellation.track(result);
    }

    private static String endpoint(String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/responses";
    }

    private static String extractOutputText(JsonObject json) {
        StringBuilder out = new StringBuilder();
        JsonArray output = json.getAsJsonArray("output");
        if (output == null) {
            return "";
        }
        for (JsonElement outputElement : output) {
            if (!outputElement.isJsonObject()) {
                continue;
            }
            JsonArray content = outputElement.getAsJsonObject().getAsJsonArray("content");
            if (content == null) {
                continue;
            }
            for (JsonElement contentElement : content) {
                if (!contentElement.isJsonObject()) {
                    continue;
                }
                JsonObject part = contentElement.getAsJsonObject();
                if (part.has("type") && "refusal".equals(part.get("type").getAsString())) {
                    throw new RuntimeException("Responses API refused the request: " + part.get("refusal"));
                }
                if (part.has("type") && !"output_text".equals(part.get("type").getAsString())) {
                    continue;
                }
                if (part.has("text") && !part.get("text").isJsonNull()) {
                    if (!out.isEmpty()) {
                        out.append('\n');
                    }
                    out.append(part.get("text").getAsString());
                }
            }
        }
        return out.toString();
    }
}
