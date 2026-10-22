package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import java.util.*;

/** Both transports enter the same call validation without serializing native calls back to JSON. */
final class AiToolCalls {
    private AiToolCalls() {}

    record Parsed(List<AiSceneAgentPipeline.ToolCall> calls, String error, JsonObject editScope) {
        static Parsed error(String message) { return new Parsed(List.of(), message, null); }
    }

    static Parsed parse(LlmProvider.Reply reply, AgentToolRegistry registry) {
        try {
            List<LlmProvider.FunctionCall> input = reply.message().calls();
            JsonObject scope = null;
            if (input.isEmpty()) {
                JsonObject object = JsonParser.parseString(LlmJsonSupport.cleanJson(
                    LlmJsonSupport.extractJson(reply.protocolText()))).getAsJsonObject();
                if (!(object.get("tool_calls") instanceof JsonArray array)) return Parsed.error("Response must contain a tool_calls array");
                input = new ArrayList<>();
                for (JsonElement element : array) {
                    JsonObject call = element.getAsJsonObject();
                    input.add(new LlmProvider.FunctionCall(string(call, "id"), string(call, "name"), call.getAsJsonObject("arguments")));
                }
                if (object.get("edit_scope") instanceof JsonObject value) scope = value;
            }
            if (input.isEmpty() || input.size() > 4) return Parsed.error("Use 1 to 4 tool calls per turn");
            Set<String> ids = new HashSet<>();
            List<AiSceneAgentPipeline.ToolCall> calls = new ArrayList<>();
            for (var call : input) {
                if (call.id() == null || call.id().isBlank() || call.name() == null || call.name().isBlank())
                    return Parsed.error("Each call requires a non-empty id and name");
                String name = call.name().trim().toLowerCase(Locale.ROOT);
                if (!ids.add(call.id())) return Parsed.error("Duplicate tool call id: " + call.id());
                AgentTool tool = registry.get(name);
                if (tool == null) return Parsed.error("Unknown tool: " + name);
                if (call.arguments() == null) return Parsed.error("Tool arguments must be an object for " + name);
                if (tool.exclusive() && input.size() != 1) return Parsed.error("Mutations and terminal tools must be the only call in their turn");
                calls.add(new AiSceneAgentPipeline.ToolCall(call.id(), name, call.arguments().deepCopy()));
            }
            return new Parsed(List.copyOf(calls), null, scope == null ? null : scope.deepCopy());
        } catch (RuntimeException invalid) {
            return Parsed.error("Invalid tool protocol: expected call objects with string id/name and object arguments");
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }
}
