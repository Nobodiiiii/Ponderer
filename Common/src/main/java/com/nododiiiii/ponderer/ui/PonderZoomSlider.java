package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** A screen-local multiplier, independent of scripted scene zoom. */
public class PonderZoomSlider extends AbstractSliderButton {
    public static final float MIN_ZOOM = 0.2f;
    public static final float MAX_ZOOM = 5f;
    private static final int PREVIEW_HINT_HEIGHT = 18;

    private final PonderUiZoomAccess zoom;

    public PonderZoomSlider(PonderUI ponder) {
        super(left(ponder.width), ponder.width < 480 ? 53 : 25, sliderWidth(ponder.width), 20,
            Component.empty(), sliderValueForZoom(((PonderUiZoomAccess) ponder).ponderer$getUserZoom()));
        zoom = (PonderUiZoomAccess) ponder;
        updateMessage();
    }

    private static double sliderValueForZoom(float zoom) {
        // Reciprocal endpoints give 0.2x / 1x / 5x at the left / center / right.
        return (Math.log(zoom) / Math.log(MAX_ZOOM) + 1) / 2;
    }

    private static int sliderWidth(int screenWidth) {
        return Math.min(144, Math.max(80, (screenWidth - 62) / 3));
    }

    private static int left(int screenWidth) {
        return screenWidth - 31 - sliderWidth(screenWidth);
    }

    public int getBarRight() {
        return getX() + getWidth() - 2;
    }

    public int getPreviewButtonY() {
        return getY() + getHeight() / 2 + 5 + PREVIEW_HINT_HEIGHT;
    }

    public void refreshValue() {
        value = sliderValueForZoom(zoom.ponderer$getUserZoom());
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("ponderer.ui.zoom",
            String.format(Locale.ROOT, "%.2fx", zoom.ponderer$getUserZoom())));
    }

    @Override
    protected void applyValue() {
        zoom.ponderer$setUserZoom((float) Math.pow(MAX_ZOOM, 2 * value - 1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && active && visible && isMouseOver(mouseX, mouseY)) {
            value = .5;
            zoom.ponderer$setUserZoom(1);
            updateMessage();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int barX = getX() + 6;
        int barY = getY() + getHeight() / 2;
        int barWidth = width - 12;
        boolean highlighted = isHoveredOrFocused();
        new BoxElement().withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(highlighted ? PonderUI.COLOR_HOVER : PonderUI.COLOR_IDLE)
            .withBorderOffset(-2)
            .at(barX - 4, barY - 4, 400).withBounds(barWidth + 8, 9).render(graphics);

        graphics.pose().pushPose();
        graphics.pose().translate(barX - 2, barY - 2, 410);
        float fill = (float) ((barWidth + 4) * value);
        var colors = PonderProgressBar.BAR_COLORS;
        var handle = highlighted ? PonderUI.COLOR_HOVER : PonderUI.COLOR_IDLE;
        UIRenderHelper.drawGradientRect(graphics.pose().last().pose(), 0, 0, 1, fill, 3,
            colors.getFirst(), colors.getFirst());
        UIRenderHelper.drawGradientRect(graphics.pose().last().pose(), 0, 0, 3, fill, 4,
            colors.getSecond(), colors.getSecond());
        float center = (barWidth + 4) / 2f;
        UIRenderHelper.drawGradientRect(graphics.pose().last().pose(), 5, center - .5f, -1, center + .5f, 6,
            PonderUI.COLOR_IDLE.getFirst(), PonderUI.COLOR_IDLE.getSecond());
        UIRenderHelper.drawGradientRect(graphics.pose().last().pose(), 10, fill - 1, -2, fill + 1, 7,
            handle.getFirst(), handle.getSecond());
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 420);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, getMessage(), getX() - 8 - font.width(getMessage()),
            getY() + (getHeight() - font.lineHeight) / 2, 0xFF_8ab6d6, false);
        graphics.pose().popPose();

        if (isHovered()) {
            int hintTop = barY + 5;
            PonderSmallHint.render(graphics, Component.translatable("ponderer.ui.zoom.hint"),
                barX + barWidth / 2, hintTop, getPreviewButtonY(),
                UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB());
        }
    }
}
