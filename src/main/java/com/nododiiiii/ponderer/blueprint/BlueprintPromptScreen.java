package com.nododiiiii.ponderer.blueprint;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Prompt screen for saving a blueprint selection.
 * Ported from Create's SchematicPromptScreen.
 */
public class BlueprintPromptScreen extends Screen {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 84;

    private EditBox nameField;
    private Button confirm;
    private Button abort;

    public BlueprintPromptScreen() {
        super(Component.translatable("ponderer.ui.blueprint.prompt.title"));
    }

    @Override
    protected void init() {
        int x = (this.width - WIDTH) / 2;
        int y = (this.height - HEIGHT) / 2;

        // Name input
        nameField = new EditBox(this.font, x + 30, y + 25, 140, 16, Component.empty());
        nameField.setTextColor(-1);
        nameField.setTextColorUneditable(-1);
        nameField.setBordered(true);
        nameField.setMaxLength(35);
        nameField.setFocused(true);
        setFocused(nameField);
        addRenderableWidget(nameField);

        // Abort button
        abort = Button.builder(Component.translatable("ponderer.ui.blueprint.prompt.discard"), b -> {
            BlueprintEvents.HANDLER.discard();
            onClose();
        }).bounds(x + 10, y + 52, 60, 20).build();
        addRenderableWidget(abort);

        // Confirm button
        confirm = Button.builder(Component.translatable("ponderer.ui.blueprint.prompt.save"), b -> {
            doConfirm();
        }).bounds(x + WIDTH - 70, y + 52, 60, 20).build();
        addRenderableWidget(confirm);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Darken game background first
        this.renderBackground(graphics);

        int x = (this.width - WIDTH) / 2;
        int y = (this.height - HEIGHT) / 2;

        // Background panel - opaque dark fill
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF333333);
        graphics.renderOutline(x, y, WIDTH, HEIGHT, 0xFF6886c5);

        // Title with shadow for readability
        graphics.drawString(this.font, this.title, x + (WIDTH - this.font.width(this.title)) / 2, y + 6, 0xFFFFFF, true);

        // Blueprint item decoration
        GuiGameElement.of(BlueprintFeature.getCarrierStack())
                .at(x + 8, y + 22, 0)
                .render(graphics);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            doConfirm();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        return nameField.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doConfirm() {
        BlueprintEvents.HANDLER.saveBlueprint(nameField.getValue());
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
