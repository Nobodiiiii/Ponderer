package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

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

    public static String createSearchText(String labelKey, @Nullable String tooltipKey) {
        StringBuilder builder = new StringBuilder(UIText.of(labelKey));
        if (tooltipKey != null && !tooltipKey.isBlank()) {
            builder.append(' ').append(UIText.of(tooltipKey));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    public static void applyTooltip(ConfigScreenList.LabeledEntry entry, String labelKey, @Nullable String tooltipKey) {
        entry.getLabelTooltip().clear();
        entry.getLabelTooltip().add(Component.literal(UIText.of(labelKey)).withStyle(ChatFormatting.WHITE));
        if (tooltipKey != null && !tooltipKey.isBlank()) {
            entry.getLabelTooltip().addAll(FontHelper.cutTextComponent(
                Component.literal(UIText.of(tooltipKey)),
                Palette.ALL_GRAY));
        }
    }

    public static void applyHint(@Nullable EditBox field, @Nullable String hintKey) {
        if (!(field instanceof HintableTextFieldWidget hintableField) || hintKey == null || hintKey.isBlank()) {
            return;
        }
        hintableField.setHint(UIText.of(hintKey));
    }

    public static int compactLabelWidth(int totalWidth) {
        return (int) (totalWidth * 0.30f) + 14;
    }

    public static int controlAreaWidth(int totalWidth, int labelWidth) {
        return Math.max(60, totalWidth - labelWidth - 8);
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
        return x + totalWidth - 4 - renderedControlWidth;
    }

    public static float halfWidthControlScale() {
        return HALF_WIDTH_CONTROL_SCALE;
    }

    public static int rightControlGap() {
        return RIGHT_CONTROL_GAP;
    }
}
