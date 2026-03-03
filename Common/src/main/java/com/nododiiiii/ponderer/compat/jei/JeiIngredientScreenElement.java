package com.nododiiiii.ponderer.compat.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

/**
 * A ScreenElement that renders any JEI-registered ingredient (items, fluids,
 * Mekanism chemicals, etc.) using JEI's unified rendering API.
 */
public class JeiIngredientScreenElement implements ScreenElement {

    private final ITypedIngredient<?> typedIngredient;

    private JeiIngredientScreenElement(ITypedIngredient<?> typedIngredient) {
        this.typedIngredient = typedIngredient;
    }

    /**
     * Create a ScreenElement for the given typed ingredient.
     * Returns null if JEI runtime is not available.
     */
    @Nullable
    public static JeiIngredientScreenElement of(ITypedIngredient<?> typedIngredient) {
        if (PondererJeiPlugin.getRuntime() == null) return null;
        return new JeiIngredientScreenElement(typedIngredient);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        IJeiRuntime rt = PondererJeiPlugin.getRuntime();
        if (rt == null) return;
        renderTyped(graphics, rt.getIngredientManager(), x, y);
    }

    @SuppressWarnings("unchecked")
    private <T> void renderTyped(GuiGraphics graphics, IIngredientManager mgr, int x, int y) {
        ITypedIngredient<T> typed = (ITypedIngredient<T>) typedIngredient;
        IIngredientType<T> type = typed.getType();
        IIngredientRenderer<T> renderer = mgr.getIngredientRenderer(type);
        var poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        renderer.render(graphics, typed.getIngredient());
        poseStack.popPose();
    }
}
