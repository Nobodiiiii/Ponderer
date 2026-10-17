package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PonderSmallHint {
    private PonderSmallHint() {
    }

    private static float getScale() {
        var font = Minecraft.getInstance().font;
        Component label = Ponder.lang().translate(AbstractPonderScreen.SLOW_TEXT).component();
        Component referenceHint = Component.translatable(Config.DEFAULT_TEXT_PROGRESS.get()
            ? "ponderer.ui.text_progress.default_off_hint"
            : "ponderer.ui.text_progress.default_on_hint");
        return (float) font.width(label) / Math.max(1, font.width(referenceHint));
    }

    public static int getRowHeight(PonderUI ponder) {
        var button = ((PonderUIAccessor) ponder).ponderer$getSlowMode();
        // Match the space from the text-progress button's outer border to its tooltip label.
        return ponder.height - 16 - (button.getY() + button.getHeight() + 4);
    }

    public static void render(GuiGraphics graphics, Component hint, int centerX, float top, float bottom, int color) {
        var font = Minecraft.getInstance().font;
        float scale = getScale();
        float hintY = top + (bottom - top - font.lineHeight * scale) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, hintY, 500);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, hint, 0, 0, color);
        graphics.pose().popPose();
    }
}
