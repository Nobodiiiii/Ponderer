package com.nododiiiii.ponderer.application.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nododiiiii.ponderer.support.ui.BoundedBoxWidget;
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
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PondererDialogScreen extends AbstractSimiScreen implements PondererUiScaling.ScaledScreen {

    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_GAP = 4;
    private static final int MAX_TEXT_WIDTH = 300;
    private static final int MIN_BUTTON_WIDTH = 78;

    private final Screen source;
    private final List<Component> titleLines;
    private final List<Component> bodyLines;
    private final List<DialogButton> buttons;
    private final List<DialogTextLine> text = new ArrayList<>();

    private int x;
    private int y;
    private int textWidth;
    private int textHeight;
    private int titleHeight;
    private int bodyHeight;
    private int bodyViewportHeight;
    private double bodyScroll;
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

    public static void confirm(Screen source, List<Component> title, List<Component> body, Consumer<Boolean> action) {
        new PondererDialogScreen(source, title, body, List.of(
            button(Component.translatable("ponderer.ui.confirm"), dialog -> {
                dialog.closeToSource();
                action.accept(true);
            }),
            button(Component.translatable("ponderer.ui.cancel"), dialog -> {
                dialog.closeToSource();
                action.accept(false);
            }))) {
            @Override
            public void onClose() {
                super.onClose();
                action.accept(false);
            }
        }.open();
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
        textWidth = Math.min(MAX_TEXT_WIDTH, Math.max(1, width - 28));

        for (Component line : titleLines) {
            addWrappedText(line.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
        }
        titleHeight = text.size() * (font.lineHeight + 1);
        for (Component line : bodyLines) {
            addWrappedText(line, false);
        }

        List<Integer> buttonWidths = new ArrayList<>();
        int availableWidth = Math.max(1, width - 28);
        int totalButtonWidth = Math.max(0, buttons.size() - 1) * BUTTON_GAP;
        for (DialogButton button : buttons) {
            int buttonWidth = Math.min(availableWidth, Math.max(MIN_BUTTON_WIDTH, font.width(button.label()) + 20));
            buttonWidths.add(buttonWidth);
            totalButtonWidth += buttonWidth;
        }
        boolean stackedButtons = totalButtonWidth > availableWidth;
        int buttonAreaHeight = stackedButtons ? buttons.size() * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP : BUTTON_HEIGHT;

        bodyHeight = text.size() * (font.lineHeight + 1) - titleHeight;
        bodyViewportHeight = Math.min(bodyHeight,
            Math.max(font.lineHeight + 1, height - titleHeight - buttonAreaHeight - 24));
        bodyScroll = Math.clamp(bodyScroll, 0, maxBodyScroll());
        textHeight = titleHeight + bodyViewportHeight + 4;

        x = (width - textWidth) / 2;
        y = Math.max(5, (height - textHeight - buttonAreaHeight - 8) / 2);

        int buttonX = x + textWidth / 2 - totalButtonWidth / 2;
        int buttonY = y + textHeight + 2;
        for (int i = 0; i < buttons.size(); i++) {
            int buttonWidth = buttonWidths.get(i);
            if (stackedButtons) buttonX = (width - buttonWidth) / 2;
            DialogButton spec = buttons.get(i);
            BoxWidget widget = new BoundedBoxWidget(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .withCallback(() -> spec.action().accept(this));
            TextStencilElement textElement = new TextStencilElement(font, spec.label().copy()).centered(true, true);
            widget.showingElement(textElement.withElementRenderer(BoxWidget.gradientFactory.apply(widget)));
            addRenderableWidget(widget);
            if (stackedButtons) buttonY += BUTTON_HEIGHT + BUTTON_GAP;
            else buttonX += buttonWidth + BUTTON_GAP;
        }

        textBackground = new BoxElement()
            .withBackground(BoxElement.COLOR_BACKGROUND_FLAT)
            .gradientBorder(AbstractSimiWidget.COLOR_DISABLED)
            .withBounds(width + 10, textHeight + buttonAreaHeight + 11)
            .at(-5, y - 5);

        if (text.size() == 1) {
            x = (width - font.width(text.get(0).text())) / 2;
        }
    }

    private void addWrappedText(Component line, boolean title) {
        if (line.getString().isEmpty()) {
            text.add(new DialogTextLine(line, title));
            return;
        }
        for (FormattedText wrapped : font.getSplitter().splitLines(line, textWidth, Style.EMPTY)) {
            text.add(new DialogTextLine(wrapped, title));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (maxBodyScroll() > 0) {
            double nextOffset = switch (keyCode) {
                case GLFW.GLFW_KEY_PAGE_UP -> bodyScroll - bodyViewportHeight;
                case GLFW.GLFW_KEY_PAGE_DOWN -> bodyScroll + bodyViewportHeight;
                case GLFW.GLFW_KEY_HOME -> 0;
                case GLFW.GLFW_KEY_END -> maxBodyScroll();
                default -> Double.NaN;
            };
            if (!Double.isNaN(nextOffset)) {
                bodyScroll = Math.clamp(nextOffset, 0, maxBodyScroll());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxBodyScroll() > 0) {
            bodyScroll = Math.clamp(bodyScroll - scrollY * (font.lineHeight + 1) * 3, 0, maxBodyScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxBodyScroll() {
        return Math.max(0, bodyHeight - bodyViewportHeight);
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
        int lineY = y;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 200);

        for (DialogTextLine line : text) {
            if (line.title()) {
                FormattedCharSequence orderedLine = Language.getInstance().getVisualOrder(line.text());
                graphics.drawString(font, orderedLine, x, lineY, 0xffff55, false);
                lineY += offset;
            }
        }

        if (maxBodyScroll() > 0)
            graphics.enableScissor(x, y + titleHeight, x + textWidth, y + titleHeight + bodyViewportHeight);
        lineY = y + titleHeight - (int) bodyScroll;
        for (DialogTextLine line : text) {
            if (!line.title()) {
                graphics.drawString(font, Language.getInstance().getVisualOrder(line.text()), x, lineY, 0xeaeaea, false);
                lineY += offset;
            }
        }
        if (maxBodyScroll() > 0) {
            graphics.disableScissor();
            int thumbHeight = Math.max(4, bodyViewportHeight * bodyViewportHeight / bodyHeight);
            int thumbY = y + titleHeight + (int) ((bodyViewportHeight - thumbHeight) * bodyScroll / maxBodyScroll());
            graphics.fill(x + textWidth + 4, thumbY, x + textWidth + 6, thumbY + thumbHeight, 0xffaaaaaa);
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
