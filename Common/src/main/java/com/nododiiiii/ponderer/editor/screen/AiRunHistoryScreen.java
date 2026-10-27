package com.nododiiiii.ponderer.editor.screen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.ai.AiRunFiles;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.editor.screen.widgets.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.editor.screen.widgets.ActionStripListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.EntryTextSupport;
import com.nododiiiii.ponderer.editor.screen.widgets.PageTurnListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.PlainTextListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.SearchableListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Persistent run review, recovery and guarded undo. */
public final class AiRunHistoryScreen extends AbstractReadonlyDeclarativeListScreen {
    private final BiConsumer<AiRunFiles, String> resume;
    private String initialRunId;
    private List<AiRunFiles> runs = List.of();
    private int selected;
    private String clarificationText = "";
    private boolean resumeAvailable, undoAvailable, diffAvailable;

    public AiRunHistoryScreen(Screen parent, BiConsumer<AiRunFiles, String> resume) {
        super(parent, "ponderer.ui.scope.editor", "ponderer.ui.ai_runs.title", UILayoutConstants.EDITOR_LIST_W);
        this.resume = resume;
    }

    public AiRunHistoryScreen(Screen parent, BiConsumer<AiRunFiles, String> resume, String runId) {
        this(parent, resume); initialRunId = runId;
    }

    @Override
    protected void init() {
        String runId = initialRunId != null ? initialRunId : current() == null ? null : current().id();
        runs = AiRunFiles.recent();
        selected = Math.min(selected, Math.max(0, runs.size() - 1));
        if (runId != null) {
            for (int i = 0; i < runs.size(); i++) {
                if (runId.equals(runs.get(i).id())) selected = i;
            }
        }
        initialRunId = null;
        super.init();
    }

    @Override
    protected int getHeaderEntryHeight() {
        // Text fields need the standard form row height for Catnip's background and padding.
        return UILayoutConstants.LIST_ENTRY_H;
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        AiRunFiles run = current();
        resumeAvailable = run != null && run.canResume();
        undoAvailable = run != null && run.canUndo();
        diffAvailable = false;
        try {
            diffAvailable = run != null && run.hasArtifact("diff.json");
        } catch (RuntimeException error) {
            setErrorMessage(error.getMessage());
        }

        entries.add(new PageTurnListEntry(
            () -> current() == null ? "0/0" : UIText.of("ponderer.ui.ai_runs.record", selected + 1, runs.size(), current().id().substring(0, 8)),
            () -> select(-1), () -> select(1),
            () -> UIText.of("ponderer.ui.ai_runs.previous"), () -> UIText.of("ponderer.ui.ai_runs.next"),
            () -> runs.size() > 1, () -> runs.size() > 1));
        entries.add(new ActionStripListEntry(List.of(
            action("resume", this::resumeDraft, () -> resumeAvailable),
            action("open", this::openScene, () -> undoAvailable),
            action("undo", this::undo, () -> undoAvailable))));
        entries.add(new ActionStripListEntry(List.of(
            action("copy_diff", this::copyDiff, () -> diffAvailable),
            action("files", this::openRunFiles, () -> current() != null))));

        PlainTextListEntry clarification = textEntry("ponderer.ui.ai_runs.clarification_label",
            "ponderer.ui.ai_runs.clarification.tooltip", "ponderer.ui.ai_runs.clarification",
            clarificationText, value -> clarificationText = value);
        clarification.field().setMaxLength(2048);
        clarification.field().setEditable(resumeAvailable);
        clarification.field().active = resumeAvailable;
        entries.add(clarification);
    }

    private ActionStripListEntry.ButtonModel action(String action, Runnable callback, BooleanSupplier active) {
        String key = "ponderer.ui.ai_runs." + action;
        return ActionStripListEntry.button(() -> UIText.of(key), () -> List.of(Component.translatable(key)),
            callback, () -> 0xFFFFFF, active);
    }

    private AiRunFiles current() { return runs.isEmpty() ? null : runs.get(selected); }

    private void select(int direction) {
        if (runs.isEmpty()) return;
        selected = Math.floorMod(selected + direction, runs.size());
        clarificationText = "";
        clearStatusMessages();
        rebuildListAtTop();
    }

    private void resumeDraft() {
        try {
            if (current() != null && current().canResume()) resume.accept(current(), clarificationText);
        } catch (RuntimeException error) {
            setErrorMessage(error.getMessage());
        }
    }

    private void copyDiff() {
        try {
            if (current() == null || !diffAvailable) return;
            minecraft.keyboardHandler.setClipboard(new GsonBuilder().setPrettyPrinting().create().toJson(current().read("diff.json")));
            setInfoMessage(UIText.of("ponderer.ui.ai_runs.copied"));
        } catch (RuntimeException error) {
            setErrorMessage(error.getMessage());
        }
    }

    private void openRunFiles() {
        try {
            if (current() != null) net.minecraft.Util.getPlatform().openFile(current().directory().toFile());
        } catch (RuntimeException error) {
            setErrorMessage(error.getMessage());
        }
    }

    private void openScene() {
        if (current() == null) return;
        try {
            DslScene saved = new GsonBuilder().registerTypeAdapter(LocalizedText.class,
                new LocalizedText.GsonAdapter()).create().fromJson(current().read("undo.json").get("scene"), DslScene.class);
            DslScene scene = com.nododiiiii.ponderer.bootstrap.ProductRuntime.engine().catalog().findByKey(saved.sceneKey());
            if (scene == null) throw new IllegalStateException(UIText.of("ponderer.cmd.scene_not_found", saved.sceneKey()));
            minecraft.setScreen(new SceneEditorScreen(scene, 0));
        } catch (RuntimeException error) { setErrorMessage(error.getMessage()); }
    }

    private void undo() {
        if (current() == null) return;
        try {
            current().undo();
            PondererClientCommands.reloadLocal();
            setInfoMessage(UIText.of("ponderer.ui.ai_runs.undone"));
            rebuildListPreservingScroll();
        } catch (Exception error) { setErrorMessage(error.getMessage()); }
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (current() == null) {
            addReportText(entries, UIText.of("ponderer.ui.ai_runs.empty"), UILayoutConstants.COLOR_HINT);
            return;
        }
        try {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.ai_runs.summary")));
            JsonObject status = current().read("status.json");
            addReportText(entries, UIText.of("ponderer.ui.ai_runs.status." + status.get("status").getAsString()), 0xFFFFFF);
            addReportText(entries, status.get("detail").getAsString(), UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
            addReportText(entries, UIText.of("ponderer.ui.ai_runs.updated_at", status.get("updated_at").getAsString()), UILayoutConstants.COLOR_HINT);
            if (current().hasArtifact("metrics.json")) {
                JsonObject metrics = current().read("metrics.json");
                addReportText(entries, UIText.of("ponderer.ui.ai_runs.metrics", metrics.get("turns"), metrics.get("tool_calls"),
                    metrics.get("input_tokens"), metrics.get("output_tokens"), metrics.get("elapsed_ms").getAsLong() / 1000),
                    UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
                if (metrics.get("usage_contains_estimates").getAsBoolean()) {
                    addReportText(entries, UIText.of("ponderer.ui.ai_runs.estimated"), UILayoutConstants.COLOR_HINT);
                }
            }
            if (current().hasArtifact("checkpoint.json")) {
                JsonObject checkpoint = current().read("checkpoint.json");
                for (String field : List.of("last_errors", "warnings")) {
                    if (checkpoint.has(field) && !checkpoint.getAsJsonArray(field).isEmpty()) {
                        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.ai_runs." + field)));
                    }
                    if (checkpoint.has(field)) for (JsonElement issue : checkpoint.getAsJsonArray(field)) {
                        JsonObject item = issue.getAsJsonObject();
                        String label = UIText.of("ponderer.ui.ai_runs." + field);
                        if (item.has("origin")) label += " (" + UIText.of("ponderer.ui.ai_runs.origin." + item.get("origin").getAsString()) + ")";
                        addReportText(entries, label + ": " + item.get("path").getAsString() + " " + item.get("message").getAsString(),
                            field.equals("last_errors") ? UILayoutConstants.COLOR_ERROR : 0xFFD37F);
                    }
                }
            }
            if (current().hasArtifact("diff.json")) {
                JsonObject diff = current().read("diff.json");
                entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.ai_runs.changes")));
                if (diff.getAsJsonArray("changed_paths").isEmpty()) {
                    addReportText(entries, UIText.of("ponderer.ui.ai_runs.no_changes"), UILayoutConstants.COLOR_HINT);
                }
                for (JsonElement path : diff.getAsJsonArray("changed_paths")) {
                    String pointer = path.getAsString();
                    addReportText(entries, pointer, 0xFFD37F);
                    addReportText(entries, "- " + valueAt(diff.getAsJsonObject("before"), pointer), 0xFF9999);
                    addReportText(entries, "+ " + valueAt(diff.getAsJsonObject("after"), pointer), 0x99E699);
                }
            }
        } catch (RuntimeException error) {
            addReportText(entries, error.getMessage(), UILayoutConstants.COLOR_ERROR);
        }
    }

    private void addReportText(List<ConfigScreenList.Entry> entries, String text, int color) {
        if (text == null || text.isBlank()) return;
        int rowWidth = currentListWidthValue() - 2 * UILayoutConstants.LIST_ROW_MARGIN;
        List<FormattedText> wrapped = font.getSplitter().splitLines(Component.literal(text),
            Math.max(1, EntryTextSupport.contentWidth(rowWidth)), Style.EMPTY);
        int linesPerEntry = Math.max(1, (getEntryHeight() - 4) / (font.lineHeight + 1));
        for (int i = 0; i < wrapped.size(); i += linesPerEntry) {
            List<String> lines = wrapped.subList(i, Math.min(wrapped.size(), i + linesPerEntry)).stream()
                .map(FormattedText::getString).toList();
            entries.add(new ReportTextEntry(lines, color));
        }
    }

    private static String valueAt(JsonElement value, String pointer) {
        try {
            for (String part : pointer.substring(1).split("/")) {
                String key = part.replace("~1", "/").replace("~0", "~");
                value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(key)) : value.getAsJsonObject().get(key);
            }
            String text = value == null ? UIText.of("ponderer.ui.ai_runs.absent") : value.toString();
            return text.length() > 1000 ? text.substring(0, 1000) + "..." : text;
        } catch (RuntimeException absent) { return UIText.of("ponderer.ui.ai_runs.absent"); }
    }

    private static final class ReportTextEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {
        private final List<String> lines;
        private final String text;
        private final int color;

        private ReportTextEntry(List<String> lines, int color) {
            super("");
            this.lines = lines;
            this.text = String.join("\n", lines);
            this.color = color;
        }

        @Override
        public boolean matchesQuery(String query) {
            return text.toLowerCase(Locale.ROOT).contains(query);
        }

        @Override
        public void highlightEntry() {
            annotations.put("highlight", ":)");
        }

        @Override
        public Component getNarration() {
            return Component.literal(text);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTicks) {
            super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
            var font = Minecraft.getInstance().font;
            int lineHeight = font.lineHeight + 1;
            int textY = y + Math.max(0, (height - lines.size() * lineHeight) / 2);
            for (String line : lines) {
                graphics.drawString(font, line, EntryTextSupport.contentLeft(x), textY, color);
                textY += lineHeight;
            }
        }
    }
}
