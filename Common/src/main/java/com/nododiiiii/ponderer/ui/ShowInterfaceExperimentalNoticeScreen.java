package com.nododiiiii.ponderer.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShowInterfaceExperimentalNoticeScreen extends AbstractSimiScreen {

    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 8;
    private static final int PADDING = 16;
    private static final int LINE_SPACING = 2;

    private static boolean hideForSession = false;

    private final List<Component> textLines = new ArrayList<>();
    private Screen source;
    private Consumer<Response> action = response -> {
    };

    private BoxWidget confirmButton;
    private BoxWidget hideForSessionButton;
    private BoxWidget cancelButton;
    private int titleLineCount;
    private int dialogX;
    private int dialogY;
    private int dialogWidth;
    private int dialogHeight;
    private int textX;
    private int textY;

    public enum Response {
        Confirm,
        HideForSession,
        Cancel
    }

    public static void openIfNeeded(@Nonnull Screen source, Runnable onProceed) {
        if (hideForSession) {
            onProceed.run();
            return;
        }

        new ShowInterfaceExperimentalNoticeScreen()
            .withAction(response -> {
                if (response == Response.Cancel) {
                    return;
                }
                if (response == Response.HideForSession) {
                    hideForSession = true;
                }
                onProceed.run();
            })
            .open(source);
    }

    public ShowInterfaceExperimentalNoticeScreen withAction(Consumer<Response> action) {
        this.action = action != null ? action : response -> {
        };
        return this;
    }

    public void open(@Nonnull Screen source) {
        this.source = source;
        Minecraft client = Minecraft.getInstance();
        this.init(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        client.screen = this;
    }

    @Override
    protected void init() {
        super.init();

        textLines.clear();
        textLines.add(Component.translatable("ponderer.ui.show_interface.experimental_notice.title"));
        titleLineCount = 1;
        textLines.add(Component.translatable("ponderer.ui.show_interface.experimental_notice.line1"));
        textLines.add(Component.translatable("ponderer.ui.show_interface.experimental_notice.line2"));
        textLines.add(Component.translatable("ponderer.ui.show_interface.experimental_notice.line3"));

        int lineHeight = font.lineHeight + LINE_SPACING;
        int textHeight = Math.max(font.lineHeight, textLines.size() * lineHeight - LINE_SPACING);
        int totalButtonWidth = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        int textWidth = textLines.stream()
            .mapToInt(font::width)
            .max()
            .orElse(totalButtonWidth);

        dialogWidth = Math.min(width - 40, Math.max(textWidth + PADDING * 2, totalButtonWidth + PADDING * 2));
        dialogHeight = PADDING * 2 + textHeight + 14 + BUTTON_HEIGHT;
        dialogX = (width - dialogWidth) / 2;
        dialogY = (height - dialogHeight) / 2;
        textX = dialogX + PADDING;
        textY = dialogY + PADDING;

        int buttonX = dialogX + (dialogWidth - totalButtonWidth) / 2;
        int buttonY = dialogY + dialogHeight - PADDING - BUTTON_HEIGHT;

        confirmButton = createButton(
            buttonX,
            buttonY,
            () -> accept(Response.Confirm));
        addRenderableWidget(confirmButton);
        buttonX += BUTTON_WIDTH + BUTTON_GAP;
        hideForSessionButton = createButton(
            buttonX,
            buttonY,
            () -> accept(Response.HideForSession));
        addRenderableWidget(hideForSessionButton);
        buttonX += BUTTON_WIDTH + BUTTON_GAP;
        cancelButton = createButton(
            buttonX,
            buttonY,
            () -> accept(Response.Cancel));
        addRenderableWidget(cancelButton);
    }

    @Override
    public void tick() {
        super.tick();
        if (source != null) {
            source.tick();
        }
    }

    @Override
    public void onClose() {
        accept(Response.Cancel);
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (source != null) {
            source.render(graphics, 0, 0, 10);
        }
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
        for (int i = 0; i < textLines.size(); i++) {
            Component line = textLines.get(i);
            int color = i < titleLineCount ? 0xFFF0D080 : 0xFFEAEAEA;
            graphics.drawString(font, line, textX, y, color, false);
            y += lineHeight;
        }

        poseStack.popPose();
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderButtonLabel(graphics, confirmButton, Component.translatable("ponderer.ui.confirm"));
        renderButtonLabel(graphics, hideForSessionButton,
            Component.translatable("ponderer.ui.show_interface.experimental_notice.hide_for_session"));
        renderButtonLabel(graphics, cancelButton, Component.translatable("ponderer.ui.cancel"));
    }

    @Override
    public void resize(@Nonnull Minecraft client, int width, int height) {
        super.resize(client, width, height);
        if (source != null) {
            source.resize(client, width, height);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private BoxWidget createButton(int x, int y, Runnable callback) {
        return new BoxWidget(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).withCallback(callback);
    }

    private void renderButtonLabel(GuiGraphics graphics, BoxWidget button, Component label) {
        if (button == null) {
            return;
        }
        int x = button.getX() + button.getWidth() / 2;
        int y = button.getY() + (button.getHeight() - font.lineHeight) / 2 + 1;
        graphics.drawCenteredString(font, label, x, y, 0xFFEAEAEA);
    }

    private void accept(Response response) {
        if (source != null) {
            ScreenOpener.open(source);
        } else {
            Minecraft.getInstance().setScreen(null);
        }
        action.accept(response);
    }
}
