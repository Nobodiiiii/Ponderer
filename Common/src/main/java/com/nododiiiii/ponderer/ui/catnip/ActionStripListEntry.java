package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class ActionStripListEntry extends ConfigScreenList.Entry implements SearchableListEntry {

    private static final int INNER_GAP = 0;
    private static final int ICON_BUTTON_SIZE = 26;

    public record ButtonModel(
        BoxWidget widget,
        @Nullable Supplier<String> labelGetter,
        @Nullable Supplier<List<Component>> tooltipGetter,
        IntSupplier colorGetter,
        BooleanSupplier activeGetter,
        int fixedWidth
    ) {
    }

    private final List<ButtonModel> buttons;

    public ActionStripListEntry(List<ButtonModel> buttons) {
        this.buttons = new ArrayList<>(buttons);
        for (ButtonModel button : this.buttons) {
            listeners.add(button.widget());
        }
    }

    public static ButtonModel button(String label, @Nullable String tooltip, Runnable action) {
        return button(
            () -> label,
            tooltip == null ? null : () -> List.of(Component.literal(tooltip)),
            action,
            () -> 0xFFFFFF,
            () -> true);
    }

    public static ButtonModel button(Supplier<String> labelGetter,
                                     @Nullable Supplier<List<Component>> tooltipGetter,
                                     Runnable action,
                                     IntSupplier colorGetter,
                                     BooleanSupplier activeGetter) {
        return new ButtonModel(new FormBoxWidget(0, 0, 68, 26).withPadding(2, 2).withCallback(action),
            labelGetter,
            tooltipGetter,
            colorGetter,
            activeGetter,
            -1);
    }

    public static ButtonModel iconButton(PonderGuiTextures texture,
                                         Runnable action,
                                         @Nullable Supplier<List<Component>> tooltipGetter,
                                         BooleanSupplier activeGetter) {
        BoxWidget widget = new FormBoxWidget(0, 0, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE).withPadding(2, 2).withCallback(action);
        DelegatedStencilElement icon = PonderIconStencils.centered(texture);
        PonderIconStencils.attach(widget, icon);
        return new ButtonModel(widget, null, tooltipGetter, () -> 0xFFFFFF, activeGetter, ICON_BUTTON_SIZE);
    }

    public static ButtonModel iconButton(PondererGuiIcons icon,
                                         Runnable action,
                                         @Nullable Supplier<List<Component>> tooltipGetter,
                                         BooleanSupplier activeGetter) {
        BoxWidget widget = new FormBoxWidget(0, 0, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE).withPadding(2, 2).withCallback(action);
        icon.attach(widget);
        return new ButtonModel(widget, null, tooltipGetter, () -> 0xFFFFFF, activeGetter, ICON_BUTTON_SIZE);
    }

    public static ButtonModel failIconButton(PonderGuiTextures texture,
                                             Runnable action,
                                             @Nullable Supplier<List<Component>> tooltipGetter,
                                             BooleanSupplier activeGetter) {
        BoxWidget widget = new FormBoxWidget(0, 0, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE).withPadding(2, 2).withCallback(action);
        DelegatedStencilElement icon = PonderIconStencils.centered(texture);
        PonderIconStencils.attachFail(widget, icon);
        return new ButtonModel(widget, null, tooltipGetter, () -> 0xFFFFFF, activeGetter, ICON_BUTTON_SIZE);
    }

    public static ButtonModel rowIconButton(DelegatedStencilElement icon,
                                            Runnable action,
                                            @Nullable Supplier<List<Component>> tooltipGetter,
                                            BooleanSupplier activeGetter,
                                            boolean danger) {
        int size = EntryTextSupport.squareSize();
        BoxWidget widget = new FormBoxWidget(0, 0, size, size).withCallback(action);
        if (danger) {
            PonderIconStencils.attachFail(widget, icon);
        } else {
            PonderIconStencils.attach(widget, icon);
        }
        return new ButtonModel(widget, null, tooltipGetter, () -> 0xFFFFFF, activeGetter, size);
    }

    @Override
    public boolean matchesQuery(String query) {
        return false;
    }

    @Override
    public void highlightEntry() {
    }

    @Override
    public void tick() {
        super.tick();
        tickButtons(buttons);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        renderButtonCluster(graphics, buttons, EntryTextSupport.contentLeft(x), EntryTextSupport.contentWidth(width), y, height,
            mouseX, mouseY, partialTicks);
    }

    static void tickButtons(List<ButtonModel> buttons) {
        for (ButtonModel button : buttons) {
            button.widget().active = button.activeGetter().getAsBoolean();
            button.widget().tick();
        }
    }

    static void renderButtonCluster(GuiGraphics graphics, List<ButtonModel> buttons, int x, int width, int y, int height,
                                    int mouseX, int mouseY, float partialTicks) {
        if (buttons.isEmpty()) {
            return;
        }

        int count = buttons.size();
        int totalGap = INNER_GAP * Math.max(0, count - 1);
        int availableWidth = Math.max(count == 1 ? 48 : 1, width - totalGap);
        int fixedWidth = 0;
        int stretchCount = 0;
        for (ButtonModel button : buttons) {
            if (button.fixedWidth() > 0) {
                fixedWidth += button.fixedWidth();
            } else {
                stretchCount++;
            }
        }
        int stretchWidth = stretchCount <= 0
            ? Math.max(36, availableWidth / count)
            : Math.max(36, (availableWidth - fixedWidth) / stretchCount);
        int stretchExtra = stretchCount <= 0
            ? 0
            : Math.max(0, availableWidth - fixedWidth - stretchWidth * stretchCount);
        int buttonHeight = Math.max(24, Math.min(26, height - 4));
        int buttonY = y + Math.max(0, (height - buttonHeight) / 2);
        int usedWidth = fixedWidth + stretchWidth * stretchCount + stretchExtra + totalGap;
        int currentX = stretchCount > 0 ? x : x + width - usedWidth;

        boolean extraAssigned = false;
        for (int i = 0; i < count; i++) {
            ButtonModel button = buttons.get(i);
            int currentWidth = button.fixedWidth() > 0 ? button.fixedWidth() : stretchWidth;
            if (button.fixedWidth() <= 0 && !extraAssigned) {
                currentWidth += stretchExtra;
                extraAssigned = true;
            }
            renderButton(graphics, button, currentX, buttonY, currentWidth, buttonHeight, mouseX, mouseY, partialTicks);
            currentX += currentWidth + INNER_GAP;
        }
    }

    private static void renderButton(GuiGraphics graphics, ButtonModel button, int x, int y, int width, int height,
                                     int mouseX, int mouseY, float partialTicks) {
        refreshTooltip(button);
        BoxWidget widget = button.widget();
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
        widget.setHeight(height);
        widget.active = button.activeGetter().getAsBoolean();
        widget.updateGradientFromState();
        widget.render(graphics, mouseX, mouseY, partialTicks);

        var font = Minecraft.getInstance().font;
        int color = widget.active ? button.colorGetter().getAsInt() : 0x777777;
        if (button.labelGetter() != null) {
            String label = font.plainSubstrByWidth(button.labelGetter().get(), Math.max(8, width - 16));
            graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2, color);
        }
    }

    static void renderMultiControlCluster(GuiGraphics graphics, List<ButtonModel> buttons, int x, int width, int y, int height,
                                    int mouseX, int mouseY, float partialTicks) {
        if (buttons.isEmpty() || (buttons.size() == 1
            && buttons.getFirst().fixedWidth() != EntryTextSupport.squareSize())) {
            renderButtonCluster(graphics, buttons, x, width, y, height, mouseX, mouseY, partialTicks);
            return;
        }

        int count = buttons.size();
        int totalGap = EntryTextSupport.multiControlGap() * Math.max(0, count - 1);
        int availableWidth = Math.max(1, width - totalGap);
        int fixedWidth = 0;
        int stretchCount = 0;
        for (ButtonModel button : buttons) {
            if (isSquare(button)) {
                fixedWidth += EntryTextSupport.squareSize();
            } else {
                stretchCount++;
            }
        }
        int stretchWidth = stretchCount <= 0
            ? 0
            : Math.max(1, (availableWidth - fixedWidth) / stretchCount);
        int stretchExtra = stretchCount <= 0
            ? 0
            : Math.max(0, availableWidth - fixedWidth - stretchWidth * stretchCount);
        int usedWidth = fixedWidth + stretchWidth * stretchCount + stretchExtra + totalGap;
        int currentX = stretchCount > 0 ? x : x + width - usedWidth;

        boolean extraAssigned = false;
        for (int i = 0; i < count; i++) {
            ButtonModel button = buttons.get(i);
            int currentWidth = isSquare(button) ? EntryTextSupport.squareSize() : stretchWidth;
            if (!isSquare(button) && !extraAssigned) {
                currentWidth += stretchExtra;
                extraAssigned = true;
            }
            renderMultiControlButton(graphics, button, currentX, y, currentWidth, height, mouseX, mouseY, partialTicks);
            currentX += currentWidth + EntryTextSupport.multiControlGap();
        }
    }

    private static boolean isSquare(ButtonModel button) {
        return button.fixedWidth() > 0 || EntryTextSupport.isSquareButton(
            button.labelGetter() == null ? null : button.labelGetter().get());
    }

    private static void renderMultiControlButton(GuiGraphics graphics, ButtonModel button, int x, int y, int width, int height,
                                     int mouseX, int mouseY, float partialTicks) {
        refreshTooltip(button);
        BoxWidget widget = button.widget();
        EntryTextSupport.setRowButtonBounds(widget, x, width, y, height);
        widget.active = button.activeGetter().getAsBoolean();
        widget.updateGradientFromState();
        widget.render(graphics, mouseX, mouseY, partialTicks);

        var font = Minecraft.getInstance().font;
        int color = widget.active ? button.colorGetter().getAsInt() : 0x777777;
        if (button.labelGetter() != null) {
            EntryTextSupport.drawButtonLabel(graphics, widget, button.labelGetter().get(), color);
        }
    }

    private static void refreshTooltip(ButtonModel button) {
        button.widget().getToolTip().clear();
        if (button.tooltipGetter() == null) {
            return;
        }
        List<Component> tooltip = button.tooltipGetter().get();
        if (tooltip != null && !tooltip.isEmpty()) {
            button.widget().getToolTip().addAll(tooltip);
        }
    }

    @Override
    public Component getNarration() {
        return CommonComponents.EMPTY;
    }
}
