package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Local run artifacts contain user scene data, never API configuration or credentials. */
public final class AiRunFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private final String id;
    private final Path directory;
    private AiRunEvents events;
    private long journalSize = -1, lastSequence;
    private final Map<String, JsonObject> projections = new HashMap<>();

    public AiRunFiles(String id) {
        this.id = UUID.fromString(id).toString();
        directory = root().resolve(this.id);
    }

    public String id() { return id; }
    public Path directory() { return directory; }

    public static Path root() {
        try { return PondererServices.PLATFORM.getConfigDir().resolve("ponderer/logs/runs"); }
        catch (Throwable unavailable) { return Path.of(System.getProperty("java.io.tmpdir"), "ponderer-test-config/ponderer/logs/runs"); }
    }

    public void write(String name, String content) {
        String type = artifactType(name);
        if (type != null) {
            JsonObject data = JsonParser.parseString(content).getAsJsonObject();
            events().emit(type, data.has("turn") ? data.get("turn").getAsInt() : 0, data);
            return;
        }
        Path target = SafePaths.resolveFileName(directory, name);
        if (target == null) throw new IllegalArgumentException("Unsafe run artifact name");
        try { atomicWrite(target, content); }
        catch (IOException error) { throw new IllegalStateException("Cannot persist AI run artifact: " + name, error); }
    }

    public JsonObject read(String name) {
        Path target = SafePaths.resolveFileName(directory, name);
        if (target == null) throw new IllegalArgumentException("Unsafe run artifact name");
        try {
            if (artifactType(name) != null) synchronized (AiRunFiles.class) {
                refreshProjections();
                if (projections.containsKey(name)) return projections.get(name).deepCopy();
            }
            return JsonParser.parseString(Files.readString(target)).getAsJsonObject();
        }
        catch (IOException error) { throw new IllegalStateException("Cannot read AI run artifact: " + name, error); }
    }

    public boolean hasArtifact(String name) {
        Path target = SafePaths.resolveFileName(directory, name);
        if (target == null) return false;
        if (artifactType(name) != null) synchronized (AiRunFiles.class) {
            try { refreshProjections(); }
            catch (IOException error) { throw new IllegalStateException("Cannot read AI run events", error); }
            if (projections.containsKey(name)) return true;
        }
        return Files.isRegularFile(target);
    }

    synchronized AiRunEvents events() {
        if (events == null) synchronized (AiRunFiles.class) {
            try { refreshProjections(); }
            catch (IOException error) { throw new IllegalStateException("Cannot read AI run events", error); }
            events = new AiRunEvents(id, lastSequence, this::appendEvent);
        }
        return events;
    }

    private void appendEvent(JsonObject event) {
        synchronized (AiRunFiles.class) {
            try {
                Files.createDirectories(directory);
                Path journal = directory.resolve("events.jsonl");
                // A process may have stopped midway through its final UTF-8 line. Only complete lines commit.
                try (var channel = java.nio.channels.FileChannel.open(journal, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    long committed = committedLength(channel);
                    if (committed != channel.size()) channel.truncate(committed);
                    refreshProjections();
                    event.addProperty("sequence", lastSequence + 1);
                    byte[] bytes = (event.toString() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    channel.position(committed);
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(false);
                }
                lastSequence = event.get("sequence").getAsLong();
                journalSize = Files.size(journal);
                String artifact = eventArtifact(event.get("type").getAsString());
                if (artifact != null) {
                    JsonObject data = event.getAsJsonObject("data");
                    projections.put(artifact, data.deepCopy());
                    atomicWrite(directory.resolve(artifact), GSON.toJson(data));
                }
            } catch (IOException error) { throw new IllegalStateException("Cannot persist AI run event", error); }
        }
    }

    private void refreshProjections() throws IOException {
        Path journal = directory.resolve("events.jsonl");
        long size = Files.isRegularFile(journal) ? Files.size(journal) : 0;
        if (journalSize == size) return;
        projections.clear(); lastSequence = 0;
        if (size > 0) {
            try (var channel = java.nio.channels.FileChannel.open(journal, StandardOpenOption.READ)) {
                long committed = committedLength(channel);
                channel.position(0);
                // Bound bytes before decoding: a torn final line may also end inside a UTF-8 character.
                java.io.InputStream source = new java.io.InputStream() {
                    private long remaining = committed;
                    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                        if (length == 0) return 0;
                        if (remaining == 0) return -1;
                        int count = channel.read(java.nio.ByteBuffer.wrap(bytes, offset, (int) Math.min(length, remaining)));
                        if (count > 0) remaining -= count;
                        return count;
                    }
                    @Override public int read() throws IOException {
                        byte[] one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
                    }
                };
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(source, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        JsonObject event = JsonParser.parseString(line).getAsJsonObject();
                        if (event.get("version").getAsInt() != 1 || !id.equals(event.get("run_id").getAsString())
                            || event.get("sequence").getAsLong() <= lastSequence)
                            throw new IOException("Invalid AI event journal ordering or version");
                        lastSequence = event.get("sequence").getAsLong();
                        String artifact = eventArtifact(event.get("type").getAsString());
                        if (artifact != null) projections.put(artifact, event.getAsJsonObject("data").deepCopy());
                    }
                }
            }
        }
        journalSize = size;
    }

    private static long committedLength(java.nio.channels.FileChannel channel) throws IOException {
        long end = channel.size();
        java.nio.ByteBuffer tail = java.nio.ByteBuffer.allocate(4096);
        while (end > 0) {
            long start = Math.max(0, end - tail.capacity());
            tail.clear(); tail.limit((int) (end - start)); channel.position(start);
            while (tail.hasRemaining()) if (channel.read(tail) < 0) break;
            for (int index = tail.position() - 1; index >= 0; index--)
                if (tail.get(index) == '\n') return start + index + 1;
            end = start;
        }
        return 0;
    }

    private static String artifactType(String name) {
        return switch (name) {
            case "status.json" -> "run_status";
            case "checkpoint.json" -> "checkpoint";
            case "metrics.json" -> "metrics";
            default -> null;
        };
    }

    private static String eventArtifact(String type) {
        return switch (type) {
            case "run_status" -> "status.json";
            case "checkpoint" -> "checkpoint.json";
            case "metrics" -> "metrics.json";
            default -> null;
        };
    }

    public void status(String status, String detail) {
        JsonObject value = new JsonObject(); value.addProperty("run_id", id);
        value.addProperty("status", status); value.addProperty("detail", detail);
        value.addProperty("updated_at", java.time.Instant.now().toString());
        write("status.json", GSON.toJson(value));
    }

    static String checkpoint(AiSceneAgentPipeline.Request request, AgentContext context, int turn, AiRunBudget budget) {
        JsonObject value = new JsonObject(); value.addProperty("version", 1); value.addProperty("turn", turn);
        value.add("structure_paths", GSON.toJsonTree(request.structurePaths().stream().map(Path::toString).toList()));
        value.addProperty("carrier", request.carrierItemId());
        value.addProperty("prompt", request.userPrompt() + (context.runState().clarifications().isEmpty() ? ""
            : "\nAdditional user instructions:\n" + String.join("\n", context.runState().clarifications())));
        value.add("reference_urls", GSON.toJsonTree(request.referenceUrls()));
        value.addProperty("existing_json", request.existingJson());
        value.addProperty("build_tutorial", request.buildTutorial()); value.addProperty("include_images", request.includeImages());
        value.addProperty("edit_mode", request.editMode().name());
        value.add("draft", context.drafts().scene()); value.add("edit_scope", context.runState().editScope());
        value.add("task_state", context.runState().taskMemory().snapshot());
        value.add("last_errors", GSON.toJsonTree(context.runState().lastErrors()));
        value.add("warnings", AiEditGuard.warnings(context, context.drafts().scene(), context.runState().lastWarnings()));
        value.add("budget", budget.summary());
        return GSON.toJson(value);
    }

    public AiSceneAgentPipeline.Request recoveryRequest() {
        JsonObject saved = read("checkpoint.json");
        if (saved.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported recovery checkpoint version");
        List<Path> paths = new ArrayList<>(); for (JsonElement path : saved.getAsJsonArray("structure_paths")) paths.add(Path.of(path.getAsString()));
        List<String> urls = new ArrayList<>(); for (JsonElement url : saved.getAsJsonArray("reference_urls")) urls.add(url.getAsString());
        return new AiSceneAgentPipeline.Request(paths, saved.get("carrier").getAsString(), saved.get("prompt").getAsString(), urls,
            saved.get("existing_json").isJsonNull() ? null : saved.get("existing_json").getAsString(),
            saved.get("build_tutorial").getAsBoolean(), saved.get("include_images").getAsBoolean(),
            saved.getAsJsonObject("draft"), saved.has("edit_scope") && saved.get("edit_scope").isJsonObject() ? saved.getAsJsonObject("edit_scope") : null,
            saved.has("edit_mode") ? AiSceneAgentPipeline.Request.EditMode.valueOf(saved.get("edit_mode").getAsString())
                : AiSceneAgentPipeline.Request.EditMode.SCOPED,
            saved.get("task_state") instanceof JsonObject memory ? memory : null);
    }

    public boolean canResume() {
        try { return hasArtifact("checkpoint.json") && !canUndo() && !Set.of("completed", "undone").contains(read("status.json").get("status").getAsString()); }
        catch (RuntimeException unavailable) { return false; }
    }

    public boolean canUndo() {
        try {
            String status = read("status.json").get("status").getAsString();
            if ("undone".equals(status) || !Files.isRegularFile(directory.resolve("undo.json"))) return false;
            JsonObject saved = read("undo.json");
            Path target = Path.of(saved.get("path").getAsString());
            return saved.has("after") && Files.isRegularFile(target) && Files.readString(target).equals(saved.get("after").getAsString());
        } catch (RuntimeException | IOException unavailable) { return false; }
    }

    public static List<AiRunFiles> recent() {
        if (!Files.isDirectory(root())) return List.of();
        try (var paths = Files.list(root())) {
            return paths.filter(Files::isDirectory).filter(path -> path.getFileName().toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                .filter(path -> Files.isRegularFile(path.resolve("status.json")) || Files.isRegularFile(path.resolve("events.jsonl")))
                .sorted(Comparator.comparingLong((Path path) -> {
                    try { return Files.getLastModifiedTime(path.resolve(Files.isRegularFile(path.resolve("events.jsonl")) ? "events.jsonl" : "status.json")).toMillis(); }
                    catch (IOException ignored) { return 0L; }
                }).reversed()).limit(50).map(path -> new AiRunFiles(path.getFileName().toString())).toList();
        } catch (IOException error) { return List.of(); }
    }

    void recordUndo(Path target, String before, DslScene scene) {
        JsonObject value = new JsonObject(); value.addProperty("path", target.toAbsolutePath().normalize().toString());
        value.addProperty("before", before); value.add("scene", GSON.toJsonTree(scene));
        value.addProperty("after", SceneStore.serializeLocalScene(scene));
        write("undo.json", GSON.toJson(value));
        status("saving", "Rollback data persisted; saving scene");
    }

    void markSaved(Path target) throws IOException {
        JsonObject undo = read("undo.json"); undo.addProperty("after", Files.readString(target));
        write("undo.json", GSON.toJson(undo));
        JsonObject after = JsonParser.parseString(undo.get("after").getAsString()).getAsJsonObject();
        JsonObject before = undo.get("before").isJsonNull() ? new JsonObject()
            : JsonParser.parseString(undo.get("before").getAsString()).getAsJsonObject();
        write("diff.json", diff(before, after));
    }

    static String diff(JsonObject before, JsonObject after) {
        JsonObject diff = new JsonObject(); diff.add("changed_paths", GSON.toJsonTree(AiEditGuard.changedPaths(before, after)));
        diff.add("before", before); diff.add("after", after); return GSON.toJson(diff);
    }

    public void undo() throws IOException {
        synchronized (SceneStore.class) {
            if (!canUndo()) throw new IllegalStateException("This run has no saved change to undo");
            JsonObject saved = read("undo.json");
            DslScene scene = GSON.fromJson(saved.get("scene"), DslScene.class);
            Path expected = SceneStore.resolveLocalScenePath(scene);
            Path target = Path.of(saved.get("path").getAsString()).toAbsolutePath().normalize();
            if (expected == null || !target.equals(expected.toAbsolutePath().normalize())) throw new IllegalStateException("Undo scene path no longer matches the local scene");
            if (!saved.has("after") || !Files.isRegularFile(target) || !Files.readString(target).equals(saved.get("after").getAsString()))
                throw new IllegalStateException("SCENE_CHANGED: the scene was edited after this run; undo would overwrite newer changes");
            if (saved.get("before").isJsonNull()) Files.delete(target);
            else atomicWrite(target, saved.get("before").getAsString());
            status("undone", "The saved AI change was reverted");
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent()); Path temporary = Files.createTempFile(target.getParent(), ".ai-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
