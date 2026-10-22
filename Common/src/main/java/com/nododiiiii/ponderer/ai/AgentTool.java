package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.ponder.DslScene;

import javax.annotation.Nullable;
import java.util.List;

public interface AgentTool {

    String name();

    String description();

    JsonObject inputSchema();

    default boolean terminal() {
        return false;
    }

    /** Explicit opt-in: only immutable snapshots and synchronized read receipts may overlap. */
    default boolean parallelSafe() { return false; }

    default boolean exclusive() { return terminal(); }

    ToolExecution execute(AgentContext context, JsonObject arguments, AiCancellationScope cancellation);

    record ToolExecution(JsonObject result, List<LlmProvider.ContentBlock.Image> images,
                         @Nullable DslScene submittedScene) {
        public static ToolExecution result(JsonObject result) {
            return new ToolExecution(result, List.of(), null);
        }

        public static ToolExecution result(JsonObject result, List<LlmProvider.ContentBlock.Image> images) {
            return new ToolExecution(result, List.copyOf(images), null);
        }

        public static ToolExecution submitted(JsonObject result, DslScene scene) {
            return new ToolExecution(result, List.of(), scene);
        }
    }
}
