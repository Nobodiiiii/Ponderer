package com.nododiiiii.ponderer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ModKeyBindings {

    public static final KeyMapping OPEN_FUNCTION_PAGE = new KeyMapping(
        "key.ponderer.open_function_page",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.ponderer"
    );

    private ModKeyBindings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_FUNCTION_PAGE);
    }
}
