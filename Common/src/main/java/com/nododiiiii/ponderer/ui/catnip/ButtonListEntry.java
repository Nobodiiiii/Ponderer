package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class ButtonListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private final String searchText;
    private final BoxWidget button;
    private final Supplier<String> labelGetter;
    private final IntSupplier colorGetter;
    private final int buttonWidth;

    public ButtonListEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                           Runnable onClick, Supplier<String> labelGetter, IntSupplier colorGetter,
                           @Nullable String buttonTooltipText) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        this.labelGetter = labelGetter;
        this.colorGetter = colorGetter;
        this.buttonWidth = buttonWidth;

        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.button = new BoxWidget(0, 0, buttonWidth, 16).withCallback(onClick);
        if (buttonTooltipText != null && !buttonTooltipText.isBlank()) {
            this.button.getToolTip().add(Component.literal(buttonTooltipText));
        }
        listeners.add(button);
    }

    public BoxWidget button() {
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
        button.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int buttonHeight = Math.max(16, height - 20);
        button.setX(x + width - buttonWidth - 4);
        button.setY(y + 10);
        button.setWidth(buttonWidth);
        button.setHeight(buttonHeight);
        button.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(Minecraft.getInstance().font, labelGetter.get(),
            button.getX() + button.getWidth() / 2,
            button.getY() + (button.getHeight() - 8) / 2,
            colorGetter.getAsInt());
    }
}
