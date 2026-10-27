package com.nododiiiii.ponderer.application.compat.jei;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.application.ui.IdFieldMode;
import com.nododiiiii.ponderer.application.ui.JeiAwareScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic JEI ghost ingredient handler that works with any Screen implementing JeiAwareScreen.
 */
public class JeiAwareGhostHandler<T extends Screen & JeiAwareScreen> implements IGhostIngredientHandler<T> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        for (JeiAwareScreen.IngredientTarget target : gui.getJeiIngredientTargets()) {
            String id = target.mode() == IdFieldMode.INGREDIENT
                ? JeiCompat.resolveIngredientId(ingredient)
                : ingredient.getItemStack().map(target.mode()::resolveItemId).orElse(null);
            if (id == null) {
                continue;
            }
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return target.area();
                }

                @Override
                public void accept(I ing) {
                    target.field().setValue(id);
                    ingredient.getItemStack().ifPresent(stack -> gui.onJeiItemDropped(target.field(), stack));
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

}
