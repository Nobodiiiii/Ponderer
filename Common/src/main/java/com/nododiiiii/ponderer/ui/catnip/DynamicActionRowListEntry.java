package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class DynamicActionRowListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {
    private final Supplier<String> labelGetter;
    private final Supplier<String> detailGetter;
    private final Supplier<String> searchTextGetter;
    private final List<ActionStripListEntry.ButtonModel> buttons;

    public DynamicActionRowListEntry(Supplier<String> labelGetter, Supplier<String> detailGetter,
                                     @Nullable Supplier<String> searchTextGetter,
                                     List<ActionStripListEntry.ButtonModel> buttons) {
        super("");
        this.labelGetter = labelGetter;
        this.detailGetter = detailGetter;
        this.searchTextGetter = searchTextGetter == null ? this::defaultSearchText : searchTextGetter;
        this.buttons = new ArrayList<>(buttons);
        for (ActionStripListEntry.ButtonModel button : this.buttons) {
            listeners.add(button.widget());
        }
    }

    @Override
    public boolean matchesQuery(String query) {
        return searchTextGetter.get().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        ActionStripListEntry.tickButtons(buttons);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        int controlWidth = Math.min(150, Math.max(96, width / 2));
        int controlX = x + width - 4 - controlWidth;
        int textWidth = Math.max(20, controlX - x - 8);
        var font = Minecraft.getInstance().font;
        int titleColor = annotations.containsKey("highlight") ? 0xFFF3D46B : 0xFFE8E8E8;
        String title = font.plainSubstrByWidth(labelGetter.get(), textWidth);
        String detail = font.plainSubstrByWidth(detailGetter.get(), textWidth);
        graphics.drawString(font, title, x + 4, y + 6, titleColor);
        if (!detail.isBlank()) {
            graphics.drawString(font, detail, x + 4, y + 18, 0xFF999999);
        }
        ActionStripListEntry.renderButtonCluster(graphics, buttons, controlX, controlWidth, y, height,
            mouseX, mouseY, partialTicks);
    }

    @Override
    public Component getNarration() {
        return Component.empty();
    }

    private String defaultSearchText() {
        return labelGetter.get() + " " + detailGetter.get();
    }
}
