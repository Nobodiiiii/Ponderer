package com.nododiiiii.ponderer.neoforge.sticksnapshot.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DeadUiScreen extends Screen {
    private final String sourceName;

    protected DeadUiScreen(Screen source) {
        super(Component.literal("Dead UI Mirror"));
        this.sourceName = source.getClass().getSimpleName();
    }

    @Override
    protected void init() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int panelW = 196;
        int panelH = 166;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        guiGraphics.fill(x - 2, y - 2, x + panelW + 2, y + panelH + 2, 0xCC1F1F1F);
        guiGraphics.fill(x, y, x + panelW, y + panelH, 0xCC2A2A2A);

        guiGraphics.drawString(this.font, "[DEAD UI MIRROR]", x + 8, y + 8, 0xFFE7D37F, false);
        guiGraphics.drawString(this.font, "Source: " + sourceName, x + 8, y + 24, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font, "No interaction / no server sync", x + 8, y + 40, 0xFFAAAAAA, false);
        guiGraphics.drawString(this.font, "Press ESC to close", x + 8, y + 56, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Keep ESC to close the dead UI overlay.
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
