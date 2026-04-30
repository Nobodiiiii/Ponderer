package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.TextStencilElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.platform.CatnipClientServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PondererDialogScreen extends AbstractSimiScreen {

    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 12;
    private static final int MAX_TEXT_WIDTH = 300;
    private static final int MIN_BUTTON_WIDTH = 70;

    private final Screen source;
    private final List<Component> titleLines;
    private final List<Component> bodyLines;
    private final List<DialogButton> buttons;
    private final List<DialogTextLine> text = new ArrayList<>();

    private int x;
    private int y;
    private int textWidth;
    private int textHeight;
    private BoxElement textBackground;

    public record DialogButton(Component label, Consumer<PondererDialogScreen> action) {
    }

    private record DialogTextLine(FormattedText text, boolean title) {
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
        Minecraft client = CatnipClientServices.CLIENT_HOOKS.getMinecraftFromScreen(source);
        this.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        this.minecraft.screen = this;
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

        text.clear();

        for (Component line : titleLines) {
            addWrappedText(line.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
        }
        for (Component line : bodyLines) {
            addWrappedText(line, false);
        }

        textHeight = text.size() * (font.lineHeight + 1) + 4;
        textWidth = MAX_TEXT_WIDTH;

        x = width / 2 - textWidth / 2 - 2;
        y = height / 2 - textHeight / 2 - 16;
        if (x + textWidth > width) {
            x = width - textWidth;
        }
        if (y + textHeight + 30 > height) {
            y = height - textHeight - 30;
        }

        int totalButtonWidth = 0;
        List<Integer> buttonWidths = new ArrayList<>();
        for (DialogButton button : buttons) {
            int buttonWidth = Math.max(MIN_BUTTON_WIDTH, font.width(button.label()) + 12);
            buttonWidths.add(buttonWidth);
            totalButtonWidth += buttonWidth;
        }
        if (!buttons.isEmpty()) {
            totalButtonWidth += Math.max(0, buttons.size() - 1) * BUTTON_GAP;
        }

        int buttonX = x + textWidth / 2 - totalButtonWidth / 2;
        int buttonY = y + textHeight + 6;
        for (int i = 0; i < buttons.size(); i++) {
            int buttonWidth = buttonWidths.get(i);
            DialogButton spec = buttons.get(i);
            BoxWidget widget = new BoxWidget(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .withCallback(() -> spec.action().accept(this));
            TextStencilElement textElement = new TextStencilElement(font, spec.label().copy()).centered(true, true);
            widget.showingElement(textElement.withElementRenderer(BoxWidget.gradientFactory.apply(widget)));
            addRenderableWidget(widget);
            buttonX += buttonWidth + BUTTON_GAP;
        }

        textBackground = new BoxElement()
            .withBackground(BoxElement.COLOR_BACKGROUND_FLAT)
            .gradientBorder(AbstractSimiWidget.COLOR_DISABLED)
            .withBounds(width + 10, textHeight + 35)
            .at(-5, y - 5);

        if (text.size() == 1) {
            x = (width - font.width(text.get(0).text())) / 2;
        }
    }

    private void addWrappedText(Component line, boolean title) {
        for (FormattedText wrapped : font.getSplitter().splitLines(line, MAX_TEXT_WIDTH, Style.EMPTY)) {
            text.add(new DialogTextLine(wrapped, title));
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
        endFrame();
        source.render(graphics, 0, 0, 10);
        prepareFrame();
        graphics.fillGradient(0, 0, this.width, this.height, 0x70101010, 0x80101010);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        textBackground.render(graphics);
        int offset = font.lineHeight + 1;
        int lineY = y - offset;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        for (DialogTextLine line : text) {
            lineY += offset;
            if (line.text() != null) {
                FormattedCharSequence orderedLine = Language.getInstance().getVisualOrder(line.text());
                graphics.drawString(font, orderedLine, x, lineY, line.title() ? 0xffff55 : 0xeaeaea, false);
            }
        }

        poseStack.popPose();
    }

    @Override
    protected void prepareFrame() {
        UIRenderHelper.swapAndBlitColor(minecraft.getMainRenderTarget(), UIRenderHelper.framebuffer);
        RenderSystem.clear(GL30.GL_STENCIL_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    @Override
    protected void endFrame() {
        UIRenderHelper.swapAndBlitColor(UIRenderHelper.framebuffer, minecraft.getMainRenderTarget());
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
