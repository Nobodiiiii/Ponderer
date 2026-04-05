package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class PlainTextListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    protected static final int CONTROL_GAP = EntryTextSupport.rightControlGap();

    protected record TrailingButton(BoxWidget widget, int width, Supplier<String> labelGetter, IntSupplier colorGetter) {
    }

    protected final ConfigTextField textField;
    private final String searchText;
    protected final List<TrailingButton> trailingButtons = new ArrayList<>();
    @Nullable
    private Supplier<String> unitTextGetter;
    private int preferredFieldWidth = -1;
    private int minimumControlWidth = -1;
    private float controlWidthScale = 1.0f;

    public PlainTextListEntry(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                              String initialValue, Consumer<String> responder) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.textField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 200, 20);
        EntryTextSupport.applyHint(textField, hintKey);
        this.textField.setValue(initialValue);
        this.textField.moveCursorToStart();
        this.textField.setResponder(responder);
        listeners.add(textField);
    }

    public ConfigTextField field() {
        return textField;
    }

    public void setValue(String value) {
        textField.setValue(value != null ? value : "");
    }

    public BoxWidget addTrailingButton(int width, Runnable onClick, Supplier<String> labelGetter,
                                       IntSupplier colorGetter, @Nullable String tooltipText) {
        BoxWidget button = new BoxWidget(0, 0, width, 16).withCallback(onClick);
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.getToolTip().add(Component.literal(tooltipText));
        }
        trailingButtons.add(new TrailingButton(button, width, labelGetter, colorGetter));
        listeners.add(button);
        return button;
    }

    public void setUnitText(@Nullable Supplier<String> unitTextGetter) {
        this.unitTextGetter = unitTextGetter;
    }

    public void setPreferredFieldWidth(int preferredFieldWidth) {
        this.preferredFieldWidth = preferredFieldWidth;
    }

    public void setMinimumControlWidth(int minimumControlWidth) {
        this.minimumControlWidth = minimumControlWidth;
    }

    public PlainTextListEntry setControlWidthScale(float controlWidthScale) {
        this.controlWidthScale = Math.max(0.1f, Math.min(1.0f, controlWidthScale));
        return this;
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
        textField.tick();
        for (TrailingButton trailingButton : trailingButtons) {
            trailingButton.widget().tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int trailingWidth = getTrailingWidth();
        int controlMinimum = minimumControlWidth > 0 ? minimumControlWidth : trailingWidth + 60;
        int renderedControlWidth = EntryTextSupport.scaledControlWidth(
            width, labelWidth, controlWidthScale, controlMinimum);
        int actualFieldWidth = Math.max(60, renderedControlWidth - trailingWidth);
        int fieldX = EntryTextSupport.rightAlignedControlX(x, width, renderedControlWidth);

        textField.setX(fieldX);
        textField.setY(y + 8);
        textField.setWidth(actualFieldWidth);
        textField.setHeight(20);
        textField.render(graphics, mouseX, mouseY, partialTicks);

        renderTrailing(graphics, y, x, width, height, mouseX, mouseY, partialTicks);
    }

    protected int getTrailingWidth() {
        int width = 0;
        if (unitTextGetter != null) {
            width += Minecraft.getInstance().font.width(unitTextGetter.get()) + CONTROL_GAP;
        }
        if (!trailingButtons.isEmpty()) {
            width += CONTROL_GAP;
        }
        for (int i = 0; i < trailingButtons.size(); i++) {
            width += trailingButtons.get(i).width();
            if (i + 1 < trailingButtons.size()) {
                width += CONTROL_GAP;
            }
        }
        return width;
    }

    protected void renderTrailing(GuiGraphics graphics, int y, int x, int width, int height,
                                  int mouseX, int mouseY, float partialTicks) {
        int cursorX = x + width - 4;
        int buttonY = y + 10;
        int buttonHeight = Math.max(16, height - 20);
        var font = Minecraft.getInstance().font;

        for (int i = trailingButtons.size() - 1; i >= 0; i--) {
            TrailingButton trailingButton = trailingButtons.get(i);
            BoxWidget button = trailingButton.widget();
            cursorX -= trailingButton.width();
            button.setX(cursorX);
            button.setY(buttonY);
            button.setWidth(trailingButton.width());
            button.setHeight(buttonHeight);
            button.render(graphics, mouseX, mouseY, partialTicks);
            graphics.drawCenteredString(font, trailingButton.labelGetter().get(),
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                trailingButton.colorGetter().getAsInt());
            cursorX -= CONTROL_GAP;
        }

        if (unitTextGetter != null) {
            String unitText = unitTextGetter.get();
            graphics.drawString(font, unitText,
                cursorX - font.width(unitText),
                y + 14,
                0xA0A0A0);
        }
    }
}
