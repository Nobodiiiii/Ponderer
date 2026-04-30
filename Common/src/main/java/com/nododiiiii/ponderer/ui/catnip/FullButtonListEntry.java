package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UILayoutConstants;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class FullButtonListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private final BoxWidget button;
    private final Supplier<String> labelGetter;
    @Nullable
    private final Supplier<String> tooltipGetter;
    private final IntSupplier colorGetter;
    private final BooleanSupplier activeGetter;
    private int maxButtonWidth = -1;

    public FullButtonListEntry(String label, @Nullable String tooltipText, Runnable onClick) {
        this(() -> label, tooltipText == null ? null : () -> tooltipText, onClick, () -> 0xFFFFFF, () -> true);
    }

    public FullButtonListEntry(Supplier<String> labelGetter, @Nullable Supplier<String> tooltipGetter,
                               Runnable onClick, IntSupplier colorGetter, BooleanSupplier activeGetter) {
        super("");
        this.labelGetter = labelGetter;
        this.tooltipGetter = tooltipGetter;
        this.colorGetter = colorGetter;
        this.activeGetter = activeGetter;
        this.button = new BoxWidget(0, 0, 200, 18).withCallback(onClick);
        listeners.add(button);
        refreshTooltip();
    }

    public BoxWidget button() {
        return button;
    }

    public FullButtonListEntry setMaxButtonWidth(int maxButtonWidth) {
        this.maxButtonWidth = maxButtonWidth <= 0 ? -1 : maxButtonWidth;
        return this;
    }

    @Override
    public boolean matchesQuery(String query) {
        return buildSearchText().contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        button.active = activeGetter.getAsBoolean();
        button.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        refreshTooltip();

        boolean compact = height <= UILayoutConstants.COMPACT_LIST_ENTRY_H;
        int buttonY = compact ? y + 4 : y + 10;
        int availableWidth = Math.max(40, width - 8);
        int buttonWidth = maxButtonWidth > 0 ? Math.min(availableWidth, maxButtonWidth) : availableWidth;
        int buttonX = x + (width - buttonWidth) / 2;
        int buttonHeight = compact ? 16 : Math.max(16, height - 20);
        button.setX(buttonX);
        button.setY(buttonY);
        button.setWidth(buttonWidth);
        button.setHeight(buttonHeight);
        button.active = activeGetter.getAsBoolean();
        button.render(graphics, mouseX, mouseY, partialTicks);

        int color = button.active ? colorGetter.getAsInt() : 0x777777;
        graphics.drawCenteredString(Minecraft.getInstance().font, labelGetter.get(),
            button.getX() + button.getWidth() / 2,
            button.getY() + (button.getHeight() - 8) / 2,
            color);
    }

    private void refreshTooltip() {
        button.getToolTip().clear();
        String tooltip = tooltipGetter != null ? tooltipGetter.get() : null;
        if (tooltip != null && !tooltip.isBlank()) {
            button.getToolTip().add(Component.literal(tooltip));
        }
    }

    private String buildSearchText() {
        StringBuilder builder = new StringBuilder(labelGetter.get());
        if (tooltipGetter != null) {
            String tooltip = tooltipGetter.get();
            if (tooltip != null && !tooltip.isBlank()) {
                builder.append(' ').append(tooltip);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
