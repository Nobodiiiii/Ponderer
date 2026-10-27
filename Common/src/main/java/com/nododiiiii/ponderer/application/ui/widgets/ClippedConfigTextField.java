package com.nododiiiii.ponderer.application.ui.widgets;

import com.nododiiiii.ponderer.application.ui.FieldType;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;

public class ClippedConfigTextField extends ConfigTextField {

    private static final int HINT_COLOR = 0x90_A0A0A0;
    private final Font hintFont;
    private String clippedHint;
    private boolean consumeRightClick = false;
    private double numericDragStep;
    private double numericDragMin;
    private double numericDragMax;
    private boolean numericDragEditable = true;

    public ClippedConfigTextField(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.hintFont = font;
    }

    public void setHint(String hint) {
        setHint(hint == null ? null : Component.literal(hint));
    }

    @Override
    public void setHint(Component hint) {
        // Ponder 1.0.87 removed the String hint field and setter. The Component API
        // is also available through EditBox in older versions.
        clippedHint = hint == null ? null : hint.getString();
        super.setHint(Component.empty());
    }

    public void setConsumeRightClick(boolean consumeRightClick) {
        this.consumeRightClick = consumeRightClick;
    }

    public void setNumericDrag(FieldType type) {
        numericDragStep = switch (type) {
            case XYZ, BLOCK_POSITION, POSITION, WORLD_POSITION, UI_POSITION, VECTOR -> 0.5;
            case TICKS -> 10;
            default -> 0;
        };
        numericDragMin = type == FieldType.TICKS ? 0 : type == FieldType.UI_POSITION ? -2 : -Double.MAX_VALUE;
        numericDragMax = type == FieldType.TICKS ? Integer.MAX_VALUE
            : type == FieldType.UI_POSITION ? 2 : Double.MAX_VALUE;
        setTooltip(numericDragStep == 0 ? null : Tooltip.create(Component.translatable(
            type == FieldType.TICKS ? "ponderer.ui.field.drag.ticks.tooltip"
                : type == FieldType.BLOCK_POSITION || type == FieldType.WORLD_POSITION
                    ? "ponderer.ui.field.drag.block_coordinate.tooltip"
                : "ponderer.ui.field.drag.coordinate.tooltip")));
    }

    public boolean supportsNumericDrag() {
        return numericDragStep > 0 && active && visible && numericDragEditable;
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        numericDragEditable = editable;
    }

    public double numericDragValue() {
        String value = getValue().trim();
        if (value.isEmpty()) {
            value = clippedHint == null || clippedHint.isBlank() ? "0" : clippedHint;
        }
        try {
            return numericDragStep == 10 ? Integer.parseInt(value) : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    public void adjustDraggedValue(double initialValue, long steps) {
        BigDecimal value = BigDecimal.valueOf(initialValue)
            .add(BigDecimal.valueOf(numericDragStep).multiply(BigDecimal.valueOf(steps)))
            .max(BigDecimal.valueOf(numericDragMin)).min(BigDecimal.valueOf(numericDragMax));
        String text = value.stripTrailingZeros().toPlainString();
        if (!text.equals(getValue())) {
            setValue(text);
            moveCursorToEnd(false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (consumeRightClick && button == 1 && isMouseOver(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        if (clippedHint == null || clippedHint.isEmpty() || !getValue().isEmpty()) {
            return;
        }

        int maxWidth = Math.max(0, getWidth() - 10);
        String clipped = hintFont.plainSubstrByWidth(clippedHint, maxWidth);
        graphics.drawString(hintFont, clipped, getX() + 5, getY() + (getHeight() - 8) / 2, HINT_COLOR);
    }
}
