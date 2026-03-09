package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor screen for ponder title and scene title.
 * Supports multi-language editing with a toggle button per text field.
 */
public class SceneDescEditorScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 240;
    private static final int WINDOW_H = 200;
    private static final int HEADER_H = 22;
    private static final int FOOTER_H = 50;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int displayH = WINDOW_H;
    private record FormWidgetRecord(AbstractWidget widget, int contentOffsetY) {}
    private final List<FormWidgetRecord> formWidgetRecords = new ArrayList<>();

    private final DslScene scene;
    private final int sceneIndex;
    private final SceneEditorScreen parent;

    // Ponder title
    private HintableTextFieldWidget ponderTitleField;
    private BoxWidget ponderTitleLangBtn;
    private String ponderTitleLang;
    private LocalizedText workingPonderTitle;

    // Scene title (only when scene.scenes[] mode)
    private boolean hasMultiScene;
    private HintableTextFieldWidget sceneTitleField;
    private BoxWidget sceneTitleLangBtn;
    private String sceneTitleLang;
    private LocalizedText workingSceneTitle;

    // Ponder ID
    private HintableTextFieldWidget ponderIdField;

    // Scene segment ID (only when scene.scenes[] mode)
    private HintableTextFieldWidget sceneIdField;

    private BoxWidget confirmButton;
    private BoxWidget cancelButton;

    private String errorMessage;

    public SceneDescEditorScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.scene_desc"));
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.parent = parent;

        this.ponderTitleLang = getCurrentLang();
        this.workingPonderTitle = scene.title != null ? scene.title : LocalizedText.of("");

        this.hasMultiScene = scene.scenes != null && !scene.scenes.isEmpty()
                && sceneIndex >= 0 && sceneIndex < scene.scenes.size();
        if (hasMultiScene) {
            this.sceneTitleLang = getCurrentLang();
            DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
            this.workingSceneTitle = sc.title != null ? sc.title : LocalizedText.of("");
        }
    }

    @Override
    protected void init() {
        formWidgetRecords.clear();
        displayH = Math.min(WINDOW_H, height - UILayoutConstants.SCREEN_MARGIN * 2);
        maxScroll = Math.max(0, WINDOW_H - displayH);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        setWindowSize(WINDOW_W, displayH);
        super.init();

        var font = Minecraft.getInstance().font;
        int x = guiLeft + 80, lx = guiLeft + 10;
        int y = 30; // content offset from guiTop
        int fieldW = 104;
        int langBtnW = 28;
        int langGap = 6;

        // Ponder title
        ponderTitleField = new SoftHintTextFieldWidget(font, x, guiTop + y - scrollOffset, fieldW, 18);
        ponderTitleField.setHint(UIText.of("ponderer.ui.scene_desc.hint.ponder_title"));
        ponderTitleField.setMaxLength(32500);
        addRenderableWidget(ponderTitleField);
        formWidgetRecords.add(new FormWidgetRecord(ponderTitleField, y));

        ponderTitleLangBtn = new PonderButton(x + fieldW + langGap + 3, guiTop + y + 3 - scrollOffset, langBtnW, 12);
        ponderTitleLangBtn.withCallback(this::togglePonderTitleLang);
        addRenderableWidget(ponderTitleLangBtn);
        formWidgetRecords.add(new FormWidgetRecord(ponderTitleLangBtn, y + 3));

        // Populate ponder title
        String val = workingPonderTitle.getExact(ponderTitleLang);
        ponderTitleField.setValue(val != null ? val : workingPonderTitle.resolve());

        y += 26;

        // Scene title (only if applicable)
        if (hasMultiScene) {
            sceneTitleField = new SoftHintTextFieldWidget(font, x, guiTop + y - scrollOffset, fieldW, 18);
            sceneTitleField.setHint(UIText.of("ponderer.ui.scene_desc.hint.scene_title"));
            sceneTitleField.setMaxLength(32500);
            addRenderableWidget(sceneTitleField);
            formWidgetRecords.add(new FormWidgetRecord(sceneTitleField, y));

            sceneTitleLangBtn = new PonderButton(x + fieldW + langGap + 3, guiTop + y + 3 - scrollOffset, langBtnW, 12);
            sceneTitleLangBtn.withCallback(this::toggleSceneTitleLang);
            addRenderableWidget(sceneTitleLangBtn);
            formWidgetRecords.add(new FormWidgetRecord(sceneTitleLangBtn, y + 3));

            String scVal = workingSceneTitle.getExact(sceneTitleLang);
            sceneTitleField.setValue(scVal != null ? scVal : workingSceneTitle.resolve());

            y += 26;
        }

        // Ponder ID
        int idFieldW = fieldW + langBtnW + langGap + 3;
        ponderIdField = new SoftHintTextFieldWidget(font, x, guiTop + y - scrollOffset, idFieldW, 18);
        ponderIdField.setHint(UIText.of("ponderer.ui.scene_desc.hint.ponder_id"));
        ponderIdField.setMaxLength(32500);
        ponderIdField.setValue(scene.id != null ? scene.id : "");
        addRenderableWidget(ponderIdField);
        formWidgetRecords.add(new FormWidgetRecord(ponderIdField, y));

        y += 26;

        // Scene segment ID (only if applicable)
        if (hasMultiScene) {
            sceneIdField = new SoftHintTextFieldWidget(font, x, guiTop + y - scrollOffset, idFieldW, 18);
            sceneIdField.setHint(UIText.of("ponderer.ui.scene_desc.hint.scene_id"));
            sceneIdField.setMaxLength(32500);
            DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
            sceneIdField.setValue(sc.id != null ? sc.id : "");
            addRenderableWidget(sceneIdField);
            formWidgetRecords.add(new FormWidgetRecord(sceneIdField, y));
        }

        // Buttons (fixed at bottom)
        int btnW = 80, btnH = 20;
        confirmButton = new PonderButton(guiLeft + 15, guiTop + displayH - 30, btnW, btnH);
        confirmButton.withCallback(this::onConfirm);
        addRenderableWidget(confirmButton);

        cancelButton = new PonderButton(guiLeft + WINDOW_W - btnW - 15, guiTop + displayH - 30, btnW, btnH);
        cancelButton.withCallback(this::returnToParent);
        addRenderableWidget(cancelButton);

        updateWidgetPositions();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background
        new BoxElement()
            .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
            .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WINDOW_W, displayH)
            .render(graphics);

        var font = Minecraft.getInstance().font;

        // Header
        graphics.drawString(font, UIText.of("ponderer.ui.scene_desc"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);

        // Scrollable labels
        int vpTop = guiTop + HEADER_H;
        int vpBot = guiTop + displayH - FOOTER_H;
        if (maxScroll > 0) {
            graphics.enableScissor(guiLeft, vpTop, guiLeft + WINDOW_W, vpBot);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -scrollOffset, 0);
        }

        int lx = guiLeft + 10;
        int y = guiTop + 33;
        int lc = UILayoutConstants.COLOR_LABEL;
        int warnColor = 0xFFFF00;

        // Ponder title label
        graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.ponder_title"), lx, y, lc);
        y += 26;

        // Scene title label
        if (hasMultiScene) {
            graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.scene_title"), lx, y, lc);
            y += 26;
        }

        // Ponder ID label
        graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.ponder_id"), lx, y, warnColor);
        y += 26;

        // Scene segment ID label
        if (hasMultiScene) {
            graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.scene_id"), lx, y, warnColor);
        }

        if (maxScroll > 0) {
            graphics.pose().popPose();
            graphics.disableScissor();
            renderScrollbar(graphics);
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // Lang button labels (scrollable)
        if (maxScroll > 0) {
            graphics.enableScissor(guiLeft, guiTop + HEADER_H, guiLeft + WINDOW_W, guiTop + displayH - FOOTER_H);
        }
        drawLangLabel(graphics, ponderTitleLangBtn, ponderTitleLang);
        if (hasMultiScene) {
            drawLangLabel(graphics, sceneTitleLangBtn, sceneTitleLang);
        }
        if (maxScroll > 0) {
            graphics.disableScissor();
        }

        // ID warning hint or error message (fixed footer)
        int hintY = guiTop + displayH - 46;
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage,
                guiLeft + WINDOW_W / 2, hintY, 0xFF5555);
        } else {
            graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_desc.id_hint"),
                guiLeft + WINDOW_W / 2, hintY, 0xAAAA00);
        }

        // Confirm / Cancel
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.save"),
            confirmButton.getX() + 40, confirmButton.getY() + 6, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
            cancelButton.getX() + 40, cancelButton.getY() + 6, 0xFFFFFF);

        graphics.pose().popPose();
    }

    private void drawLangLabel(GuiGraphics graphics, BoxWidget btn, String lang) {
        var font = Minecraft.getInstance().font;
        String label = lang.length() > 5 ? lang.substring(0, 5) : lang;
        graphics.drawCenteredString(font, label, btn.getX() + btn.getWidth() / 2, btn.getY() + 2, 0xAAFFAA);
    }

    private void onConfirm() {
        errorMessage = null;

        // Validate ponder ID
        String newPonderId = ponderIdField.getValue().trim();
        if (!newPonderId.isEmpty() && !newPonderId.equals(scene.id)) {
            for (DslScene s : SceneRuntime.getScenes()) {
                if (s != scene && newPonderId.equals(s.id)) {
                    errorMessage = Component.translatable("ponderer.ui.scene_desc.error.ponder_id_exists", newPonderId).getString();
                    return;
                }
            }
        }

        // Validate scene segment ID
        if (hasMultiScene && sceneIdField != null) {
            String newSceneId = sceneIdField.getValue().trim();
            DslScene.SceneSegment currentSeg = scene.scenes.get(sceneIndex);
            if (!newSceneId.isEmpty() && !newSceneId.equals(currentSeg.id)) {
                for (int i = 0; i < scene.scenes.size(); i++) {
                    if (i != sceneIndex && newSceneId.equals(scene.scenes.get(i).id)) {
                        errorMessage = Component.translatable("ponderer.ui.scene_desc.error.scene_id_exists", newSceneId).getString();
                        return;
                    }
                }
            }
        }

        // Save ponder title
        String pTitle = ponderTitleField.getValue();
        if (!pTitle.isEmpty()) {
            workingPonderTitle.setForLang(ponderTitleLang, pTitle);
        }
        scene.title = workingPonderTitle;

        // Save scene title
        if (hasMultiScene && sceneTitleField != null) {
            String scTitle = sceneTitleField.getValue();
            if (!scTitle.isEmpty()) {
                workingSceneTitle.setForLang(sceneTitleLang, scTitle);
            }
            scene.scenes.get(sceneIndex).title = workingSceneTitle;
        }

        // Save ponder ID
        if (!newPonderId.isEmpty()) {
            scene.id = newPonderId;
        }

        // Save scene segment ID
        if (hasMultiScene && sceneIdField != null) {
            String newSceneId = sceneIdField.getValue().trim();
            if (!newSceneId.isEmpty()) {
                scene.scenes.get(sceneIndex).id = newSceneId;
            }
        }

        SceneStore.saveSceneToLocal(scene);
        returnToParent();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta * UILayoutConstants.SCROLL_SPEED));
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updateWidgetPositions() {
        int vpTop = guiTop + HEADER_H;
        int vpBot = guiTop + displayH - FOOTER_H;
        for (FormWidgetRecord rec : formWidgetRecords) {
            int newY = guiTop + rec.contentOffsetY - scrollOffset;
            rec.widget.setY(newY);
            if (maxScroll > 0) {
                rec.widget.visible = (newY + rec.widget.getHeight() > vpTop) && (newY < vpBot);
            } else {
                rec.widget.visible = true;
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int barX = guiLeft + WINDOW_W - UILayoutConstants.SCROLLBAR_W - 2;
        int vpTop = guiTop + HEADER_H;
        int vpBot = guiTop + displayH - FOOTER_H;
        int trackH = vpBot - vpTop;
        graphics.fill(barX, vpTop, barX + UILayoutConstants.SCROLLBAR_W, vpBot, UILayoutConstants.COLOR_SCROLLBAR_BG);
        int contentH = WINDOW_H - HEADER_H - FOOTER_H;
        if (contentH <= 0) return;
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB, trackH * trackH / contentH);
        int thumbY = vpTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(barX, thumbY, barX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH, UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(keyCode, scanCode, modifiers);
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox)
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() != null && getFocused().charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }

    // ---- Language toggle logic ----

    private void togglePonderTitleLang() {
        String currentText = ponderTitleField.getValue();
        if (!currentText.isEmpty()) {
            workingPonderTitle.setForLang(ponderTitleLang, currentText);
        }
        ponderTitleLang = nextLang(ponderTitleLang);
        String val = workingPonderTitle.getExact(ponderTitleLang);
        ponderTitleField.setValue(val != null ? val : "");
    }

    private void toggleSceneTitleLang() {
        if (sceneTitleField == null) return;
        String currentText = sceneTitleField.getValue();
        if (!currentText.isEmpty()) {
            workingSceneTitle.setForLang(sceneTitleLang, currentText);
        }
        sceneTitleLang = nextLang(sceneTitleLang);
        String val = workingSceneTitle.getExact(sceneTitleLang);
        sceneTitleField.setValue(val != null ? val : "");
    }

    /** Toggle between MC current language and en_us. */
    private String nextLang(String current) {
        String mcLang = getCurrentLang();
        if (current.equals("en_us") && !"en_us".equals(mcLang)) {
            return mcLang;
        }
        return "en_us";
    }

    private static String getCurrentLang() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }
}
