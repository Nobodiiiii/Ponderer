package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class CustomBackgroundTextFieldWidget extends HintableTextFieldWidget {

    /** Custom background color, or -1 for default. */
    private int customBackgroundColor = -1;

    public CustomBackgroundTextFieldWidget(Font font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    /**
     * Set a custom background color for this text field.
     * 
     * @param color The background color in ARGB format, or -1 to use default
     */
    public void setBackgroundColor(int color) {
        this.customBackgroundColor = color;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Render custom background if set
        if (customBackgroundColor != -1) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, customBackgroundColor);
            // Render border
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, 0xFF_666666);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0xFF_666666);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, 0xFF_666666);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, 0xFF_666666);

            // Render text
            renderText(graphics);
        } else {
            super.renderWidget(graphics, mouseX, mouseY, partialTicks);
        }
    }

    /**
     * Render text without background
     */
    private void renderText(GuiGraphics graphics) {
        // This is a simplified version of the text rendering logic
        // It just draws the text, assuming the background is already rendered
        String text = getValue();
        int color = 0xFF_FFFFFF; // White text color

        // Clip text to fit within the field, prioritizing the end of the text
        int maxW = this.width - 10;
        String displayText = text;

        // Check if text fits without truncation
        if (font.width(displayText) <= maxW) {
            // Text fits completely, no truncation needed
        } else {
            // Calculate how much text we can show from the end
            int ellipsisWidth = font.width("...");
            int availableWidth = maxW - ellipsisWidth;

            // Ensure availableWidth is positive
            if (availableWidth <= 0) {
                // Not enough space even for ellipsis, just show ellipsis
                displayText = "...";
            } else {
                // Use character pointer approach: start from the end and move forward
                StringBuilder sb = new StringBuilder();
                int currentWidth = 0;

                // Iterate from the end of the string to the beginning
                for (int i = text.length() - 1; i >= 0; i--) {
                    char c = text.charAt(i);
                    String charStr = String.valueOf(c);
                    int charWidth = font.width(charStr);

                    // Check if adding this character would exceed the available width
                    if (currentWidth + charWidth > availableWidth) {
                        break;
                    }

                    // Add the character to the beginning of the string builder
                    sb.insert(0, c);
                    currentWidth += charWidth;
                }

                // If we found some text, show it with ellipsis
                if (sb.length() > 0) {
                    displayText = "..." + sb.toString();
                } else {
                    // Not enough space for any text, just show ellipsis
                    displayText = "...";
                }
            }
        }

        // Draw text
        graphics.drawString(font, displayText, getX() + 5, getY() + (height - 8) / 2, color);
    }
}