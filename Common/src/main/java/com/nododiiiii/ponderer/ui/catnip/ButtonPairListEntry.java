package com.nododiiiii.ponderer.ui.catnip;

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

public class ButtonPairListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private static final int GAP = 8;

    private record ButtonModel(BoxWidget widget, Supplier<String> labelGetter,
                               @Nullable Supplier<String> tooltipGetter, IntSupplier colorGetter,
                               BooleanSupplier activeGetter) {
    }

    private final ButtonModel left;
    @Nullable
    private final ButtonModel right;

    public ButtonPairListEntry(String leftLabel, @Nullable String leftTooltip, Runnable leftAction,
                               @Nullable String rightLabel, @Nullable String rightTooltip, @Nullable Runnable rightAction) {
        this(
            createButtonModel(leftLabel, leftTooltip, leftAction, () -> 0xFFFFFF, () -> true),
            rightLabel == null || rightAction == null ? null
                : createButtonModel(rightLabel, rightTooltip, rightAction, () -> 0xFFFFFF, () -> true));
    }

    public ButtonPairListEntry(ButtonModel left, @Nullable ButtonModel right) {
        super("");
        this.left = left;
        this.right = right;
        listeners.add(left.widget());
        if (right != null) {
            listeners.add(right.widget());
        }
        refreshTooltip(left);
        if (right != null) {
            refreshTooltip(right);
        }
    }

    @Override
    public boolean matchesQuery(String query) {
        StringBuilder search = new StringBuilder(searchText(left));
        if (right != null) {
            search.append(' ').append(searchText(right));
        }
        return search.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        tickButton(left);
        if (right != null) {
            tickButton(right);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        int buttonHeight = Math.max(16, height - 16);
        int buttonY = y + Math.max(4, (height - buttonHeight) / 2);
        int buttonWidth = right == null ? width - 8 : (width - GAP - 8) / 2;

        renderButton(graphics, left, x + 4, buttonY, buttonWidth, buttonHeight, mouseX, mouseY, partialTicks);
        if (right != null) {
            renderButton(graphics, right, x + 4 + buttonWidth + GAP, buttonY, buttonWidth, buttonHeight,
                mouseX, mouseY, partialTicks);
        }
    }

    private static ButtonModel createButtonModel(String label, @Nullable String tooltip, Runnable action,
                                                 IntSupplier colorGetter, BooleanSupplier activeGetter) {
        return new ButtonModel(new BoxWidget(0, 0, 120, 18).withCallback(action),
            () -> label,
            tooltip == null ? null : () -> tooltip,
            colorGetter,
            activeGetter);
    }

    private static void tickButton(ButtonModel model) {
        model.widget().active = model.activeGetter().getAsBoolean();
        model.widget().tick();
    }

    private static void renderButton(GuiGraphics graphics, ButtonModel model, int x, int y, int width, int height,
                                     int mouseX, int mouseY, float partialTicks) {
        refreshTooltip(model);
        BoxWidget widget = model.widget();
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
        widget.setHeight(height);
        widget.active = model.activeGetter().getAsBoolean();
        widget.render(graphics, mouseX, mouseY, partialTicks);

        int color = widget.active ? model.colorGetter().getAsInt() : 0x777777;
        graphics.drawCenteredString(Minecraft.getInstance().font, model.labelGetter().get(),
            widget.getX() + widget.getWidth() / 2,
            widget.getY() + (widget.getHeight() - 8) / 2,
            color);
    }

    private static void refreshTooltip(ButtonModel model) {
        model.widget().getToolTip().clear();
        String tooltip = model.tooltipGetter() != null ? model.tooltipGetter().get() : null;
        if (tooltip != null && !tooltip.isBlank()) {
            model.widget().getToolTip().add(Component.literal(tooltip));
        }
    }

    private static String searchText(ButtonModel model) {
        StringBuilder builder = new StringBuilder(model.labelGetter().get());
        if (model.tooltipGetter() != null) {
            String tooltip = model.tooltipGetter().get();
            if (tooltip != null && !tooltip.isBlank()) {
                builder.append(' ').append(tooltip);
            }
        }
        return builder.toString();
    }
}
