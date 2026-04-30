package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.RenderElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public class ToggleListEntry extends ButtonListEntry {

    private static final int TOGGLE_ICON_SIZE = 16;

    private final BooleanSupplier stateGetter;
    private final RenderElement enabled;
    private final RenderElement disabled;

    public ToggleListEntry(String labelKey, @Nullable String tooltipKey,
                           BooleanSupplier stateGetter, Runnable onToggle) {
        super(labelKey, tooltipKey, 35, onToggle, () -> "", () -> 0xFFFFFF, (String) null);
        this.stateGetter = stateGetter;
        this.enabled = PonderGuiTextures.ICON_CONFIRM.asStencil()
            .withElementRenderer((ms, width, height, alpha) ->
                UIRenderHelper.angledGradient(ms, 0, 0, height / 2, height, width, AbstractSimiWidget.COLOR_SUCCESS));
        this.disabled = PonderGuiTextures.ICON_DISABLE.asStencil()
            .withElementRenderer((ms, width, height, alpha) ->
                UIRenderHelper.angledGradient(ms, 0, 0, height / 2, height, width, AbstractSimiWidget.COLOR_FAIL));
    }

    @Override
    protected int getRenderedButtonWidth(int controlWidth) {
        return Math.min(buttonWidth, controlWidth);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        RenderElement icon = stateGetter.getAsBoolean() ? enabled : disabled;
        icon.at(
            Math.max(0, (button().getWidth() - TOGGLE_ICON_SIZE) / 2),
            Math.max(0, (button().getHeight() - TOGGLE_ICON_SIZE) / 2));
        button().showingElement(icon);
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
    }
}
