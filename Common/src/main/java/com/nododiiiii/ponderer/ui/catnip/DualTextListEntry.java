package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

public class DualTextListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private final String searchText;
    private final ConfigTextField firstField;
    private final ConfigTextField secondField;
    private int firstPreferredWidth = -1;
    private int secondPreferredWidth = -1;

    public DualTextListEntry(String labelKey, @Nullable String tooltipKey,
                             @Nullable String firstHint, @Nullable String secondHint) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.firstField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 60, 20);
        this.secondField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 60, 20);
        this.firstField.setMaxLength(32);
        this.secondField.setMaxLength(32);
        EntryTextSupport.applyHint(firstField, firstHint);
        EntryTextSupport.applyHint(secondField, secondHint);
        listeners.add(firstField);
        listeners.add(secondField);
    }

    public void setPreferredWidths(int firstWidth, int secondWidth) {
        this.firstPreferredWidth = firstWidth;
        this.secondPreferredWidth = secondWidth;
    }

    public ConfigTextField firstField() {
        return firstField;
    }

    public ConfigTextField secondField() {
        return secondField;
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
        firstField.tick();
        secondField.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int available = Math.max(120, width - labelWidth - 16);
        int firstWidth = firstPreferredWidth > 0 ? firstPreferredWidth : Math.max(40, (available - 5) / 2);
        int secondWidth = secondPreferredWidth > 0 ? secondPreferredWidth : Math.max(40, (available - 5) / 2);
        int clusterWidth = firstWidth + 5 + secondWidth;
        if (clusterWidth > available) {
            int fieldWidth = Math.max(40, (available - 5) / 2);
            firstWidth = fieldWidth;
            secondWidth = fieldWidth;
            clusterWidth = firstWidth + 5 + secondWidth;
        }
        int fieldX = x + width - 4 - clusterWidth;
        int fieldY = y + 8;

        firstField.setX(fieldX);
        firstField.setY(fieldY);
        firstField.setWidth(firstWidth);
        firstField.setHeight(20);
        firstField.render(graphics, mouseX, mouseY, partialTicks);

        secondField.setX(fieldX + firstWidth + 5);
        secondField.setY(fieldY);
        secondField.setWidth(secondWidth);
        secondField.setHeight(20);
        secondField.render(graphics, mouseX, mouseY, partialTicks);
    }
}
