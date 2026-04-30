package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UILayoutConstants;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.function.Supplier;

public class SectionHeaderListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private final Supplier<String> titleGetter;

    public SectionHeaderListEntry(String title) {
        this(() -> title);
    }

    public SectionHeaderListEntry(Supplier<String> titleGetter) {
        super("");
        this.titleGetter = titleGetter;
    }

    @Override
    public boolean matchesQuery(String query) {
        return titleGetter.get().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        String title = titleGetter.get();
        var font = Minecraft.getInstance().font;
        int color = annotations.containsKey("highlight") ? 0xFFF3D46B : 0xFFCCCC77;
        boolean compact = height <= UILayoutConstants.COMPACT_LIST_ENTRY_H;
        graphics.drawString(font, title, x + 4, compact ? y + 8 : y + 11, color);
        int lineY = compact ? y + height - 3 : y + height - 10;
        graphics.fill(x + 4, lineY, x + width - 4, lineY + 1, 0x40FFFFFF);
    }
}
