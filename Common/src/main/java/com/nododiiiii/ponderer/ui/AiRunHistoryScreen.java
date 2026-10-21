package com.nododiiiii.ponderer.ui;

import com.google.gson.*;
import com.nododiiiii.ponderer.ai.AiRunFiles;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Persistent run review, recovery and guarded undo. */
public final class AiRunHistoryScreen extends Screen {
    private final Screen parent;
    private final BiConsumer<AiRunFiles, String> resume;
    private List<AiRunFiles> runs = List.of();
    private List<FormattedCharSequence> lines = List.of();
    private int selected, scroll;
    private String notice = "";
    private EditBox clarification;

    public AiRunHistoryScreen(Screen parent, BiConsumer<AiRunFiles, String> resume) {
        super(Component.translatable("ponderer.ui.ai_runs.title")); this.parent = parent; this.resume = resume;
    }

    @Override protected void init() {
        runs = AiRunFiles.recent(); selected = Math.min(selected, Math.max(0, runs.size() - 1));
        int left = width / 2 - 160;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> select(-1)).bounds(left, 27, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> select(1)).bounds(left + 292, 27, 28, 20).build());
        clarification = new EditBox(font, left, height - 78, 320, 20, Component.translatable("ponderer.ui.ai_runs.clarification"));
        clarification.setHint(Component.translatable("ponderer.ui.ai_runs.clarification")); clarification.setMaxLength(2048);
        addRenderableWidget(clarification);
        addAction("resume", left, height - 53, () -> { if (current() != null && current().canResume()) resume.accept(current(), clarification.getValue()); }).active = current() != null && current().canResume();
        addAction("open", left + 108, height - 53, this::openScene).active = current() != null && current().canUndo();
        addAction("undo", left + 216, height - 53, this::undo).active = current() != null && current().canUndo();
        addAction("copy_diff", left, height - 29, () -> {
            try { if (current() != null) minecraft.keyboardHandler.setClipboard(new GsonBuilder().setPrettyPrinting().create().toJson(current().read("diff.json"))); }
            catch (RuntimeException error) { notice = error.getMessage(); }
        });
        addAction("files", left + 108, height - 29, () -> { if (current() != null) net.minecraft.Util.getPlatform().openFile(current().directory().toFile()); });
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(left + 216, height - 29, 104, 20).build());
        rebuildReport();
    }

    private Button addAction(String action, int x, int y, Runnable callback) {
        return addRenderableWidget(Button.builder(Component.translatable("ponderer.ui.ai_runs." + action), b -> callback.run()).bounds(x, y, 104, 20).build());
    }

    private AiRunFiles current() { return runs.isEmpty() ? null : runs.get(selected); }
    private void select(int direction) {
        if (runs.isEmpty()) return;
        selected = Math.floorMod(selected + direction, runs.size()); scroll = 0; notice = ""; rebuildWidgets();
    }

    private void openScene() {
        try {
            DslScene saved = new GsonBuilder().registerTypeAdapter(com.nododiiiii.ponderer.ponder.LocalizedText.class,
                new com.nododiiiii.ponderer.ponder.LocalizedText.GsonAdapter()).create().fromJson(current().read("undo.json").get("scene"), DslScene.class);
            DslScene scene = SceneRuntime.findByKey(saved.sceneKey());
            if (scene == null) throw new IllegalStateException(UIText.of("ponderer.cmd.scene_not_found", saved.sceneKey()));
            minecraft.setScreen(new SceneEditorScreen(scene, 0));
        } catch (RuntimeException error) { notice = error.getMessage(); }
    }

    private void undo() {
        try { current().undo(); PondererClientCommands.reloadLocal(); notice = UIText.of("ponderer.ui.ai_runs.undone"); rebuildWidgets(); }
        catch (Exception error) { notice = error.getMessage(); }
    }

    private void rebuildReport() {
        List<String> report = new ArrayList<>();
        if (current() == null) report.add(UIText.of("ponderer.ui.ai_runs.empty"));
        else try {
            JsonObject status = current().read("status.json");
            report.add(UIText.of("ponderer.ui.ai_runs.status." + status.get("status").getAsString()));
            report.add(status.get("detail").getAsString());
            report.add(status.get("updated_at").getAsString());
            if (java.nio.file.Files.isRegularFile(current().directory().resolve("metrics.json"))) {
                JsonObject metrics = current().read("metrics.json");
                report.add(UIText.of("ponderer.ui.ai_runs.metrics", metrics.get("turns"), metrics.get("tool_calls"),
                    metrics.get("input_tokens"), metrics.get("output_tokens"), metrics.get("elapsed_ms").getAsLong() / 1000));
                if (metrics.get("usage_contains_estimates").getAsBoolean()) report.add(UIText.of("ponderer.ui.ai_runs.estimated"));
            }
            if (java.nio.file.Files.isRegularFile(current().directory().resolve("checkpoint.json"))) {
                JsonObject checkpoint = current().read("checkpoint.json");
                for (String field : List.of("last_errors", "warnings")) {
                    if (checkpoint.has(field)) for (JsonElement issue : checkpoint.getAsJsonArray(field)) {
                        JsonObject item = issue.getAsJsonObject();
                        String label = UIText.of("ponderer.ui.ai_runs." + field);
                        if (item.has("origin")) label += " (" + UIText.of("ponderer.ui.ai_runs.origin." + item.get("origin").getAsString()) + ")";
                        report.add(label + ": " + item.get("path").getAsString() + " " + item.get("message").getAsString());
                    }
                }
            }
            if (java.nio.file.Files.isRegularFile(current().directory().resolve("diff.json"))) {
                JsonObject diff = current().read("diff.json");
                report.add(UIText.of("ponderer.ui.ai_runs.changes"));
                for (JsonElement path : diff.getAsJsonArray("changed_paths")) {
                    String pointer = path.getAsString(); report.add(pointer);
                    report.add("- " + valueAt(diff.getAsJsonObject("before"), pointer));
                    report.add("+ " + valueAt(diff.getAsJsonObject("after"), pointer));
                }
            }
        } catch (RuntimeException error) { report.add(error.getMessage()); }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String text : report) wrapped.addAll(font.split(Component.literal(text == null ? "" : text), Math.max(100, width - 48)));
        lines = List.copyOf(wrapped);
    }

    private static String valueAt(JsonElement value, String pointer) {
        try {
            for (String part : pointer.substring(1).split("/")) {
                String key = part.replace("~1", "/").replace("~0", "~");
                value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(key)) : value.getAsJsonObject().get(key);
            }
            String text = value == null ? "(absent)" : value.toString();
            return text.length() > 1000 ? text.substring(0, 1000) + "..." : text;
        } catch (RuntimeException absent) { return "(absent)"; }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int visible = Math.max(1, (height - 147) / 11);
        scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visible), scroll - (int) (vertical * 3)));
        return true;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font, runs.isEmpty() ? "0/0" : (selected + 1) + "/" + runs.size() + "  " + current().id().substring(0, 8), width / 2, 33, 0xAAAAAA);
        graphics.enableScissor(20, 52, width - 20, height - 95);
        for (int i = scroll, y = 54; i < lines.size() && y < height - 95; i++, y += 11) graphics.drawString(font, lines.get(i), 24, y, 0xDDDDDD);
        graphics.disableScissor();
        if (!notice.isEmpty()) graphics.drawCenteredString(font, notice, width / 2, height - 92, 0xFFAA55);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
