package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.TreeMap;
import java.util.function.Consumer;

/** SSE assembly only. Partial JSON/tool arguments are never executed. */
final class AiStreamingResponse {
    private final String protocol;
    private final Consumer<LlmProvider.StreamUpdate> progress;
    private final JsonObject result = new JsonObject();
    private final JsonObject message = new JsonObject();
    private final TreeMap<Integer, JsonObject> blocks = new TreeMap<>();
    private final TreeMap<Integer, StringBuilder> arguments = new TreeMap<>();
    private final StringBuilder text = new StringBuilder(), reasoning = new StringBuilder();
    private JsonObject completed;
    private String finish;
    private boolean ended;
    private long received, lastUpdate;

    private AiStreamingResponse(String protocol, Consumer<LlmProvider.StreamUpdate> progress) {
        this.protocol = protocol; this.progress = progress;
    }

    static JsonObject read(String protocol, Reader source, Consumer<LlmProvider.StreamUpdate> progress,
                           AiCancellationScope cancellation) throws IOException {
        AiStreamingResponse stream = new AiStreamingResponse(protocol, progress);
        BufferedReader reader = new BufferedReader(source);
        StringBuilder data = new StringBuilder(); String line; long total = 0;
        while ((line = reader.readLine()) != null) {
            cancellation.throwIfCancelled();
            total += line.length();
            if (total > 32_000_000) throw new IOException("AI response stream exceeds the local size limit");
            if (line.isEmpty()) {
                if (!data.isEmpty()) { stream.event(data.toString()); data.setLength(0); }
                if (stream.ended) break;
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring(line.startsWith("data: ") ? 6 : 5));
            }
        }
        if (!data.isEmpty()) stream.event(data.toString());
        if (!stream.ended) throw new IOException("AI response stream ended before its completion event");
        stream.notifyProgress();
        return stream.finish();
    }

    private void event(String data) throws IOException {
        if ("[DONE]".equals(data)) { if ("chat".equals(protocol)) ended = true; return; }
        JsonObject event;
        try { event = JsonParser.parseString(data).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid AI stream event"); }
        String type = string(event, "type");
        if (event.has("error") || "error".equals(type) || "response.failed".equals(type)) {
            JsonObject source = "response.failed".equals(type) && event.get("response") instanceof JsonObject response ? response : event;
            JsonObject error = source.get("error") instanceof JsonObject detail ? detail : new JsonObject();
            String code = error.has("code") ? string(error, "code") : string(error, "type");
            throw AiHttpException.rejected(java.util.Set.of("overloaded_error", "server_error").contains(code) ? 503
                : java.util.Set.of("rate_limit_error", "rate_limit_exceeded").contains(code) ? 429 : 400,
                "", source.toString());
        }
        if ("responses".equals(protocol)) {
            if ("response.output_text.delta".equals(type)) text.append(string(event, "delta"));
            if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
                completed = event.getAsJsonObject("response"); ended = true;
            }
            if (type.endsWith(".delta") && event.has("delta") && event.get("delta").isJsonPrimitive()) received += event.get("delta").getAsString().length();
        } else if ("anthropic".equals(protocol)) anthropic(event, type);
        else chat(event);
        if (progress != null && received > 0 && System.nanoTime() - lastUpdate > 250_000_000) {
            lastUpdate = System.nanoTime(); notifyProgress();
        }
    }

    private void chat(JsonObject event) {
        if (event.has("usage") && event.get("usage").isJsonObject()) result.add("usage", event.get("usage"));
        if (!(event.get("choices") instanceof JsonArray choices)) return;
        for (JsonElement element : choices) {
            JsonObject choice = element.getAsJsonObject();
            if (choice.has("index") && choice.get("index").getAsInt() != 0) continue;
            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) finish = choice.get("finish_reason").getAsString();
            if (!(choice.get("delta") instanceof JsonObject delta)) continue;
            append(text, delta, "content"); append(reasoning, delta, "reasoning_content");
            if (delta.get("tool_calls") instanceof JsonArray calls) for (JsonElement entry : calls) {
                JsonObject call = entry.getAsJsonObject(); int index = call.get("index").getAsInt();
                JsonObject stored = blocks.computeIfAbsent(index, key -> {
                    JsonObject value = new JsonObject(); value.addProperty("type", "function");
                    value.add("function", new JsonObject()); return value;
                });
                if (call.has("id")) stored.add("id", call.get("id"));
                if (call.get("function") instanceof JsonObject function) {
                    JsonObject target = stored.getAsJsonObject("function");
                    if (function.has("name")) target.addProperty("name", string(target, "name") + string(function, "name"));
                    StringBuilder args = arguments.computeIfAbsent(index, key -> new StringBuilder());
                    append(args, function, "arguments");
                }
            }
        }
    }

    private void anthropic(JsonObject event, String type) {
        switch (type) {
            case "message_start" -> {
                JsonObject start = event.getAsJsonObject("message");
                for (var field : start.entrySet()) result.add(field.getKey(), field.getValue().deepCopy());
            }
            case "content_block_start" -> {
                JsonObject block = event.getAsJsonObject("content_block").deepCopy();
                blocks.put(event.get("index").getAsInt(), block);
                if ("text".equals(string(block, "type"))) text.append(string(block, "text"));
            }
            case "content_block_delta" -> {
                int index = event.get("index").getAsInt(); JsonObject block = blocks.get(index);
                if (block == null) throw new IllegalStateException("AI stream delta has no content block");
                JsonObject delta = event.getAsJsonObject("delta");
                switch (string(delta, "type")) {
                    case "input_json_delta" -> append(arguments.computeIfAbsent(index, key -> new StringBuilder()), delta, "partial_json");
                    case "text_delta", "thinking_delta", "signature_delta" -> {
                        String field = switch (string(delta, "type")) { case "text_delta" -> "text"; case "thinking_delta" -> "thinking"; default -> "signature"; };
                        String value = string(delta, field); block.addProperty(field, string(block, field) + value); received += value.length();
                        if ("text_delta".equals(string(delta, "type"))) text.append(value);
                    }
                    case "citations_delta" -> {
                        if (!block.has("citations")) block.add("citations", new JsonArray());
                        block.getAsJsonArray("citations").add(delta.get("citation"));
                    }
                    default -> { }
                }
            }
            case "message_delta" -> {
                if (event.get("delta") instanceof JsonObject delta) for (var field : delta.entrySet()) result.add(field.getKey(), field.getValue());
                if (event.get("usage") instanceof JsonObject usage) {
                    if (!result.has("usage")) result.add("usage", new JsonObject());
                    for (var field : usage.entrySet()) result.getAsJsonObject("usage").add(field.getKey(), field.getValue());
                }
            }
            case "message_stop" -> ended = true;
            default -> { }
        }
    }

    private JsonObject finish() throws IOException {
        if ("responses".equals(protocol)) {
            if (completed == null) throw new IOException("AI stream has no final response");
            return completed;
        }
        JsonArray content = new JsonArray();
        for (var entry : blocks.entrySet()) {
            JsonObject block = entry.getValue(); StringBuilder args = arguments.get(entry.getKey());
            if (args != null) {
                if ("chat".equals(protocol)) block.getAsJsonObject("function").addProperty("arguments", args.toString());
                else try { block.add("input", JsonParser.parseString(args.toString())); }
                catch (RuntimeException invalid) {
                    boolean limited = "max_tokens".equals(string(result, "stop_reason"));
                    if (!limited) throw new IOException("AI stream contains incomplete tool arguments");
                }
            }
            content.add(block);
        }
        if ("anthropic".equals(protocol)) result.add("content", content);
        else {
            if (finish == null) throw new IOException("AI stream has no finish reason");
            message.addProperty("role", "assistant"); message.addProperty("content", text.toString());
            if (!reasoning.isEmpty()) message.addProperty("reasoning_content", reasoning.toString());
            if (!content.isEmpty()) message.add("tool_calls", content);
            JsonObject choice = new JsonObject(); choice.add("message", message); choice.addProperty("finish_reason", finish);
            JsonArray choices = new JsonArray(); choices.add(choice); result.add("choices", choices);
        }
        return result;
    }

    private void append(StringBuilder target, JsonObject source, String key) {
        String delta = string(source, key); target.append(delta); received += delta.length();
    }
    private void notifyProgress() {
        if (progress != null) progress.accept(new LlmProvider.StreamUpdate(received, text.toString()));
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key); return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
