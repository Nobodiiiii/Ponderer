package com.nododiiiii.ponderer.editor.api;

import javax.annotation.Nullable;
import net.createmod.catnip.gui.element.ScreenElement;

/** Optional ingredient presentation while dragging bindings in the editor. */
public interface IngredientPresentation {
    @Nullable default ScreenElement resolve(String id, @Nullable String kind) { return null; }
    @Nullable default String visibilityHint() { return null; }
    IngredientPresentation NONE = new IngredientPresentation() {};
}
