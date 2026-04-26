package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PondererDialogScreen extends AbstractSimiScreen {

    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 8;
    private static final int PADDING = 16;
    private static final int LINE_SPACING = 2;
    private static final int MAX_TEXT_WIDTH = 300;
    private static final int MIN_BUTTON_WIDTH = 100;

    private final Screen source;
    private final List<Component> titleLines;
    private final List<Component> bodyLines;
    private final List<DialogButton> buttons;
    private final List<ButtonState> buttonStates = new ArrayList<>();
    private final List<FormattedCharSequence> renderedLines = new ArrayList<>();

    private int titleLineCount;
    private int dialogX;
    private int dialogY;
    private int dialogWidth;
    private int dialogHeight;
    private int textX;
    private int textY;

    public record DialogButton(Component label, Consumer<PondererDialogScreen> action) {
    }

    private record ButtonState(DialogButton spec, BoxWidget widget) {
    }

    public PondererDialogScreen(@Nonnull Screen source,
                                List<Component> titleLines,
                                List<Component> bodyLines,
                                List<DialogButton> buttons) {
        this.source = source;
        this.titleLines = List.copyOf(titleLines);
        this.bodyLines = List.copyOf(bodyLines);
        this.buttons = List.copyOf(buttons);
    }

    public static DialogButton button(Component label, Consumer<PondererDialogScreen> action) {
        return new DialogButton(label, action);
    }

    public static DialogButton closeButton(Component label) {
        return button(label, PondererDialogScreen::closeToSource);
    }

    public void open() {
        Minecraft client = Minecraft.getInstance();
        this.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        client.screen = this;
    }

    public Screen source() {
        return source;
    }

    public void closeToSource() {
        Minecraft.getInstance().screen = source;
    }

    @Override
    protected void init() {
        super.init();

        renderedLines.clear();
        buttonStates.clear();

        int wrapWidth = Math.min(MAX_TEXT_WIDTH, Math.max(120, width - PADDING * 2 - 40));
        for (Component line : titleLines) {
            renderedLines.addAll(font.split(line, wrapWidth));
        }
        titleLineCount = renderedLines.size();
        for (Component line : bodyLines) {
            renderedLines.addAll(font.split(line, wrapWidth));
        }

        int lineHeight = font.lineHeight + LINE_SPACING;
        int textHeight = Math.max(font.lineHeight, renderedLines.size() * lineHeight - LINE_SPACING);
        int totalButtonWidth = 0;
        List<Integer> buttonWidths = new ArrayList<>();
        for (DialogButton button : buttons) {
            int buttonWidth = Math.max(MIN_BUTTON_WIDTH, font.width(button.label()) + 18);
            buttonWidths.add(buttonWidth);
            totalButtonWidth += buttonWidth;
        }
        if (!buttons.isEmpty()) {
            totalButtonWidth += Math.max(0, buttons.size() - 1) * BUTTON_GAP;
        }

        int textWidth = renderedLines.stream()
            .mapToInt(font::width)
            .max()
            .orElse(totalButtonWidth);

        dialogWidth = Math.min(width - 40, Math.max(textWidth + PADDING * 2, totalButtonWidth + PADDING * 2));
        dialogHeight = PADDING * 2 + textHeight + (buttons.isEmpty() ? 0 : 14 + BUTTON_HEIGHT);
        dialogX = (width - dialogWidth) / 2;
        dialogY = (height - dialogHeight) / 2;
        textX = dialogX + PADDING;
        textY = dialogY + PADDING;

        int buttonX = dialogX + (dialogWidth - totalButtonWidth) / 2;
        int buttonY = dialogY + dialogHeight - PADDING - BUTTON_HEIGHT;
        for (int i = 0; i < buttons.size(); i++) {
            int buttonWidth = buttonWidths.get(i);
            DialogButton spec = buttons.get(i);
            BoxWidget widget = new BoxWidget(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .withCallback(() -> spec.action().accept(this));
            addRenderableWidget(widget);
            buttonStates.add(new ButtonState(spec, widget));
            buttonX += buttonWidth + BUTTON_GAP;
        }
    }

    @Override
    public void tick() {
        super.tick();
        source.tick();
    }

    @Override
    public void onClose() {
        closeToSource();
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        source.render(graphics, 0, 0, 10);
        graphics.fillGradient(0, 0, width, height, 0xE8101010, 0xF0101010);
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xF0202020);
        graphics.renderOutline(dialogX, dialogY, dialogWidth, dialogHeight, 0xFF6A6A6A);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        int y = textY;
        int lineHeight = font.lineHeight + LINE_SPACING;
        for (int i = 0; i < renderedLines.size(); i++) {
            int color = i < titleLineCount ? 0xFFF0D080 : 0xFFEAEAEA;
            graphics.drawString(font, renderedLines.get(i), textX, y, color, false);
            y += lineHeight;
        }

        poseStack.popPose();
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        for (ButtonState state : buttonStates) {
            BoxWidget button = state.widget();
            int x = button.getX() + button.getWidth() / 2;
            int y = button.getY() + (button.getHeight() - font.lineHeight) / 2 + 1;
            graphics.drawCenteredString(font, state.spec().label(), x, y, 0xFFEAEAEA);
        }
    }

    @Override
    public void resize(@Nonnull Minecraft client, int width, int height) {
        super.resize(client, width, height);
        source.resize(client, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
