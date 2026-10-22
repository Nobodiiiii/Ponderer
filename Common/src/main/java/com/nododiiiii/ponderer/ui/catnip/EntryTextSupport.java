package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import com.nododiiiii.ponderer.ui.UILayoutConstants;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

public final class EntryTextSupport {

    private static final int RIGHT_CONTROL_GAP = 8;
    private static final float HALF_WIDTH_CONTROL_SCALE = 0.5f;

    public record AlignedControlBounds(int x, int width) {
        public int right() {
            return x + width;
        }

        public int rightAlignedContentX(int contentWidth) {
            return right() - contentWidth;
        }
    }

    private EntryTextSupport() {
    }

    public static String createSearchText(String labelKey, @Nullable String... tooltipKeys) {
        StringBuilder builder = new StringBuilder(UIText.of(labelKey));
        for (String tooltip : tooltipTexts(tooltipKeys)) {
            builder.append(' ').append(tooltip);
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    public static void applyTooltip(ConfigScreenList.LabeledEntry entry, String labelKey, @Nullable String... tooltipKeys) {
        entry.getLabelTooltip().clear();
        entry.getLabelTooltip().add(Component.literal(UIText.of(labelKey)).withStyle(ChatFormatting.WHITE));
        for (String tooltip : tooltipTexts(tooltipKeys)) {
            entry.getLabelTooltip().addAll(FontHelper.cutTextComponent(
                Component.literal(tooltip),
                Palette.ALL_GRAY));
        }
    }

    private static Set<String> tooltipTexts(@Nullable String[] tooltipKeys) {
        Set<String> texts = new LinkedHashSet<>();
        if (tooltipKeys != null) {
            for (String key : tooltipKeys) {
                if (key != null && !key.isBlank()) {
                    String text = UIText.of(key);
                    if (!text.isBlank()) texts.add(text);
                }
            }
        }
        return texts;
    }

    public static void applyHint(@Nullable EditBox field, @Nullable String hintKey) {
        if (!(field instanceof HintableTextFieldWidget hintableField) || hintKey == null || hintKey.isBlank()) {
            return;
        }
        hintableField.setHint(Component.literal(UIText.of(hintKey)));
    }

    public static int compactLabelWidth(int totalWidth) {
        return (int) (totalWidth * 0.30f) + 14;
    }

    public static int controlAreaWidth(int totalWidth, int labelWidth) {
        return Math.max(60, totalWidth - labelWidth - RIGHT_CONTROL_GAP - UILayoutConstants.ENTRY_CONTENT_PADDING);
    }

    public static int scaledControlWidth(int totalWidth, int labelWidth, float controlWidthScale, int minimumWidth) {
        int fullControlWidth = controlAreaWidth(totalWidth, labelWidth);
        int scaledWidth = Math.max(minimumWidth, Math.round(fullControlWidth * controlWidthScale));
        return Math.min(fullControlWidth, scaledWidth);
    }

    public static AlignedControlBounds rightAlignedControlBounds(int x, int totalWidth, int labelWidth,
                                                                 float controlWidthScale, int minimumWidth) {
        int renderedControlWidth = scaledControlWidth(totalWidth, labelWidth, controlWidthScale, minimumWidth);
        return new AlignedControlBounds(rightAlignedControlX(x, totalWidth, renderedControlWidth), renderedControlWidth);
    }

    public static int rightAlignedControlX(int x, int totalWidth, int renderedControlWidth) {
        return contentRight(x, totalWidth) - renderedControlWidth;
    }

    public static int contentLeft(int x) {
        return x + UILayoutConstants.ENTRY_CONTENT_PADDING;
    }

    public static int contentRight(int x, int totalWidth) {
        return x + totalWidth - UILayoutConstants.ENTRY_CONTENT_PADDING;
    }

    public static int contentWidth(int totalWidth) {
        return Math.max(0, totalWidth - 2 * UILayoutConstants.ENTRY_CONTENT_PADDING);
    }

    public static int fullButtonY(int rowY, int rowHeight) {
        return rowHeight <= UILayoutConstants.COMPACT_LIST_ENTRY_H ? rowY
            : rowY + UILayoutConstants.FULL_BUTTON_VERTICAL_PADDING;
    }

    public static int fullButtonHeight(int rowHeight) {
        return rowHeight <= UILayoutConstants.COMPACT_LIST_ENTRY_H ? UILayoutConstants.FULL_BUTTON_MIN_H
            : Math.max(UILayoutConstants.FULL_BUTTON_MIN_H,
                rowHeight - 2 * UILayoutConstants.FULL_BUTTON_VERTICAL_PADDING);
    }

    public static int inputX(int layoutX) {
        return layoutX + UILayoutConstants.INPUT_OUTER_PADDING;
    }

    public static int inputWidth(int layoutWidth) {
        return Math.max(1, layoutWidth - 2 * UILayoutConstants.INPUT_OUTER_PADDING);
    }

    public static int inputY(int layoutY) {
        return layoutY + UILayoutConstants.INPUT_OUTER_PADDING;
    }

    public static int inputHeight(int layoutHeight) {
        return Math.max(1, layoutHeight - 2 * UILayoutConstants.INPUT_OUTER_PADDING);
    }

    public static int inputOuterHeight() {
        return UILayoutConstants.LIST_INPUT_OUTER_H;
    }

    public static void setInputHorizontalBounds(EditBox field, int layoutX, int layoutWidth) {
        field.setX(inputX(layoutX));
        field.setWidth(inputWidth(layoutWidth));
    }

    public static int controlHeight() {
        return inputOuterHeight();
    }

    public static int controlY(int rowY, int rowHeight) {
        return rowY + Math.max(0, (rowHeight - controlHeight()) / 2);
    }

    public static int inputGap() {
        return multiControlGap();
    }

    public static int inputControlGap() {
        return multiControlGap();
    }

    public static int multiControlGap() {
        return UILayoutConstants.MULTI_CONTROL_GAP;
    }

    public static int squareSize() {
        return UILayoutConstants.MULTI_SQUARE_SIZE;
    }

    /** Allocated bounds include the transparent padding; widget bounds remain visible/clickable bounds. */
    public static void setInputBounds(EditBox field, int layoutX, int layoutY, int layoutWidth, int layoutHeight) {
        setInputHorizontalBounds(field, layoutX, layoutWidth);
        field.setY(inputY(layoutY));
        ((com.nododiiiii.ponderer.mixin.AbstractWidgetSizeAccessor) field).ponderer$setHeight(inputHeight(layoutHeight));
    }

    public static void setRowInputBounds(EditBox field, int layoutX, int layoutWidth, int rowY, int rowHeight) {
        setInputBounds(field, layoutX, controlY(rowY, rowHeight), layoutWidth, inputOuterHeight());
    }

    public static boolean isSquareButton(@Nullable String label) {
        return label == null || label.codePointCount(0, label.length()) <= 1;
    }

    public static int buttonWidth(@Nullable String label, int preferredWidth) {
        return isSquareButton(label) ? squareSize()
            : Math.max(preferredWidth, Minecraft.getInstance().font.width(label) + 12);
    }

    public static void setRowButtonBounds(BoxWidget button, int visibleX, int visibleWidth, int rowY, int rowHeight) {
        int visibleHeight = controlHeight();
        int visibleY = rowY + Math.max(0, (rowHeight - visibleHeight) / 2);
        setBoxHorizontalBounds(button, visibleX, visibleWidth);
        button.setY(visibleY);
        button.setHeight(visibleHeight);
    }

    public static void drawButtonLabel(net.minecraft.client.gui.GuiGraphics graphics, BoxWidget button,
                                       String label, int color) {
        var font = Minecraft.getInstance().font;
        int textWidth = button.getWidth() - 4;
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label, Math.max(0, textWidth)),
            button.getX() + button.getWidth() / 2, button.getY() + (button.getHeight() - 8) / 2, color);
    }

    /** Box widget dimensions already include the complete visible border. */
    public static void setBoxHorizontalBounds(BoxWidget widget, int visibleX, int visibleWidth) {
        widget.setX(visibleX);
        widget.setWidth(Math.max(1, visibleWidth));
    }

    public static float halfWidthControlScale() {
        return HALF_WIDTH_CONTROL_SCALE;
    }

    public static int rightControlGap() {
        return RIGHT_CONTROL_GAP;
    }
}
