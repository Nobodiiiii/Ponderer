package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded snapshot reads; unclassified tools are ordering barriers. Results retain call order. */
final class AiToolExecutor implements AutoCloseable {
    record Completed(AiSceneAgentPipeline.ToolCall call, AgentTool.ToolExecution execution, long elapsedMillis) {}
    private final AgentToolRegistry registry;
    private final AgentContext context;
    private final AiCancellationScope cancellation;
    private final AiRunEvents events;
    private final ExecutorService reads = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "ponderer-ai-read"); thread.setDaemon(true); return thread;
    });

    AiToolExecutor(AgentToolRegistry registry, AgentContext context, AiCancellationScope cancellation, AiRunEvents events) {
        this.registry = registry; this.context = context; this.cancellation = cancellation; this.events = events;
        cancellation.trackResource(this);
    }

    List<Completed> execute(List<AiSceneAgentPipeline.ToolCall> calls, int turn) {
        List<Completed> completed = new ArrayList<>();
        Consumer<Completed> accept = completed::add;
        for (int index = 0; index < calls.size();) {
            cancellation.throwIfCancelled();
            if (!registry.get(calls.get(index).name()).parallelSafe()) {
                accept.accept(executeOne(calls.get(index++), turn));
                continue;
            }
            int end = index + 1;
            while (end < calls.size() && registry.get(calls.get(end).name()).parallelSafe()) end++;
            if (end == index + 1) { accept.accept(executeOne(calls.get(index++), turn)); continue; }
            List<CompletableFuture<Completed>> pending = new ArrayList<>();
            try {
                for (int i = index; i < end; i++) {
                    var call = calls.get(i);
                    pending.add(cancellation.track(CompletableFuture.supplyAsync(() -> executeOne(call, turn), reads)));
                }
                for (var future : pending) {
                    cancellation.throwIfCancelled();
                    try { accept.accept(future.join()); }
                    catch (CompletionException error) {
                        if (error.getCause() instanceof RuntimeException cause) throw cause;
                        throw error;
                    }
                }
            } finally {
                for (var future : pending) if (!future.isDone()) future.cancel(true);
            }
            index = end;
        }
        return List.copyOf(completed);
    }

    private Completed executeOne(AiSceneAgentPipeline.ToolCall call, int turn) {
        cancellation.throwIfCancelled();
        JsonObject data = new JsonObject(); data.addProperty("tool", call.name()); data.addProperty("call_id", call.id());
        data.add("arguments", call.arguments());
        data.addProperty("parallel_safe", registry.get(call.name()).parallelSafe());
        events.emit("tool_started", turn, data);
        events.progress(AiSceneAgentPipeline.statusForTool(call.name()));
        long started = System.nanoTime();
        AgentTool.ToolExecution execution;
        try {
            AgentTool tool = registry.get(call.name());
            AiJsonSchema.validate(tool.inputSchema(), call.arguments(), "$arguments");
            execution = tool.execute(context, call.arguments(), cancellation);
        } catch (CancellationException | AiRunStoppedException stopped) { throw stopped; }
        catch (RuntimeException error) {
            JsonObject failure = new JsonObject(); failure.addProperty("ok", false);
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            String code = message.matches("(?s)^[A-Z][A-Z_]+:.*") ? message.substring(0, message.indexOf(':'))
                : error instanceof IllegalArgumentException ? "INVALID_ARGUMENTS" : "TOOL_EXECUTION_ERROR";
            failure.addProperty("code", code); failure.addProperty("error", message);
            failure.addProperty("retryable", true); failure.add("draft", context.drafts().descriptor());
            execution = AgentTool.ToolExecution.result(failure);
        }
        cancellation.throwIfCancelled();
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        data.addProperty("elapsed_ms", elapsed); data.add("result", execution.result());
        events.emit("tool_finished", turn, data);
        return new Completed(call, execution, elapsed);
    }

    @Override public void close() {
        reads.shutdownNow();
        cancellation.releaseResource(this);
    }
}
