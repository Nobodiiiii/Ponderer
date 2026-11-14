package com.nododiiiii.ponderer.runtime.interfaces;

import com.nododiiiii.ponderer.runtime.spi.InterfaceEditingExtension;
import com.nododiiiii.ponderer.runtime.spi.IngredientResolver;

/** Optional UI capabilities. Installing a module returns the handle used to uninstall it. */
public final class InterfaceExtensions {
    private static InterfaceEditingExtension editing = InterfaceEditingExtension.NONE;
    private static IngredientResolver ingredients = IngredientResolver.NONE;
    private static com.nododiiiii.ponderer.runtime.spi.InterfaceMenuAdapter menus = (session, descriptor, nextId, report) -> null;
    public static com.nododiiiii.ponderer.runtime.spi.InterfaceMenuAdapter menus() { return menus; }
    public static AutoCloseable installMenus(com.nododiiiii.ponderer.runtime.spi.InterfaceMenuAdapter adapter) {
        menus = adapter;
        return () -> { if (menus == adapter) menus = (session, descriptor, nextId, report) -> null; };
    }
    private InterfaceExtensions() {}
    public static InterfaceEditingExtension editing() { return editing; }
    public static IngredientResolver ingredients() { return ingredients; }
    public static AutoCloseable installEditing(InterfaceEditingExtension extension) {
        editing = extension;
        return () -> { if (editing == extension) editing = InterfaceEditingExtension.NONE; };
    }
    public static AutoCloseable installIngredients(IngredientResolver extension) {
        ingredients = extension;
        return () -> { if (ingredients == extension) ingredients = IngredientResolver.NONE; };
    }
}
