package com.nododiiiii.ponderer.ui.catnip;

import com.mojang.blaze3d.systems.RenderSystem;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Screen-level BoxWidget whose tooltip draw resets the render state first.
 *
 * <p>Some sidebar/action buttons render textured stencils immediately before their tooltip. When a
 * downstream renderer leaves depth or shader state in a surprising configuration, the vanilla
 * tooltip background can still appear while the font layer vanishes. Resetting the common state
 * here makes tooltip text rendering deterministic without re-enabling any list scissor.
 */
public class ScreenTooltipBoxWidget extends BoxWidget {

    public ScreenTooltipBoxWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isHovered() || getToolTip().isEmpty()) {
            return;
        }

        graphics.flush();
        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        super.renderTooltip(graphics, mouseX, mouseY, partialTicks);
        graphics.flush();
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }
}
