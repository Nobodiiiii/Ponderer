package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
        this.keyField.moveCursorToStart(false);
        this.valueField.moveCursorToStart(false);
        this.keyField.setHint(Component.literal("facing"));
        this.valueField.setHint(Component.literal("north"));
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
        int buttonWidth = EntryTextSupport.squareSize();
        int equalsWidth = Minecraft.getInstance().font.width("=");
        int gap = EntryTextSupport.inputControlGap();
        int minControlWidth = 48 * 2 + buttonWidth + equalsWidth + gap * 3;
        int controlWidth = Math.max(minControlWidth, EntryTextSupport.controlAreaWidth(width, labelWidth));
        int fieldsWidth = controlWidth - buttonWidth - equalsWidth - gap * 3;
        int keyWidth = Math.max(48, fieldsWidth / 2);
        int valueWidth = Math.max(48, fieldsWidth - keyWidth);
        int fieldX = EntryTextSupport.rightAlignedControlX(x, width, controlWidth);
        EntryTextSupport.setRowInputBounds(keyField, fieldX, keyWidth, y, height);
        keyField.render(graphics, mouseX, mouseY, partialTicks);

        int equalsX = fieldX + keyWidth + gap;
        int valueX = equalsX + equalsWidth + gap;
        EntryTextSupport.setRowInputBounds(valueField, valueX, valueWidth, y, height);
        valueField.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawString(Minecraft.getInstance().font, "=",
            equalsX,
            EntryTextSupport.controlY(y, height) + (EntryTextSupport.controlHeight() - 8) / 2,
            0xA0A0A0);

        EntryTextSupport.setRowButtonBounds(removeButton, valueX + valueWidth + gap, buttonWidth, y, height);
        removeButton.render(graphics, mouseX, mouseY, partialTicks);
    }
}
