package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.editor.api.IngredientPresentation;
import net.createmod.catnip.gui.element.ScreenElement;

/** Product installation supplies JEI rendering and visibility hints to the editor. */
public final class EditorJeiIngredients implements IngredientPresentation {
    @Override public ScreenElement resolve(String id, String kind) { return JeiCompat.resolveIngredientById(id, kind); }
    @Override public String visibilityHint() { return JeiCompat.getIngredientListVisibilityHint(); }
}
