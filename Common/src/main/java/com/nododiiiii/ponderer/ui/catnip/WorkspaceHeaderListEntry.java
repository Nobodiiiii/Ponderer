package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class WorkspaceHeaderListEntry extends ConfigScreenList.Entry implements SearchableListEntry {

    private static final int OUTER_GAP = 8;
    private static final int INNER_GAP = 8;
    private static final int BUTTON_WIDTH = 22;
    private static final int BUTTON_HEIGHT = 16;

    public record HeaderButton(
        BoxWidget widget,
        @Nullable Supplier<String> labelGetter,
        @Nullable Supplier<List<Component>> tooltipGetter,
        BooleanSupplier activeGetter
    ) {
    }

    private final Supplier<String> titleGetter;
    private final Supplier<String> subtitleGetter;
    private final List<HeaderButton> buttons;

    public WorkspaceHeaderListEntry(Supplier<String> titleGetter,
                                    Supplier<String> subtitleGetter,
                                    List<HeaderButton> buttons) {
        this.titleGetter = titleGetter;
        this.subtitleGetter = subtitleGetter;
        this.buttons = new ArrayList<>(buttons);
        for (HeaderButton button : this.buttons) {
            listeners.add(button.widget());
        }
    }

    public static HeaderButton button(String label, Runnable action, @Nullable String tooltip,
                                      BooleanSupplier activeGetter) {
        return new HeaderButton(
            new BoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withPadding(2, 2).withCallback(action),
            () -> label,
            tooltip == null ? null : () -> List.of(Component.literal(tooltip)),
            activeGetter);
    }

    public static HeaderButton iconButton(DelegatedStencilElement icon,
                                          Runnable action,
                                          @Nullable Supplier<List<Component>> tooltipGetter,
                                          BooleanSupplier activeGetter) {
        BoxWidget widget = new BoxWidget(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(action);
        PonderIconStencils.attach(widget, icon);
        return new HeaderButton(widget, null, tooltipGetter, activeGetter);
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
        for (HeaderButton button : buttons) {
            button.widget().active = button.activeGetter().getAsBoolean();
            button.widget().tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        UIRenderHelper.streak(graphics, 0, x - 10, y + height / 2, height - 6, width / 8 * 7, new Color(0xdd_000000));
        UIRenderHelper.streak(graphics, 180, x + (int) (width * 1.35f) + 10, y + height / 2, height - 6,
            width / 8 * 7, new Color(0xdd_000000));

        int buttonsWidth = buttons.size() * BUTTON_WIDTH + Math.max(0, buttons.size() - 1) * INNER_GAP;
        int buttonX = x + width - OUTER_GAP - buttonsWidth;
        int buttonY = y + Math.max(4, (height - BUTTON_HEIGHT) / 2);
        int textRight = buttonX - INNER_GAP;

        var font = Minecraft.getInstance().font;
        String title = font.plainSubstrByWidth(titleGetter.get(), Math.max(20, textRight - x - 10));
        String subtitle = font.plainSubstrByWidth(subtitleGetter.get(), Math.max(20, textRight - x - 10));
        graphics.drawString(font, title, x + 10, y + 8, UIRenderHelper.COLOR_TEXT_STRONG_ACCENT.getFirst().getRGB());
        graphics.drawString(font, subtitle, x + 10, y + 22, UIRenderHelper.COLOR_TEXT.getSecond().getRGB());

        for (HeaderButton button : buttons) {
            renderButton(graphics, button, buttonX, buttonY, mouseX, mouseY, partialTicks);
            buttonX += BUTTON_WIDTH + INNER_GAP;
        }
    }

    private static void renderButton(GuiGraphics graphics, HeaderButton button, int x, int y,
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

        int color = widget.active ? 0xFFFFFF : 0x777777;
        graphics.drawCenteredString(Minecraft.getInstance().font, button.labelGetter().get(),
            x + BUTTON_WIDTH / 2, y + (BUTTON_HEIGHT - 8) / 2, color);
    }

    private static void refreshTooltip(HeaderButton button) {
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
