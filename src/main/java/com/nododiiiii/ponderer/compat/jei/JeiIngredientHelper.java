package com.nododiiiii.ponderer.compat.jei;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Internal helper that directly uses JEI API classes.
 * Only called from JeiCompat after JEI availability is confirmed.
 */
final class JeiIngredientHelper {

    private JeiIngredientHelper() {}

    /**
     * Create a ScreenElement from an arbitrary ingredient object using JEI's renderer.
     */
    @Nullable
    static ScreenElement createScreenElement(Object ingredient) {
        IJeiRuntime rt = PondererJeiPlugin.getRuntime();
        if (rt == null) return null;
        IIngredientManager mgr = rt.getIngredientManager();
        Optional<? extends ITypedIngredient<?>> typed = createTyped(mgr, ingredient);
        return typed.map(JeiIngredientScreenElement::of).orElse(null);
    }

    /**
     * Resolve an ingredient by its registry ID, searching across all JEI-registered ingredient types.
     * Tries each registered type and checks if any ingredient matches the given ID.
     */
    @Nullable
    static ScreenElement resolveById(String id) {
        IJeiRuntime rt = PondererJeiPlugin.getRuntime();
        if (rt == null) return null;
        IIngredientManager mgr = rt.getIngredientManager();
        ResourceLocation target = ResourceLocation.tryParse(id);
        if (target == null) return null;

        for (IIngredientType<?> type : mgr.getRegisteredIngredientTypes()) {
            ScreenElement result = searchType(mgr, type, target);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Resolve the registry ID from a JEI ITypedIngredient.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    static String resolveId(Object typedIngredientObj) {
        if (!(typedIngredientObj instanceof ITypedIngredient<?> typed)) return null;
        IJeiRuntime rt = PondererJeiPlugin.getRuntime();
        if (rt == null) return null;
        IIngredientManager mgr = rt.getIngredientManager();
        return resolveIdTyped(mgr, (ITypedIngredient<Object>) typed);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<ITypedIngredient<T>> createTyped(IIngredientManager mgr, Object ingredient) {
        Optional<IIngredientType<T>> typeOpt = (Optional<IIngredientType<T>>) (Optional<?>) mgr.getIngredientTypeChecked(ingredient);
        return typeOpt.flatMap(type -> mgr.createTypedIngredient(type, (T) ingredient));
    }

    private static <T> ScreenElement searchType(IIngredientManager mgr, IIngredientType<T> type, ResourceLocation target) {
        IIngredientHelper<T> helper = mgr.getIngredientHelper(type);
        Collection<T> allIngredients = mgr.getAllIngredients(type);
        for (T ingredient : allIngredients) {
            ResourceLocation loc = helper.getResourceLocation(ingredient);
            if (target.equals(loc)) {
                Optional<ITypedIngredient<T>> typed = mgr.createTypedIngredient(type, ingredient);
                if (typed.isPresent()) {
                    return JeiIngredientScreenElement.of(typed.get());
                }
            }
        }
        return null;
    }

    private static <T> String resolveIdTyped(IIngredientManager mgr, ITypedIngredient<T> typed) {
        IIngredientHelper<T> helper = mgr.getIngredientHelper(typed.getType());
        ResourceLocation loc = helper.getResourceLocation(typed.getIngredient());
        return loc != null ? loc.toString() : null;
    }

    /**
     * Collect ALL JEI ingredient entries (items, fluids, chemicals, etc.).
     * Returns a list of {id, displayName, path, kind} string arrays.
     */
    static List<String[]> getAllExtraEntries() {
        IJeiRuntime rt = PondererJeiPlugin.getRuntime();
        if (rt == null) return List.of();
        IIngredientManager mgr = rt.getIngredientManager();
        List<String[]> result = new ArrayList<>();

        for (IIngredientType<?> type : mgr.getRegisteredIngredientTypes()) {
            collectEntriesForType(mgr, type, result);
        }
        return result;
    }

    private static String inferKind(IIngredientType<?> type) {
        String className = type.getIngredientClass().getSimpleName();
        if (className.contains("ItemStack")) return "item";
        if (className.contains("FluidStack")) return "fluid";
        return className.toLowerCase(Locale.ROOT).replace("stack", "");
    }

    private static <T> void collectEntriesForType(IIngredientManager mgr, IIngredientType<T> type,
                                                    List<String[]> result) {
        IIngredientHelper<T> helper = mgr.getIngredientHelper(type);
        String kind = inferKind(type);
        Collection<T> allIngredients = mgr.getAllIngredients(type);
        for (T ingredient : allIngredients) {
            ResourceLocation loc = helper.getResourceLocation(ingredient);
            if (loc == null) continue;
            String id = loc.toString();
            String displayName = helper.getDisplayName(ingredient).toLowerCase(Locale.ROOT);
            String path = loc.getPath().toLowerCase(Locale.ROOT);
            result.add(new String[]{id, displayName, path, kind});
        }
    }
}
