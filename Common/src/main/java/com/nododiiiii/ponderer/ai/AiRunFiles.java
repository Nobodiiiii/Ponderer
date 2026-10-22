package com.nododiiiii.ponderer.ai;

import com.google.gson.*;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
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
        Path target = SafePaths.resolveFileName(directory, name);
        if (target == null) throw new IllegalArgumentException("Unsafe run artifact name");
        try { atomicWrite(target, content); }
        catch (IOException error) { throw new IllegalStateException("Cannot persist AI run artifact: " + name, error); }
    }

    public JsonObject read(String name) {
        Path target = SafePaths.resolveFileName(directory, name);
        if (target == null) throw new IllegalArgumentException("Unsafe run artifact name");
        try { return JsonParser.parseString(Files.readString(target)).getAsJsonObject(); }
        catch (IOException error) { throw new IllegalStateException("Cannot read AI run artifact: " + name, error); }
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
                : AiSceneAgentPipeline.Request.EditMode.SCOPED);
    }

    public boolean canResume() {
        try { return Files.isRegularFile(directory.resolve("checkpoint.json")) && !canUndo() && !Set.of("completed", "undone").contains(read("status.json").get("status").getAsString()); }
        catch (RuntimeException unavailable) { return false; }
    }

    public boolean canReview() {
        try { return "awaiting_review".equals(read("status.json").get("status").getAsString())
            && Files.isRegularFile(directory.resolve("checkpoint.json")); }
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
                .filter(path -> Files.isRegularFile(path.resolve("status.json")))
                .sorted(Comparator.comparingLong((Path path) -> {
                    try { return Files.getLastModifiedTime(path.resolve("status.json")).toMillis(); }
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
