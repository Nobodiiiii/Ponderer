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

    private final BooleanSupplier stateGetter;
    private final RenderElement enabled;
    private final RenderElement disabled;

    public ToggleListEntry(String labelKey, @Nullable String tooltipKey,
                           BooleanSupplier stateGetter, Runnable onToggle) {
        super(labelKey, tooltipKey, 35, onToggle, () -> "", () -> 0xFFFFFF, null);
        this.stateGetter = stateGetter;
        this.enabled = PonderGuiTextures.ICON_CONFIRM.asStencil()
            .withElementRenderer((ms, width, height, alpha) ->
                UIRenderHelper.angledGradient(ms, 0, 0, height / 2, height, width, AbstractSimiWidget.COLOR_SUCCESS))
            .at(10, 0);
        this.disabled = PonderGuiTextures.ICON_DISABLE.asStencil()
            .withElementRenderer((ms, width, height, alpha) ->
                UIRenderHelper.angledGradient(ms, 0, 0, height / 2, height, width, AbstractSimiWidget.COLOR_FAIL))
            .at(10, 0);
    }

    @Override
    public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float partialTicks) {
        button().showingElement(stateGetter.getAsBoolean() ? enabled : disabled);
        super.render(graphics, index, y, x, width, height, mouseX, mouseY, hovered, partialTicks);
    }
}
