package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Abstract base class for step editor screens.
 * Supports both "add new step" and "edit existing step" modes.
 * Subclasses implement buildForm(), populateFromStep(), buildStep().
 */
public abstract class AbstractStepEditorScreen extends AbstractSimiScreen implements JeiAwareScreen {

    protected static final int WINDOW_W = 220;
    protected static final int FORM_TOP = 26;
    protected static final int ROW_HEIGHT = 22;
    private static final int BOTTOM_SECTION = 60;

    protected final DslScene scene;
    protected final int sceneIndex;
    protected final SceneEditorScreen parent;
    @Nullable
    protected Screen returnScreen;

    /**
     * If non-negative, we are editing an existing step at this index. Otherwise
     * adding new.
     */
    protected final int editIndex;
    /** The existing step being edited, or null for add mode. */
    @Nullable
    protected final DslScene.DslStep existingStep;

    protected BoxWidget confirmButton;
    protected BoxWidget cancelButton;
    protected String errorMessage = null;
    protected String infoMessage = null;

    /** Common keyframe toggle - available for all step types. */
    protected boolean attachKeyFrame = false;
    private BoxWidget keyFrameToggle;

    /** Subclasses override to hide the keyframe toggle row. */
    protected boolean showsKeyFrame() { return true; }

    /** Index after which to insert the new step. -1 means append. */
    protected int insertAfterIndex = -1;

    // -- Block property list state (used by screens that override usesBlockProps) --
    @Nullable
    protected List<String[]> blockPropEntries;
    @Nullable
    protected List<HintableTextFieldWidget[]> blockPropFields;
    @Nullable
    protected List<PonderButton> blockPropRemoveBtns;
    @Nullable
    protected PonderButton blockPropAddBtn;

    private boolean initialPopulateDone = false;
    @Nullable
    private Map<String, String> pendingReinitRestore = null;

    // -- JEI integration state --
    private boolean jeiActive = false;
    @Nullable
    private HintableTextFieldWidget jeiTargetField = null;
    @Nullable
    private IdFieldMode jeiMode = null;

    /**
     * If non-null, the form should be restored from this snapshot (after a pick operation).
     * Set via {@link #setPendingPickRestore(Map)} before the screen is opened.
     */
    @Nullable
    private Map<String, String> pendingPickRestore = null;

    // -- Scroll support --
    /** Current vertical scroll offset in pixels. */
    protected int scrollOffset = 0;
    /** Maximum scroll offset (0 = no scroll needed). */
    private int maxScroll = 0;
    /** Tracked form widgets and their content-relative Y offsets for scroll repositioning. */
    private final List<FormWidgetRecord> formWidgetRecords = new ArrayList<>();
    private record FormWidgetRecord(AbstractWidget widget, int contentOffsetY) {}
    private final List<StepEditorEntry> formEntries = new ArrayList<>();

    /** Registered tooltip regions: hover over label area to see description. */
    protected final List<TooltipRegion> tooltipRegions = new ArrayList<>();

    protected record TooltipRegion(int x, int y, int w, int h, String text, boolean scrollable) {
        TooltipRegion(int x, int y, int w, int h, String text) {
            this(x, y, w, h, text, true);
        }
        boolean contains(int mx, int my, int scrollOff) {
            int adjustedY = scrollable ? y - scrollOff : y;
            return mx >= x && mx < x + w && my >= adjustedY && my < adjustedY + h;
        }
    }

    /**
     * Create in "add" mode.
     */
    protected AbstractStepEditorScreen(Component title, DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(title, scene, sceneIndex, parent, -1, null);
    }

    /**
     * Create in "edit" mode.
     */
    protected AbstractStepEditorScreen(Component title, DslScene scene, int sceneIndex, SceneEditorScreen parent,
            int editIndex, @Nullable DslScene.DslStep existingStep) {
        super(title);
        this.scene = scene;
        this.sceneIndex = sceneIndex;
        this.parent = parent;
        this.editIndex = editIndex;
        this.existingStep = existingStep;
    }

    protected boolean isEditMode() {
        return editIndex >= 0 && existingStep != null;
    }

    public AbstractStepEditorScreen setReturnScreen(@Nullable Screen returnScreen) {
        this.returnScreen = returnScreen;
        return this;
    }

    public AbstractStepEditorScreen setInsertAfterIndex(int index) {
        this.insertAfterIndex = index;
        return this;
    }

    /**
     * Set a form snapshot to restore when the screen initializes (used after coordinate picking).
     */
    public AbstractStepEditorScreen setPendingPickRestore(@Nullable Map<String, String> snapshot) {
        this.pendingPickRestore = snapshot;
        return this;
    }

    @Override
    protected void init() {
        if (usesBlockProps()) {
            preExtractBlockProps();
        }
        setWindowSize(WINDOW_W, getWindowHeight());
        super.init();
        tooltipRegions.clear();
        formWidgetRecords.clear();

        int wH = getWindowHeight();
        int btnW = 80;
        int btnH = 20;

        confirmButton = new PonderButton(guiLeft + 15, guiTop + wH - 30, btnW, btnH);
        confirmButton.withCallback(this::onConfirm);
        addRenderableWidget(confirmButton);

        cancelButton = new PonderButton(guiLeft + WINDOW_W - btnW - 15, guiTop + wH - 30, btnW, btnH);
        cancelButton.withCallback(this::returnToParent);
        addRenderableWidget(cancelButton);

        // KeyFrame toggle (common to all step types), placed above confirm/cancel
        if (showsKeyFrame()) {
            int kfY = guiTop + wH - 58;
            keyFrameToggle = createToggle(guiLeft + 70, kfY);
            keyFrameToggle.withCallback(() -> attachKeyFrame = !attachKeyFrame);
            addRenderableWidget(keyFrameToggle);
            addLabelTooltip(guiLeft + 10, kfY + 3, UIText.of("ponderer.ui.key_frame"),
                    UIText.of("ponderer.ui.key_frame.tooltip"), false);
        } else {
            keyFrameToggle = null;
        }

        // Record child count before buildForm so we can track form-specific widgets
        int childCountBeforeForm = children().size();

        buildForm();

        // Track form widgets (everything added during buildForm) for scroll repositioning
        List<? extends GuiEventListener> allChildren = children();
        for (int i = childCountBeforeForm; i < allChildren.size(); i++) {
            if (allChildren.get(i) instanceof AbstractWidget aw) {
                int contentOffsetY = aw.getY() - guiTop - FORM_TOP;
                formWidgetRecords.add(new FormWidgetRecord(aw, contentOffsetY));
            }
        }
        updateScrollPositions();

        if (!initialPopulateDone) {
            if (isEditMode()) {
                populateFromStep(existingStep);
            }

            // If returning from a pick operation, restore the saved form snapshot
            if (pendingPickRestore != null) {
                restoreFromSnapshot(pendingPickRestore);
                pendingPickRestore = null;
            }
            initialPopulateDone = true;
        }

        // After reinit (add/remove prop entry), restore non-prop field values
        if (pendingReinitRestore != null) {
            restoreFromSnapshot(pendingReinitRestore);
            pendingReinitRestore = null;
        }
    }

    /** Computes window height from form row count, clamped to screen height. */
    protected int getWindowHeight() {
        int bottomH = showsKeyFrame() ? BOTTOM_SECTION : 38;
        int contentH = FORM_TOP + getFormRowCount() * ROW_HEIGHT + bottomH;
        if (height <= 0) {
            maxScroll = 0;
            return contentH;
        }
        int maxH = height - UILayoutConstants.SCREEN_MARGIN * 2;
        if (contentH <= maxH) {
            maxScroll = 0;
            return contentH;
        }
        maxScroll = contentH - maxH;
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        return maxH;
    }

    /** Whether scrolling is currently active (content taller than viewport). */
    protected boolean isScrollEnabled() {
        return maxScroll > 0;
    }

    /** Viewport top Y (first pixel of the scrollable form area). */
    private int viewportTop() {
        return guiTop + FORM_TOP;
    }

    /** Viewport bottom Y (last pixel of the scrollable form area, just above bottom section). */
    private int viewportBottom() {
        int bottomH = showsKeyFrame() ? BOTTOM_SECTION : 38;
        return guiTop + getWindowHeight() - bottomH;
    }

    /** Reposition all tracked form widgets based on current scrollOffset and update visibility. */
    private void updateScrollPositions() {
        if (formWidgetRecords.isEmpty()) return;
        int vTop = viewportTop();
        int vBot = viewportBottom();
        for (FormWidgetRecord rec : formWidgetRecords) {
            int newY = guiTop + FORM_TOP + rec.contentOffsetY - scrollOffset;
            rec.widget.setY(newY);
            if (isScrollEnabled()) {
                boolean visible = (newY + rec.widget.getHeight() > vTop) && (newY < vBot);
                rec.widget.visible = visible;
            } else {
                rec.widget.visible = true;
            }
        }
    }

    /**
     * Number of form rows in this step editor. Determines auto-calculated window
     * height.
     */
    protected final int getFormRowCount() {
        formEntries.clear();
        collectFormEntries(formEntries);
        int rows = 0;
        for (StepEditorEntry entry : formEntries) {
            rows += Math.max(0, entry.rows());
        }
        return rows;
    }

    /** Subclasses declare their form rows in this method. */
    protected abstract void collectFormEntries(List<StepEditorEntry> entries);

    /** Builds the form from declarative entries. */
    protected final void buildForm() {
        beginForm();
        formEntries.clear();
        collectFormEntries(formEntries);
        for (StepEditorEntry entry : formEntries) {
            entry.build(this);
        }
    }

    /** Subclasses populate form fields from an existing step (for edit mode). */
    protected void populateFromStep(DslScene.DslStep step) {
        // Base: populate common keyframe toggle
        attachKeyFrame = Boolean.TRUE.equals(step.attachKeyFrame);
    }

    /**
     * Subclasses build a DslStep from the current form values. Return null if
     * validation fails.
     */
    @Nullable
    protected abstract DslScene.DslStep buildStep();

    /** Subclasses return the title string for the header. */
    protected abstract String getHeaderTitle();

    private void onConfirm() {
        DslScene.DslStep step = buildStep();
        if (step == null)
            return;

        // Apply common keyframe setting
        if (attachKeyFrame)
            step.attachKeyFrame = true;

        if (isEditMode()) {
            parent.replaceStepAndSave(editIndex, step);
        } else {
            parent.insertStepAndSave(insertAfterIndex, step);
        }
        returnToParent();
    }

    /** Navigate back to the parent SceneEditorScreen. */
    protected void returnToParent() {
        Minecraft.getInstance().setScreen(returnScreen != null ? returnScreen : parent);
    }

    @Override
    public void onClose() {
        // ESC key or other close -> return to parent list, not to game
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int wH = getWindowHeight();
        new BoxElement()
                .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
                .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
                .at(guiLeft, guiTop, 0)
                .withBounds(WINDOW_W, wH)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        String header = getHeaderTitle() + (isEditMode() ? UIText.of("ponderer.ui.edit_suffix") : "");
        graphics.drawString(font, header, guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);

        // Scroll: scissor + translate for form label rendering
        if (isScrollEnabled()) {
            graphics.enableScissor(guiLeft, viewportTop(), guiLeft + WINDOW_W, viewportBottom());
            graphics.pose().pushPose();
            graphics.pose().translate(0, -scrollOffset, 0);
        }

        renderForm(graphics, mouseX, mouseY, partialTicks);

        if (isScrollEnabled()) {
            graphics.pose().popPose();
            graphics.disableScissor();
            renderScrollbar(graphics);
        }

        // KeyFrame label (fixed, not scrolled)
        if (showsKeyFrame()) {
            int kfY = guiTop + wH - 58;
            graphics.drawString(font, UIText.of("ponderer.ui.key_frame"), guiLeft + 10, kfY + 3, UILayoutConstants.COLOR_LABEL);
        }

        if (errorMessage != null) {
            graphics.drawString(font, errorMessage, guiLeft + 10, guiTop + wH - 45, UILayoutConstants.COLOR_ERROR);
        }
        if (infoMessage != null && !infoMessage.isEmpty()) {
            int y = guiTop + wH - 45;
            if (errorMessage != null) {
                y -= 10;
            }
            graphics.drawString(font, infoMessage, guiLeft + 10, y, 0x66FF66);
        }
    }

    /** Render a scrollbar track and thumb on the right edge of the form area. */
    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int barX = guiLeft + WINDOW_W - UILayoutConstants.SCROLLBAR_W - 2;
        int vTop = viewportTop();
        int vBot = viewportBottom();
        int trackH = vBot - vTop;

        // Track background
        graphics.fill(barX, vTop, barX + UILayoutConstants.SCROLLBAR_W, vBot, UILayoutConstants.COLOR_SCROLLBAR_BG);

        // Thumb
        int contentH = getFormRowCount() * ROW_HEIGHT;
        if (contentH <= 0) return;
        int thumbH = Math.max(UILayoutConstants.SCROLLBAR_MIN_THUMB, trackH * trackH / contentH);
        int thumbY = vTop + (int) ((float) scrollOffset / maxScroll * (trackH - thumbH));
        graphics.fill(barX, thumbY, barX + UILayoutConstants.SCROLLBAR_W, thumbY + thumbH, UILayoutConstants.COLOR_SCROLLBAR_FG);
    }

    /**
     * Renders text that overlays on widget buttons (toggle V/X, cycle button
     * labels,
     * confirm/cancel labels). Called after widgets render to avoid being covered by
     * semi-transparent button backgrounds.
     */
    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;

        // Push z above PonderButton (z=420) so text isn't occluded by depth test
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // KeyFrame toggle V/X
        if (showsKeyFrame() && keyFrameToggle != null) {
            renderToggleState(graphics, keyFrameToggle, attachKeyFrame);
        }

        // Confirm / Cancel button labels
        graphics.drawCenteredString(font,
                isEditMode() ? UIText.of("ponderer.ui.save") : UIText.of("ponderer.ui.confirm"),
                confirmButton.getX() + 40, confirmButton.getY() + 6, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                cancelButton.getX() + 40, cancelButton.getY() + 6, 0xFFFFFF);

        // Subclass button-overlay text (scissored when scrolling)
        if (isScrollEnabled()) {
            graphics.enableScissor(guiLeft, viewportTop(), guiLeft + WINDOW_W, viewportBottom());
        }
        renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        if (isScrollEnabled()) {
            graphics.disableScissor();
        }

        graphics.pose().popPose();

        // Tooltip on top of everything (z=600, above buttons z=420 and text z=500)
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        renderHoveredTooltip(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    /**
     * Subclasses override this to render text on top of widget buttons
     * (toggle states, cycle button labels). Rendered after widgets.
     */
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Auto-render foreground elements registered via addFormXxx() helpers
        if (!autoFgElements.isEmpty()) {
            var font = Minecraft.getInstance().font;
            for (AutoFg el : autoFgElements) {
                if (el instanceof FgToggle t) {
                    renderToggleState(graphics, t.widget, t.state.getAsBoolean());
                } else if (el instanceof FgJeiBtn j) {
                    renderJeiButtonLabel(graphics, j.btn);
                } else if (el instanceof FgPickBtn p) {
                    renderPickButtonLabel(graphics, p.btn);
                } else if (el instanceof FgNbtBtn n) {
                    renderNbtButtonLabel(graphics, n.btn);
                } else if (el instanceof FgBlockPickBtn b) {
                    renderBlockPickButtonLabel(graphics, b.btn);
                } else if (el instanceof FgHeldItemBtn h) {
                    renderHeldItemButtonLabel(graphics, h.btn);
                } else if (el instanceof FgCycleBtn c) {
                    graphics.drawCenteredString(font, c.labelGetter.get(),
                            c.btn.getX() + c.btn.getWidth() / 2, c.btn.getY() + 2, c.colorGetter.getAsInt());
                } else if (el instanceof FgLangBtn lb) {
                    String lang = lb.langGetter.get();
                    if (lang.length() > 5) lang = lang.substring(0, 5);
                    graphics.drawCenteredString(font, lang,
                            lb.btn.getX() + lb.btn.getWidth() / 2, lb.btn.getY() + 2, 0xAAFFAA);
                }
            }
        }
        // Auto-render block props foreground if applicable
        if (blockPropFields != null || blockPropRemoveBtns != null || blockPropAddBtn != null) {
            renderBlockPropsForeground(graphics);
        }
    }

    /** Render tooltip for whichever region the mouse is hovering over. */
    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TooltipRegion region : tooltipRegions) {
            if (region.contains(mouseX, mouseY, scrollOffset)) {
                renderTooltipBox(graphics, region.text(), mouseX, mouseY);
                break;
            }
        }
    }

    /** Draw a styled tooltip box near the mouse cursor. */
    private void renderTooltipBox(GuiGraphics graphics, String text, int mx, int my) {
        var font = Minecraft.getInstance().font;
        // Split long text into lines of ~35 chars
        List<String> lines = wrapText(text, 35);
        int lineH = 10;
        int tw = 0;
        for (String l : lines)
            tw = Math.max(tw, font.width(l));
        int tooltipW = tw + 10;
        int tooltipH = lines.size() * lineH + 6;

        int tx = mx + 10;
        int ty = my - tooltipH - 4;
        if (tx + tooltipW > this.width)
            tx = mx - tooltipW - 4;
        if (ty < 0)
            ty = my + 16;

        graphics.fill(tx - 2, ty - 2, tx + tooltipW + 2, ty + tooltipH + 2, 0xF0_100020);
        graphics.fill(tx - 1, ty - 1, tx + tooltipW + 1, ty + tooltipH + 1, 0xC0_5040a0);
        graphics.fill(tx, ty, tx + tooltipW, ty + tooltipH, 0xF0_100020);

        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), tx + 4, ty + 3 + i * lineH, 0xFF_CCCCCC);
        }
    }

    /** Simple word-wrap. */
    private static List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        while (text.length() > maxChars) {
            int sp = text.lastIndexOf(' ', maxChars);
            if (sp <= 0)
                sp = maxChars;
            lines.add(text.substring(0, sp));
            text = text.substring(sp).stripLeading();
        }
        if (!text.isEmpty())
            lines.add(text);
        return lines;
    }

    /** Subclasses render their form labels and decorations.
     * Default implementation auto-renders labels registered via addFormXxx() helpers. */
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!autoLabels.isEmpty()) {
            var font = Minecraft.getInstance().font;
            for (AutoLabel lbl : autoLabels) {
                graphics.drawString(font, lbl.text, lbl.x, lbl.y, lbl.color);
            }
        }
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isScrollEnabled()) {
            int oldOffset = scrollOffset;
            scrollOffset = Mth.clamp(scrollOffset - (int)(delta * UILayoutConstants.SCROLL_SPEED), 0, maxScroll);
            if (scrollOffset != oldOffset) {
                updateScrollPositions();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // -- Shared utility methods for subclasses --

    /**
     * Register a tooltip region. When mouse hovers over (x, y, w, h), the text is
     * shown.
     * Typically call this in buildForm() after drawing labels, passing the label
     * area.
     */
    protected void addTooltip(int x, int y, int w, int h, String text) {
        tooltipRegions.add(new TooltipRegion(x, y, w, h, text, true));
    }

    /** Register a tooltip with explicit scrollable flag. */
    protected void addTooltip(int x, int y, int w, int h, String text, boolean scrollable) {
        tooltipRegions.add(new TooltipRegion(x, y, w, h, text, scrollable));
    }

    /**
     * Convenience: register a tooltip for a label drawn at (x, y) with the given
     * label string.
     * The region covers the label width + some padding, height 12px.
     */
    protected void addLabelTooltip(int x, int y, String label, String tooltip) {
        var font = Minecraft.getInstance().font;
        tooltipRegions.add(new TooltipRegion(x, y - 1, font.width(label) + 4, 12, tooltip, true));
    }

    /** Register a label tooltip with explicit scrollable flag. */
    protected void addLabelTooltip(int x, int y, String label, String tooltip, boolean scrollable) {
        var font = Minecraft.getInstance().font;
        tooltipRegions.add(new TooltipRegion(x, y - 1, font.width(label) + 4, 12, tooltip, scrollable));
    }

    // -- Pick button support --

    /**
     * Create a small pick button [P] next to an XYZ field group.
     * When clicked, saves the form state and returns to PonderUI for coordinate picking.
     *
     * @param x           button x position
     * @param y           button y position
     * @param target      which field group to fill (POS1, POS2, LOOK_AT, POINT)
     * @param halfOffset  whether to add +0.5 offset (entity/text fields use block center)
     * @return the pick button widget
     */
    protected PonderButton createPickButton(int x, int y, PickState.TargetField target, boolean halfOffset) {
        PonderButton btn = new PonderButton(x, y + 3, 14, 12);
        btn.withCallback(() -> {
            Map<String, String> snapshot = snapshotForm();
            snapshot.put("_keyFrame", String.valueOf(attachKeyFrame));

            PickState.TargetField effectiveTarget = target;
            boolean effectiveHalfOffset = halfOffset;
            if (target == PickState.TargetField.POINT && isInterfaceStartScene()) {
                effectiveTarget = PickState.TargetField.UI_POINT;
                effectiveHalfOffset = false;
            }

            PickState.startPick(
                    effectiveTarget,
                    snapshot,
                    getStepType(),
                    editIndex,
                    insertAfterIndex,
                    scene,
                    sceneIndex,
                    parent,
                        effectiveHalfOffset
            );
            // Navigate to PonderUI for coordinate picking
            PickState.openPonderUIForPick();
        });
        addRenderableWidget(btn);
        addTooltip(x, y + 3, 14, 12, UIText.of("ponderer.ui.pick.tooltip"));
        return btn;
    }

    private boolean isInterfaceStartScene() {
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return false;
        }
        if (sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return false;
        }
        List<DslScene.DslStep> steps = scene.scenes.get(sceneIndex).steps;
        if (steps == null) {
            return false;
        }
        for (DslScene.DslStep step : steps) {
            if (step == null || step.type == null || step.type.isBlank()) {
                continue;
            }
            return "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    /**
     * Convenience overload for block-coordinate fields (no offset).
     */
    protected PonderButton createPickButton(int x, int y, PickState.TargetField target) {
        return createPickButton(x, y, target, false);
    }

    /**
     * Return the step type string for this editor (e.g. "set_block").
     * Used by PickState to re-create the correct editor screen after picking.
     */
    protected abstract String getStepType();

    /**
     * Capture the current form field values into a string map.
     * Keys should match those used in {@link #restoreFromSnapshot(Map)}.
     */
    protected final Map<String, String> snapshotForm() {
        Map<String, String> snapshot = new HashMap<>();
        for (StepEditorEntry entry : formEntries) {
            entry.snapshot(snapshot);
        }
        appendCustomSnapshot(snapshot);
        return snapshot;
    }

    /**
     * Restore form field values from a previously captured snapshot.
     * Called after returning from a pick operation.
     */
    protected final void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        for (StepEditorEntry entry : formEntries) {
            entry.restore(snapshot);
        }
        restoreCustomSnapshot(snapshot);
    }

    /** Append screen-specific snapshot values that are not covered by declarative entries. */
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
    }

    /** Restore screen-specific snapshot values that are not covered by declarative entries. */
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
    }

    /** Rebuild the current form layout while preserving declarative field state. */
    protected final void rebuildFormPreservingState() {
        var mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Map<String, String> snapshot = snapshotForm();
        init(mc, width, height);
        restoreFromSnapshot(snapshot);
    }

    /**
     * Helper to restore the keyFrame toggle from a snapshot.
     * Subclasses should call this in their restoreFromSnapshot().
     */
    protected void restoreKeyFrame(Map<String, String> snapshot) {
        String kf = snapshot.get("_keyFrame");
        if (kf != null) attachKeyFrame = Boolean.parseBoolean(kf);
    }

    /** Restore user feedback line for successful NBT capture, with fallback when lang key is missing. */
    protected void restoreNbtPickNotice(Map<String, String> snapshot) {
        if (!snapshot.containsKey(NbtPickState.SNAPSHOT_NOTICE_KEY)) return;
        String pickedName = snapshot.get(NbtPickState.SNAPSHOT_NOTICE_KEY);
        String translated = UIText.of("ponderer.ui.nbt_pick.filled", pickedName);
        infoMessage = "ponderer.ui.nbt_pick.filled".equals(translated)
                ? ("NBT <- " + pickedName)
                : translated;
    }

    // -- Field creation helpers --

    protected HintableTextFieldWidget createTextField(int x, int y, int w, int h, String hint) {
        var font = Minecraft.getInstance().font;
        HintableTextFieldWidget field = new SoftHintTextFieldWidget(font, x, y, w, h);
        field.setHint(hint);
        field.setMaxLength(32500);
        addRenderableWidget(field);
        return field;
    }

    protected HintableTextFieldWidget createSmallNumberField(int x, int y, int w, String hint) {
        var font = Minecraft.getInstance().font;
        HintableTextFieldWidget field = new SoftHintTextFieldWidget(font, x, y, w, 18);
        field.setHint(hint);
        field.setMaxLength(10);
        addRenderableWidget(field);
        return field;
    }

    protected BoxWidget createToggle(int x, int y) {
        return new PonderButton(x + 3, y + 3, 12, 12);
    }

    /**
     * Create an inline form button (cycle button, lang toggle, etc.)
     * that visually aligns with EditBox fields.
     */
    protected PonderButton createFormButton(int x, int y, int w) {
        return new PonderButton(x + 3, y + 3, w, 12);
    }

    /** PonderPalette name -> RGB color value mapping for text display. */
    protected static final Map<String, Integer> PALETTE_COLORS = Map.ofEntries(
            Map.entry("white", 0xEEEEEE),
            Map.entry("black", 0x221111),
            Map.entry("red", 0xFF5D6C),
            Map.entry("green", 0x8CBA51),
            Map.entry("blue", 0x5F6CAF),
            Map.entry("slow", 0x22FF22),
            Map.entry("medium", 0x0084FF),
            Map.entry("fast", 0xFF55FF),
            Map.entry("input", 0x7FCDE0),
            Map.entry("output", 0xDDC166));

    /** Get the display color for a PonderPalette name, defaulting to white. */
    protected static int getPaletteColor(String name) {
        return PALETTE_COLORS.getOrDefault(name.toLowerCase(), 0xFFFFFF);
    }

    protected static final int FIELD_TO_BUTTON_GAP = 7;
    protected static final int BUTTON_TO_BUTTON_GAP = 8;

    protected void renderToggleState(GuiGraphics graphics, BoxWidget toggle, boolean state) {
        var font = Minecraft.getInstance().font;
        String label = state ? "V" : "X";
        int color = state ? 0xFF_55FF55 : 0xFF_FF5555;
        graphics.drawCenteredString(font, label, toggle.getX() + 7, toggle.getY() + 2, color);
    }

    /**
     * Render a "P" label on a pick button. Call this in renderFormForeground().
     */
    protected void renderPickButtonLabel(GuiGraphics graphics, PonderButton pickButton) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, "+",
                pickButton.getX() + 7, pickButton.getY() + 2, 0x80FFFF);
    }

    /** Create an NBT-capture button that returns to real world and captures target NBT on right-click. */
    protected PonderButton createNbtPickButton(int x, int y, String nbtSnapshotKey) {
        PonderButton btn = new PonderButton(x, y + 3, 14, 12);
        btn.withCallback(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            errorMessage = null;
            infoMessage = null;

            Map<String, String> snapshot = snapshotForm();
            snapshot.put("_keyFrame", String.valueOf(attachKeyFrame));
            NbtPickState.startPick(
                    snapshot,
                    nbtSnapshotKey,
                    getStepType(),
                    editIndex,
                    insertAfterIndex,
                    scene,
                    sceneIndex,
                    parent
            );
            mc.setScreen(null);
        });
        addRenderableWidget(btn);
        addTooltip(x, y + 3, 14, 12, UIText.of("ponderer.ui.nbt_pick.tooltip"));
        return btn;
    }

    protected PonderButton createBlockPickButton(int x, int y, String nbtSnapshotKey) {
        PonderButton btn = new PonderButton(x, y + 3, 14, 12);
        btn.withCallback(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            errorMessage = null;
            infoMessage = null;

            Map<String, String> snapshot = snapshotForm();
            snapshot.put("_keyFrame", String.valueOf(attachKeyFrame));
            NbtPickState.startPick(
                    snapshot,
                    nbtSnapshotKey,
                    true,
                    getStepType(),
                    editIndex,
                    insertAfterIndex,
                    scene,
                    sceneIndex,
                    parent
            );
            mc.setScreen(null);
        });
        addRenderableWidget(btn);
        addTooltip(x, y + 3, 14, 12, UIText.of("ponderer.ui.block_pick.tooltip"));
        return btn;
    }

    protected void renderBlockPickButtonLabel(GuiGraphics graphics, PonderButton btn) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, "+", btn.getX() + 7, btn.getY() + 2, 0x66FF66);
    }

    protected PonderButton createHeldItemButton(int x, int y, Consumer<ItemStack> onItemPicked) {
        PonderButton btn = new PonderButton(x, y + 3, 14, 12);
        btn.withCallback(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) {
                held = mc.player.getOffhandItem();
            }
            if (held.isEmpty()) {
                errorMessage = UIText.of("ponderer.ui.held_item.error.empty");
                return;
            }
            errorMessage = null;
            infoMessage = null;
            onItemPicked.accept(held);
            infoMessage = UIText.of("ponderer.ui.nbt_pick.filled", held.getHoverName().getString());
        });
        addRenderableWidget(btn);
        addTooltip(x, y + 3, 14, 12, UIText.of("ponderer.ui.held_item.tooltip"));
        return btn;
    }

    protected void renderHeldItemButtonLabel(GuiGraphics graphics, PonderButton btn) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, "+", btn.getX() + 7, btn.getY() + 2, 0x66FF66);
    }

    protected void renderNbtButtonLabel(GuiGraphics graphics, PonderButton btn) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, "+", btn.getX() + 7, btn.getY() + 2, 0x66FF66);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Declarative Form Row API ───────────────────────────────────────
    // Subclasses call addFormXxx() methods in buildForm() instead of
    // manually computing coordinates. Labels and foreground text are
    // auto-rendered by base renderForm() / renderFormForeground().
    // ═══════════════════════════════════════════════════════════════════

    /** Return value for {@link #addFormTextFieldWithJei}. */
    protected record FieldWithJei(HintableTextFieldWidget field, @Nullable PonderButton jeiBtn) {}
    /** Return value for {@link #addFormXyzRow}. */
    protected record XyzFieldGroup(HintableTextFieldWidget x, HintableTextFieldWidget y, HintableTextFieldWidget z, @Nullable PonderButton pickBtn) {}
    /** Return value for {@link #addFormTextFieldWithLang}. */
    protected record FieldWithLang(HintableTextFieldWidget field, BoxWidget langBtn) {}
    /** Return value for text field rows with a single trailing button. */
    protected record FieldWithButton(HintableTextFieldWidget field, PonderButton button) {}
    /** Return value for dual-field rows. */
    protected record DualFieldGroup(HintableTextFieldWidget first, HintableTextFieldWidget second) {}

    /** Tracked label for auto-rendering in renderForm(). */
    private record AutoLabel(String text, int x, int y, int color) {}
    private final List<AutoLabel> autoLabels = new ArrayList<>();

    /** Tracked foreground element for auto-rendering in renderFormForeground(). */
    private sealed interface AutoFg {}
    private record FgToggle(BoxWidget widget, BooleanSupplier state) implements AutoFg {}
    private record FgJeiBtn(@Nullable PonderButton btn) implements AutoFg {}
    private record FgPickBtn(PonderButton btn) implements AutoFg {}
    private record FgNbtBtn(PonderButton btn) implements AutoFg {}
    private record FgBlockPickBtn(PonderButton btn) implements AutoFg {}
    private record FgHeldItemBtn(PonderButton btn) implements AutoFg {}
    private record FgCycleBtn(BoxWidget btn, Supplier<String> labelGetter, IntSupplier colorGetter) implements AutoFg {}
    private record FgLangBtn(BoxWidget btn, Supplier<String> langGetter) implements AutoFg {}
    private final List<AutoFg> autoFgElements = new ArrayList<>();

    /** Current Y position during form building. Set by beginForm(). */
    private int formCursorY;

    /** Initialize the form layout cursor. Call at the start of buildForm(). */
    protected void beginForm() {
        formCursorY = guiTop + FORM_TOP;
        autoLabels.clear();
        autoFgElements.clear();
    }

    /** Current field X position (left edge of control column). */
    protected int fieldX() { return guiLeft + 70; }

    /** Current form Y position. For custom row layouts, read this to position widgets manually. */
    protected int formY() { return formCursorY; }

    /** Advance to the next form row. */
    protected void nextFormRow() { formCursorY += ROW_HEIGHT; }

    /** Register a label + tooltip at the current row. Called by addFormXxx helpers. */
    protected void addFormLabel(String labelKey, @Nullable String tooltipKey) {
        addFormLabel(labelKey, tooltipKey, UILayoutConstants.COLOR_LABEL);
    }

    /** Register a label + tooltip at the current row with a custom color. */
    protected void addFormLabel(String labelKey, @Nullable String tooltipKey, int color) {
        int lx = guiLeft + 10;
        String label = UIText.of(labelKey);
        autoLabels.add(new AutoLabel(label, lx, formCursorY + 3, color));
        if (tooltipKey != null) {
            addLabelTooltip(lx, formCursorY + 3, label, UIText.of(tooltipKey));
        }
    }

    // ── Row builders ────────────────────────────────────────────────────

    /** Add a text field row with label. */
    protected HintableTextFieldWidget addFormTextField(String labelKey, @Nullable String tooltipKey, String hint, int fieldW) {
        addFormLabel(labelKey, tooltipKey);
        HintableTextFieldWidget field = createTextField(fieldX(), formCursorY, fieldW, 18, hint);
        nextFormRow();
        return field;
    }

    /** Add a text field row with label and JEI browse button. */
    protected FieldWithJei addFormTextFieldWithJei(String labelKey, @Nullable String tooltipKey, String hint, int fieldW, IdFieldMode mode) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        HintableTextFieldWidget field = createTextField(fx, formCursorY, fieldW, 18, hint);
        PonderButton jeiBtn = createJeiButton(fx + fieldW + 5, formCursorY, field, mode);
        if (jeiBtn != null) autoFgElements.add(new FgJeiBtn(jeiBtn));
        nextFormRow();
        return new FieldWithJei(field, jeiBtn);
    }

    /** Add a text field with JEI button (standard 124px field width). */
    protected FieldWithJei addFormTextFieldWithJei(String labelKey, @Nullable String tooltipKey, String hint, IdFieldMode mode) {
        return addFormTextFieldWithJei(labelKey, tooltipKey, hint, 124, mode);
    }

    /** Return value for {@link #addFormTextFieldWithJeiAndBlockPick}. */
    protected record FieldWithJeiAndBlockPick(HintableTextFieldWidget field, @Nullable PonderButton jeiBtn, PonderButton blockPickBtn) {}

    /** Return value for JEI + secondary-button rows. */
    protected record FieldWithJeiAndSecondButton(HintableTextFieldWidget field, @Nullable PonderButton jeiBtn, PonderButton secondBtn) {}

    @FunctionalInterface
    protected interface SecondaryButtonFactory {
        PonderButton create(int x, int y);
    }

    /** Add a text field row with JEI button plus a second action button. */
    protected FieldWithJeiAndSecondButton addFormTextFieldWithJeiAndSecondButton(String labelKey, @Nullable String tooltipKey,
                                                                                  String hint, IdFieldMode mode,
                                                                                  SecondaryButtonFactory buttonFactory) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        int fieldW = 105;
        HintableTextFieldWidget field = createTextField(fx, formCursorY, fieldW, 18, hint);
        int jeiX = fx + fieldW + FIELD_TO_BUTTON_GAP;
        PonderButton jeiBtn = createJeiButton(jeiX, formCursorY, field, mode);
        if (jeiBtn != null) autoFgElements.add(new FgJeiBtn(jeiBtn));

        int secondX = jeiX + 14 + BUTTON_TO_BUTTON_GAP;
        PonderButton secondBtn = buttonFactory.create(secondX, formCursorY);
        nextFormRow();
        return new FieldWithJeiAndSecondButton(field, jeiBtn, secondBtn);
    }

    /** Add a text field with JEI button and block pick button. */
    protected FieldWithJeiAndBlockPick addFormTextFieldWithJeiAndBlockPick(String labelKey, @Nullable String tooltipKey, String hint, IdFieldMode mode, String nbtSnapshotKey) {
        FieldWithJeiAndSecondButton row = addFormTextFieldWithJeiAndSecondButton(
                labelKey, tooltipKey, hint, mode,
                (x, y) -> createBlockPickButton(x, y, nbtSnapshotKey));
        PonderButton blockPickBtn = row.secondBtn();
        autoFgElements.add(new FgBlockPickBtn(blockPickBtn));
        return new FieldWithJeiAndBlockPick(row.field(), row.jeiBtn(), blockPickBtn);
    }

    /** Return value for {@link #addFormTextFieldWithJeiAndHeldItem}. */
    protected record FieldWithJeiAndHeldItem(HintableTextFieldWidget field, @Nullable PonderButton jeiBtn, PonderButton heldItemBtn) {}

    /** Add a text field with JEI button and held-item pick button. */
    protected FieldWithJeiAndHeldItem addFormTextFieldWithJeiAndHeldItem(String labelKey, @Nullable String tooltipKey, String hint, IdFieldMode mode, Consumer<ItemStack> onItemPicked) {
        FieldWithJeiAndSecondButton row = addFormTextFieldWithJeiAndSecondButton(
            labelKey, tooltipKey, hint, mode,
            (x, y) -> createHeldItemButton(x, y, onItemPicked));
        PonderButton heldItemBtn = row.secondBtn();
        autoFgElements.add(new FgHeldItemBtn(heldItemBtn));
        return new FieldWithJeiAndHeldItem(row.field(), row.jeiBtn(), heldItemBtn);
    }

        /** Return value for {@link #addFormTextFieldWithJeiAndNbtPick}. */
        protected record FieldWithJeiAndNbtPick(HintableTextFieldWidget field, @Nullable PonderButton jeiBtn, PonderButton nbtPickBtn) {}

        /** Add a text field with JEI button and world-NBT pick button. */
        protected FieldWithJeiAndNbtPick addFormTextFieldWithJeiAndNbtPick(String labelKey, @Nullable String tooltipKey,
                                           String hint, IdFieldMode mode,
                                           String nbtSnapshotKey) {
        FieldWithJeiAndSecondButton row = addFormTextFieldWithJeiAndSecondButton(
            labelKey, tooltipKey, hint, mode,
            (x, y) -> createNbtPickButton(x, y, nbtSnapshotKey));
        PonderButton nbtPickBtn = row.secondBtn();
        autoFgElements.add(new FgNbtBtn(nbtPickBtn));
        return new FieldWithJeiAndNbtPick(row.field(), row.jeiBtn(), nbtPickBtn);
        }

    /** Add an NBT text field row with a world-capture (+) button. */
    protected HintableTextFieldWidget addFormNbtField(String labelKey, @Nullable String tooltipKey, String hint,
                                                      int fieldW, String nbtSnapshotKey) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        HintableTextFieldWidget field = createTextField(fx, formCursorY, fieldW, 18, hint);
        PonderButton nbtBtn = createNbtPickButton(fx + fieldW + 5, formCursorY, nbtSnapshotKey);
        autoFgElements.add(new FgNbtBtn(nbtBtn));
        nextFormRow();
        return field;
    }

    /** Add a text field row with a single trailing action button. */
    protected FieldWithButton addFormTextFieldWithButton(String labelKey, @Nullable String tooltipKey,
                                                         String hint, int fieldW,
                                                         Runnable onClick,
                                                         Supplier<String> buttonLabelGetter,
                                                         IntSupplier buttonColorGetter,
                                                         @Nullable String buttonTooltip) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        HintableTextFieldWidget field = createTextField(fx, formCursorY, fieldW, 18, hint);
        PonderButton button = new PonderButton(fx + fieldW + 5, formCursorY + 3, 14, 12);
        button.withCallback(onClick);
        addRenderableWidget(button);
        if (buttonTooltip != null) {
            addTooltip(button.getX(), button.getY(), button.getWidth(), button.getHeight(), buttonTooltip);
        }
        autoFgElements.add(new FgCycleBtn(button, buttonLabelGetter, buttonColorGetter));
        nextFormRow();
        return new FieldWithButton(field, button);
    }

    /** Add an XYZ coordinate field row with optional pick button. */
    protected XyzFieldGroup addFormXyzRow(String labelKey, @Nullable String tooltipKey,
                                          @Nullable PickState.TargetField target, boolean halfOffset) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        int sw = 38;
        var xf = createSmallNumberField(fx, formCursorY, sw, "X");
        var yf = createSmallNumberField(fx + sw + 5, formCursorY, sw, "Y");
        var zf = createSmallNumberField(fx + 2 * (sw + 5), formCursorY, sw, "Z");
        PonderButton pb = null;
        if (target != null) {
            pb = createPickButton(fx + 3 * (sw + 5), formCursorY, target, halfOffset);
            autoFgElements.add(new FgPickBtn(pb));
        }
        nextFormRow();
        return new XyzFieldGroup(xf, yf, zf, pb);
    }

    /** Add an XYZ coordinate field row with pick button (no half-offset). */
    protected XyzFieldGroup addFormXyzRow(String labelKey, @Nullable String tooltipKey, PickState.TargetField target) {
        return addFormXyzRow(labelKey, tooltipKey, target, false);
    }

    /** Add an XYZ field row without pick button. */
    protected XyzFieldGroup addFormXyzRow(String labelKey, @Nullable String tooltipKey) {
        return addFormXyzRow(labelKey, tooltipKey, null, false);
    }

    /** Add an XYZ coordinate row with a custom trailing action button. */
    protected XyzFieldGroup addFormXyzRowWithButton(String labelKey, @Nullable String tooltipKey,
                                                    Runnable onClick,
                                                    Supplier<String> buttonLabelGetter,
                                                    IntSupplier buttonColorGetter,
                                                    @Nullable String buttonTooltip) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        int sw = 38;
        var xf = createSmallNumberField(fx, formCursorY, sw, "X");
        var yf = createSmallNumberField(fx + sw + 5, formCursorY, sw, "Y");
        var zf = createSmallNumberField(fx + 2 * (sw + 5), formCursorY, sw, "Z");
        PonderButton button = new PonderButton(fx + 3 * (sw + 5), formCursorY + 3, 14, 12);
        button.withCallback(onClick);
        addRenderableWidget(button);
        if (buttonTooltip != null) {
            addTooltip(button.getX(), button.getY(), button.getWidth(), button.getHeight(), buttonTooltip);
        }
        autoFgElements.add(new FgCycleBtn(button, buttonLabelGetter, buttonColorGetter));
        nextFormRow();
        return new XyzFieldGroup(xf, yf, zf, button);
    }

    /** Add a toggle (checkbox) row. */
    protected BoxWidget addFormToggle(String labelKey, @Nullable String tooltipKey,
                                      BooleanSupplier stateGetter, Runnable onToggle) {
        addFormLabel(labelKey, tooltipKey);
        BoxWidget toggle = createToggle(fieldX(), formCursorY);
        toggle.withCallback(onToggle);
        addRenderableWidget(toggle);
        autoFgElements.add(new FgToggle(toggle, stateGetter));
        nextFormRow();
        return toggle;
    }

    /** Add a cycle button row. */
    protected BoxWidget addFormCycleButton(String labelKey, @Nullable String tooltipKey,
                                           int btnW, Runnable onClick,
                                           Supplier<String> labelGetter, IntSupplier colorGetter) {
        addFormLabel(labelKey, tooltipKey);
        BoxWidget btn = createFormButton(fieldX(), formCursorY, btnW);
        btn.withCallback(onClick);
        addRenderableWidget(btn);
        autoFgElements.add(new FgCycleBtn(btn, labelGetter, colorGetter));
        nextFormRow();
        return btn;
    }

    /** Add a cycle button with default white text. */
    protected BoxWidget addFormCycleButton(String labelKey, @Nullable String tooltipKey,
                                           int btnW, Runnable onClick, Supplier<String> labelGetter) {
        return addFormCycleButton(labelKey, tooltipKey, btnW, onClick, labelGetter, () -> 0xFFFFFF);
    }

    /** Add a small number field row with optional unit label. */
    protected HintableTextFieldWidget addFormNumberField(String labelKey, @Nullable String tooltipKey,
                                                         String hint, int fieldW, @Nullable String unitKey) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        HintableTextFieldWidget field = createSmallNumberField(fx, formCursorY, fieldW, hint);
        if (unitKey != null) {
            autoLabels.add(new AutoLabel(UIText.of(unitKey), fx + fieldW + 10, formCursorY + 3,
                    UILayoutConstants.COLOR_HINT));
        }
        nextFormRow();
        return field;
    }

    /** Add a small number field row without unit. */
    protected HintableTextFieldWidget addFormNumberField(String labelKey, @Nullable String tooltipKey,
                                                         String hint, int fieldW) {
        return addFormNumberField(labelKey, tooltipKey, hint, fieldW, null);
    }

    /** Add a text field row with label and language toggle button for bilingual input. */
    protected FieldWithLang addFormTextFieldWithLang(String labelKey, @Nullable String tooltipKey,
                                                     String hint, int fieldW,
                                                     Supplier<String> langGetter, Runnable onToggle) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        HintableTextFieldWidget field = createTextField(fx, formCursorY, fieldW, 18, hint);
        int langBtnW = 28;
        int gap = 6;
        BoxWidget langBtn = createFormButton(fx + fieldW + gap, formCursorY, langBtnW);
        langBtn.withCallback(onToggle);
        addRenderableWidget(langBtn);
        autoFgElements.add(new FgLangBtn(langBtn, langGetter));
        nextFormRow();
        return new FieldWithLang(field, langBtn);
    }

    /** Add a row with two small number/text fields under one label. */
    protected DualFieldGroup addFormDualNumberField(String labelKey, @Nullable String tooltipKey,
                                                    String firstHint, int firstWidth,
                                                    String secondHint, int secondWidth) {
        addFormLabel(labelKey, tooltipKey);
        int fx = fieldX();
        HintableTextFieldWidget first = createSmallNumberField(fx, formCursorY, firstWidth, firstHint);
        HintableTextFieldWidget second = createSmallNumberField(fx + firstWidth + 5, formCursorY, secondWidth, secondHint);
        nextFormRow();
        return new DualFieldGroup(first, second);
    }

    /** Add a block properties section (dynamic rows). Advances cursor by blockPropRowCount(). */
    protected void addFormBlockProps(String labelKey, @Nullable String tooltipKey) {
        addFormLabel(labelKey, tooltipKey);
        formCursorY = buildBlockPropsUI(fieldX(), formCursorY);
    }

    // ═══════════════════════════════════════════════════════════════════

    // -- JEI integration --

    /** Public getters for JEI compat layer (IGuiProperties). */
    public int getGuiLeft() { return guiLeft; }
    public int getGuiTop() { return guiTop; }
    public int getGuiWidth() { return WINDOW_W; }
    public int getGuiHeight() { return getWindowHeight(); }

    /** Returns the text field that should receive the JEI-selected ID. */
    @Nullable
    public HintableTextFieldWidget getJeiTargetField() {
        return jeiTargetField;
    }

    /** Deactivate JEI overlay (called after successful selection). */
    public void deactivateJei() {
        jeiActive = false;
        jeiTargetField = null;
        jeiMode = null;
        JeiCompat.clearActiveEditor();
    }

    /** Show an error message when an incompatible item is clicked in JEI. */
    public void showJeiIncompatibleWarning(IdFieldMode mode) {
        errorMessage = switch (mode) {
            case BLOCK -> UIText.of("ponderer.ui.jei.error.not_block");
            case ENTITY -> UIText.of("ponderer.ui.jei.error.not_spawn_egg");
            case ITEM, INGREDIENT -> null;
        };
    }

    /**
     * Create a JEI browse button next to an ID text field.
     * Returns null if JEI is not installed (button won't be shown).
     */
    @Nullable
    protected PonderButton createJeiButton(int x, int y,
            HintableTextFieldWidget targetField, IdFieldMode mode) {
        if (!JeiCompat.isAvailable()) return null;

        PonderButton btn = new PonderButton(x, y + 3, 14, 12);
        btn.withCallback(() -> {
            if (jeiActive) {
                deactivateJei();
            } else {
                jeiActive = true;
                jeiTargetField = targetField;
                jeiMode = mode;
                JeiCompat.setActiveEditor(this, mode);
            }
        });
        addRenderableWidget(btn);
        addTooltip(x, y + 3, 14, 12, UIText.of("ponderer.ui.jei_browse.tooltip"));
        return btn;
    }

    /** Render a "J" label on a JEI button. Call in renderFormForeground(). */
    protected void renderJeiButtonLabel(GuiGraphics graphics, @Nullable PonderButton btn) {
        if (btn == null) return;
        var font = Minecraft.getInstance().font;
        int color = jeiActive ? 0x55FF55 : 0xAAAAFF;
        graphics.drawCenteredString(font, "J", btn.getX() + 7, btn.getY() + 2, color);
    }

    @Override
    public void removed() {
        super.removed();
        if (jeiActive) {
            deactivateJei();
        }
    }

    @Nullable
    protected Double parseDouble(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", fieldName);
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            errorMessage = UIText.of("ponderer.ui.error.invalid_number", fieldName);
            return null;
        }
    }

    protected double parseDoubleOr(String value, double fallback) {
        if (value == null || value.trim().isEmpty())
            return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    protected Float parseFloat(String value, String fieldName) {
        Double d = parseDouble(value, fieldName);
        return d == null ? null : d.floatValue();
    }

    @Nullable
    protected Integer parseInt(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", fieldName);
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            errorMessage = UIText.of("ponderer.ui.error.invalid_integer", fieldName);
            return null;
        }
    }

    protected int parseIntOr(String value, int fallback) {
        if (value == null || value.trim().isEmpty())
            return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // -- Block property list support --

    /** Override to return true if this editor uses the dynamic block property list. */
    protected boolean usesBlockProps() { return false; }

    /** Number of form rows occupied by block prop entries + add button. */
    protected int blockPropRowCount() {
        return blockPropEntries == null ? 0 : blockPropEntries.size() + 1;
    }

    private void preExtractBlockProps() {
        if (blockPropEntries != null) return;

        // If this screen was reopened from a pick action, prefer the picked properties.
        // In edit mode, existingStep props are stale and should not override fresh capture data.
        if (pendingPickRestore != null && pendingPickRestore.containsKey("prop_count")) {
            int count = Integer.parseInt(pendingPickRestore.get("prop_count"));
            blockPropEntries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                blockPropEntries.add(new String[]{
                        pendingPickRestore.getOrDefault("prop_key_" + i, ""),
                        pendingPickRestore.getOrDefault("prop_val_" + i, "")
                });
            }
            if (blockPropEntries.isEmpty()) blockPropEntries.add(new String[]{"", ""});
            return;
        }

        if (isEditMode() && existingStep != null
                && existingStep.blockProperties != null && !existingStep.blockProperties.isEmpty()) {
            blockPropEntries = new ArrayList<>();
            for (var entry : existingStep.blockProperties.entrySet()) {
                blockPropEntries.add(new String[]{entry.getKey(), entry.getValue()});
            }
            return;
        }

        blockPropEntries = new ArrayList<>();
        blockPropEntries.add(new String[]{"", ""});
    }

    /**
     * Build block property list widgets at the given position.
     * Returns the Y coordinate for the next form row below the add button.
     */
    protected int buildBlockPropsUI(int x, int y) {
        blockPropFields = new ArrayList<>();
        blockPropRemoveBtns = new ArrayList<>();
        int keyW = 55, valW = 55;
        int valX = x + keyW + 14;  // 14px gap for "=" label
        int rmX = x + 129;         // align with J / pick buttons
        int addW = 140;             // spans full row (x+3 to x+143)

        for (int i = 0; i < blockPropEntries.size(); i++) {
            String[] entry = blockPropEntries.get(i);
            HintableTextFieldWidget keyField = createTextField(x, y, keyW, 18, "facing");
            HintableTextFieldWidget valField = createTextField(valX, y, valW, 18, "north");
            keyField.setValue(entry[0]);
            valField.setValue(entry[1]);

            PonderButton rmBtn = new PonderButton(rmX, y + 3, 14, 12);
            final int idx = i;
            rmBtn.withCallback(() -> removeBlockPropEntry(idx));
            addRenderableWidget(rmBtn);

            blockPropFields.add(new HintableTextFieldWidget[]{keyField, valField});
            blockPropRemoveBtns.add(rmBtn);
            y += ROW_HEIGHT;
        }

        blockPropAddBtn = new PonderButton(x + 3, y + 3, addW, 12);
        blockPropAddBtn.withCallback(this::addBlockPropEntry);
        addRenderableWidget(blockPropAddBtn);
        y += ROW_HEIGHT;

        return y;
    }

    private void addBlockPropEntry() {
        syncBlockPropFieldsToEntries();
        blockPropEntries.add(new String[]{"", ""});
        blockPropFields = null;
        pendingReinitRestore = snapshotForm();
        init(minecraft, width, height);
    }

    private void removeBlockPropEntry(int idx) {
        syncBlockPropFieldsToEntries();
        blockPropEntries.remove(idx);
        if (blockPropEntries.isEmpty()) blockPropEntries.add(new String[]{"", ""});
        blockPropFields = null;
        pendingReinitRestore = snapshotForm();
        init(minecraft, width, height);
    }

    protected void syncBlockPropFieldsToEntries() {
        if (blockPropFields == null || blockPropEntries == null) return;
        for (int i = 0; i < blockPropFields.size() && i < blockPropEntries.size(); i++) {
            blockPropEntries.get(i)[0] = blockPropFields.get(i)[0].getValue();
            blockPropEntries.get(i)[1] = blockPropFields.get(i)[1].getValue();
        }
    }

    /** Collect block property entries into a Map for the DslStep. */
    @Nullable
    protected Map<String, String> collectBlockProperties() {
        syncBlockPropFieldsToEntries();
        if (blockPropEntries == null) return null;
        Map<String, String> map = new HashMap<>();
        for (String[] entry : blockPropEntries) {
            String key = entry[0].trim();
            String val = entry[1].trim();
            if (!key.isEmpty() && !val.isEmpty()) {
                map.put(key, val);
            }
        }
        return map.isEmpty() ? null : map;
    }

    /** Save block prop entries into a snapshot map. */
    protected void snapshotBlockProps(Map<String, String> m) {
        if (blockPropEntries == null) return;
        m.put("prop_count", String.valueOf(blockPropEntries.size()));
        for (int i = 0; i < blockPropEntries.size(); i++) {
            m.put("prop_key_" + i, blockPropEntries.get(i)[0]);
            m.put("prop_val_" + i, blockPropEntries.get(i)[1]);
        }
    }

    /** Restore block prop field widget values from a snapshot. */
    protected void restoreBlockProps(Map<String, String> snapshot) {
        if (blockPropFields == null || !snapshot.containsKey("prop_count")) return;
        int count = Integer.parseInt(snapshot.get("prop_count"));
        for (int i = 0; i < count && i < blockPropFields.size(); i++) {
            blockPropFields.get(i)[0].setValue(snapshot.getOrDefault("prop_key_" + i, ""));
            blockPropFields.get(i)[1].setValue(snapshot.getOrDefault("prop_val_" + i, ""));
        }
    }

    /** Render "=" labels and button text for block prop list. Call in renderFormForeground. */
    protected void renderBlockPropsForeground(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        if (blockPropFields != null) {
            for (HintableTextFieldWidget[] kv : blockPropFields) {
                int eqX = kv[0].getX() + kv[0].getWidth() + 3;
                int eqY = kv[0].getY() + 5;
                graphics.drawString(font, "=", eqX, eqY, UILayoutConstants.COLOR_LABEL);
            }
        }
        if (blockPropRemoveBtns != null) {
            for (PonderButton rmBtn : blockPropRemoveBtns) {
                graphics.drawCenteredString(font, "x", rmBtn.getX() + 7, rmBtn.getY() + 2, 0xFF5555);
            }
        }
        if (blockPropAddBtn != null) {
            int cx = blockPropAddBtn.getX() + blockPropAddBtn.getWidth() / 2;
            graphics.drawCenteredString(font, "+", cx, blockPropAddBtn.getY() + 2, 0x80FF80);
        }
    }
}
