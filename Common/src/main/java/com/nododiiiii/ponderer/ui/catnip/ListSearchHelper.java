package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;

import javax.annotation.Nullable;
import java.util.Locale;

public final class ListSearchHelper {

    private ListSearchHelper() {
    }

    public static void applySearchFilter(@Nullable ConfigScreenList list, @Nullable HintableTextFieldWidget search,
                                         @Nullable String query, int entryHeight) {
        if (list == null || search == null) {
            return;
        }

        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            list.setScrollAmount(0);
            search.setTextColor(UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
            return;
        }

        for (ConfigScreenList.Entry entry : list.children()) {
            if (entry instanceof SearchableListEntry searchable && searchable.matchesQuery(normalized)) {
                searchable.highlightEntry();
                int index = list.children().indexOf(entry);
                if (index >= 0) {
                    list.setScrollAmount(index * (double) entryHeight);
                }
                search.setTextColor(UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
                return;
            }
        }

        list.setScrollAmount(0);
        search.setTextColor(AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB());
    }
}
