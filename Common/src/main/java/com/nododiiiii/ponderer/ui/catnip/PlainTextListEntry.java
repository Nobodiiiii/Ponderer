package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.FieldType;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.Rect2i;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class PlainTextListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    protected static final int CONTROL_GAP = EntryTextSupport.multiControlGap();

    protected record TrailingButton(BoxWidget widget, int width, Supplier<String> labelGetter, IntSupplier colorGetter) {
        int renderedWidth() {
            return EntryTextSupport.buttonWidth(labelGetter.get(), width);
        }
    }

    protected final ConfigTextField textField;
    private final String labelKey;
    @Nullable
    private final String contextTooltipKey;
    private String searchText;
    private boolean preserveDefaultHint;
    protected final List<TrailingButton> trailingButtons = new ArrayList<>();
    @Nullable
    private Supplier<String> trailingTextGetter;
    private int preferredFieldWidth = -1;
    private int minimumControlWidth = -1;
    private float controlWidthScale = 1.0f;
    @Nullable
    private IngredientSlotWidget ingredientSlot;
    @Nullable
    private IdFieldMode ingredientMode;
    @Nullable
    private Rect2i ingredientSlotArea;
    @Nullable
    private Rect2i renderedFieldArea;

    public PlainTextListEntry(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                              String initialValue, Consumer<String> responder) {
        super(UIText.of(labelKey));
        this.labelKey = labelKey;
        this.contextTooltipKey = tooltipKey;
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.textField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 200, 20);
        this.textField.setMaxLength(20000);
        EntryTextSupport.applyHint(textField, hintKey);
        this.textField.setValue(initialValue);
        this.textField.moveCursorToStart();
        this.textField.setResponder(responder);
        listeners.add(textField);
    }

    public ConfigTextField field() {
        return textField;
    }

    public void setSharedField(FieldType type) {
        String tooltipKey = type.tooltipKey();
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);
        ((ClippedConfigTextField) textField).setNumericDrag(type);
        if (!preserveDefaultHint) {
            EntryTextSupport.applyHint(textField, type.hintKey());
        }
        searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey, contextTooltipKey);
    }

    /** Numeric defaults remain visible instead of the shared descriptive hint. */
    public void setDefaultHint(String hintKey) {
        preserveDefaultHint = true;
        EntryTextSupport.applyHint(textField, hintKey);
    }

    public void setValue(String value) {
        textField.setValue(value != null ? value : "");
    }

    public void addIngredientSlot(IdFieldMode mode, @Nullable String tooltipText) {
        ingredientMode = mode;
        ingredientSlot = new IngredientSlotWidget(mode, textField::getValue, tooltipText);
    }

    @Nullable
    public IdFieldMode ingredientMode() {
        return ingredientMode;
    }

    @Nullable
    public Rect2i ingredientSlotArea() {
        return ingredientSlotArea;
    }

    public void clearRenderedAreas() {
        ingredientSlotArea = null;
        renderedFieldArea = null;
    }

    @Nullable
    public Rect2i renderedFieldArea() {
        return renderedFieldArea;
    }

    public void renderIngredientTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (ingredientSlot != null && ingredientSlotArea != null && ingredientSlotArea.contains(mouseX, mouseY)) {
            ingredientSlot.renderSlotTooltip(graphics, mouseX, mouseY);
        }
    }

    public BoxWidget addTrailingButton(int width, Runnable onClick, Supplier<String> labelGetter,
                                       IntSupplier colorGetter, @Nullable String tooltipText) {
        BoxWidget button = new FormBoxWidget(0, 0, width, 16).withCallback(onClick);
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.getToolTip().add(Component.literal(tooltipText));
        }
        trailingButtons.add(new TrailingButton(button, width, labelGetter, colorGetter));
        listeners.add(button);
        return button;
    }

    public BoxWidget addTrailingIcon(PondererGuiIcons icon, Runnable onClick, @Nullable String tooltipText) {
        BoxWidget button = addTrailingButton(EntryTextSupport.squareSize(), onClick, () -> "", () -> 0xFFFFFF, tooltipText);
        return icon.attach(button);
    }

    public void setTrailingText(@Nullable Supplier<String> trailingTextGetter) {
        this.trailingTextGetter = trailingTextGetter;
    }

    public void setUnitText(@Nullable Supplier<String> unitTextGetter) {
        setTrailingText(unitTextGetter);
    }

    public PlainTextListEntry setPreferredFieldWidth(int preferredFieldWidth) {
        this.preferredFieldWidth = preferredFieldWidth <= 0 ? -1 : preferredFieldWidth;
        return this;
    }

    public PlainTextListEntry setMinimumControlWidth(int minimumControlWidth) {
        this.minimumControlWidth = minimumControlWidth <= 0 ? -1 : minimumControlWidth;
        return this;
    }

    public PlainTextListEntry setControlWidthScale(float controlWidthScale) {
        this.controlWidthScale = Math.max(0.1f, Math.min(1.0f, controlWidthScale));
        return this;
    }

    public PlainTextListEntry setHalfWidthControl(int minimumControlWidth) {
        return setControlWidthScale(EntryTextSupport.halfWidthControlScale())
            .setMinimumControlWidth(minimumControlWidth);
    }

    @Override
    public boolean matchesQuery(String query) {
        return searchText.contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    protected int getLabelWidth(int totalWidth) {
        return EntryTextSupport.compactLabelWidth(totalWidth);
    }

    @Override
    public void tick() {
        super.tick();
        for (TrailingButton trailingButton : trailingButtons) {
            trailingButton.widget().tick();
        }
    }

    protected boolean hasMultipleControls() {
        return ingredientSlot != null || !trailingButtons.isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int trailingWidth = getTrailingWidth();
        int leadingWidth = ingredientSlot == null ? 0 : IngredientSlotWidget.OUTER_SIZE + EntryTextSupport.inputControlGap();
        int controlMinimum = minimumControlWidth > 0 ? minimumControlWidth : leadingWidth + trailingWidth + 60;
        if (preferredFieldWidth > 0) {
            controlMinimum = Math.max(controlMinimum, preferredFieldWidth + leadingWidth + trailingWidth);
        }
        EntryTextSupport.AlignedControlBounds controlBounds = EntryTextSupport.rightAlignedControlBounds(
            x, width, labelWidth, controlWidthScale, controlMinimum);
        int actualFieldWidth = Math.max(60, controlBounds.width() - leadingWidth - trailingWidth);
        int fieldX = controlBounds.x() + leadingWidth;

        if (ingredientSlot != null) {
            ingredientSlot.setX(EntryTextSupport.inputX(controlBounds.x()));
            ingredientSlot.setY(EntryTextSupport.inputY(EntryTextSupport.controlY(y, height)));
            ingredientSlotArea = new Rect2i(ingredientSlot.getX(), ingredientSlot.getY(),
                IngredientSlotWidget.SIZE, IngredientSlotWidget.SIZE);
            ingredientSlot.render(graphics, mouseX, mouseY, partialTicks);
        }

        EntryTextSupport.setRowInputBounds(textField, fieldX, actualFieldWidth, y, height);
        renderedFieldArea = new Rect2i(textField.getX(), textField.getY(), textField.getWidth(), textField.getHeight());
        textField.render(graphics, mouseX, mouseY, partialTicks);

        renderTrailing(graphics, y, x, width, height, mouseX, mouseY, partialTicks);
    }

    protected int getTrailingWidth() {
        int width = 0;
        String trailingText = getTrailingText();
        if (!trailingText.isEmpty()) {
            width += Minecraft.getInstance().font.width(trailingText);
            if (!trailingButtons.isEmpty()) {
                width += CONTROL_GAP;
            }
        }
        for (int i = 0; i < trailingButtons.size(); i++) {
            width += trailingButtons.get(i).renderedWidth();
            if (i + 1 < trailingButtons.size()) {
                width += CONTROL_GAP;
            }
        }
        return width == 0 ? 0 : width + (hasMultipleControls()
            ? EntryTextSupport.inputControlGap() : EntryTextSupport.rightControlGap());
    }

    protected void renderTrailing(GuiGraphics graphics, int y, int x, int width, int height,
                                  int mouseX, int mouseY, float partialTicks) {
        int cursorX = EntryTextSupport.contentRight(x, width);
        var font = Minecraft.getInstance().font;

        for (int i = trailingButtons.size() - 1; i >= 0; i--) {
            TrailingButton trailingButton = trailingButtons.get(i);
            BoxWidget button = trailingButton.widget();
            int buttonWidth = trailingButton.renderedWidth();
            cursorX -= buttonWidth;
            EntryTextSupport.setRowButtonBounds(button, cursorX, buttonWidth, y, height);
            button.render(graphics, mouseX, mouseY, partialTicks);
            EntryTextSupport.drawButtonLabel(graphics, button, trailingButton.labelGetter().get(),
                trailingButton.colorGetter().getAsInt());
            cursorX -= CONTROL_GAP;
        }

        String trailingText = getTrailingText();
        if (!trailingText.isEmpty()) {
            graphics.drawString(font, trailingText,
                cursorX - font.width(trailingText),
                EntryTextSupport.controlY(y, height) + (EntryTextSupport.controlHeight() - 8) / 2,
                0xA0A0A0);
        }
    }

    private String getTrailingText() {
        if (trailingTextGetter == null) {
            return "";
        }
        String trailingText = trailingTextGetter.get();
        return trailingText == null ? "" : trailingText;
    }
}
