package com.nododiiiii.ponderer.blueprint;

import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.UIText;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Prompt screen for saving a blueprint selection.
 * Ported from Create's SchematicPromptScreen.
 */
public class BlueprintPromptScreen extends AbstractSimiScreen {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 84;

    private EditBox nameField;
    private boolean awaitingOverrideConfirm;

    // Simi-style clickable button areas
    private int saveX, saveY, saveW, saveH;
    private int discardX, discardY, discardW, discardH;

    public BlueprintPromptScreen() {
        super(Component.translatable("ponderer.ui.blueprint.prompt.title"));
    }

    @Override
    protected void init() {
        setWindowSize(WIDTH, HEIGHT);
        super.init();

        // Name input
        nameField = new EditBox(this.font, guiLeft + 30, guiTop + 25, 140, 16, Component.empty());
        nameField.setTextColor(-1);
        nameField.setTextColorUneditable(-1);
        nameField.setBordered(true);
        nameField.setMaxLength(35);
        nameField.setFocused(true);
        nameField.setResponder(s -> resetOverrideState());
        setFocused(nameField);
        addRenderableWidget(nameField);

        // Button positions — save left, discard right
        saveX = guiLeft + 10;
        saveY = guiTop + 52;
        saveW = 60;
        saveH = 20;

        discardX = guiLeft + WIDTH - 70;
        discardY = guiTop + 52;
        discardW = 60;
        discardH = 20;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background panel
        new BoxElement()
            .withBackground(new Color(0xdd_000000, true))
            .gradientBorder(new Color(awaitingOverrideConfirm ? 0x60_ff6666 : 0x60_c0c0ff, true),
                            new Color(awaitingOverrideConfirm ? 0x30_ff6666 : 0x30_c0c0ff, true))
            .at(guiLeft, guiTop, 0)
            .withBounds(WIDTH, HEIGHT)
            .render(graphics);

        var font = Minecraft.getInstance().font;

        // Title
        graphics.drawCenteredString(font, this.title, guiLeft + WIDTH / 2, guiTop + 6, 0xFFFFFF);
        graphics.fill(guiLeft + 5, guiTop + 18, guiLeft + WIDTH - 5, guiTop + 19, 0x60_FFFFFF);

        // Blueprint item decoration
        GuiGameElement.of(BlueprintFeature.getCarrierStack())
                .at(guiLeft + 8, guiTop + 22, 0)
                .render(graphics);

        // Override warning
        if (awaitingOverrideConfirm) {
            Component warn = Component.translatable("ponderer.ui.blueprint.prompt.override_warn");
            int warnWidth = font.width(warn);
            graphics.drawString(font, warn, guiLeft + (WIDTH - warnWidth) / 2, guiTop + HEIGHT - 34, 0xFF6666, true);
        }

        // Simi-style save button (left)
        String saveLabel = awaitingOverrideConfirm
            ? UIText.of("ponderer.ui.blueprint.prompt.confirm_override")
            : UIText.of("ponderer.ui.blueprint.prompt.save");
        renderSimiButton(graphics, font, saveX, saveY, saveW, saveH, saveLabel, mouseX, mouseY);

        // Simi-style discard button (right)
        String discardLabel = UIText.of("ponderer.ui.blueprint.prompt.discard");
        renderSimiButton(graphics, font, discardX, discardY, discardW, discardH, discardLabel, mouseX, mouseY);
    }

    private void renderSimiButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                   int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bgColor = hovered ? 0x80_4466aa : 0x60_333366;
        int borderColor = hovered ? 0xCC_6688cc : 0x60_555588;
        graphics.fill(x, y, x + w, y + h, bgColor);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        graphics.drawString(font, label, textX, textY, hovered ? 0xFFFFFF : 0xCCCCCC);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= saveX && mouseX < saveX + saveW
                && mouseY >= saveY && mouseY < saveY + saveH) {
                doConfirm();
                return true;
            }
            if (mouseX >= discardX && mouseX < discardX + discardW
                && mouseY >= discardY && mouseY < discardY + discardH) {
                BlueprintEvents.HANDLER.discard();
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            doConfirm();
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() != null && getFocused().charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }

    private void doConfirm() {
        String name = nameField.getValue().trim();
        if (!awaitingOverrideConfirm && SceneStore.isBuiltinStructureName(name)) {
            awaitingOverrideConfirm = true;
            return;
        }
        BlueprintEvents.HANDLER.saveBlueprint(nameField.getValue());
        onClose();
    }

    private void resetOverrideState() {
        awaitingOverrideConfirm = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
