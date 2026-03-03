package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Generic JEI ghost ingredient handler that works with any Screen implementing JeiAwareScreen.
 */
public class JeiAwareGhostHandler<T extends Screen & JeiAwareScreen> implements IGhostIngredientHandler<T> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        IdFieldMode mode = PondererJeiPlugin.getActiveMode();
        if (mode == null) return List.of();

        HintableTextFieldWidget targetField = gui.getJeiTargetField();
        if (targetField == null) return List.of();

        // INGREDIENT mode: accept any JEI ingredient type
        if (mode == IdFieldMode.INGREDIENT) {
            String id = JeiIngredientHelper.resolveId(ingredient);
            if (id == null) return List.of();

            Rect2i area = new Rect2i(
                    targetField.getX(), targetField.getY(),
                    targetField.getWidth(), targetField.getHeight()
            );

            return List.of(new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ing) {
                    targetField.setValue(id);
                }
            });
        }

        // Other modes: only accept items
        Optional<ItemStack> stackOpt = ingredient.getItemStack();
        if (stackOpt.isEmpty()) return List.of();

        if (!StepEditorGhostHandler.isCompatible(stackOpt.get(), mode)) return List.of();

        Rect2i area = new Rect2i(
                targetField.getX(), targetField.getY(),
                targetField.getWidth(), targetField.getHeight()
        );

        return List.of(new Target<I>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ing) {
                if (ing instanceof ItemStack stack) {
                    String id = StepEditorGhostHandler.resolveId(stack, mode);
                    if (id != null) {
                        targetField.setValue(id);
                    }
                }
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
