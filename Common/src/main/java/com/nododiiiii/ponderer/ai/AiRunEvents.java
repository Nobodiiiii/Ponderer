package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.function.Consumer;

/** Ordered run facts. Persistence completes before a progress notification is delivered. */
public final class AiRunEvents {
    private final String runId;
    private final Consumer<JsonObject> sink;
    private Consumer<String> onProgress = ignored -> {};
    private Consumer<AiOutputUpdate> onOutput = ignored -> {};
    private long sequence;

    AiRunEvents(String runId, long sequence, Consumer<JsonObject> sink) {
        this.runId = runId;
        this.sequence = sequence;
        this.sink = sink;
    }

    synchronized void onProgress(Consumer<String> listener) { onProgress = listener; }
    public synchronized void onOutput(Consumer<AiOutputUpdate> listener) { onOutput = listener; }
    synchronized void output(int turn, int attempt, String text, boolean nativeTools) {
        onOutput.accept(new AiOutputUpdate(turn, attempt, AiOutputUpdate.visibleText(text, nativeTools)));
    }

    synchronized void emit(String type, int turn, JsonObject data) {
        JsonObject event = new JsonObject();
        event.addProperty("version", 1);
        event.addProperty("run_id", runId);
        event.addProperty("sequence", sequence + 1);
        event.addProperty("type", type);
        event.addProperty("turn", turn);
        event.addProperty("at", Instant.now().toString());
        event.add("data", data.deepCopy());
        sink.accept(event);
        sequence++;
        if ("progress".equals(type)) onProgress.accept(data.get("message").getAsString());
    }

    void progress(String message) {
        JsonObject data = new JsonObject(); data.addProperty("message", message);
        emit("progress", 0, data);
    }
}
