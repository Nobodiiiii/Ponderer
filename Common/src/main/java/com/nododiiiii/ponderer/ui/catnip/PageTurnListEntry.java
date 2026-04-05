package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class PageTurnListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private static final int OUTER_GAP = 4;
    private static final int INNER_GAP = 8;
    private static final int BUTTON_WIDTH = 22;

    private final BoxWidget prevButton;
    private final BoxWidget nextButton;
    private final Supplier<String> centerTextGetter;
    @Nullable
    private final Supplier<String> prevTooltipGetter;
    @Nullable
    private final Supplier<String> nextTooltipGetter;
    private final BooleanSupplier prevActiveGetter;
    private final BooleanSupplier nextActiveGetter;

    public PageTurnListEntry(Supplier<String> centerTextGetter,
                             Runnable onPrev, Runnable onNext,
                             @Nullable Supplier<String> prevTooltipGetter,
                             @Nullable Supplier<String> nextTooltipGetter,
                             BooleanSupplier prevActiveGetter,
                             BooleanSupplier nextActiveGetter) {
        super("");
        this.centerTextGetter = centerTextGetter;
        this.prevTooltipGetter = prevTooltipGetter;
        this.nextTooltipGetter = nextTooltipGetter;
        this.prevActiveGetter = prevActiveGetter;
        this.nextActiveGetter = nextActiveGetter;
        this.prevButton = new BoxWidget(0, 0, BUTTON_WIDTH, 18).withCallback(onPrev);
        this.nextButton = new BoxWidget(0, 0, BUTTON_WIDTH, 18).withCallback(onNext);
        listeners.add(prevButton);
        listeners.add(nextButton);
        refreshTooltip(prevButton, prevTooltipGetter);
        refreshTooltip(nextButton, nextTooltipGetter);
    }

    @Override
    public boolean matchesQuery(String query) {
        StringBuilder builder = new StringBuilder(centerTextGetter.get());
        if (prevTooltipGetter != null) {
            builder.append(' ').append(prevTooltipGetter.get());
        }
        if (nextTooltipGetter != null) {
            builder.append(' ').append(nextTooltipGetter.get());
        }
        return builder.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        prevButton.active = prevActiveGetter.getAsBoolean();
        nextButton.active = nextActiveGetter.getAsBoolean();
        prevButton.tick();
        nextButton.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        refreshTooltip(prevButton, prevTooltipGetter);
        refreshTooltip(nextButton, nextTooltipGetter);

        int buttonHeight = Math.max(16, height - 20);
        int buttonY = y + 10;

        prevButton.setX(x + OUTER_GAP);
        prevButton.setY(buttonY);
        prevButton.setWidth(BUTTON_WIDTH);
        prevButton.setHeight(buttonHeight);
        prevButton.active = prevActiveGetter.getAsBoolean();
        prevButton.render(graphics, mouseX, mouseY, partialTicks);

        nextButton.setX(x + width - OUTER_GAP - BUTTON_WIDTH);
        nextButton.setY(buttonY);
        nextButton.setWidth(BUTTON_WIDTH);
        nextButton.setHeight(buttonHeight);
        nextButton.active = nextActiveGetter.getAsBoolean();
        nextButton.render(graphics, mouseX, mouseY, partialTicks);

        var font = Minecraft.getInstance().font;
        int centerLeft = prevButton.getX() + prevButton.getWidth() + INNER_GAP;
        int centerRight = nextButton.getX() - INNER_GAP;
        int centerX = (centerLeft + centerRight) / 2;
        int centerY = buttonY + (buttonHeight - 8) / 2;
        int textColor = annotations.containsKey("highlight") ? 0xFFF3D46B : 0xFFFFFF;

        graphics.drawCenteredString(font, "<",
            prevButton.getX() + prevButton.getWidth() / 2,
            centerY,
            prevButton.active ? 0xFFFFFF : 0x777777);
        graphics.drawCenteredString(font, ">",
            nextButton.getX() + nextButton.getWidth() / 2,
            centerY,
            nextButton.active ? 0xFFFFFF : 0x777777);
        graphics.drawCenteredString(font,
            font.plainSubstrByWidth(centerTextGetter.get(), Math.max(20, centerRight - centerLeft)),
            centerX,
            centerY,
            textColor);
    }

    private static void refreshTooltip(BoxWidget button, @Nullable Supplier<String> tooltipGetter) {
        button.getToolTip().clear();
        if (tooltipGetter == null) {
            return;
        }
        String tooltip = tooltipGetter.get();
        if (tooltip != null && !tooltip.isBlank()) {
            button.getToolTip().add(Component.literal(tooltip));
        }
    }
}
