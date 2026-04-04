package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class PlainTextListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    protected final ConfigTextField textField;
    private final String searchText;

    public PlainTextListEntry(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                              String initialValue, Consumer<String> responder) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.textField = new ConfigTextField(Minecraft.getInstance().font, 0, 0, 200, 20);
        EntryTextSupport.applyHint(textField, hintKey);
        this.textField.setResponder(responder);
        this.textField.setValue(initialValue);
        this.textField.moveCursorToStart();
        listeners.add(textField);
    }

    public void setValue(String value) {
        textField.setValue(value != null ? value : "");
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
        return (int) (totalWidth * labelWidthMult) + 30;
    }

    @Override
    public void tick() {
        super.tick();
        textField.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int fieldX = x + labelWidth + 4;
        int trailingWidth = getTrailingWidth();

        textField.setX(fieldX);
        textField.setY(y + 8);
        textField.setWidth(Math.max(60, width - labelWidth - trailingWidth - 4));
        textField.setHeight(20);
        textField.render(graphics, mouseX, mouseY, partialTicks);

        renderTrailing(graphics, y, x, width, height, mouseX, mouseY, partialTicks);
    }

    protected int getTrailingWidth() {
        return 0;
    }

    protected void renderTrailing(GuiGraphics graphics, int y, int x, int width, int height,
                                  int mouseX, int mouseY, float partialTicks) {
    }
}
