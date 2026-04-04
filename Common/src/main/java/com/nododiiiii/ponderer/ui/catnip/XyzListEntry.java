package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.ConfigTextField;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class XyzListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private record TrailingButton(BoxWidget widget, int width, Supplier<String> labelGetter, IntSupplier colorGetter) {
    }

    private final String searchText;
    private final ConfigTextField xField;
    private final ConfigTextField yField;
    private final ConfigTextField zField;
    private final List<TrailingButton> trailingButtons = new ArrayList<>();

    public XyzListEntry(String labelKey, @Nullable String tooltipKey,
                        @Nullable String xHint, @Nullable String yHint, @Nullable String zHint) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.xField = new ConfigTextField(Minecraft.getInstance().font, 0, 0, 52, 20);
        this.yField = new ConfigTextField(Minecraft.getInstance().font, 0, 0, 52, 20);
        this.zField = new ConfigTextField(Minecraft.getInstance().font, 0, 0, 52, 20);
        this.xField.setMaxLength(32);
        this.yField.setMaxLength(32);
        this.zField.setMaxLength(32);
        EntryTextSupport.applyHint(xField, xHint);
        EntryTextSupport.applyHint(yField, yHint);
        EntryTextSupport.applyHint(zField, zHint);
        listeners.add(xField);
        listeners.add(yField);
        listeners.add(zField);
    }

    public ConfigTextField xField() {
        return xField;
    }

    public ConfigTextField yField() {
        return yField;
    }

    public ConfigTextField zField() {
        return zField;
    }

    public BoxWidget addTrailingButton(int width, Runnable onClick, Supplier<String> labelGetter,
                                       IntSupplier colorGetter, @Nullable String tooltipText) {
        BoxWidget button = new BoxWidget(0, 0, width, 16).withCallback(onClick);
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.getToolTip().add(Component.literal(tooltipText));
        }
        trailingButtons.add(new TrailingButton(button, width, labelGetter, colorGetter));
        listeners.add(button);
        return button;
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
        xField.tick();
        yField.tick();
        zField.tick();
        for (TrailingButton trailingButton : trailingButtons) {
            trailingButton.widget().tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int labelWidth = getLabelWidth(width);
        int buttonsWidth = 0;
        for (TrailingButton trailingButton : trailingButtons) {
            buttonsWidth += trailingButton.width() + 4;
        }

        int available = Math.max(120, width - labelWidth - buttonsWidth - 16);
        int fieldWidth = Math.max(34, (available - 10) / 3);
        int fieldX = x + labelWidth + 4;
        int fieldY = y + 8;

        xField.setX(fieldX);
        xField.setY(fieldY);
        xField.setWidth(fieldWidth);
        xField.setHeight(20);
        xField.render(graphics, mouseX, mouseY, partialTicks);

        yField.setX(fieldX + fieldWidth + 5);
        yField.setY(fieldY);
        yField.setWidth(fieldWidth);
        yField.setHeight(20);
        yField.render(graphics, mouseX, mouseY, partialTicks);

        zField.setX(fieldX + (fieldWidth + 5) * 2);
        zField.setY(fieldY);
        zField.setWidth(fieldWidth);
        zField.setHeight(20);
        zField.render(graphics, mouseX, mouseY, partialTicks);

        int cursorX = x + width - 4;
        int buttonY = y + 10;
        int buttonHeight = Math.max(16, height - 20);
        var font = Minecraft.getInstance().font;
        for (int i = trailingButtons.size() - 1; i >= 0; i--) {
            TrailingButton trailingButton = trailingButtons.get(i);
            BoxWidget button = trailingButton.widget();
            cursorX -= trailingButton.width();
            button.setX(cursorX);
            button.setY(buttonY);
            button.setWidth(trailingButton.width());
            button.setHeight(buttonHeight);
            button.render(graphics, mouseX, mouseY, partialTicks);
            graphics.drawCenteredString(font, trailingButton.labelGetter().get(),
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                trailingButton.colorGetter().getAsInt());
            cursorX -= 4;
        }
    }
}
