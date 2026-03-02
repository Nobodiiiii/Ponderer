package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonArray;
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
 * Endpoint: {base}/v1/messages
 * Auth: x-api-key header
 * Response: content[0].text
 */
public class AnthropicProvider implements LlmProvider {

    @Override
    public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                               String baseUrl, String apiKey, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", Config.AI_MAX_TOKENS.get());
        body.addProperty("system", systemPrompt);

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

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return HttpClientFactory.get().sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
                    JsonArray contentArr = json.getAsJsonArray("content");
                    if (contentArr == null || contentArr.isEmpty()) {
                        String stopReason = json.has("stop_reason") && !json.get("stop_reason").isJsonNull()
                            ? json.get("stop_reason").getAsString() : "unknown";
                        throw new RuntimeException("Anthropic API returned no content (stop_reason: " + stopReason
                            + "). Response: " + responseBody.substring(0, Math.min(500, responseBody.length())));
                    }
                    JsonObject firstBlock = contentArr.get(0).getAsJsonObject();
                    if (!firstBlock.has("text") || firstBlock.get("text").isJsonNull()) {
                        throw new RuntimeException("Anthropic API content block has no text. Response: "
                            + responseBody.substring(0, Math.min(500, responseBody.length())));
                    }
                    return firstBlock.get("text").getAsString();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse Anthropic response: " + e.getMessage()
                        + "\nResponse: " + responseBody.substring(0, Math.min(500, responseBody.length())), e);
                }
            });
    }
}
