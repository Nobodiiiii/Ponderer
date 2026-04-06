package com.nododiiiii.ponderer;

import com.mojang.blaze3d.platform.InputConstants;
import com.nododiiiii.ponderer.mixin.PonderKeybindsAccessor;
import net.createmod.ponder.enums.PonderKeybinds;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Key binding definitions (platform-agnostic).
 * Registration is done by each platform's entry point.
 */
public final class ModKeyBindings {

    public record ManagedBinding(String translationKey, KeyMapping mapping, InputConstants.Key defaultKey) {
        public void resetToDefault() {
            mapping.setKey(defaultKey);
        }

        public boolean isDefault() {
            return mapping.isDefault();
        }
    }

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

    public static final List<KeyMapping> ALL_MAPPINGS = List.of(
        OPEN_FUNCTION_PAGE,
        TRIGGER_PONDER
    );

    public static final List<ManagedBinding> MANAGED_BINDINGS = List.of(
        managed("key.ponderer.open_function_page", OPEN_FUNCTION_PAGE, GLFW.GLFW_KEY_V),
        managed("key.ponderer.trigger_ponder", TRIGGER_PONDER, GLFW.GLFW_KEY_C),
        managed("ponderer.ui.keybindings.upstream_ponder", upstreamPonderMapping(), GLFW.GLFW_KEY_W)
    );

    private ModKeyBindings() {}

    public static List<KeyMapping> all() {
        return ALL_MAPPINGS;
    }

    public static List<ManagedBinding> managedBindings() {
        return MANAGED_BINDINGS;
    }

    private static KeyMapping upstreamPonderMapping() {
        return ((PonderKeybindsAccessor) (Object) PonderKeybinds.PONDER).ponderer$getMapping();
    }

    private static ManagedBinding managed(String translationKey, KeyMapping mapping, int defaultKeyCode) {
        return new ManagedBinding(translationKey, mapping, InputConstants.Type.KEYSYM.getOrCreate(defaultKeyCode));
    }
}
