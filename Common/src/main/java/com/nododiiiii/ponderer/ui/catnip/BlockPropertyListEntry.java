package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

public class BlockPropertyListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private final String searchText;
    private final ConfigTextField keyField;
    private final ConfigTextField valueField;
    private final BoxWidget removeButton;

    public BlockPropertyListEntry(String labelKey, @Nullable String tooltipKey,
                                  String keyValue, String propertyValue, Runnable onRemove) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.keyField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 70, 20);
        this.valueField = new ClippedConfigTextField(Minecraft.getInstance().font, 0, 0, 70, 20);
        this.keyField.setValue(keyValue);
        this.valueField.setValue(propertyValue);
        this.keyField.moveCursorToStart();
        this.valueField.moveCursorToStart();
        this.keyField.setHint("facing");
        this.valueField.setHint("north");
        this.removeButton = new BoxWidget(0, 0, 20, 16).withCallback(onRemove);
        listeners.add(keyField);
        listeners.add(valueField);
        listeners.add(removeButton);
    }

    public ConfigTextField keyField() {
        return keyField;
    }

    public ConfigTextField valueField() {
        return valueField;
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
        keyField.tick();
        valueField.tick();
        removeButton.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int buttonWidth = 20;
        int available = Math.max(140, width - labelWidth - buttonWidth - 24);
        int fieldWidth = Math.max(48, (available - 17) / 2);
        int clusterWidth = fieldWidth * 2 + 17 + buttonWidth + 8;
        int fieldX = x + width - 4 - clusterWidth;
        int fieldY = y + 8;

        keyField.setX(fieldX);
        keyField.setY(fieldY);
        keyField.setWidth(fieldWidth);
        keyField.setHeight(20);
        keyField.render(graphics, mouseX, mouseY, partialTicks);

        valueField.setX(fieldX + fieldWidth + 17);
        valueField.setY(fieldY);
        valueField.setWidth(fieldWidth);
        valueField.setHeight(20);
        valueField.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawString(Minecraft.getInstance().font, "=",
            fieldX + fieldWidth + 6,
            y + 14,
            0xA0A0A0);

        int buttonHeight = Math.max(16, height - 20);
        removeButton.setX(fieldX + fieldWidth * 2 + 17 + 8);
        removeButton.setY(y + 10);
        removeButton.setWidth(buttonWidth);
        removeButton.setHeight(buttonHeight);
        removeButton.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(Minecraft.getInstance().font, "x",
            removeButton.getX() + removeButton.getWidth() / 2,
            removeButton.getY() + (removeButton.getHeight() - 8) / 2,
            0xFF5555);
    }
}
