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
}
