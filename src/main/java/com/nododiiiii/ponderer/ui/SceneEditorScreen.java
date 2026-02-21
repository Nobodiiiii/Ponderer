package com.nododiiiii.ponderer.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ponder editor screen - shows all steps in the current scene.
 * Each row has small inline buttons: [^] [v] [x] [+] [C] for move-up, move-down,
 * delete, insert-after, copy.
 * Click the row text area to edit. Hover to see config details tooltip.
 * "+ Add Step" opens the type selector; "Split" inserts a new scene.
 * Supports undo/redo via Ctrl+Z / Ctrl+Y.
 */
public class SceneEditorScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 280;
    private static final int WINDOW_H = 240;
    private static final int STEP_ROW_HEIGHT = 18;
    /** Width reserved on the right side for inline action buttons: 6 buttons x 14px */
    private static final int ACTION_BTN_AREA = 84;
    /** Each small inline button width */
    private static final int SMALL_BTN = 14;

    /** Client-side clipboard for copy/paste. Holds a deep-copied step. */
    private static DslScene.DslStep clipboard = null;

    private static final Gson STEP_GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    private final DslScene scene;
    private int sceneIndex;
    private int scrollOffset = 0;
    private final UndoManager undoManager = new UndoManager();

    /** Row index currently hovered by the mouse (text area only), or -1. */
    private int hoveredRow = -1;
    /** Which action button is hovered: 0=up, 1=down, 2=insert, 3=copy, 4=paste, 5=delete, -1=none */
    private int hoveredAction = -1;
    /** Temp field to track which row the hovered action button belongs to. */
    private int hoveredActionRow = -1;

    private BoxWidget addStepButton;
    private BoxWidget pasteButton;
    private BoxWidget splitButton;
    private BoxWidget backButton;
    private BoxWidget descButton;
    private BoxWidget deleteSceneButton;
    private BoxWidget prevSceneBtn;
    private BoxWidget nextSceneBtn;

    public SceneEditorScreen(DslScene scene, int sceneIndex) {
        super(Component.translatable("ponderer.ui.scene_editor"));
        this.scene = scene;
        this.sceneIndex = sceneIndex;
    }

    /* -------- Clipboard helpers -------- */

    private static DslScene.DslStep deepCopy(DslScene.DslStep step) {
        return STEP_GSON.fromJson(STEP_GSON.toJson(step), DslScene.DslStep.class);
    }

    /* -------- Geometry helpers -------- */

    private int listTop() {
        return guiTop + (getSceneCount() > 1 ? 35 : 25);
    }

    private int listBottom() {
        return guiTop + WINDOW_H - 55;
    }

    private int maxVisible() {
        return (listBottom() - listTop()) / STEP_ROW_HEIGHT;
    }

    private int textRight() {
        return guiLeft + WINDOW_W - 8 - ACTION_BTN_AREA;
    }

    /* -------- Init -------- */

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, WINDOW_H);
        super.init();

        // Bottom bar: 6 buttons (Desc, Add, Paste, Split, Del, Back)
        int btnW = 38, btnH = 18;
        int btnY = guiTop + WINDOW_H - 45;
        int gap = 6;
        int bx = guiLeft + 6;

        // "Desc" opens the scene description editor
        descButton = new PonderButton(bx, btnY, btnW, btnH);
        descButton.withCallback(() -> ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this)));
        addRenderableWidget(descButton);
        bx += btnW + gap;

        // "+ Add Step" opens the type-selector popup (append mode)
        addStepButton = new PonderButton(bx, btnY, btnW, btnH);
        addStepButton.withCallback(() -> ScreenOpener.open(new StepTypeSelectorScreen(scene, sceneIndex, this)));
        addRenderableWidget(addStepButton);
        bx += btnW + gap;

        // "Paste" inserts the clipboard step
        pasteButton = new PonderButton(bx, btnY, btnW, btnH);
        pasteButton.withCallback(() -> {
            if (clipboard != null) {
                DslScene.DslStep pasted = deepCopy(clipboard);
                insertStepAndSave(-1, pasted);
                this.init(Minecraft.getInstance(), this.width, this.height);
            }
        });
        addRenderableWidget(pasteButton);
        bx += btnW + gap;

        // "Split" inserts a next_scene step at the end
        splitButton = new PonderButton(bx, btnY, btnW, btnH);
        splitButton.withCallback(this::insertSplitStep);
        addRenderableWidget(splitButton);
        bx += btnW + gap;

        // "Delete" - delete entire scene with confirmation
        deleteSceneButton = new PonderButton(bx, btnY, btnW, btnH);
        deleteSceneButton.withCallback(this::confirmDeleteScene);
        addRenderableWidget(deleteSceneButton);

        // "Back" - reload Ponder scenes and exit
        backButton = new PonderButton(guiLeft + WINDOW_W - btnW - 6, btnY, btnW, btnH);
        backButton.withCallback(this::reloadAndExit);
        addRenderableWidget(backButton);

        // Scene navigation buttons [<] [>] in the title bar area
        int sceneCount = getSceneCount();
        if (sceneCount > 1) {
            int navBtnW = 14, navBtnH = 12;
            prevSceneBtn = new PonderButton(guiLeft + WINDOW_W - navBtnW * 2 - 14, guiTop + 15, navBtnW, navBtnH);
            prevSceneBtn.withCallback(() -> switchScene(sceneIndex - 1));
            addRenderableWidget(prevSceneBtn);

            nextSceneBtn = new PonderButton(guiLeft + WINDOW_W - navBtnW - 8, guiTop + 15, navBtnW, navBtnH);
            nextSceneBtn.withCallback(() -> switchScene(sceneIndex + 1));
            addRenderableWidget(nextSceneBtn);
        } else {
            prevSceneBtn = null;
            nextSceneBtn = null;
        }
    }

    private int getSceneCount() {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes.size();
        }
        return getScenes().size();
    }

    private void switchScene(int newIndex) {
        int count = getSceneCount();
        if (count <= 1)
            return;
        // Wrap around
        if (newIndex < 0)
            newIndex = count - 1;
        if (newIndex >= count)
            newIndex = 0;
        sceneIndex = newIndex;
        scrollOffset = 0;
        undoManager.clear();
        this.init(Minecraft.getInstance(), this.width, this.height);
    }

    /* -------- Render -------- */

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

        // Title - show ponder title (resolved), scene info on second line
        int sceneCount = getSceneCount();
        String sceneTitle = (scene.title != null && !scene.title.isEmpty())
                ? scene.title.resolve()
                : scene.id;
        String titleText = UIText.of("ponderer.ui.scene_editor.title", sceneTitle);
        int titleMaxW = WINDOW_W - 20;
        if (sceneCount > 1)
            titleMaxW -= 44; // reserve space for nav buttons
        String clippedTitle = font.plainSubstrByWidth(titleText, titleMaxW);
        graphics.drawString(font, clippedTitle, guiLeft + 10, guiTop + 4, 0xFFFFFF);

        // Second line: scene pagination (only when multi-scene)
        if (sceneCount > 1) {
            String sceneName = "";
            if (sceneIndex >= 0 && sceneIndex < sceneCount) {
                if (scene.scenes != null && sceneIndex < scene.scenes.size()) {
                    DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
                    if (sc.title != null && !sc.title.isEmpty()) {
                        sceneName = sc.title.resolve();
                    } else {
                        sceneName = sc.id != null ? sc.id : String.valueOf(sceneIndex + 1);
                    }
                } else {
                    sceneName = String.valueOf(sceneIndex + 1);
                }
            }
            String pageInfo = "[" + (sceneIndex + 1) + "/" + sceneCount + ": " + sceneName + "]";
            graphics.drawString(font, pageInfo, guiLeft + 10, guiTop + 16, 0xA0A0A0);
        }

        // (nav button labels drawn in renderWindowForeground)

        // Separator
        int separatorY = sceneCount > 1 ? guiTop + 30 : guiTop + 20;
        graphics.fill(guiLeft + 5, separatorY, guiLeft + WINDOW_W - 5, separatorY + 1, 0x60_FFFFFF);

        // Steps list
        List<DslScene.DslStep> steps = getSteps();
        clampScrollOffset(steps.size());
        int lt = listTop();
        int tr = textRight();

        hoveredRow = -1;
        hoveredAction = -1;

        if (steps.isEmpty()) {
            graphics.drawString(font, UIText.of("ponderer.ui.scene_editor.no_steps"), guiLeft + 10, lt + 4, 0x808080);
        } else {
            int end = Math.min(steps.size(), scrollOffset + maxVisible());
            for (int i = scrollOffset; i < end; i++) {
                int y = lt + (i - scrollOffset) * STEP_ROW_HEIGHT;
                DslScene.DslStep step = steps.get(i);
                String label = (i + 1) + ". " + formatStep(step);

                // Hit-test text area
                boolean textHover = mouseX >= guiLeft + 5 && mouseX < tr
                        && mouseY >= y && mouseY < y + STEP_ROW_HEIGHT;
                if (textHover)
                    hoveredRow = i;

                // Background stripe
                if (textHover) {
                    graphics.fill(guiLeft + 5, y, guiLeft + WINDOW_W - 5, y + STEP_ROW_HEIGHT, 0x40_80a0ff);
                } else if ((i % 2) == 0) {
                    graphics.fill(guiLeft + 5, y, guiLeft + WINDOW_W - 5, y + STEP_ROW_HEIGHT, 0x20_FFFFFF);
                }

                // Label text (clipped to text area)
                String clipped = font.plainSubstrByWidth(label, tr - guiLeft - 12);
                graphics.drawString(font, clipped, guiLeft + 10, y + 5, textHover ? 0xFFFFFF : 0xDDDDDD);

                // Inline action buttons: [^] [v] [+] [C] [P] [x]
                int bx = tr + 2;
                int by = y + 1;
                drawSmallButton(graphics, bx, by, "^", 0, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + SMALL_BTN, by, "v", 1, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + 2 * SMALL_BTN, by, "+", 2, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + 3 * SMALL_BTN, by, "C", 3, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + 4 * SMALL_BTN, by, "P", 4, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + 5 * SMALL_BTN, by, "x", 5, i, mouseX, mouseY);
            }
        }

        // Scroll indicator (right-aligned on the redo hint line)
        if (steps.size() > maxVisible()) {
            String hint = "(" + (scrollOffset + 1) + "-"
                    + Math.min(scrollOffset + maxVisible(), steps.size()) + " / " + steps.size() + ")";
            graphics.drawString(font, hint, guiLeft + WINDOW_W - 10 - font.width(hint), guiTop + WINDOW_H - 20, 0x808080);
        }

        // Footer hint (undo/redo always shown)
        graphics.drawString(font, UIText.of("ponderer.ui.scene_editor.hint_undo"),
                guiLeft + 10, guiTop + WINDOW_H - 20, 0x606060);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // Nav button labels
        if (getSceneCount() > 1 && prevSceneBtn != null && nextSceneBtn != null) {
            graphics.drawCenteredString(font, "<",
                    prevSceneBtn.getX() + 7, prevSceneBtn.getY() + 2, 0xFFFFFF);
            graphics.drawCenteredString(font, ">",
                    nextSceneBtn.getX() + 7, nextSceneBtn.getY() + 2, 0xFFFFFF);
        }

        // Button labels
        int btnHalfW = 19;
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.desc"),
                descButton.getX() + btnHalfW, descButton.getY() + 5, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.add"),
                addStepButton.getX() + btnHalfW, addStepButton.getY() + 5, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.paste"),
                pasteButton.getX() + btnHalfW, pasteButton.getY() + 5,
                clipboard != null ? 0xFFFFFF : 0x808080);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.split"),
                splitButton.getX() + btnHalfW, splitButton.getY() + 5, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.delete_scene"),
                deleteSceneButton.getX() + btnHalfW, deleteSceneButton.getY() + 5, 0xFF5555);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.back"),
                backButton.getX() + btnHalfW, backButton.getY() + 5, 0xFFFFFF);

        graphics.pose().popPose();

        // Tooltip for hovered inline action button
        if (hoveredAction >= 0) {
            String tooltipKey = switch (hoveredAction) {
                case 0 -> "ponderer.ui.scene_editor.btn.move_up";
                case 1 -> "ponderer.ui.scene_editor.btn.move_down";
                case 2 -> "ponderer.ui.scene_editor.btn.insert";
                case 3 -> "ponderer.ui.scene_editor.btn.copy";
                case 4 -> "ponderer.ui.scene_editor.btn.paste_after";
                case 5 -> "ponderer.ui.scene_editor.btn.delete";
                default -> null;
            };
            if (tooltipKey != null) {
                String tip = UIText.of(tooltipKey);
                int tw = font.width(tip) + 8;
                int tx = mouseX + 10;
                int ty = mouseY - 14;
                if (tx + tw > this.width) tx = mouseX - tw - 4;
                if (ty < 0) ty = mouseY + 16;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 600);
                graphics.fill(tx - 2, ty - 2, tx + tw + 2, ty + 12, 0xF0_100020);
                graphics.fill(tx - 1, ty - 1, tx + tw + 1, ty + 11, 0xC0_5040a0);
                graphics.fill(tx, ty, tx + tw, ty + 10, 0xF0_100020);
                graphics.drawString(font, tip, tx + 4, ty + 1, 0xFF_CCCCCC);
                graphics.pose().popPose();
            }
        }
    }

    private void drawSmallButton(GuiGraphics graphics, int x, int y, String label,
            int actionId, int rowIndex, int mouseX, int mouseY) {
        boolean disabled = isActionDisabled(actionId, rowIndex);
        boolean hovered = !disabled && mouseX >= x && mouseX < x + SMALL_BTN
                && mouseY >= y && mouseY < y + STEP_ROW_HEIGHT - 2;
        if (hovered) {
            hoveredRow = -1; // prevent text-area hover when on button
            hoveredAction = actionId;
            hoveredActionRow = rowIndex;
        }
        int bg = disabled ? 0x10_FFFFFF : (hovered ? 0x60_FFFFFF : 0x30_FFFFFF);
        graphics.fill(x, y, x + SMALL_BTN, y + STEP_ROW_HEIGHT - 2, bg);

        int iconColor;
        if (disabled) {
            iconColor = 0xFF_404040;
        } else {
            iconColor = switch (actionId) {
                case 0 -> 0xFF_FFFFFF; // move up
                case 1 -> 0xFF_FFFFFF; // move down
                case 2 -> 0xFF_80FF80; // green for insert
                case 3 -> 0xFF_80C0FF; // blue for copy
                case 4 -> clipboard != null ? 0xFF_FFD080 : 0xFF_606060; // orange for paste, gray when empty
                case 5 -> 0xFF_FF5555; // red for delete
                default -> 0xFFFFFFFF;
            };
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        if (actionId == 0) {
            // Up arrow triangle icon
            int cx = x + SMALL_BTN / 2;
            int cy = y + (STEP_ROW_HEIGHT - 2) / 2;
            drawUpArrow(graphics, cx, cy, iconColor);
        } else if (actionId == 1) {
            // Down arrow triangle icon
            int cx = x + SMALL_BTN / 2;
            int cy = y + (STEP_ROW_HEIGHT - 2) / 2;
            drawDownArrow(graphics, cx, cy, iconColor);
        } else {
            var font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, label, x + SMALL_BTN / 2, y + 4, iconColor);
        }

        graphics.pose().popPose();
    }

    /** Draw a small upward-pointing triangle centered at (cx, cy). */
    private void drawUpArrow(GuiGraphics graphics, int cx, int cy, int color) {
        graphics.fill(cx - 1, cy - 2, cx + 1, cy - 1, color);
        graphics.fill(cx - 2, cy - 1, cx + 2, cy,     color);
        graphics.fill(cx - 3, cy,     cx + 3, cy + 1,  color);
        graphics.fill(cx - 4, cy + 1, cx + 4, cy + 2,  color);
    }

    /** Draw a small downward-pointing triangle centered at (cx, cy). */
    private void drawDownArrow(GuiGraphics graphics, int cx, int cy, int color) {
        graphics.fill(cx - 4, cy - 2, cx + 4, cy - 1, color);
        graphics.fill(cx - 3, cy - 1, cx + 3, cy,     color);
        graphics.fill(cx - 2, cy,     cx + 2, cy + 1,  color);
        graphics.fill(cx - 1, cy + 1, cx + 1, cy + 2,  color);
    }

    /**
     * Returns true if the given action should be disabled for the step at rowIndex.
     * Every scene segment's first show_structure cannot be deleted or moved.
     * The second step's move-up is also disabled to prevent swapping with the protected first step.
     */
    private boolean isActionDisabled(int actionId, int rowIndex) {
        List<DslScene.DslStep> steps = getSteps();
        if (steps.isEmpty()) return false;

        DslScene.DslStep first = steps.get(0);
        if (first == null || !"show_structure".equalsIgnoreCase(first.type)) return false;

        // First step: disable delete, move-up, move-down
        if (rowIndex == 0 && (actionId == 5 || actionId == 0 || actionId == 1)) {
            return true;
        }
        // Second step: disable move-up (prevents swapping above the protected first step)
        if (rowIndex == 1 && actionId == 0) {
            return true;
        }
        return false;
    }

    /* -------- Input -------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check action buttons first
            if (hoveredAction >= 0 && hoveredActionRow >= 0) {
                List<DslScene.DslStep> steps = getSteps();
                if (hoveredActionRow < steps.size() && !isActionDisabled(hoveredAction, hoveredActionRow)) {
                    switch (hoveredAction) {
                        case 0 -> moveStepUp(hoveredActionRow);
                        case 1 -> moveStepDown(hoveredActionRow);
                        case 2 -> openInsertAfter(hoveredActionRow);
                        case 3 -> copyStep(hoveredActionRow);
                        case 4 -> pasteAfter(hoveredActionRow);
                        case 5 -> removeStepAndSave(hoveredActionRow);
                    }
                    return true;
                }
            }
            // Text area click -> edit
            if (hoveredRow >= 0) {
                List<DslScene.DslStep> steps = getSteps();
                if (hoveredRow < steps.size()) {
                    DslScene.DslStep step = steps.get(hoveredRow);
                    AbstractStepEditorScreen editor = StepEditorFactory.createEditScreen(step, hoveredRow, scene,
                            sceneIndex, this);
                    if (editor != null) {
                        ScreenOpener.open(editor);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<DslScene.DslStep> steps = getSteps();
        clampScrollOffset(steps.size());
        int mv = maxVisible();
        if (steps.size() > mv) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, steps.size() - mv));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                performUndo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                performRedo();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clampScrollOffset(int stepCount) {
        int maxOffset = Math.max(0, stepCount - maxVisible());
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
    }

    /* -------- Copy / Insert helpers -------- */

    private void copyStep(int index) {
        List<DslScene.DslStep> steps = getSteps();
        if (index >= 0 && index < steps.size()) {
            clipboard = deepCopy(steps.get(index));
        }
    }

    private void openInsertAfter(int afterIndex) {
        ScreenOpener.open(new StepTypeSelectorScreen(scene, sceneIndex, this, 0, afterIndex));
    }

    private void pasteAfter(int afterIndex) {
        if (clipboard != null) {
            DslScene.DslStep pasted = deepCopy(clipboard);
            insertStepAndSave(afterIndex, pasted);
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    /* -------- Undo / Redo -------- */

    private void performUndo() {
        List<DslScene.DslStep> restored = undoManager.undo(getSteps());
        if (restored != null) {
            setMutableSteps(restored);
            saveToFile();
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    private void performRedo() {
        List<DslScene.DslStep> restored = undoManager.redo(getSteps());
        if (restored != null) {
            setMutableSteps(restored);
            saveToFile();
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    /** Replace the step list for the current scene/segment. */
    private void setMutableSteps(List<DslScene.DslStep> newSteps) {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            if (sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
                scene.scenes.get(sceneIndex).steps = new ArrayList<>(newSteps);
                return;
            }
        }
        scene.steps = new ArrayList<>(newSteps);
    }

    /* -------- Step list helpers -------- */

    private List<DslScene.DslStep> getSteps() {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            // Direct access to the scenes[] array (no virtual splitting)
            if (sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
                List<DslScene.DslStep> steps = scene.scenes.get(sceneIndex).steps;
                return steps != null ? steps : List.of();
            }
        }
        // Flat steps mode: use getScenes() which splits by next_scene
        List<DslScene.SceneSegment> scenes = getScenes();
        if (sceneIndex >= 0 && sceneIndex < scenes.size()) {
            List<DslScene.DslStep> steps = scenes.get(sceneIndex).steps;
            return steps != null ? steps : List.of();
        }
        return scene.steps != null ? scene.steps : List.of();
    }

    /**
     * Returns a mutable steps list for the current scene. Creates/wraps as needed.
     */
    private List<DslScene.DslStep> getMutableSteps() {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            if (sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
                DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
                if (sc.steps == null) {
                    sc.steps = new ArrayList<>();
                } else if (!(sc.steps instanceof ArrayList)) {
                    sc.steps = new ArrayList<>(sc.steps);
                }
                return sc.steps;
            }
        }
        if (scene.steps == null) {
            scene.steps = new ArrayList<>();
        } else if (!(scene.steps instanceof ArrayList)) {
            scene.steps = new ArrayList<>(scene.steps);
        }
        return scene.steps;
    }

    private List<DslScene.SceneSegment> getScenes() {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes;
        }
        List<DslScene.SceneSegment> result = new ArrayList<>();
        DslScene.SceneSegment current = new DslScene.SceneSegment();
        current.steps = new ArrayList<>();

        if (scene.steps != null) {
            for (DslScene.DslStep step : scene.steps) {
                if (step != null && step.type != null && "next_scene".equalsIgnoreCase(step.type)) {
                    if (!current.steps.isEmpty())
                        result.add(current);
                    current = new DslScene.SceneSegment();
                    current.steps = new ArrayList<>();
                    continue;
                }
                current.steps.add(step);
            }
        }
        if (!current.steps.isEmpty())
            result.add(current);
        if (result.isEmpty()) {
            DslScene.SceneSegment fallback = new DslScene.SceneSegment();
            fallback.steps = new ArrayList<>();
            result.add(fallback);
        }
        return result;
    }

    private String formatStep(DslScene.DslStep step) {
        if (step == null || step.type == null)
            return UIText.of("ponderer.ui.invalid");
        return switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" ->
                UIText.of("ponderer.ui.step.summary.show_structure", stepTypeName("show_structure"));
            case "idle" -> UIText.of("ponderer.ui.step.summary.idle", stepTypeName("idle"), step.durationOrDefault(20),
                    UIText.of("ponderer.ui.ticks"));
            case "text" -> UIText.of("ponderer.ui.step.summary.text", stepTypeName("text"),
                    truncate(step.text != null ? step.text.resolve() : "", 20));
            case "shared_text" -> UIText.of("ponderer.ui.step.summary.shared_text", stepTypeName("shared_text"),
                    step.key != null ? step.key : "?");
            case "create_entity" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("create_entity"),
                    step.entity != null ? step.entity : "?");
            case "create_item_entity" -> UIText.of("ponderer.ui.step.summary.single_arg",
                    stepTypeName("create_item_entity"), step.item != null ? step.item : "?");
            case "rotate_camera_y" -> UIText.of("ponderer.ui.step.summary.rotate_camera",
                    stepTypeName("rotate_camera_y"), step.degrees != null ? step.degrees : 90);
            case "show_controls" -> UIText.of(
                    "ponderer.ui.step.summary.show_controls_action_item",
                    stepTypeName("show_controls"),
                    controlActionName(step.action),
                    step.item != null && !step.item.isBlank() ? step.item : UIText.of("ponderer.ui.none"));
            case "encapsulate_bounds" -> stepTypeName("encapsulate_bounds");
            case "play_sound" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("play_sound"),
                    step.sound != null ? step.sound : "?");
            case "set_block" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("set_block"),
                    step.block != null ? step.block : "?");
                case "destroy_block" -> stepTypeName("destroy_block");
                case "replace_blocks" -> UIText.of("ponderer.ui.step.summary.single_arg", stepTypeName("replace_blocks"),
                    step.block != null ? step.block : "?");
                case "hide_section" -> stepTypeName("hide_section");
                case "show_section_and_merge" -> stepTypeName("show_section_and_merge");
                case "toggle_redstone_power" -> stepTypeName("toggle_redstone_power");
                case "modify_block_entity_nbt" -> stepTypeName("modify_block_entity_nbt");
                case "rotate_section" -> stepTypeName("rotate_section");
                case "move_section" -> stepTypeName("move_section");
                case "indicate_redstone" -> stepTypeName("indicate_redstone");
                case "indicate_success" -> stepTypeName("indicate_success");
            case "next_scene" -> UIText.of("ponderer.ui.step.summary.next_scene");
            default -> step.type;
        };
    }

    private String stepTypeName(String type) {
        return UIText.of("ponderer.ui.step.type." + type);
    }

    private String controlActionName(String action) {
        if (action == null || action.isBlank()) {
            return UIText.of("ponderer.ui.none");
        }
        String key = "ponderer.ui.show_controls.action." + action.toLowerCase(Locale.ROOT);
        String translated = UIText.of(key);
        return key.equals(translated) ? action : translated;
    }

    private String truncate(String text, int max) {
        if (text == null)
            return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    /* -------- Persistence (called by child editors) -------- */

    /** Append a new step and save. */
    public void addStepAndSave(DslScene.DslStep newStep) {
        insertStepAndSave(-1, newStep);
    }

    /** Insert a new step after the given index and save. If afterIndex is -1, append. */
    public void insertStepAndSave(int afterIndex, DslScene.DslStep newStep) {
        undoManager.saveState(getSteps());
        List<DslScene.DslStep> steps = getMutableSteps();
        if (afterIndex >= 0 && afterIndex < steps.size()) {
            steps.add(afterIndex + 1, newStep);
        } else {
            steps.add(newStep);
        }
        saveToFile();
    }

    /** Replace an existing step at the given index and save. */
    public void replaceStepAndSave(int index, DslScene.DslStep newStep) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
            undoManager.saveState(getSteps());
            steps.set(index, newStep);
            saveToFile();
        }
    }

    /** Remove a step at the given index and save. */
    public void removeStepAndSave(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
            undoManager.saveState(getSteps());
            steps.remove(index);
            clampScrollOffset(steps.size());
            saveToFile();
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    /** Swap step at index with the one above. */
    private void moveStepUp(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index > 0 && index < steps.size()) {
            undoManager.saveState(getSteps());
            DslScene.DslStep temp = steps.get(index);
            steps.set(index, steps.get(index - 1));
            steps.set(index - 1, temp);
            saveToFile();
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    /** Swap step at index with the one below. */
    private void moveStepDown(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size() - 1) {
            undoManager.saveState(getSteps());
            DslScene.DslStep temp = steps.get(index);
            steps.set(index, steps.get(index + 1));
            steps.set(index + 1, temp);
            saveToFile();
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    /**
     * Split: creates a new scene in scenes[] or inserts next_scene marker for flat
     * steps.
     */
    private void insertSplitStep() {
        undoManager.saveState(getSteps());
        int newSceneIndex = -1;

        if (scene.scenes != null && !scene.scenes.isEmpty()
                && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            // scenes[] mode: create a new scene after the current one
            DslScene.SceneSegment newScene = new DslScene.SceneSegment();
            newScene.steps = new ArrayList<>();
            // Auto-populate with show_structure + idle(20t)
            DslScene.DslStep showStep = new DslScene.DslStep();
            showStep.type = "show_structure";
            newScene.steps.add(showStep);
            DslScene.DslStep idleStep = new DslScene.DslStep();
            idleStep.type = "idle";
            idleStep.duration = 20;
            newScene.steps.add(idleStep);
            newScene.id = "new_" + (scene.scenes.size() + 1);
            if (!(scene.scenes instanceof ArrayList)) {
                scene.scenes = new ArrayList<>(scene.scenes);
            }
            scene.scenes.add(sceneIndex + 1, newScene);
            newSceneIndex = sceneIndex + 1;
        } else {
            // Flat steps mode: insert a next_scene marker + show_structure + idle(20t)
            DslScene.DslStep ns = new DslScene.DslStep();
            ns.type = "next_scene";
            getMutableSteps().add(ns);
            DslScene.DslStep showStep = new DslScene.DslStep();
            showStep.type = "show_structure";
            getMutableSteps().add(showStep);
            DslScene.DslStep idleStep = new DslScene.DslStep();
            idleStep.type = "idle";
            idleStep.duration = 20;
            getMutableSteps().add(idleStep);
        }
        saveToFile();

        if (newSceneIndex >= 0) {
            // Switch to the new scene and open description editor
            sceneIndex = newSceneIndex;
            scrollOffset = 0;
            ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this));
        } else {
            this.init(Minecraft.getInstance(), this.width, this.height);
        }
    }

    /** Save the scene JSON to file without reloading Ponder. */
    private void saveToFile() {
        SceneStore.saveSceneToLocal(scene);
    }

    /**
     * Reload Ponder scenes from disk and reopen the Ponder UI. Called on explicit
     * exit.
     */
    private void confirmDeleteScene() {
        Minecraft mc = Minecraft.getInstance();
        int sceneCount = getSceneCount();

        if (sceneCount > 1 && scene.scenes != null && !scene.scenes.isEmpty()) {
            // Multiple scenes: delete current scene only
            String sceneName = "";
            if (sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
                DslScene.SceneSegment sc = scene.scenes.get(sceneIndex);
                if (sc.title != null && !sc.title.isEmpty()) {
                    sceneName = sc.title.resolve();
                } else {
                    sceneName = sc.id != null ? sc.id : String.valueOf(sceneIndex + 1);
                }
            }
            mc.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            deleteCurrentScene();
                        } else {
                            mc.setScreen(this);
                        }
                    },
                    Component.translatable("ponderer.ui.scene_editor.delete_scene_title"),
                    Component.translatable("ponderer.ui.scene_editor.delete_scene_msg", sceneName)));
        } else {
            // Single scene / flat steps: deleting means deleting the whole ponder
            mc.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            deletePonderAndExit();
                        } else {
                            mc.setScreen(this);
                        }
                    },
                    Component.translatable("ponderer.ui.scene_editor.delete_ponder_title"),
                    Component.translatable("ponderer.ui.scene_editor.delete_ponder_msg", scene.id)));
        }
    }

    private void deleteCurrentScene() {
        if (scene.scenes != null && scene.scenes.size() > 1
                && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            if (!(scene.scenes instanceof ArrayList)) {
                scene.scenes = new ArrayList<>(scene.scenes);
            }
            scene.scenes.remove(sceneIndex);
            // Adjust index
            if (sceneIndex >= scene.scenes.size()) {
                sceneIndex = scene.scenes.size() - 1;
            }
            saveToFile();
            scrollOffset = 0;
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(this);
        }
    }

    private void deletePonderAndExit() {
        boolean deleted = SceneStore.deleteSceneLocal(scene.id);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        if (deleted) {
            SceneStore.reloadFromDisk();
            mc.execute(PonderIndex::reload);
        }
    }

    private void reloadAndExit() {
        // Close this screen immediately to avoid overlap
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);

        SceneStore.reloadFromDisk();
        ResourceLocation itemId = null;
        if (scene.items != null && !scene.items.isEmpty()) {
            itemId = ResourceLocation.tryParse(scene.items.get(0));
        }
        final ResourceLocation reopenId = itemId;
        final String targetSceneId = computeCurrentSceneId();

        mc.execute(() -> {
            PonderIndex.reload();
            if (reopenId != null && PonderIndex.getSceneAccess().doScenesExistForId(reopenId)) {
                PonderUI ui = PonderUI.of(reopenId);
                if (targetSceneId != null) {
                    navigateToScene(ui, targetSceneId);
                }
                mc.setScreen(ui);
            }
        });
    }

    /**
     * Compute the PonderScene ID that corresponds to the current editor scene index.
     * Mirrors the ID construction logic in DynamicPonderPlugin.
     */
    @Nullable
    private String computeCurrentSceneId() {
        ResourceLocation baseId = ResourceLocation.tryParse(scene.id);
        if (baseId == null) return null;
        String basePath = baseId.getPath();

        List<DslScene.SceneSegment> sceneList = getScenes();
        if (sceneList.size() <= 1) {
            return "ponderer:" + basePath;
        }
        if (sceneIndex < 0 || sceneIndex >= sceneList.size()) {
            return "ponderer:" + basePath;
        }
        DslScene.SceneSegment sc = sceneList.get(sceneIndex);
        String suffix = (sc.id != null && !sc.id.isBlank()) ? sc.id : String.valueOf(sceneIndex + 1);
        return "ponderer:" + basePath + "_" + suffix;
    }

    /**
     * Navigate a PonderUI to the scene matching the given scene ID.
     */
    private static void navigateToScene(PonderUI ui, String targetSceneId) {
        PonderUIAccessor accessor = (PonderUIAccessor) (Object) ui;
        List<PonderScene> scenes = accessor.ponderer$getScenes();
        for (int i = 0; i < scenes.size(); i++) {
            if (targetSceneId.equals(scenes.get(i).getId().toString())) {
                accessor.ponderer$setIndex(i);
                accessor.ponderer$getLazyIndex().chase(i, 1.0f / 3, LerpedFloat.Chaser.EXP);
                accessor.ponderer$getLazyIndex().startWithValue(i);
                break;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
