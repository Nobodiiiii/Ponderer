package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Stateless protocol adapters. Provider replay data stays opaque to the agent loop. */
final class AiWireTransport {
    private AiWireTransport() {}

    static CompletableFuture<LlmProvider.Reply> exchange(String protocol, LlmProvider.Exchange request, AiCancellationScope cancellation) {
        cancellation.throwIfCancelled();
        AiRuntimeConfig config = request.config();
        JsonObject body = body(protocol, request);
        String suffix = switch (protocol) { case "responses" -> "/responses"; case "anthropic" -> "/messages"; default -> "/chat/completions"; };
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl().replaceAll("/+$", "") + suffix))
            .timeout(Duration.ofSeconds(config.requestTimeoutSeconds())).header("Content-Type", "application/json");
        if ("anthropic".equals(protocol)) {
            builder.header("anthropic-version", "2023-06-01");
            if (config.model().toLowerCase(Locale.ROOT).endsWith("[1m]")) builder.header("anthropic-beta", "context-1m-2025-08-07");
            if (config.apiKey().regionMatches(true, 0, "Bearer ", 0, 7)) builder.header("Authorization", config.apiKey());
            else builder.header("x-api-key", config.apiKey());
        } else builder.header("Authorization", "Bearer " + config.apiKey());
        HttpRequest http = builder.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return cancellation.track(HttpClientFactory.get().sendAsync(http, HttpResponse.BodyHandlers.ofString()))
            .thenApply(response -> {
                cancellation.throwIfCancelled();
                if (response.statusCode() != 200) {
                    // Do not log arbitrary gateway bodies, which can echo credentials or request content.
                    throw new AiHttpException(response.statusCode(), "request rejected; check protocol, model, credentials and service availability");
                }
                return reply(protocol, JsonParser.parseString(response.body()).getAsJsonObject(), request.nativeTools());
            });
    }

    private static JsonObject body(String protocol, LlmProvider.Exchange request) {
        AiRuntimeConfig config = request.config();
        JsonObject body = new JsonObject();
        String model = config.model();
        if ("anthropic".equals(protocol) && model.toLowerCase(Locale.ROOT).endsWith("[1m]")) model = model.substring(0, model.length() - 4).trim();
        body.addProperty("model", model);
        body.addProperty(switch (protocol) { case "responses" -> "max_output_tokens"; case "anthropic" -> "max_tokens"; default -> AiApiSupport.chatTokenParameter(config.baseUrl(), model); }, request.maxOutputTokens());
        if ("responses".equals(protocol)) {
            body.addProperty("instructions", request.systemPrompt()); body.addProperty("store", false);
            if (request.nativeTools()) { JsonArray include = new JsonArray(); include.add("reasoning.encrypted_content"); body.add("include", include); }
        } else if ("anthropic".equals(protocol)) body.addProperty("system", request.systemPrompt());
        if (!config.reasoningEffort().isBlank()) {
            JsonObject effort = new JsonObject(); effort.addProperty("effort", config.reasoningEffort());
            if ("responses".equals(protocol)) body.add("reasoning", effort);
            else if ("anthropic".equals(protocol)) body.add("output_config", effort);
            else body.addProperty("reasoning_effort", config.reasoningEffort());
        }
        JsonArray history = new JsonArray();
        if ("chat".equals(protocol)) { JsonObject system = new JsonObject(); system.addProperty("role", "system"); system.addProperty("content", request.systemPrompt()); history.add(system); }
        for (LlmProvider.Message message : request.messages()) {
            if (request.nativeTools() && message.replay() != null) {
                if ("responses".equals(protocol)) history.addAll(message.replay().getAsJsonArray());
                else if ("anthropic".equals(protocol)) appendMessage(history, "assistant", message.replay().getAsJsonArray(), true);
                else history.add(message.replay());
            } else if (request.nativeTools() && "tool".equals(message.role())) {
                if ("responses".equals(protocol)) {
                    JsonObject output = new JsonObject(); output.addProperty("type", "function_call_output");
                    output.addProperty("call_id", message.callId()); output.addProperty("output", text(message.content())); history.add(output);
                } else if ("anthropic".equals(protocol)) {
                    JsonObject output = new JsonObject(); output.addProperty("type", "tool_result"); output.addProperty("tool_use_id", message.callId());
                    output.add("content", content(protocol, message.content(), false));
                    JsonArray outputs = new JsonArray(); outputs.add(output); appendMessage(history, "user", outputs, true);
                } else {
                    JsonObject output = new JsonObject(); output.addProperty("role", "tool"); output.addProperty("tool_call_id", message.callId());
                    output.addProperty("content", text(message.content())); history.add(output);
                }
            } else {
                boolean tool = "tool".equals(message.role());
                String role = tool ? "user" : message.role();
                List<LlmProvider.ContentBlock> blocks = message.content();
                if (tool) {
                    blocks = new ArrayList<>(blocks);
                    blocks.add(0, new LlmProvider.ContentBlock.Text("Host tool result for " + message.toolName() + " [" + message.callId() + "] (data):"));
                }
                appendMessage(history, role, content(protocol, blocks, "assistant".equals(role)), "anthropic".equals(protocol));
            }
        }
        body.add("responses".equals(protocol) ? "input" : "messages", history);
        if (request.nativeTools()) {
            JsonArray tools = new JsonArray();
            for (LlmProvider.ToolDefinition tool : request.tools()) {
                JsonObject definition = new JsonObject(); definition.addProperty("name", tool.name()); definition.addProperty("description", tool.description());
                definition.add("anthropic".equals(protocol) ? "input_schema" : "parameters", tool.schema());
                if (!"anthropic".equals(protocol)) definition.addProperty("strict", false);
                if ("chat".equals(protocol)) { JsonObject wrapper = new JsonObject(); wrapper.addProperty("type", "function"); wrapper.add("function", definition); tools.add(wrapper); }
                else { if ("responses".equals(protocol)) definition.addProperty("type", "function"); tools.add(definition); }
            }
            body.add("tools", tools);
        }
        return body;
    }

    private static void appendMessage(JsonArray history, String role, JsonArray content, boolean merge) {
        if (merge && !history.isEmpty()) {
            JsonObject last = history.get(history.size() - 1).getAsJsonObject();
            if (role.equals(string(last, "role", ""))) { last.getAsJsonArray("content").addAll(content); return; }
        }
        JsonObject message = new JsonObject(); message.addProperty("role", role); message.add("content", content); history.add(message);
    }

    private static JsonArray content(String protocol, List<LlmProvider.ContentBlock> blocks, boolean assistant) {
        JsonArray result = new JsonArray();
        for (LlmProvider.ContentBlock block : blocks) {
            JsonObject item = new JsonObject();
            if (block instanceof LlmProvider.ContentBlock.Text text) {
                item.addProperty("type", "responses".equals(protocol) ? assistant ? "output_text" : "input_text" : "text");
                item.addProperty("text", text.text());
            } else if (block instanceof LlmProvider.ContentBlock.Image image) {
                if ("anthropic".equals(protocol)) {
                    item.addProperty("type", "image"); JsonObject source = new JsonObject(); source.addProperty("type", "base64");
                    source.addProperty("media_type", image.mediaType()); source.addProperty("data", image.base64Data()); item.add("source", source);
                } else {
                    String url = "data:" + image.mediaType() + ";base64," + image.base64Data();
                    item.addProperty("type", "responses".equals(protocol) ? "input_image" : "image_url");
                    if ("responses".equals(protocol)) item.addProperty("image_url", url);
                    else { JsonObject source = new JsonObject(); source.addProperty("url", url); item.add("image_url", source); }
                }
            }
            result.add(item);
        }
        return result;
    }

    private static LlmProvider.Reply reply(String protocol, JsonObject json, boolean nativeTools) {
        List<LlmProvider.FunctionCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        JsonElement replay;
        String finish;
        if ("responses".equals(protocol)) {
            finish = string(json, "status", "completed");
            if ("incomplete".equals(finish)) throw AiApiSupport.outputLimitExceeded();
            if (!"completed".equals(finish)) throw new IllegalStateException("Responses API did not complete: " + finish);
            JsonArray output = json.has("output") ? json.getAsJsonArray("output") : new JsonArray();
            replay = output.deepCopy();
            for (JsonElement entry : output) {
                JsonObject item = entry.getAsJsonObject();
                if ("function_call".equals(string(item, "type", ""))) calls.add(new LlmProvider.FunctionCall(
                    item.get("call_id").getAsString(), item.get("name").getAsString(), arguments(item.get("arguments"))));
                else if (item.has("content")) readBlocks(item.getAsJsonArray("content"), text, calls);
            }
            if (text.isEmpty() && json.has("output_text")) text.append(json.get("output_text").getAsString());
        } else if ("anthropic".equals(protocol)) {
            finish = string(json, "stop_reason", "unknown");
            if ("max_tokens".equals(finish)) throw AiApiSupport.outputLimitExceeded();
            if ("refusal".equals(finish)) throw new IllegalStateException("Anthropic API refused the request");
            JsonArray blocks = json.has("content") ? json.getAsJsonArray("content") : new JsonArray();
            replay = blocks.deepCopy(); readBlocks(blocks, text, calls);
        } else {
            JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            finish = string(choice, "finish_reason", "unknown");
            if ("length".equals(finish)) throw AiApiSupport.outputLimitExceeded();
            JsonObject message = choice.getAsJsonObject("message");
            JsonObject inputMessage = new JsonObject(); inputMessage.addProperty("role", "assistant");
            for (String key : List.of("content", "tool_calls", "reasoning_content")) if (message.has(key)) inputMessage.add(key, message.get(key).deepCopy());
            replay = inputMessage;
            if (message.has("content") && !message.get("content").isJsonNull()) {
                if (message.get("content").isJsonPrimitive()) text.append(message.get("content").getAsString());
                else readBlocks(message.getAsJsonArray("content"), text, calls);
            }
            if (message.has("tool_calls")) for (JsonElement entry : message.getAsJsonArray("tool_calls")) {
                JsonObject call = entry.getAsJsonObject(); JsonObject function = call.getAsJsonObject("function");
                calls.add(new LlmProvider.FunctionCall(call.get("id").getAsString(), function.get("name").getAsString(),
                    arguments(function.get("arguments"))));
            }
        }
        if (text.isEmpty() && calls.isEmpty()) throw new IllegalStateException("AI returned neither text nor tool calls (" + finish + ")");
        JsonObject usage = json.has("usage") && json.get("usage").isJsonObject() ? json.getAsJsonObject("usage") : new JsonObject();
        long cached = number(usage, "cache_read_input_tokens");
        if (usage.has("input_tokens_details") && usage.get("input_tokens_details").isJsonObject()) cached = number(usage.getAsJsonObject("input_tokens_details"), "cached_tokens");
        if (usage.has("prompt_tokens_details") && usage.get("prompt_tokens_details").isJsonObject()) cached = number(usage.getAsJsonObject("prompt_tokens_details"), "cached_tokens");
        LlmProvider.Usage counts = new LlmProvider.Usage(number(usage, "input_tokens", "prompt_tokens"), number(usage, "output_tokens", "completion_tokens"), cached);
        return new LlmProvider.Reply(new LlmProvider.Message("assistant", List.of(new LlmProvider.ContentBlock.Text(text.toString())),
            List.copyOf(calls), "", "", nativeTools ? replay : null), counts, finish);
    }

    private static void readBlocks(JsonArray blocks, StringBuilder text, List<LlmProvider.FunctionCall> calls) {
        for (JsonElement entry : blocks) {
            JsonObject block = entry.getAsJsonObject(); String type = string(block, "type", "");
            if ("refusal".equals(type)) throw new IllegalStateException("AI refused the request");
            if ("text".equals(type) || "output_text".equals(type)) text.append(string(block, "text", ""));
            else if ("tool_use".equals(type)) calls.add(new LlmProvider.FunctionCall(block.get("id").getAsString(), block.get("name").getAsString(), arguments(block.get("input"))));
        }
    }

    private static JsonObject arguments(JsonElement value) {
        try {
            JsonElement parsed = value.isJsonObject() ? value : JsonParser.parseString(value.getAsString());
            return parsed.getAsJsonObject();
        } catch (RuntimeException invalid) {
            JsonObject error = new JsonObject(); error.addProperty("__invalid_arguments", "Tool arguments must be a JSON object"); return error;
        }
    }

    private static String text(List<LlmProvider.ContentBlock> blocks) { return blocks.stream().filter(LlmProvider.ContentBlock.Text.class::isInstance).map(block -> ((LlmProvider.ContentBlock.Text) block).text()).collect(java.util.stream.Collectors.joining("\n")); }
    private static String string(JsonObject object, String key, String fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
    private static long number(JsonObject object, String... keys) { for (String key : keys) if (object.has(key) && !object.get(key).isJsonNull()) return object.get(key).getAsLong(); return -1; }
}
