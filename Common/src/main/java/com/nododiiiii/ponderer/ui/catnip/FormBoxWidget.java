package com.nododiiiii.ponderer.ui.catnip;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A {@link BoxWidget} for use inside a {@link net.createmod.catnip.config.ui.ConfigScreenList} entry.
 *
 * <p>While entries render, the list keeps a GL scissor clipped to its own bounds (see
 * {@code ConfigScreenList#renderList}). A plain {@code BoxWidget} draws its tooltip inline during
 * {@code render()}, so the tooltip gets cut off at the list's left/right edges. This subclass lifts
 * the scissor around the tooltip draw — mirroring what catnip's {@code ConfigScreenList.LabeledEntry}
 * already does for the entry label tooltip — so button tooltips can extend past the form boundary.
 *
 * <p>Only use this for widgets that render <em>within</em> an active scissor (i.e. list entry
 * controls). At screen level there is no scissor to restore, and re-enabling the scissor test with a
 * stale box would clip later widgets; use a plain {@link BoxWidget} there.
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
