package com.nododiiiii.ponderer.ui;

import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.ai.AiRunFiles;
import com.nododiiiii.ponderer.ai.AiSceneGenerator;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BiConsumer;

/** Reviews an unsaved draft in the same playback component used by the scene editor. */
public final class AiSceneReviewScreen extends Screen {
    private final Screen parent;
    private final AiSceneGenerator.PendingReview review;
    private final BiConsumer<AiRunFiles, String> revise;
    private final Runnable onSaved;
    private final DslScene scene;
    private final SceneEditorPreview preview = new SceneEditorPreview();
    private EditBox feedback;
    private Button save;
    private int segment, step;
    private boolean loaded, saving;
    private String notice = "", feedbackText = "";

    public AiSceneReviewScreen(Screen parent, AiSceneGenerator.PendingReview review,
                               BiConsumer<AiRunFiles, String> revise, Runnable onSaved) {
        super(Component.translatable("ponderer.ui.ai_review.title"));
        this.parent = parent; this.review = review; this.revise = revise; this.onSaved = onSaved; scene = review.scene();
    }

    @Override protected void init() {
        int left = width / 2 - 160;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> selectSegment(-1)).bounds(left, 27, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> selectSegment(1)).bounds(left + 292, 27, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> selectStep(-1)).bounds(left, 50, 28, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ponderer.ui.ai_review.play"), b -> preview.playStep(step)).bounds(left + 230, 50, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> selectStep(1)).bounds(left + 292, 50, 28, 20).build());
        preview.setBounds(20, 75, Math.max(100, width - 40), Math.max(50, height - 205));
        feedback = new EditBox(font, left, height - 77, 320, 20, Component.translatable("ponderer.ui.ai_review.feedback"));
        feedback.setHint(Component.translatable("ponderer.ui.ai_review.feedback")); feedback.setMaxLength(2048);
        feedback.setValue(feedbackText); feedback.setResponder(value -> feedbackText = value); addRenderableWidget(feedback);
        save = addRenderableWidget(Button.builder(Component.translatable("ponderer.ui.ai_review.save"), b -> save()).bounds(left, height - 51, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ponderer.ui.ai_review.revise"), b -> revise()).bounds(left + 81, height - 51, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ponderer.ui.ai_review.diff"), b -> {
            if (!saving) minecraft.setScreen(new AiRunHistoryScreen(this, revise, review.files().id()));
        }).bounds(left + 162, height - 51, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(left + 243, height - 51, 77, 20).build());
        loadSegment();
    }

    private void loadSegment() {
        loaded = preview.load(scene, segment); preview.seekStep(step);
        if (!loaded) notice = UIText.of("ponderer.ui.ai_review.preview_failed");
        save.active = loaded && !saving;
    }
    private void selectSegment(int direction) {
        if (saving) return;
        segment = Math.floorMod(segment + direction, scene.scenes.size()); step = 0; notice = ""; loadSegment();
    }
    private void selectStep(int direction) {
        if (saving) return;
        step = Math.floorMod(step + direction, scene.scenes.get(segment).steps.size()); preview.seekStep(step);
    }
    private void save() {
        if (saving || !loaded) return;
        saving = true; save.active = false;
        AiSceneGenerator.saveReviewed(review, path -> { saving = false; onSaved.run(); minecraft.setScreen(parent); },
            error -> { saving = false; notice = error; save.active = loaded; });
    }
    private void revise() {
        if (saving || feedback.getValue().isBlank()) return;
        try {
            JsonObject value = new JsonObject(); value.addProperty("segment", scene.scenes.get(segment).id);
            value.addProperty("step", step); value.addProperty("feedback", feedback.getValue());
            value.addProperty("preview_loaded", loaded); value.addProperty("preview_notice", notice);
            review.files().write("review_feedback.json", value.toString());
            revise.accept(review.files(), "Preview feedback for segment " + scene.scenes.get(segment).id + ", step " + step + ":\n"
                + feedback.getValue() + (loaded ? "" : "\nPreview compilation failed; inspect this segment before editing."));
        } catch (RuntimeException error) { notice = error.getMessage(); }
    }

    @Override public void tick() {
        super.tick();
        if (loaded) try { preview.tick(); } catch (RuntimeException error) { loaded = false; save.active = false; notice = UIText.of("ponderer.ui.ai_review.preview_failed"); }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (loaded) try { preview.render(graphics, mouseX, mouseY, partialTick); preview.renderTooltip(graphics, mouseX, mouseY); }
        catch (RuntimeException error) { loaded = false; save.active = false; notice = UIText.of("ponderer.ui.ai_review.preview_failed"); }
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font, (segment + 1) + "/" + scene.scenes.size() + "  " + scene.scenes.get(segment).id, width / 2, 33, 0xCCCCCC);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.ai_review.step", step + 1, scene.scenes.get(segment).steps.size()), width / 2 - 15, 56, 0xCCCCCC);
        if (!notice.isBlank()) graphics.drawCenteredString(font, font.plainSubstrByWidth(notice, Math.max(100, width - 40)), width / 2, height - 96, 0xFFAA55);
    }
    @Override public boolean mouseClicked(double x, double y, int button) { return preview.mouseClicked(x, y, button) || super.mouseClicked(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return preview.mouseDragged(x, y, button, dx, dy) || super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return preview.mouseReleased(x, y, button) || super.mouseReleased(x, y, button); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return preview.mouseScrolled(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return !feedback.isFocused() && preview.keyPressed(key) || super.keyPressed(key, scan, modifiers); }
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return preview.keyReleased(key) || super.keyReleased(key, scan, modifiers); }
    @Override public void removed() { preview.close(); super.removed(); }
    @Override public void onClose() { if (!saving) minecraft.setScreen(parent); }
}
