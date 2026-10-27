package com.nododiiiii.ponderer.application.ui.widgets;

import com.nododiiiii.ponderer.support.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Remote-browser row layout with the structure picker's selection indicator. */
public class SelectionRowListEntry extends DynamicActionRowListEntry {
    private final int extraButtonCount;

    public SelectionRowListEntry(Supplier<String> title, Supplier<String> detail,
                                 @Nullable Supplier<String> searchText, BooleanSupplier selected,
                                 @Nullable Runnable onSelect, @Nullable Supplier<List<Component>> tooltip,
                                 Supplier<ItemStack> icon, List<ActionStripListEntry.ButtonModel> extraButtons) {
        super(title, detail, searchText, buttons(selected, onSelect, tooltip, extraButtons), icon, () -> false);
        extraButtonCount = extraButtons.size();
    }

    private static List<ActionStripListEntry.ButtonModel> buttons(BooleanSupplier selected,
            @Nullable Runnable onSelect, @Nullable Supplier<List<Component>> tooltip,
            List<ActionStripListEntry.ButtonModel> extraButtons) {
        List<ActionStripListEntry.ButtonModel> buttons = new ArrayList<>();
        buttons.add(ActionStripListEntry.button(
            () -> selectionLabel(selected.getAsBoolean()), tooltip,
            () -> {
                if (onSelect != null) onSelect.run();
            },
            () -> selected.getAsBoolean() ? 0xFFE082 : 0xFFFFFF,
            () -> true));
        buttons.addAll(extraButtons);
        return buttons;
    }

    private static String selectionLabel(boolean selected) {
        return selected ? "● " + UIText.of("ponderer.ui.selection.selected")
            : UIText.of("ponderer.ui.selection.select");
    }

    @Override
    protected int controlWidth(int width) {
        var font = Minecraft.getInstance().font;
        int selectWidth = Math.max(68, Math.max(font.width(selectionLabel(false)), font.width(selectionLabel(true))) + 16);
        return selectWidth + extraButtonCount * (EntryTextSupport.squareSize() + EntryTextSupport.multiControlGap());
    }
}
