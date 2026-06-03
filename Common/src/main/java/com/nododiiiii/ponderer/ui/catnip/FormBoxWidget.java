package com.nododiiiii.ponderer.ui.catnip;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A BoxWidget that can render tooltips outside a ConfigScreenList scissor.
 */
public class FormBoxWidget extends BoxWidget {

    public FormBoxWidget() {
        super();
    }

    public FormBoxWidget(int x, int y) {
        super(x, y);
    }

    public FormBoxWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!isHovered() || getToolTip().isEmpty()) {
            return;
        }

        RenderSystem.disableScissor();
        graphics.pose().pushPose();
        super.renderTooltip(graphics, mouseX, mouseY, partialTicks);
        graphics.flush();
        graphics.pose().popPose();
        GlStateManager._enableScissorTest();
    }
}
