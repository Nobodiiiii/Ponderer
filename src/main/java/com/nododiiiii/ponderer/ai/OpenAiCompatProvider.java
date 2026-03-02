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
 * LLM provider for OpenAI-compatible Chat Completions API.
 * Covers: OpenAI, DeepSeek, Groq, Together, Ollama, LM Studio, etc.
 * Endpoint: {base}/v1/chat/completions
 * Auth: Authorization Bearer header
 * Response: choices[0].message.content
 */
public class OpenAiCompatProvider implements LlmProvider {

    @Override
    public CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                               String baseUrl, String apiKey, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", Config.AI_MAX_TOKENS.get());

        JsonArray messages = new JsonArray();

        // System message
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");

        // Check if we have images - if so, use content array format
        boolean hasImages = userContent.stream().anyMatch(b -> b instanceof ContentBlock.Image);

        if (hasImages) {
            JsonArray content = new JsonArray();
            for (ContentBlock block : userContent) {
                if (block instanceof ContentBlock.Text t) {
                    JsonObject textPart = new JsonObject();
                    textPart.addProperty("type", "text");
                    textPart.addProperty("text", t.text());
                    content.add(textPart);
                } else if (block instanceof ContentBlock.Image img) {
                    JsonObject imagePart = new JsonObject();
                    imagePart.addProperty("type", "image_url");
                    JsonObject imageUrl = new JsonObject();
                    imageUrl.addProperty("url", "data:" + img.mediaType() + ";base64," + img.base64Data());
                    imagePart.add("image_url", imageUrl);
                    content.add(imagePart);
                }
            }
            userMsg.add("content", content);
        } else {
            // Text-only: use simple string content
            StringBuilder sb = new StringBuilder();
            for (ContentBlock block : userContent) {
                if (block instanceof ContentBlock.Text t) {
                    if (!sb.isEmpty()) sb.append("\n\n");
                    sb.append(t.text());
                }
            }
            userMsg.addProperty("content", sb.toString());
        }

        messages.add(userMsg);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return HttpClientFactory.get().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new RuntimeException("OpenAI API error " + response.statusCode() + ": " + response.body());
                }
                try {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray choices = json.getAsJsonArray("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw new RuntimeException("API returned no choices. Response: "
                            + response.body().substring(0, Math.min(500, response.body().length())));
                    }
                    JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                    if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
                        // content can be null when the model refuses or hits a content filter
                        String finishReason = choices.get(0).getAsJsonObject().has("finish_reason")
                            ? choices.get(0).getAsJsonObject().get("finish_reason").getAsString() : "unknown";
                        throw new RuntimeException("API returned empty content (finish_reason: " + finishReason + ")");
                    }
                    return message.get("content").getAsString();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse API response: " + e.getMessage()
                        + "\nResponse: " + response.body().substring(0, Math.min(500, response.body().length())), e);
                }
            });
    }
}
