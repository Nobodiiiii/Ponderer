package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Editor screen for ponder title and scene title.
 * Supports multi-language editing with a toggle button per text field.
 */
public class SceneDescEditorScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 240;
    private static final int WINDOW_H = 160;

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

    private BoxWidget confirmButton;
    private BoxWidget cancelButton;

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
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        var font = Minecraft.getInstance().font;
        int x = guiLeft + 80, lx = guiLeft + 10;
        int y = guiTop + 30;
        int fieldW = 108;
        int langBtnW = 28;

        // Ponder title
        ponderTitleField = new SoftHintTextFieldWidget(font, x, y, fieldW, 18);
        ponderTitleField.setHint(UIText.of("ponderer.ui.scene_desc.hint.ponder_title"));
        ponderTitleField.setMaxLength(200);
        addRenderableWidget(ponderTitleField);

        ponderTitleLangBtn = new PonderButton(x + fieldW + 4, y, langBtnW, 12);
        ponderTitleLangBtn.withCallback(this::togglePonderTitleLang);
        addRenderableWidget(ponderTitleLangBtn);

        // Populate ponder title
        String val = workingPonderTitle.getExact(ponderTitleLang);
        ponderTitleField.setValue(val != null ? val : workingPonderTitle.resolve());

        y += 26;

        // Scene title (only if applicable)
        if (hasMultiScene) {
            sceneTitleField = new SoftHintTextFieldWidget(font, x, y, fieldW, 18);
            sceneTitleField.setHint(UIText.of("ponderer.ui.scene_desc.hint.scene_title"));
            sceneTitleField.setMaxLength(200);
            addRenderableWidget(sceneTitleField);

            sceneTitleLangBtn = new PonderButton(x + fieldW + 4, y, langBtnW, 12);
            sceneTitleLangBtn.withCallback(this::toggleSceneTitleLang);
            addRenderableWidget(sceneTitleLangBtn);

            String scVal = workingSceneTitle.getExact(sceneTitleLang);
            sceneTitleField.setValue(scVal != null ? scVal : workingSceneTitle.resolve());
        }

        // Buttons
        int btnW = 80, btnH = 20;
        confirmButton = new PonderButton(guiLeft + 15, guiTop + WINDOW_H - 30, btnW, btnH);
        confirmButton.withCallback(this::onConfirm);
        addRenderableWidget(confirmButton);

        cancelButton = new PonderButton(guiLeft + WINDOW_W - btnW - 15, guiTop + WINDOW_H - 30, btnW, btnH);
        cancelButton.withCallback(this::returnToParent);
        addRenderableWidget(cancelButton);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background
        new BoxElement()
            .withBackground(new Color(0xdd_000000, true))
            .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WINDOW_W, WINDOW_H)
            .render(graphics);

        var font = Minecraft.getInstance().font;

        // Header
        graphics.drawString(font, UIText.of("ponderer.ui.scene_desc"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, 0x60_FFFFFF);

        int lx = guiLeft + 10;
        int y = guiTop + 33;
        int lc = 0xCCCCCC;

        // Ponder title label
        graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.ponder_title"), lx, y, lc);
        y += 26;

        // Scene title label + lang button label
        if (hasMultiScene) {
            graphics.drawString(font, UIText.of("ponderer.ui.scene_desc.scene_title"), lx, y, lc);
        }
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // Lang button labels
        drawLangLabel(graphics, ponderTitleLangBtn, ponderTitleLang);
        if (hasMultiScene) {
            drawLangLabel(graphics, sceneTitleLangBtn, sceneTitleLang);
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
        graphics.drawCenteredString(font, label, btn.getX() + 14, btn.getY() + 2, 0xAAFFAA);
    }

    private void onConfirm() {
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

        SceneStore.saveSceneToLocal(scene);
        returnToParent();
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
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
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
