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

    private static final int BOX_WIDGET_VISUAL_INSET = 4;
    private static final int TEXT_FIELD_BORDER_NUDGE = 2;

    private final String searchText;
    private final BoxWidget button;
    private final Supplier<String> labelGetter;
    private final IntSupplier colorGetter;
    @Nullable
    private final Supplier<String> buttonTooltipGetter;
    protected final int buttonWidth;
    private int minimumControlWidth = -1;
    private float controlWidthScale = 1.0f;

    public ButtonListEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                           Runnable onClick, Supplier<String> labelGetter, IntSupplier colorGetter,
                           @Nullable String buttonTooltipText) {
        this(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter,
            buttonTooltipText == null ? null : () -> buttonTooltipText);
    }

    public ButtonListEntry(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                           Runnable onClick, Supplier<String> labelGetter, IntSupplier colorGetter,
                           @Nullable Supplier<String> buttonTooltipGetter) {
        super(UIText.of(labelKey));
        this.searchText = EntryTextSupport.createSearchText(labelKey, tooltipKey);
        this.labelGetter = labelGetter;
        this.colorGetter = colorGetter;
        this.buttonTooltipGetter = buttonTooltipGetter;
        this.buttonWidth = buttonWidth;

        EntryTextSupport.applyTooltip(this, labelKey, tooltipKey);

        this.button = new BoxWidget(0, 0, buttonWidth, 16).withCallback(onClick);
        listeners.add(button);
        refreshButtonTooltip();
    }

    public BoxWidget button() {
        return button;
    }

    public ButtonListEntry setControlWidthScale(float controlWidthScale) {
        this.controlWidthScale = Math.max(0.1f, Math.min(1.0f, controlWidthScale));
        return this;
    }

    public ButtonListEntry setMinimumControlWidth(int minimumControlWidth) {
        this.minimumControlWidth = minimumControlWidth <= 0 ? -1 : minimumControlWidth;
        return this;
    }

    public ButtonListEntry setHalfWidthControl(int minimumControlWidth) {
        return setControlWidthScale(EntryTextSupport.halfWidthControlScale())
            .setMinimumControlWidth(minimumControlWidth);
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

    protected int getMinimumControlWidth() {
        return minimumControlWidth > 0 ? minimumControlWidth : buttonWidth;
    }

    protected int getRenderedButtonWidth(int controlWidth) {
        return controlWidth;
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

        refreshButtonTooltip();
        EntryTextSupport.AlignedControlBounds controlBounds = EntryTextSupport.rightAlignedControlBounds(
            x, width, getLabelWidth(width), controlWidthScale, getMinimumControlWidth());
        int renderedButtonWidth = Math.min(controlBounds.width(), getRenderedButtonWidth(controlBounds.width()));
        int visibleButtonX = controlBounds.rightAlignedContentX(renderedButtonWidth) - TEXT_FIELD_BORDER_NUDGE;
        int widgetX = visibleButtonX + BOX_WIDGET_VISUAL_INSET;
        int widgetWidth = Math.max(1, renderedButtonWidth - BOX_WIDGET_VISUAL_INSET * 2);
        int buttonHeight = Math.max(16, height - 20);
        button.setX(widgetX);
        button.setY(y + 10);
        button.setWidth(widgetWidth);
        button.setHeight(buttonHeight);
        button.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(Minecraft.getInstance().font, labelGetter.get(),
            button.getX() + button.getWidth() / 2,
            button.getY() + (button.getHeight() - 8) / 2,
            colorGetter.getAsInt());
    }

    private void refreshButtonTooltip() {
        button.getToolTip().clear();
        if (buttonTooltipGetter == null) {
            return;
        }
        String tooltipText = buttonTooltipGetter.get();
        if (tooltipText != null && !tooltipText.isBlank()) {
            button.getToolTip().add(Component.literal(tooltipText));
        }
    }
}
