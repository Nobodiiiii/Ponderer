package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ai.AiOutputUpdate;
import com.nododiiiii.ponderer.ui.catnip.EntryTextSupport;
import com.nododiiiii.ponderer.ui.catnip.FormBoxWidget;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** A bounded transcript. Scrolling up pauses following until the reader returns to the bottom. */
final class AiOutputPanel extends AbstractWidget {
    private static final int TEXT = UIRenderHelper.COLOR_TEXT.getFirst().getRGB();
    private static final int MUTED = UIRenderHelper.COLOR_TEXT.getSecond().getRGB();
    private static final int ACCENT = UIRenderHelper.COLOR_TEXT_STRONG_ACCENT.getFirst().getRGB();
    private static final int ERROR = AbstractSimiWidget.COLOR_FAIL.getFirst().getRGB();
    private static final int INSET = 10;

    static final class State {
        private record Entry(String id, String text, boolean assistant, boolean error) {}
        private final List<Entry> entries = new ArrayList<>();
        private String status = "";
        private boolean error;
        private int revision;
        private double scroll;
        private boolean follow = true;

        void clear() {
            entries.clear(); status = ""; error = false; scroll = 0; follow = true; revision++;
        }

        void output(AiOutputUpdate update) {
            if (update.text().isBlank()) return;
            String id = update.turn() + ":" + update.attempt();
            String text = update.text();
            if (text.length() > 12_000) text = text.substring(0, 12_000) + "…";
            Entry replacement = new Entry(id, text, true, false);
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (id.equals(entries.get(i).id())) {
                    if (!replacement.equals(entries.get(i))) { entries.set(i, replacement); revision++; }
                    return;
                }
            }
            append(replacement);
        }

        void status(String message, boolean error, boolean retain) {
            status = message == null ? "" : message;
            this.error = error;
            if (retain && !status.isBlank()) {
                Entry previous = entries.isEmpty() ? null : entries.get(entries.size() - 1);
                if (previous == null || previous.assistant() || !previous.text().equals(status))
                    append(new Entry("", status, false, error));
            }
        }

        private void append(Entry entry) {
            entries.add(entry);
            if (entries.size() > 128) entries.remove(0);
            revision++;
        }
    }

    private record Line(FormattedCharSequence text, int color) {}
    private final State state;
    private final BoxElement background = new BoxElement()
        .withBackground(BoxElement.COLOR_BACKGROUND_TRANSPARENT)
        .gradientBorder(AbstractSimiWidget.COLOR_DISABLED)
        .withBorderOffset(-2);
    private final BoxElement scrollThumb = new BoxElement()
        .withBackground(BoxElement.COLOR_BACKGROUND_TRANSPARENT)
        .gradientBorder(AbstractSimiWidget.COLOR_IDLE)
        .withBorderOffset(-2);
    private final FormBoxWidget followButton;
    private final List<Line> lines = new ArrayList<>();
    private int renderedRevision = -1;
    private int renderedWidth = -1;

    AiOutputPanel(int x, int y, int width, int height, State state) {
        super(x, y, width, height, Component.translatable("ponderer.ui.ai_generate.output.title"));
        this.state = state;
        followButton = new FormBoxWidget(x + INSET, getBottom() - 24, Math.max(1, width - 2 * INSET), 20)
            .withCallback(this::followLatest);
    }

    void tick() { followButton.tick(); }

    private void followLatest() { state.follow = true; state.scroll = maxScroll(); }

    private int bodyTop() { return getY() + 28; }
    private int getBottom() { return getY() + getHeight(); }
    private int getRight() { return getX() + getWidth(); }
    private int bodyBottom() { return Math.max(bodyTop(), getBottom() - 56); }
    private int lineHeight() { return Minecraft.getInstance().font.lineHeight + 3; }
    private int maxScroll() { return Math.max(0, lines.size() * lineHeight() - (bodyBottom() - bodyTop())); }

    private void layoutText() {
        if (renderedRevision == state.revision && renderedWidth == width) return;
        lines.clear();
        var font = Minecraft.getInstance().font;
        for (State.Entry entry : state.entries) {
            if (!lines.isEmpty()) lines.add(new Line(FormattedCharSequence.EMPTY, MUTED));
            if (entry.assistant()) lines.add(new Line(Component.literal("AI").getVisualOrderText(), ACCENT));
            for (var line : font.split(Component.literal(entry.text()), Math.max(1, width - 2 * INSET - 5)))
                lines.add(new Line(line, entry.error() ? ERROR : entry.assistant() ? TEXT : MUTED));
        }
        renderedRevision = state.revision;
        renderedWidth = width;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        layoutText();
        state.scroll = state.follow ? maxScroll() : Math.min(state.scroll, maxScroll());
        var font = Minecraft.getInstance().font;
        background.at(getX(), getY()).withBounds(width - 4, height).render(graphics);
        graphics.drawString(font, getMessage(), getX() + INSET, getY() + INSET, ACCENT);
        graphics.enableScissor(getX(), bodyTop(), getRight() - 6, bodyBottom());
        try {
            if (lines.isEmpty()) {
                int y = bodyTop();
                for (var line : font.split(Component.translatable("ponderer.ui.ai_generate.output.empty"),
                        Math.max(1, width - 2 * INSET))) {
                    graphics.drawString(font, line, getX() + INSET, y, MUTED);
                    y += lineHeight();
                }
            }
            int first = Math.max(0, (int) state.scroll / lineHeight());
            for (int i = first; i < lines.size(); i++) {
                int y = bodyTop() + i * lineHeight() - (int) state.scroll;
                if (y >= bodyBottom()) break;
                Line line = lines.get(i);
                graphics.drawString(font, line.text(), getX() + INSET, y, line.color());
            }
        } finally { graphics.disableScissor(); }
        if (maxScroll() > 0) {
            int track = bodyBottom() - bodyTop();
            int thumb = Math.min(track, Math.max(12, track * track / (lines.size() * lineHeight())));
            int top = bodyTop() + (int) ((track - thumb) * state.scroll / maxScroll());
            scrollThumb.at(getRight() - 10, top).withBounds(4, thumb).render(graphics);
        }
        // Status lives inside the output panel, never over the form or its footer.
        graphics.enableScissor(getX() + INSET, getBottom() - 50, getRight() - INSET, getBottom() - 28);
        try {
            int y = getBottom() - 50;
            for (var line : font.split(Component.literal(state.status), Math.max(1, width - 2 * INSET))) {
                graphics.drawString(font, line, getX() + INSET, y, state.error ? ERROR : MUTED);
                y += font.lineHeight + 1;
            }
        } finally { graphics.disableScissor(); }
        followButton.active = !state.follow;
        followButton.setFocused(isFocused() && !state.follow);
        followButton.updateGradientFromState();
        followButton.render(graphics, mouseX, mouseY, partialTicks);
        EntryTextSupport.drawButtonLabel(graphics, followButton, UIText.of(state.follow
                ? "ponderer.ui.ai_generate.output.following" : "ponderer.ui.ai_generate.output.latest"),
            state.follow ? MUTED : TEXT);
    }

    private void scrollBy(double amount) {
        layoutText();
        state.scroll = Math.max(0, Math.min(maxScroll(), state.scroll + amount));
        state.follow = state.scroll >= maxScroll();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        if (!isMouseOver(x, y)) return false;
        scrollBy(-vertical * lineHeight() * 3);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !isMouseOver(x, y)) return false;
        followButton.mouseClicked(x, y, button);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scrollBy(-lineHeight());
            case GLFW.GLFW_KEY_DOWN -> scrollBy(lineHeight());
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-(bodyBottom() - bodyTop()));
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(bodyBottom() - bodyTop());
            case GLFW.GLFW_KEY_HOME -> scrollBy(-maxScroll());
            case GLFW.GLFW_KEY_END -> scrollBy(maxScroll());
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> followLatest();
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, state.status);
    }
}
