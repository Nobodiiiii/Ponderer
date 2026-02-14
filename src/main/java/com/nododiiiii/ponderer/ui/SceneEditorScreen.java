package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ponder editor screen - shows all steps in the current scene.
 * Each row has small inline buttons: [^] [v] [x] for move-up, move-down,
 * delete.
 * Click the row text area to edit. Hover to see config details tooltip.
 * "+ Add Step" opens the type selector; "Split" inserts a new scene.
 */
public class SceneEditorScreen extends AbstractSimiScreen {

    private static final int WINDOW_W = 280;
    private static final int WINDOW_H = 240;
    private static final int STEP_ROW_HEIGHT = 18;
    /** Width reserved on the right side for inline action buttons */
    private static final int ACTION_BTN_AREA = 42;
    /** Each small inline button width */
    private static final int SMALL_BTN = 14;

    private static final ResourceLocation ICON_MOVE_UP = ResourceLocation.fromNamespaceAndPath("minecraft",
            "server_list/move_up");
    private static final ResourceLocation ICON_MOVE_DOWN = ResourceLocation.fromNamespaceAndPath("minecraft",
            "server_list/move_down");
    private static final ResourceLocation ICON_DELETE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "container/beacon/cancel");

    private final DslScene scene;
    private int sceneIndex;
    private int scrollOffset = 0;

    /** Row index currently hovered by the mouse (text area only), or -1. */
    private int hoveredRow = -1;
    /** Which action button is hovered: 0=up, 1=down, 2=delete, -1=none */
    private int hoveredAction = -1;

    private BoxWidget addStepButton;
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

        int btnW = 44, btnH = 18;
        int btnY = guiTop + WINDOW_H - 45;
        int gap = 8;
        int bx = guiLeft + 8;

        // "Desc" opens the scene description editor
        descButton = new PonderButton(bx, btnY, btnW, btnH);
        descButton.withCallback(() -> ScreenOpener.open(new SceneDescEditorScreen(scene, sceneIndex, this)));
        addRenderableWidget(descButton);
        bx += btnW + gap;

        // "+ Add Step" opens the type-selector popup
        addStepButton = new PonderButton(bx, btnY, btnW, btnH);
        addStepButton.withCallback(() -> ScreenOpener.open(new StepTypeSelectorScreen(scene, sceneIndex, this)));
        addRenderableWidget(addStepButton);
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
        backButton = new PonderButton(guiLeft + WINDOW_W - btnW - 8, btnY, btnW, btnH);
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
        this.init(Minecraft.getInstance(), this.width, this.height);
    }

    /* -------- Render -------- */

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background
        new BoxElement()
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 100)
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

                // Inline action buttons: [^] [v] [x]
                int bx = tr + 2;
                int by = y + 1;
                drawSmallButton(graphics, bx, by, "^", 0, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + SMALL_BTN, by, "v", 1, i, mouseX, mouseY);
                drawSmallButton(graphics, bx + 2 * SMALL_BTN, by, "x", 2, i, mouseX, mouseY);
            }
        }

        // Scroll indicator
        if (steps.size() > maxVisible()) {
            String hint = "(" + (scrollOffset + 1) + "-"
                    + Math.min(scrollOffset + maxVisible(), steps.size()) + " / " + steps.size() + ")";
            graphics.drawString(font, hint, guiLeft + WINDOW_W - 10 - font.width(hint), guiTop + 8, 0x808080);
        }

        // Footer hint
        graphics.drawString(font, UIText.of("ponderer.ui.scene_editor.hint"),
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
        int btnHalfW = 22;
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.desc"),
                descButton.getX() + btnHalfW, descButton.getY() + 5, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.add"),
                addStepButton.getX() + btnHalfW, addStepButton.getY() + 5, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.split"),
                splitButton.getX() + btnHalfW, splitButton.getY() + 5, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.delete_scene"),
                deleteSceneButton.getX() + btnHalfW, deleteSceneButton.getY() + 5, 0xFF5555);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.scene_editor.back"),
                backButton.getX() + btnHalfW, backButton.getY() + 5, 0xFFFFFF);

        graphics.pose().popPose();
    }

    private void drawSmallButton(GuiGraphics graphics, int x, int y, String label,
            int actionId, int rowIndex, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + SMALL_BTN
                && mouseY >= y && mouseY < y + STEP_ROW_HEIGHT - 2;
        if (hovered) {
            hoveredRow = -1; // prevent text-area hover when on button
            hoveredAction = actionId;
            // Store the row for action handling
            hoveredActionRow = rowIndex;
        }
        int bg = hovered ? 0x60_FFFFFF : 0x30_FFFFFF;
        graphics.fill(x, y, x + SMALL_BTN, y + STEP_ROW_HEIGHT - 2, bg);

        ResourceLocation icon = switch (actionId) {
            case 0 -> ICON_MOVE_UP;
            case 1 -> ICON_MOVE_DOWN;
            case 2 -> ICON_DELETE;
            default -> null;
        };

        if (icon != null) {
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            if (actionId == 2) {
                // Delete icon (14x14 fits the button perfectly)
                graphics.blitSprite(icon, x, y + 1, 14, 14);
            } else {
                // Move Up/Down icons (using 24x24 as requested, centered on the 14x16 button)
                graphics.blitSprite(icon, x, y - 4, 24, 24);
            }
            graphics.pose().popPose();
        } else {
            var font = Minecraft.getInstance().font;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            graphics.drawCenteredString(font, label, x + SMALL_BTN / 2, y + 4, 0xFFFFFFFF);
            graphics.pose().popPose();
        }
    }

    /** Temp field to track which row the hovered action button belongs to. */
    private int hoveredActionRow = -1;

    /* -------- Input -------- */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check action buttons first
            if (hoveredAction >= 0 && hoveredActionRow >= 0) {
                List<DslScene.DslStep> steps = getSteps();
                if (hoveredActionRow < steps.size()) {
                    switch (hoveredAction) {
                        case 0 -> moveStepUp(hoveredActionRow);
                        case 1 -> moveStepDown(hoveredActionRow);
                        case 2 -> removeStepAndSave(hoveredActionRow);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<DslScene.DslStep> steps = getSteps();
        clampScrollOffset(steps.size());
        int mv = maxVisible();
        if (steps.size() > mv) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) scrollY, steps.size() - mv));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        getMutableSteps().add(newStep);
        saveToFile();
    }

    /** Replace an existing step at the given index and save. */
    public void replaceStepAndSave(int index, DslScene.DslStep newStep) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
            steps.set(index, newStep);
            saveToFile();
        }
    }

    /** Remove a step at the given index and save. */
    public void removeStepAndSave(int index) {
        List<DslScene.DslStep> steps = getMutableSteps();
        if (index >= 0 && index < steps.size()) {
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
        int newSceneIndex = -1;

        if (scene.scenes != null && !scene.scenes.isEmpty()
                && sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
            // scenes[] mode: create a new scene after the current one
            DslScene.SceneSegment newScene = new DslScene.SceneSegment();
            newScene.steps = new ArrayList<>();
            newScene.id = "new_" + (scene.scenes.size() + 1);
            if (!(scene.scenes instanceof ArrayList)) {
                scene.scenes = new ArrayList<>(scene.scenes);
            }
            scene.scenes.add(sceneIndex + 1, newScene);
            newSceneIndex = sceneIndex + 1;
        } else {
            // Flat steps mode: insert a next_scene marker
            DslScene.DslStep ns = new DslScene.DslStep();
            ns.type = "next_scene";
            getMutableSteps().add(ns);
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

        mc.execute(() -> {
            PonderIndex.reload();
            if (reopenId != null && PonderIndex.getSceneAccess().doScenesExistForId(reopenId)) {
                mc.setScreen(PonderUI.of(reopenId));
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
