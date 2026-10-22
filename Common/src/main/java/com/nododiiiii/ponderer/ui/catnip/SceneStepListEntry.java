package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.createmod.catnip.lang.FontHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class SceneStepListEntry extends ConfigScreenList.LabeledEntry implements SearchableListEntry {

    private static final int BUTTON_GAP = EntryTextSupport.multiControlGap();
    private static final int BUTTON_WIDTH = 26;
    private static final int BUTTON_HEIGHT = 24;
    private static final int MULTI_BUTTON_MIN_WIDTH = 18;
    private static final Couple<Color> SELECTED_COLORS = Couple.create(
        new Color(SectionHeaderRenderer.TEXT_COLOR),
        new Color(SectionHeaderRenderer.TEXT_COLOR).scaleAlpha(0xDD / 255f)).map(Color::setImmutable);

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
    private final Supplier<String> previewGetter;
    @Nullable
    private final Runnable mainClick;
    private final List<ActionButton> buttons;
    private int lastX;
    private int lastY;
    private int lastHeight;
    private int lastLabelWidth;

    public SceneStepListEntry(Supplier<String> labelGetter,
                              Supplier<String> previewGetter,
                              @Nullable Runnable mainClick,
                              List<ActionButton> buttons) {
        super("");
        this.labelGetter = labelGetter;
        this.previewGetter = previewGetter;
        this.mainClick = mainClick;
        this.buttons = new ArrayList<>(buttons);
        for (ActionButton button : this.buttons) {
            listeners.add(button.widget());
        }
        refreshLabelTooltip(labelGetter.get(), previewGetter.get());
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
            new FormBoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withPadding(2, 2).withCallback(primaryClick),
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
        BoxWidget widget = new FormBoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(primaryClick);
        PonderIconStencils.attach(widget, icon);
        return createIconButton(widget, tooltipGetter, secondaryClick, activeGetter);
    }

    public static ActionButton iconButton(PondererGuiIcons icon,
                                          @Nullable Supplier<List<Component>> tooltipGetter,
                                          Runnable primaryClick,
                                          @Nullable Runnable secondaryClick,
                                          BooleanSupplier activeGetter) {
        BoxWidget widget = new FormBoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(primaryClick);
        icon.attach(widget);
        return createIconButton(widget, tooltipGetter, secondaryClick, activeGetter);
    }

    public static ActionButton iconButton(PondererGuiIcons icon,
                                          @Nullable Supplier<List<Component>> tooltipGetter,
                                          Runnable primaryClick,
                                          @Nullable Runnable secondaryClick,
                                          BooleanSupplier activeGetter,
                                          boolean selected) {
        ActionButton button = iconButton(icon, tooltipGetter, primaryClick, secondaryClick, activeGetter);
        if (selected) {
            button.widget().withCustomTheme(SELECTED_COLORS, SELECTED_COLORS, SELECTED_COLORS, null);
        }
        return button;
    }

    public static ActionButton dangerIconButton(DelegatedStencilElement icon,
                                                @Nullable Supplier<List<Component>> tooltipGetter,
                                                Runnable primaryClick,
                                                @Nullable Runnable secondaryClick,
                                                BooleanSupplier activeGetter) {
        BoxWidget widget = new FormBoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(primaryClick);
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
        return (labelGetter.get() + " " + previewGetter.get()).toLowerCase(Locale.ROOT).contains(query);
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
        if (buttons.size() <= 1) {
            return Math.max(100, EntryTextSupport.contentWidth(totalWidth) - controlAreaWidth()
                - EntryTextSupport.rightControlGap());
        }
        return Math.max(100, EntryTextSupport.contentWidth(totalWidth) - controlAreaWidth()
            - BUTTON_GAP);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        String title = labelGetter.get();
        String preview = previewGetter.get();
        refreshLabelTooltip(title, preview);
        lastX = x;
        lastY = y;
        lastHeight = height;
        lastLabelWidth = getLabelWidth(width);

        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(0, lastLabelWidth - 20);
        int titleWidth = preview.isEmpty() ? textWidth
            : Math.max(textWidth * 3 / 5, textWidth - font.width(" " + preview));
        String visibleTitle = ellipsize(font, title, titleWidth);
        label.withText(visibleTitle);

        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);

        if (!preview.isEmpty()) {
            int previewX = x + 10 + font.width(visibleTitle);
            String visiblePreview = ellipsize(font, " " + preview, textWidth - font.width(visibleTitle));
            graphics.drawString(font, visiblePreview, previewX, y + height / 2 - 4, 0xFFB0B0B0, false);
        }

        int clusterWidth = controlAreaWidth();
        boolean multiple = buttons.size() > 1;
        int buttonX = EntryTextSupport.contentRight(x, width) - clusterWidth;
        for (ActionButton button : buttons) {
            renderButton(graphics, button, buttonX, y, height, multiple, mouseX, mouseY, partialTicks);
            buttonX += (multiple ? buttonWidth(button) : BUTTON_WIDTH) + BUTTON_GAP;
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
        if (buttons.size() == 1) {
            return BUTTON_WIDTH;
        }
        return buttons.stream().mapToInt(SceneStepListEntry::buttonWidth).sum()
            + Math.max(0, buttons.size() - 1) * BUTTON_GAP;
    }

    private static int buttonWidth(ActionButton button) {
        return EntryTextSupport.buttonWidth(button.labelGetter() == null ? null : button.labelGetter().get(), MULTI_BUTTON_MIN_WIDTH);
    }

    private boolean isInsideMainArea(double mouseX, double mouseY) {
        return mouseX >= lastX
            && mouseX < lastX + lastLabelWidth
            && mouseY >= lastY + 5
            && mouseY < lastY + lastHeight - 5;
    }

    @Nullable
    private ActionButton findButtonAt(double mouseX, double mouseY) {
        for (ActionButton actionButton : buttons) {
            if (actionButton.widget().isMouseOver(mouseX, mouseY)) {
                return actionButton;
            }
        }
        return null;
    }

    private void refreshLabelTooltip(String title, String preview) {
        labelTooltip.clear();
        labelTooltip.add(Component.literal(title));
        if (!preview.isEmpty()) {
            labelTooltip.addAll(FontHelper.cutTextComponent(Component.literal(preview), FontHelper.Palette.ALL_GRAY));
        }
    }

    private static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String ellipsis = "…";
        if (width < font.width(ellipsis)) return "";
        return font.plainSubstrByWidth(text, width - font.width(ellipsis)) + ellipsis;
    }

    private static void renderButton(GuiGraphics graphics, ActionButton button, int x, int y, int rowHeight, boolean multiple,
                                     int mouseX, int mouseY, float partialTicks) {
        refreshTooltip(button);
        BoxWidget widget = button.widget();
        if (multiple) {
            EntryTextSupport.setRowButtonBounds(widget, x, buttonWidth(button), y, rowHeight);
        } else {
            widget.setX(x);
            widget.setY(y + Math.max(0, (rowHeight - BUTTON_HEIGHT) / 2));
            widget.setWidth(BUTTON_WIDTH);
            widget.setHeight(BUTTON_HEIGHT);
        }
        widget.active = button.activeGetter().getAsBoolean();
        widget.updateGradientFromState();
        widget.render(graphics, mouseX, mouseY, partialTicks);

        if (button.labelGetter() == null) {
            return;
        }

        var font = Minecraft.getInstance().font;
        int color = widget.active ? button.colorGetter().getAsInt() : 0x777777;
        EntryTextSupport.drawButtonLabel(graphics, widget, button.labelGetter().get(), color);
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
