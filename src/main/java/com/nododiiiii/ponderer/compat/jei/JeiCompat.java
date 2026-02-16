package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import net.minecraftforge.fml.ModList;

/**
 * Safe entry point for JEI integration.
 * All JEI class references are isolated behind isAvailable() checks,
 * so this class never triggers JEI class loading when JEI is absent.
 */
public final class JeiCompat {
    private static boolean checked = false;
    private static boolean available = false;

    private JeiCompat() {}

    /** Returns true if JEI is loaded. Safe to call at any time. */
    public static boolean isAvailable() {
        if (!checked) {
            checked = true;
            available = ModList.get().isLoaded("jei");
        }
        return available;
    }

    /** Tell JEI plugin to show overlay for this editor screen. */
    public static void setActiveEditor(AbstractStepEditorScreen screen, IdFieldMode mode) {
        if (!isAvailable()) return;
        PondererJeiPlugin.setActiveEditor(screen, mode);
    }

    /** Tell JEI plugin to hide the overlay. */
    public static void clearActiveEditor() {
        if (!isAvailable()) return;
        PondererJeiPlugin.clearActiveEditor();
    }
}
