package com.nododiiiii.ponderer.runtime.viewer;

import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** A screen-local multiplier, independent of scripted scene zoom. */
public class PonderZoomSlider extends AbstractSliderButton {
    public static final float MIN_ZOOM = 0.2f;
    public static final float MAX_ZOOM = 5f;

    // Align the bar and preview button with the text panel's outer right border.
    private static final int HEADER_MARGIN = PonderTextListWidget.OUTER_RIGHT_PADDING;
    private static final int LABEL_GAP = 4;

    private final PonderUI ponder;
    private final PonderUiZoomAccess zoom;
    private final Component label = Component.translatable("ponderer.ui.zoom.label");
    private Component multiplier = Component.empty();

    public PonderZoomSlider(PonderUI ponder) {
        super(left(ponder.width), HEADER_MARGIN - 6, sliderWidth(ponder.width), 20,
            Component.empty(), sliderValueForZoom(((PonderUiZoomAccess) ponder).ponderer$getUserZoom()));
        this.ponder = ponder;
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
        // The bar's outer border ends two pixels before the widget's right edge.
        return screenWidth - HEADER_MARGIN - sliderWidth(screenWidth) + 2;
    }

    public int getBarRight() {
        return getX() + getWidth() - 2;
    }

    public int getPreviewButtonY() {
        return getY() + getHeight() / 2 + 5
            + PonderSmallHint.getRowHeight(ponder);
    }

    public void refreshValue() {
        value = sliderValueForZoom(zoom.ponderer$getUserZoom());
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        multiplier = Component.literal(String.format(Locale.ROOT, "%.2fx", zoom.ponderer$getUserZoom()));
        setMessage(Component.translatable("ponderer.ui.zoom", multiplier));
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
        var font = ponder.getFontRenderer();
        int labelWidth = font.width(label);
        int labelX = barX - 4 - LABEL_GAP - labelWidth;
        int labelY = barY - (font.lineHeight - 1) / 2;
        boolean labelHovered = mouseX >= labelX && mouseX < labelX + labelWidth
            && mouseY >= labelY && mouseY < labelY + font.lineHeight;
        boolean hovered = isHovered() || labelHovered;
        boolean highlighted = hovered || isFocused();
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
        graphics.drawString(font, label, labelX, labelY, 0xFFFFFFFF, false);
        graphics.pose().popPose();

        if (hovered) {
            float hintTop = barY + 5;
            float hintBottom = getPreviewButtonY();
            float barCenterY = barY + 0.5f;
            int hintCenterX = barX + barWidth / 2;
            int multiplierCenterX = Math.round(barX - 2 + fill);
            int hintColor = UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB();
            PonderSmallHint.render(graphics, multiplier,
                multiplierCenterX, 2 * barCenterY - hintBottom, 2 * barCenterY - hintTop, hintColor);
            PonderSmallHint.render(graphics, Component.translatable("ponderer.ui.zoom.hint"),
                hintCenterX, hintTop, hintBottom, hintColor);
        }
    }
}
