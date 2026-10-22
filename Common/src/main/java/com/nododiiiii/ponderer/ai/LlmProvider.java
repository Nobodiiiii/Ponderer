package com.nododiiiii.ponderer.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over different LLM API providers.
 * Supports Anthropic Messages, OpenAI Responses, and compatible Chat Completions APIs.
 */
public interface LlmProvider {

    record FunctionCall(String id, String name, com.google.gson.JsonObject arguments) {}
    record ToolDefinition(String name, String description, com.google.gson.JsonObject schema) {}
    record Message(String role, List<ContentBlock> content, List<FunctionCall> calls,
                   String callId, String toolName, com.google.gson.JsonElement replay) {
        public Message { content = List.copyOf(content); calls = List.copyOf(calls); }
        public static Message user(String text) { return new Message("user", List.of(new ContentBlock.Text(text)), List.of(), "", "", null); }
        public static Message tool(String id, String name, com.google.gson.JsonObject result, List<ContentBlock.Image> images) {
            java.util.ArrayList<ContentBlock> content = new java.util.ArrayList<>();
            content.add(new ContentBlock.Text(result.toString())); content.addAll(images);
            return new Message("tool", content, List.of(), id, name, null);
        }
    }
    record Usage(long inputTokens, long outputTokens, long cachedTokens) {
        public static final Usage UNKNOWN = new Usage(-1, -1, -1);
    }
    record Exchange(String systemPrompt, List<Message> messages, List<ToolDefinition> tools,
                    AiRuntimeConfig config, boolean nativeTools, int maxOutputTokens,
                    java.util.function.Consumer<StreamUpdate> onStream) {
        public Exchange(String system, List<Message> messages, List<ToolDefinition> tools,
                        AiRuntimeConfig config, boolean nativeTools, int maxOutputTokens) {
            this(system, messages, tools, config, nativeTools, maxOutputTokens, null);
        }
    }
    record StreamUpdate(long receivedCharacters) {}
    record Reply(Message message, Usage usage, String finishReason) {
        public boolean outputLimited() { return List.of("max_output_tokens", "max_tokens", "length").contains(finishReason); }
        public String completeText() {
            if (outputLimited()) throw AiApiSupport.outputLimitExceeded();
            return protocolText();
        }
        public String protocolText() {
            if (!message.calls().isEmpty()) {
                com.google.gson.JsonObject wrapper = new com.google.gson.JsonObject();
                com.google.gson.JsonArray calls = new com.google.gson.JsonArray();
                for (FunctionCall call : message.calls()) {
                    com.google.gson.JsonObject item = new com.google.gson.JsonObject();
                    item.addProperty("id", call.id()); item.addProperty("name", call.name()); item.add("arguments", call.arguments()); calls.add(item);
                }
                wrapper.add("tool_calls", calls); return wrapper.toString();
            }
            return message.content().stream().filter(ContentBlock.Text.class::isInstance)
                .map(block -> ((ContentBlock.Text) block).text()).collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    default boolean supportsNativeTools(AiRuntimeConfig config) { return false; }

    /** Legacy adapters retain the typed host history but receive a text projection at their boundary. */
    default CompletableFuture<Reply> exchange(Exchange request, AiCancellationScope cancellation) {
        java.util.ArrayList<ContentBlock> blocks = new java.util.ArrayList<>();
        for (Message message : request.messages()) {
            blocks.add(new ContentBlock.Text("[" + message.role() + " " + message.toolName() + " " + message.callId() + "]"));
            blocks.addAll(message.content());
        }
        AiRuntimeConfig config = request.config();
        return generate(request.systemPrompt(), blocks, config.baseUrl(), config.apiKey(), config.model(), cancellation)
            .thenApply(text -> new Reply(new Message("assistant", List.of(new ContentBlock.Text(text)), List.of(), "", "", null), Usage.UNKNOWN, "unknown"));
    }

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

    /**
     * Cancellable generation overload. Custom providers can rely on this default;
     * built-in HTTP providers override it so the underlying request is cancelled too.
     */
    default CompletableFuture<String> generate(String systemPrompt, List<ContentBlock> userContent,
                                                String baseUrl, String apiKey, String model,
                                                AiCancellationScope cancellation) {
        cancellation.throwIfCancelled();
        return cancellation.track(generate(systemPrompt, userContent, baseUrl, apiKey, model));
    }
}
