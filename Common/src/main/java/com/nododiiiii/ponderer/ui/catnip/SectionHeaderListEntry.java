package com.nododiiiii.ponderer.ui.catnip;

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
        SectionHeaderRenderer.render(graphics, Minecraft.getInstance().font, titleGetter.get(),
            EntryTextSupport.contentLeft(x), EntryTextSupport.contentRight(x, width),
            y, height, annotations.containsKey("highlight"));
    }
}
