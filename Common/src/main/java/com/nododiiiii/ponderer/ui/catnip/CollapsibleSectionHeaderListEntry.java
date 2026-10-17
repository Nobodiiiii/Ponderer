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

    private static final int BUTTON_WIDTH = 20;
    private static final int BUTTON_HEIGHT = 16;

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
        int buttonY = y + Math.max(0, (height - buttonHeight) / 2);
        int buttonX = EntryTextSupport.contentRight(x, width) - BUTTON_WIDTH;
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
        int contentLeft = EntryTextSupport.contentLeft(x);
        int titleRight = buttonX - EntryTextSupport.rightControlGap();
        int titleWidth = Math.max(20, titleRight - contentLeft);
        graphics.drawString(font, font.plainSubstrByWidth(title, titleWidth), contentLeft,
            compact ? y + 8 : y + 11, color);
        int lineY = compact ? y + height - 3 : y + height - 10;
        graphics.fill(contentLeft, lineY, titleRight, lineY + 1, 0x40FFFFFF);

        PondererGuiIcons icon = collapsedGetter.getAsBoolean() ? PondererGuiIcons.EXPAND : PondererGuiIcons.COLLAPSE;
        icon.renderCentered(graphics, toggleButton);
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
