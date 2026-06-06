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

    public OpenAiResponsesProvider(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                               String baseUrl, String apiKey, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("instructions", systemPrompt);
        body.addProperty("max_output_tokens", maxTokens);

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
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return HttpClientFactory.get().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("Responses API error " + response.statusCode() + ": " + response.body());
                }
                try {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
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
    }

    private static String endpoint(String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base.endsWith("/v1") ? base + "/responses" : base + "/v1/responses";
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
