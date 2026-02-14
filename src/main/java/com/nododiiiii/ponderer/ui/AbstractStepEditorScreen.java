package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for step editor screens.
 * Supports both "add new step" and "edit existing step" modes.
 * Subclasses implement buildForm(), populateFromStep(), buildStep().
 */
public abstract class AbstractStepEditorScreen extends AbstractSimiScreen {

    protected static final int WINDOW_W = 220;
    protected static final int FORM_TOP = 26;
    protected static final int ROW_HEIGHT = 22;
    private static final int BOTTOM_SECTION = 60;

    private static final ResourceLocation ICON_CONFIRM = ResourceLocation.fromNamespaceAndPath("minecraft",
            "container/beacon/confirm");
    private static final ResourceLocation ICON_CANCEL = ResourceLocation.fromNamespaceAndPath("minecraft",
            "container/beacon/cancel");

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

    /** Common keyframe toggle - available for all step types. */
    protected boolean attachKeyFrame = false;
    private BoxWidget keyFrameToggle;

    /** Registered tooltip regions: hover over label area to see description. */
    protected final List<TooltipRegion> tooltipRegions = new ArrayList<>();

    protected record TooltipRegion(int x, int y, int w, int h, String text) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
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

    @Override
    protected void init() {
        setWindowSize(WINDOW_W, getWindowHeight());
        super.init();
        tooltipRegions.clear();

        int btnW = 80;
        int btnH = 20;

        confirmButton = new PonderButton(guiLeft + 15, guiTop + getWindowHeight() - 30, btnW, btnH);
        confirmButton.withCallback(this::onConfirm);
        addRenderableWidget(confirmButton);

        cancelButton = new PonderButton(guiLeft + WINDOW_W - btnW - 15, guiTop + getWindowHeight() - 30, btnW, btnH);
        cancelButton.withCallback(this::returnToParent);
        addRenderableWidget(cancelButton);

        // KeyFrame toggle (common to all step types), placed above confirm/cancel
        int kfY = guiTop + getWindowHeight() - 58;
        keyFrameToggle = createToggle(guiLeft + 70, kfY);
        keyFrameToggle.withCallback(() -> attachKeyFrame = !attachKeyFrame);
        addRenderableWidget(keyFrameToggle);
        addLabelTooltip(guiLeft + 10, kfY + 3, UIText.of("ponderer.ui.key_frame"),
                UIText.of("ponderer.ui.key_frame.tooltip"));

        buildForm();

        if (isEditMode()) {
            populateFromStep(existingStep);
        }
    }

    /** Computes window height from form row count. */
    protected int getWindowHeight() {
        return FORM_TOP + getFormRowCount() * ROW_HEIGHT + BOTTOM_SECTION;
    }

    /**
     * Number of form rows in this step editor. Determines auto-calculated window
     * height.
     */
    protected abstract int getFormRowCount();

    /** Subclasses create their input fields/widgets in this method. */
    protected abstract void buildForm();

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
            parent.addStepAndSave(step);
        }
        returnToParent();
    }

    /** Navigate back to the parent SceneEditorScreen. */
    private void returnToParent() {
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
                .withBackground(new Color(0xdd_000000, true))
                .gradientBorder(new Color(0x60_c0c0ff, true), new Color(0x30_c0c0ff, true))
                .at(guiLeft, guiTop, 100)
                .withBounds(WINDOW_W, wH)
                .render(graphics);

        var font = Minecraft.getInstance().font;

        String header = getHeaderTitle() + (isEditMode() ? UIText.of("ponderer.ui.edit_suffix") : "");
        graphics.drawString(font, header, guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + WINDOW_W - 5, guiTop + 21, 0x60_FFFFFF);

        renderForm(graphics, mouseX, mouseY, partialTicks);

        // KeyFrame label (not on button, safe here)
        int kfY = guiTop + wH - 58;
        graphics.drawString(font, UIText.of("ponderer.ui.key_frame"), guiLeft + 10, kfY + 3, 0xCCCCCC);

        if (errorMessage != null) {
            graphics.drawString(font, errorMessage, guiLeft + 10, guiTop + wH - 45, 0xFF5555);
        }
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
        renderToggleState(graphics, keyFrameToggle, attachKeyFrame);

        // Confirm / Cancel button labels
        graphics.drawCenteredString(font,
                isEditMode() ? UIText.of("ponderer.ui.save") : UIText.of("ponderer.ui.confirm"),
                confirmButton.getX() + 40, confirmButton.getY() + 6, 0xFFFFFF);
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
                cancelButton.getX() + 40, cancelButton.getY() + 6, 0xFFFFFF);

        // Subclass button-overlay text
        renderFormForeground(graphics, mouseX, mouseY, partialTicks);

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
    }

    /** Render tooltip for whichever region the mouse is hovering over. */
    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TooltipRegion region : tooltipRegions) {
            if (region.contains(mouseX, mouseY)) {
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

    /** Subclasses render their form labels and decorations. */
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
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

    // -- Shared utility methods for subclasses --

    /**
     * Register a tooltip region. When mouse hovers over (x, y, w, h), the text is
     * shown.
     * Typically call this in buildForm() after drawing labels, passing the label
     * area.
     */
    protected void addTooltip(int x, int y, int w, int h, String text) {
        tooltipRegions.add(new TooltipRegion(x, y, w, h, text));
    }

    /**
     * Convenience: register a tooltip for a label drawn at (x, y) with the given
     * label string.
     * The region covers the label width + some padding, height 12px.
     */
    protected void addLabelTooltip(int x, int y, String label, String tooltip) {
        var font = Minecraft.getInstance().font;
        tooltipRegions.add(new TooltipRegion(x, y - 1, font.width(label) + 4, 12, tooltip));
    }

    protected HintableTextFieldWidget createTextField(int x, int y, int w, int h, String hint) {
        var font = Minecraft.getInstance().font;
        HintableTextFieldWidget field = new SoftHintTextFieldWidget(font, x, y, w, h);
        field.setHint(hint);
        field.setMaxLength(200);
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

    protected void renderToggleState(GuiGraphics graphics, BoxWidget toggle, boolean state) {
        ResourceLocation icon = state ? ICON_CONFIRM : ICON_CANCEL;
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blitSprite(icon, toggle.getX() + 1, toggle.getY() + 1, 10, 10);
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
}
