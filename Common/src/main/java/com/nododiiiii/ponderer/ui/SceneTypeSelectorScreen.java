package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Popup for selecting the start step type when creating a new scene segment.
 */
public class SceneTypeSelectorScreen extends AbstractSimiScreen {

    private static final int W = 210;
    private static final int H = 120;

    private final SceneEditorScreen parent;

    private PonderButton showStructureButton;
    private PonderButton showInterfaceButton;
    private PonderButton cancelButton;

    public SceneTypeSelectorScreen(SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.scene_selector"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        setWindowSize(W, H);
        super.init();

        int buttonW = W - 20;
        int buttonX = guiLeft + 10;

        showStructureButton = new PonderButton(buttonX, guiTop + 36, buttonW, 18);
        showStructureButton.withCallback(() -> onConfirmType("show_structure"));
        addRenderableWidget(showStructureButton);

        showInterfaceButton = new PonderButton(buttonX, guiTop + 58, buttonW, 18);
        showInterfaceButton.withCallback(() -> onConfirmType("show_interface"));
        addRenderableWidget(showInterfaceButton);

        cancelButton = new PonderButton(guiLeft + W - 56, guiTop + H - 22, 46, 16);
        cancelButton.withCallback(() -> Minecraft.getInstance().setScreen(parent));
        addRenderableWidget(cancelButton);
    }

    private void onConfirmType(String type) {
        parent.insertSplitStep(type);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        new BoxElement()
            .withBackground(new Color(UILayoutConstants.COLOR_BG, true))
            .gradientBorder(new Color(UILayoutConstants.COLOR_BORDER_TOP, true), new Color(UILayoutConstants.COLOR_BORDER_BOT, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(W, H)
            .render(graphics);

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UIText.of("ponderer.ui.scene_selector.title"), guiLeft + 10, guiTop + 8, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 20, guiLeft + W - 5, guiTop + 21, UILayoutConstants.COLOR_SEPARATOR);
        graphics.drawString(font, UIText.of("ponderer.ui.scene_selector.desc"), guiLeft + 10, guiTop + 24, 0xCCCCCC);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        graphics.drawCenteredString(font, UIText.of("ponderer.ui.step.type.show_structure"),
            showStructureButton.getX() + showStructureButton.getWidth() / 2,
            showStructureButton.getY() + 5,
            0xFFFFFF);

        graphics.drawCenteredString(font, UIText.of("ponderer.ui.step.type.show_interface"),
            showInterfaceButton.getX() + showInterfaceButton.getWidth() / 2,
            showInterfaceButton.getY() + 5,
            0xFFFFFF);

        graphics.drawCenteredString(font, UIText.of("ponderer.ui.cancel"),
            cancelButton.getX() + 23,
            cancelButton.getY() + 4,
            0xFFFFFF);

        graphics.pose().popPose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
