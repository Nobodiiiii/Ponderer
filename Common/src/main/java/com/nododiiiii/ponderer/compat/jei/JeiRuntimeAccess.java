package com.nododiiiii.ponderer.compat.jei;

import mezz.jei.api.runtime.IJeiRuntime;
import javax.annotation.Nullable;

public final class JeiRuntimeAccess {
    private static IJeiRuntime runtime;
    private JeiRuntimeAccess() {}
    public static void install(@Nullable IJeiRuntime value) { runtime = value; }
    @Nullable public static IJeiRuntime getRuntime() { return runtime; }
    public static boolean isIngredientListNotDisplayed() {
        return runtime != null && !runtime.getIngredientListOverlay().isListDisplayed();
    }
}
