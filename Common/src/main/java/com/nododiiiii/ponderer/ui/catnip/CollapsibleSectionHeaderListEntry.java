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
import java.util.function.Supplier;

public class CollapsibleSectionHeaderListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private static final int BUTTON_WIDTH = 12;
    private static final int BUTTON_HEIGHT = 8;
    private static final int OUTER_GAP = 4;

    private final Supplier<String> titleGetter;
    private final BooleanSupplier collapsedGetter;
    @Nullable
    private final Supplier<String> collapsedTooltipGetter;
    @Nullable
    private final Supplier<String> expandedTooltipGetter;
    private final BoxWidget toggleButton;

    public CollapsibleSectionHeaderListEntry(Supplier<String> titleGetter,
                                             BooleanSupplier collapsedGetter,
                                             Runnable onToggle,
                                             @Nullable Supplier<String> collapsedTooltipGetter,
                                             @Nullable Supplier<String> expandedTooltipGetter) {
        super("");
        this.titleGetter = titleGetter;
        this.collapsedGetter = collapsedGetter;
        this.collapsedTooltipGetter = collapsedTooltipGetter;
        this.expandedTooltipGetter = expandedTooltipGetter;
        this.toggleButton = new FormBoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withPadding(2, 2).withCallback(onToggle);
        listeners.add(toggleButton);
    }

    @Override
    public boolean matchesQuery(String query) {
        return titleGetter.get().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        toggleButton.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        refreshTooltip();

        boolean compact = height <= UILayoutConstants.COMPACT_LIST_ENTRY_H;
        int buttonHeight = BUTTON_HEIGHT;
        int buttonY = y + Math.max(4, (height - buttonHeight) / 2);
        int buttonX = x + width - OUTER_GAP - BUTTON_WIDTH;
        toggleButton.setX(buttonX);
        toggleButton.setY(buttonY);
        toggleButton.setWidth(BUTTON_WIDTH);
        toggleButton.setHeight(buttonHeight);
        toggleButton.active = true;
        toggleButton.updateGradientFromState();
        toggleButton.render(graphics, mouseX, mouseY, partialTicks);

        String title = titleGetter.get();
        var font = Minecraft.getInstance().font;
        int color = annotations.containsKey("highlight") ? 0xFFF3D46B : 0xFFCCCC77;
        int titleWidth = Math.max(20, buttonX - x - OUTER_GAP * 3);
        graphics.drawString(font, font.plainSubstrByWidth(title, titleWidth), x + OUTER_GAP,
            compact ? y + 8 : y + 11, color);
        int lineY = compact ? y + height - 3 : y + height - 10;
        graphics.fill(x + OUTER_GAP, lineY, buttonX - OUTER_GAP, lineY + 1, 0x40FFFFFF);

        String symbol = collapsedGetter.getAsBoolean() ? "+" : "-";
        graphics.drawCenteredString(font, symbol, toggleButton.getX() + toggleButton.getWidth() / 2,
            toggleButton.getY() + (toggleButton.getHeight() - 8) / 2, 0xFFFFFF);
    }

    private void refreshTooltip() {
        toggleButton.getToolTip().clear();
        Supplier<String> tooltipGetter = collapsedGetter.getAsBoolean()
            ? collapsedTooltipGetter
            : expandedTooltipGetter;
        String tooltip = tooltipGetter == null ? null : tooltipGetter.get();
        if (tooltip != null && !tooltip.isBlank()) {
            toggleButton.getToolTip().add(Component.literal(tooltip));
        }
    }
}
