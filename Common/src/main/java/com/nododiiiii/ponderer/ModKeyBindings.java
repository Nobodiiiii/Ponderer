package com.nododiiiii.ponderer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Key binding definitions (platform-agnostic).
 * Registration is done by each platform's entry point.
 */
public final class ModKeyBindings {

    public static final KeyMapping OPEN_FUNCTION_PAGE = new KeyMapping(
        "key.ponderer.open_function_page",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.ponderer"
    );

    public static final KeyMapping TRIGGER_PONDER = new KeyMapping(
        "key.ponderer.trigger_ponder",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.ponderer"
    );

    private ModKeyBindings() {}
}
