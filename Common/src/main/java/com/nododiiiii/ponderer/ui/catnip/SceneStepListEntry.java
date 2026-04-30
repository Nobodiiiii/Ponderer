package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class SceneStepListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private static final int OUTER_GAP = 8;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_WIDTH = 18;
    private static final int BUTTON_HEIGHT = 16;

    public record ActionButton(
        BoxWidget widget,
        @Nullable Supplier<String> labelGetter,
        @Nullable Supplier<List<Component>> tooltipGetter,
        @Nullable Runnable secondaryClick,
        IntSupplier colorGetter,
        BooleanSupplier activeGetter
    ) {
    }

    private final Supplier<String> labelGetter;
    @Nullable
    private final Runnable mainClick;
    private final List<ActionButton> buttons;
    private int lastX;
    private int lastY;
    private int lastWidth;
    private int lastHeight;
    private int lastLabelWidth;

    public SceneStepListEntry(Supplier<String> labelGetter,
                              @Nullable Runnable mainClick,
                              List<ActionButton> buttons) {
        super("");
        this.labelGetter = labelGetter;
        this.mainClick = mainClick;
        this.buttons = new ArrayList<>(buttons);
        for (ActionButton button : this.buttons) {
            listeners.add(button.widget());
        }
        refreshLabelTooltip();
    }

    public static ActionButton button(String label,
                                      @Nullable Supplier<List<Component>> tooltipGetter,
                                      Runnable primaryClick,
                                      @Nullable Runnable secondaryClick,
                                      IntSupplier colorGetter,
                                      BooleanSupplier activeGetter) {
        return button(() -> label, tooltipGetter, primaryClick, secondaryClick, colorGetter, activeGetter);
    }

    public static ActionButton button(Supplier<String> labelGetter,
                                      @Nullable Supplier<List<Component>> tooltipGetter,
                                      Runnable primaryClick,
                                      @Nullable Runnable secondaryClick,
                                      IntSupplier colorGetter,
                                      BooleanSupplier activeGetter) {
        return new ActionButton(
            new BoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withPadding(2, 2).withCallback(primaryClick),
            labelGetter,
            tooltipGetter,
            secondaryClick,
            colorGetter,
            activeGetter);
    }

    public static ActionButton iconButton(DelegatedStencilElement icon,
                                          @Nullable Supplier<List<Component>> tooltipGetter,
                                          Runnable primaryClick,
                                          @Nullable Runnable secondaryClick,
                                          BooleanSupplier activeGetter) {
        BoxWidget widget = new BoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(primaryClick);
        PonderIconStencils.attach(widget, icon);
        return createIconButton(widget, tooltipGetter, secondaryClick, activeGetter);
    }

    public static ActionButton dangerIconButton(DelegatedStencilElement icon,
                                                @Nullable Supplier<List<Component>> tooltipGetter,
                                                Runnable primaryClick,
                                                @Nullable Runnable secondaryClick,
                                                BooleanSupplier activeGetter) {
        BoxWidget widget = new BoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(primaryClick);
        PonderIconStencils.attachFail(widget, icon);
        return createIconButton(widget, tooltipGetter, secondaryClick, activeGetter);
    }

    private static ActionButton createIconButton(BoxWidget widget,
                                                 @Nullable Supplier<List<Component>> tooltipGetter,
                                                 @Nullable Runnable secondaryClick,
                                                 BooleanSupplier activeGetter) {
        return new ActionButton(
            widget,
            null,
            tooltipGetter,
            secondaryClick,
            () -> 0xFFFFFF,
            activeGetter);
    }

    @Override
    public boolean matchesQuery(String query) {
        return labelGetter.get().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void highlightEntry() {
        annotations.put("highlight", ":)");
    }

    @Override
    public void tick() {
        super.tick();
        for (ActionButton button : buttons) {
            button.widget().active = button.activeGetter().getAsBoolean();
            button.widget().tick();
        }
    }

    @Override
    protected int getLabelWidth(int totalWidth) {
        return Math.max(100, totalWidth - controlAreaWidth() - 12);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        label.withText(labelGetter.get());
        refreshLabelTooltip();
        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;
        lastLabelWidth = getLabelWidth(width);

        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        int clusterWidth = controlAreaWidth() - OUTER_GAP - 4;
        int buttonX = x + width - OUTER_GAP - clusterWidth;
        int buttonY = y + Math.max(4, (height - BUTTON_HEIGHT) / 2);
        for (ActionButton button : buttons) {
            renderButton(graphics, button, buttonX, buttonY, mouseX, mouseY, partialTicks);
            buttonX += BUTTON_WIDTH + BUTTON_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            ActionButton secondaryTarget = findButtonAt(mouseX, mouseY);
            if (secondaryTarget != null
                && secondaryTarget.activeGetter().getAsBoolean()
                && secondaryTarget.secondaryClick() != null) {
                secondaryTarget.secondaryClick().run();
                return true;
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && mainClick != null && isInsideMainArea(mouseX, mouseY)) {
            mainClick.run();
            return true;
        }

        return false;
    }

    private int controlAreaWidth() {
        if (buttons.isEmpty()) {
            return 0;
        }
        return buttons.size() * BUTTON_WIDTH + Math.max(0, buttons.size() - 1) * BUTTON_GAP + OUTER_GAP + 4;
    }

    private boolean isInsideMainArea(double mouseX, double mouseY) {
        return mouseX >= lastX
            && mouseX < lastX + lastLabelWidth
            && mouseY >= lastY + 5
            && mouseY < lastY + lastHeight - 5;
    }

    @Nullable
    private ActionButton findButtonAt(double mouseX, double mouseY) {
        int clusterWidth = controlAreaWidth() - OUTER_GAP - 4;
        int buttonX = lastX + lastWidth - OUTER_GAP - clusterWidth;
        int buttonY = lastY + Math.max(4, (lastHeight - BUTTON_HEIGHT) / 2);
        for (ActionButton actionButton : buttons) {
            if (mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
                return actionButton;
            }
            buttonX += BUTTON_WIDTH + BUTTON_GAP;
        }
        return null;
    }

    private void refreshLabelTooltip() {
        labelTooltip.clear();
        labelTooltip.add(Component.literal(labelGetter.get()));
    }

    private static void renderButton(GuiGraphics graphics, ActionButton button, int x, int y,
                                     int mouseX, int mouseY, float partialTicks) {
        refreshTooltip(button);
        BoxWidget widget = button.widget();
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(BUTTON_WIDTH);
        widget.setHeight(BUTTON_HEIGHT);
        widget.active = button.activeGetter().getAsBoolean();
        widget.updateGradientFromState();
        widget.render(graphics, mouseX, mouseY, partialTicks);

        if (button.labelGetter() == null) {
            return;
        }

        var font = Minecraft.getInstance().font;
        int color = widget.active ? button.colorGetter().getAsInt() : 0x777777;
        String label = font.plainSubstrByWidth(button.labelGetter().get(), BUTTON_WIDTH - 4);
        graphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2, y + (BUTTON_HEIGHT - 8) / 2, color);
    }

    private static void refreshTooltip(ActionButton button) {
        button.widget().getToolTip().clear();
        if (button.tooltipGetter() == null) {
            return;
        }
        List<Component> tooltip = button.tooltipGetter().get();
        if (tooltip != null && !tooltip.isEmpty()) {
            button.widget().getToolTip().addAll(tooltip);
        }
    }
}
