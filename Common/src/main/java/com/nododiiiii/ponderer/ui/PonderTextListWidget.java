package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.ponder.TextIndexStore;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Side panel listing every text / shared_text entry that occurs in the current
 * Ponder scene segment. Clicking a row seeks the scene to the tick at which
 * that text appears, mirroring keyframe navigation in {@link PonderUI#seekToTime(int)}.
 *
 * Visibility starts from the client configuration and can be toggled for this
 * Ponder screen using the Comfy Reading button set up in PonderUIMixin.
 */
public class PonderTextListWidget extends AbstractWidget {

    /** Desired width when there is room. The actual width is computed dynamically. */
    public static final int DESIRED_PANEL_WIDTH = 144;
    /** Right padding — matches the slowMode button's right padding. */
    public static final int DESIRED_RIGHT_PADDING = 30;
    public static final int TOP_Y = 70;
    public static final int BOTTOM_PAD = 60; // leave room for the bottom button row

    private static final int BORDER_SIZE = 2;
    private static final int ROW_HEIGHT = 11;
    private static final int HEADER_HEIGHT = 14;
    private static final int PADDING_X = 7;
    private static final int CONTENT_RIGHT_PAD = 10;

    // Share Ponder's speech-box, text and button colors rather than a separate panel palette.
    private static final int HEADER_RULE     = PonderUI.COLOR_IDLE.getSecond().getRGB();
    private static final int HEADER_TEXT     = UIRenderHelper.COLOR_TEXT.getFirst().getRGB();
    private static final int SUBHEADER_TEXT  = UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB();
    private static final int ACCENT_PLAYED   = PonderUI.COLOR_HIGHLIGHT.getFirst().getRGB();
    private static final int ACCENT_UNPLAYED = PonderUI.COLOR_IDLE.getFirst().getRGB();
    private static final int TEXT_PLAYED     = UIRenderHelper.COLOR_TEXT.getFirst().getRGB();
    private static final int TEXT_HOVER      = UIRenderHelper.COLOR_TEXT_ACCENT.getFirst().getRGB();
    private static final int TEXT_UNPLAYED   = UIRenderHelper.COLOR_TEXT_DARKER.getSecond().getRGB();
    private static final int ROW_HOVER_BG    = PonderButton.COLOR_IDLE.getSecond().getRGB();
    private static final int SCROLL_TRACK    = PonderUI.COLOR_IDLE.getSecond().getRGB();
    private static final int SCROLL_THUMB    = PonderButton.COLOR_IDLE.getFirst().getRGB();

    private final PonderUI ponderUi;
    private boolean configuredDefault;
    private boolean panelVisible;
    private int scrollOffset;

    public PonderTextListWidget(PonderUI ponderUi) {
        super(computeLeft(ponderUi.width) - BORDER_SIZE, top(ponderUi.width) - BORDER_SIZE,
            computeWidth(ponderUi.width) + 2 * BORDER_SIZE,
            Math.max(60, ponderUi.height - top(ponderUi.width) - BOTTOM_PAD) + 2 * BORDER_SIZE,
            Component.empty());
        this.ponderUi = ponderUi;
        this.configuredDefault = Config.DEFAULT_TEXT_PROGRESS.get();
        this.panelVisible = configuredDefault;
    }

    private static int top(int screenWidth) {
        return screenWidth < 480 ? TOP_Y + 28 : TOP_Y;
    }

    public boolean isPanelVisible() {
        boolean currentDefault = Config.DEFAULT_TEXT_PROGRESS.get();
        if (configuredDefault != currentDefault) {
            configuredDefault = currentDefault;
            panelVisible = currentDefault;
        }
        return panelVisible;
    }

    public void setPanelVisible(boolean visible) {
        configuredDefault = Config.DEFAULT_TEXT_PROGRESS.get();
        panelVisible = visible;
    }

    public void togglePanel() {
        setPanelVisible(!isPanelVisible());
    }

    private static int rightEdge(int screenWidth) {
        return screenWidth - DESIRED_RIGHT_PADDING;
    }

    /**
     * Compute the panel's left x.
     *
     * Right edge is fixed at {@code screenWidth - DESIRED_RIGHT_PADDING} (matches slowMode's right padding).
     * Left edge is normally {@code right - DESIRED_PANEL_WIDTH}, but is clamped so it does not extend left of
     * {@link net.createmod.ponder.foundation.ui.PonderProgressBar}'s right end ({@code width/2 + 110}); the panel
     * shrinks in width when the screen is narrow rather than crossing that boundary.
     */
    private static int computeLeft(int screenWidth) {
        int desiredLeft = rightEdge(screenWidth) - DESIRED_PANEL_WIDTH;
        int progressBarRight = screenWidth / 2 + 110;
        return Math.max(desiredLeft, progressBarRight);
    }

    private static int computeWidth(int screenWidth) {
        return Math.max(0, rightEdge(screenWidth) - computeLeft(screenWidth));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!shouldShow()) {
            return;
        }
        List<TextIndexStore.Entry> entries = currentEntries();
        if (entries.isEmpty()) {
            return;
        }

        int x = getX() + BORDER_SIZE;
        int y = getY() + BORDER_SIZE;
        int w = getWidth() - 2 * BORDER_SIZE;
        int h = getHeight() - 2 * BORDER_SIZE;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        renderSpeechBox(graphics, x, y, w, h);
        // BoxElement draws immediately and writes depth; keep the list above its background.
        graphics.pose().translate(0, 0, 20);
        RenderSystem.enableBlend();

        Font font = Minecraft.getInstance().font;

        // Single localized header.
        Component header = Component.translatable("ponderer.ui.text_progress");
        graphics.drawString(font, header, x + PADDING_X, y + 4, HEADER_TEXT, false);

        String countLabel = String.valueOf(entries.size());
        int countWidth = font.width(countLabel);
        graphics.drawString(font, countLabel,
            x + w - PADDING_X - countWidth, y + 4, SUBHEADER_TEXT, false);

        graphics.fill(x + 3, y + HEADER_HEIGHT, x + w - 3, y + HEADER_HEIGHT + 1, HEADER_RULE);

        int listTop = y + HEADER_HEIGHT + 4;
        int listBottom = y + h - 4;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        int currentSceneTime = ponderUi.getActiveScene().getCurrentTime();
        int textWidth = w - PADDING_X - CONTENT_RIGHT_PAD - 6;

        graphics.enableScissor(x, listTop, x + w, listBottom);
        for (int row = 0; row < visibleRows; row++) {
            int index = row + scrollOffset;
            if (index >= entries.size()) break;
            TextIndexStore.Entry e = entries.get(index);

            int rowY = listTop + row * ROW_HEIGHT;
            boolean played = e.tick() <= currentSceneTime;
            boolean hovered = mouseX >= x + 3 && mouseX < x + w - CONTENT_RIGHT_PAD
                && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1;

            if (hovered) {
                graphics.fill(x + 3, rowY - 1, x + w - CONTENT_RIGHT_PAD, rowY + ROW_HEIGHT - 1, ROW_HOVER_BG);
            }

            int accent = played ? ACCENT_PLAYED : ACCENT_UNPLAYED;
            graphics.fill(x + 3, rowY, x + 5, rowY + ROW_HEIGHT - 2, accent);

            int textColor;
            if (hovered) {
                textColor = TEXT_HOVER;
            } else if (played) {
                textColor = TEXT_PLAYED;
            } else {
                textColor = TEXT_UNPLAYED;
            }
            FormattedCharSequence line = truncate(font, e, textWidth);
            graphics.drawString(font, line, x + PADDING_X + 2, rowY + 1, textColor, false);
        }
        graphics.disableScissor();

        if (entries.size() > visibleRows && visibleRows > 0) {
            int trackTop = listTop;
            int trackBottom = listBottom;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(10, trackH * visibleRows / entries.size());
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            int barX = x + w - 5;
            graphics.fill(barX, trackTop, barX + 2, trackBottom, SCROLL_TRACK);
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, SCROLL_THUMB);
        }
        graphics.pose().popPose();

        TextIndexStore.Entry hoveredEntry = entryAt(mouseX, mouseY, entries, listTop, listBottom, visibleRows);
        if (hoveredEntry != null) {
            graphics.renderTooltip(font, Component.literal(previewLabel(hoveredEntry)), mouseX, mouseY);
        }
    }

    private void renderSpeechBox(GuiGraphics graphics, int x, int y, int w, int h) {
        PonderButton readingButton = ((PonderUIAccessor) ponderUi).ponderer$getSlowMode();
        var borderColors = readingButton.isHoveredOrFocused() ? PonderButton.COLOR_HOVER : PonderButton.COLOR_IDLE;
        graphics.flush();
        RenderSystem.enableDepthTest();
        new BoxElement()
            .withBackground(PonderUI.BACKGROUND_FLAT)
            .gradientBorder(borderColors)
            .withBorderOffset(0)
            .at(x, y, 0)
            .withBounds(w, h)
            .render(graphics);

        // Use the same downward divot as "Next Up", aligned to the actual Comfy Reading button.
        int pointerX = readingButton.getX() + readingButton.getWidth() / 2
            - PonderGuiTextures.SPEECH_TOOLTIP_BACKGROUND.getWidth() / 2 + 1;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 10);
        PonderGuiTextures.SPEECH_TOOLTIP_BACKGROUND.render(graphics, pointerX, y + h - 1);
        PonderGuiTextures.SPEECH_TOOLTIP_COLOR.render(graphics, pointerX, y + h - 1, borderColors.getSecond());
        graphics.pose().popPose();
    }

    private FormattedCharSequence truncate(Font font, TextIndexStore.Entry e, int maxWidth) {
        String label = previewLabel(e).replace('\n', ' ').replace('\r', ' ');
        if (font.width(label) > maxWidth) {
            int chars = label.length();
            while (chars > 0 && font.width(label.substring(0, chars) + "…") > maxWidth) {
                chars--;
            }
            label = label.substring(0, chars) + "…";
        }
        return Component.literal(label).getVisualOrderText();
    }

    private static String previewLabel(TextIndexStore.Entry e) {
        if (e.shared()) {
            return "[shared] " + e.preview();
        }
        String p = e.preview();
        return (p == null || p.isEmpty()) ? "(empty)" : p;
    }

    private TextIndexStore.Entry entryAt(int mouseX, int mouseY, List<TextIndexStore.Entry> entries,
                                          int listTop, int listBottom, int visibleRows) {
        int x = getX() + BORDER_SIZE;
        int w = getWidth() - 2 * BORDER_SIZE;
        if (mouseX < x + 3 || mouseX >= x + w - CONTENT_RIGHT_PAD) return null;
        if (mouseY < listTop || mouseY >= listBottom) return null;
        int row = (mouseY - listTop) / ROW_HEIGHT;
        if (row < 0 || row >= visibleRows) return null;
        int index = row + scrollOffset;
        if (index < 0 || index >= entries.size()) return null;
        return entries.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shouldShow() || button != 0) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        List<TextIndexStore.Entry> entries = currentEntries();
        if (entries.isEmpty()) return false;

        int y = getY() + BORDER_SIZE;
        int h = getHeight() - 2 * BORDER_SIZE;
        int listTop = y + HEADER_HEIGHT + 4;
        int listBottom = y + h - 4;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        TextIndexStore.Entry e = entryAt((int) mouseX, (int) mouseY, entries, listTop, listBottom, visibleRows);
        if (e == null) return false;
        ponderUi.seekToTime(e.tick());
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (!shouldShow()) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        List<TextIndexStore.Entry> entries = currentEntries();
        if (entries.isEmpty()) return false;

        int listTop = getY() + BORDER_SIZE + HEADER_HEIGHT + 4;
        int listBottom = getY() + getHeight() - BORDER_SIZE - 4;
        int visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        if (maxScroll <= 0) return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return shouldShow() && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        // never focusable
    }

    private boolean shouldShow() {
        if (!this.visible) return false;
        if (!isPanelVisible()) return false;
        if (PickState.isActive() || InterfaceSlotEditState.isActive()) return false;
        PonderScene active = ponderUi.getActiveScene();
        return active != null;
    }

    private List<TextIndexStore.Entry> currentEntries() {
        PonderScene active = ponderUi.getActiveScene();
        return active == null ? List.of() : TextIndexStore.get(active);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // panel is purely visual; no narration
    }
}
