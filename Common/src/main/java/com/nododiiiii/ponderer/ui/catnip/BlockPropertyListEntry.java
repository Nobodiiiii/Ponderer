package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

public class BlockPropertyListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private static final int CONTROL_GAP = EntryTextSupport.rightControlGap();

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
        this.keyField.moveCursorToStart(false);
        this.valueField.moveCursorToStart(false);
        this.keyField.setHint("facing");
        this.valueField.setHint("north");
        this.removeButton = new FormBoxWidget(0, 0, 20, 16).withCallback(onRemove);
        PonderIconStencils.attachFail(removeButton, PonderIconStencils.centered(PonderGuiTextures.ICON_DISABLE));
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
        removeButton.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int buttonWidth = 20;
        int equalsWidth = Minecraft.getInstance().font.width("=");
        int minControlWidth = 48 * 2 + buttonWidth + equalsWidth + CONTROL_GAP * 3;
        int controlWidth = Math.max(minControlWidth, EntryTextSupport.controlAreaWidth(width, labelWidth));
        int fieldsWidth = controlWidth - buttonWidth - equalsWidth - CONTROL_GAP * 3;
        int keyWidth = Math.max(48, fieldsWidth / 2);
        int valueWidth = Math.max(48, fieldsWidth - keyWidth);
        int fieldX = x + labelWidth + 4;
        int fieldY = y + 8;

        keyField.setX(fieldX);
        keyField.setY(fieldY);
        keyField.setWidth(keyWidth);
        keyField.setHeight(20);
        keyField.render(graphics, mouseX, mouseY, partialTicks);

        int equalsX = fieldX + keyWidth + CONTROL_GAP;
        int valueX = equalsX + equalsWidth + CONTROL_GAP;
        valueField.setX(valueX);
        valueField.setY(fieldY);
        valueField.setWidth(valueWidth);
        valueField.setHeight(20);
        valueField.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawString(Minecraft.getInstance().font, "=",
            equalsX,
            y + 14,
            0xA0A0A0);

        int buttonHeight = Math.max(16, height - 20);
        removeButton.setX(valueX + valueWidth + CONTROL_GAP);
        removeButton.setY(y + 10);
        removeButton.setWidth(buttonWidth);
        removeButton.setHeight(buttonHeight);
        removeButton.render(graphics, mouseX, mouseY, partialTicks);
    }
}
