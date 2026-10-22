package com.nododiiiii.ponderer.compat.jei;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A ScreenElement that renders any JEI-registered ingredient (items, fluids,
 * Mekanism chemicals, etc.) using JEI's unified rendering API.
 */
public class JeiIngredientScreenElement implements ScreenElement {

    @Nullable
    private static final MethodHandle DRAW_TOOLTIP = findNativeTooltip();

    private final ITypedIngredient<?> typedIngredient;
    @Nullable
    private IngredientView ingredientView;

    private record IngredientView(IJeiRuntime runtime, IDrawable drawable, IRecipeSlotDrawable slot) {}

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
        getIngredientView(rt).drawable().draw(graphics, x, y);
    }

    public boolean renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        IJeiRuntime rt = PondererJeiPlugin.getRuntime();
        if (rt == null) return false;
        IRecipeSlotDrawable slot = getIngredientView(rt).slot();
        if (slot.getDisplayedIngredient().isEmpty()) return false;
        if (DRAW_TOOLTIP == null) {
            return renderLegacyTooltip(graphics, rt.getIngredientManager(), slot, mouseX, mouseY);
        }
        try {
            DRAW_TOOLTIP.invokeExact(slot, graphics, mouseX, mouseY);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to draw JEI ingredient tooltip", e);
        }
        return true;
    }

    private IngredientView getIngredientView(IJeiRuntime runtime) {
        if (ingredientView == null || ingredientView.runtime() != runtime) {
            // Keep explicitly selected ingredients visible even if hidden in JEI's list.
            IRecipeSlotDrawable slot = runtime.getRecipeManager().createRecipeSlotDrawable(
                RecipeIngredientRole.RENDER_ONLY, List.of(Optional.of(typedIngredient)), Set.of(0), 0);
            IDrawable drawable = runtime.getJeiHelpers().getGuiHelper().createDrawableIngredient(typedIngredient);
            ingredientView = new IngredientView(runtime, drawable, slot);
        }
        return ingredientView;
    }

    @Nullable
    private static MethodHandle findNativeTooltip() {
        try {
            // Added in JEI 19.22. Use a class literal so Fabric remaps GuiGraphics.
            return MethodHandles.publicLookup().findVirtual(IRecipeSlotDrawable.class, "drawTooltip",
                MethodType.methodType(void.class, GuiGraphics.class, int.class, int.class));
        } catch (NoSuchMethodException e) {
            return null; // JEI 19.21
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access JEI's public tooltip API", e);
        }
    }

    // JEI 19.21 exposes tooltip content but has no public tooltip drawing method.
    @SuppressWarnings("removal")
    private boolean renderLegacyTooltip(GuiGraphics graphics, IIngredientManager mgr,
                                        IRecipeSlotDrawable slot, int mouseX, int mouseY) {
        var lines = slot.getTooltip();
        if (lines.isEmpty()) return false;
        graphics.renderComponentTooltip(getFont(mgr, typedIngredient), lines, mouseX, mouseY);
        return true;
    }

    private static <T> Font getFont(IIngredientManager mgr, ITypedIngredient<T> typed) {
        return mgr.getIngredientRenderer(typed.getType())
            .getFontRenderer(Minecraft.getInstance(), typed.getIngredient());
    }
}
