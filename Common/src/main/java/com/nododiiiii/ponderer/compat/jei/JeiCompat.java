package com.nododiiiii.ponderer.compat.jei;

import com.nododiiiii.ponderer.ui.AbstractStepEditorScreen;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.JeiAwareScreen;
import net.createmod.catnip.gui.element.ScreenElement;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Safe entry point for JEI integration.
 * All JEI class references are isolated behind isAvailable() checks,
 * so this class never triggers JEI class loading when JEI is absent.
 */
public final class JeiCompat {
    private static boolean checked = false;
    private static boolean available = false;

    public record IngredientDescriptor(String id, @Nullable String kind) {}

    private JeiCompat() {}

    /** Returns true if JEI is loaded. Safe to call at any time. */
    public static boolean isAvailable() {
        if (!checked) {
            checked = true;
            available = PondererServices.PLATFORM.isModLoaded("jei");
        }
        return available;
    }

    /** Tell JEI plugin to show overlay for this editor screen. */
    public static void setActiveEditor(AbstractStepEditorScreen screen, IdFieldMode mode) {
        if (!isAvailable()) return;
        PondererJeiPlugin.setActiveEditor(screen, mode);
    }

    /** Tell JEI plugin to show overlay for any JEI-aware screen. */
    public static void setActiveScreen(JeiAwareScreen screen, IdFieldMode mode) {
        if (!isAvailable()) return;
        PondererJeiPlugin.setActiveScreen(screen, mode);
    }

    /** Tell JEI plugin to hide the overlay. */
    public static void clearActiveEditor() {
        if (!isAvailable()) return;
        PondererJeiPlugin.clearActiveEditor();
    }

    /**
     * Returns true when PonderUI should skip JEI's default event-driven draw pass
     * and render the active overlay manually at the very end of the screen render.
     */
    public static boolean shouldRenderPonderUiOverlayManually(Screen screen) {
        if (!isAvailable()) return false;
        return PondererJeiPlugin.shouldRenderPonderUiOverlayManually(screen);
    }

    /**
     * Render JEI's current ingredient/bookmark overlays manually on top of PonderUI.
     */
    public static void renderPonderUiOverlay(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                             float partialTicks) {
        if (!isAvailable()) return;
        PondererJeiPlugin.renderPonderUiOverlay(screen, graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * Render JEI's manual tooltip layer for the active PonderUI overlay.
     */
    public static void renderPonderUiTooltips(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isAvailable()) return;
        PondererJeiPlugin.renderPonderUiTooltips(screen, graphics, mouseX, mouseY);
    }

    /**
     * Create a ScreenElement that renders the given ingredient object using JEI.
     * The ingredient can be an ItemStack, FluidStack, or any JEI-registered type.
     * Returns null if JEI is unavailable or the ingredient is not recognized.
     */
    @Nullable
    public static ScreenElement createIngredientElement(Object ingredient) {
        if (!isAvailable()) return null;
        return JeiIngredientHelper.createScreenElement(ingredient);
    }

    /**
     * Create a ScreenElement for a fluid using JEI, in a platform-agnostic way.
     * Creates the appropriate FluidStack type via the platform's SPI helper.
     * @param fluid the Minecraft Fluid
     * @param amount amount in mB
     * @return ScreenElement or null
     */
    @Nullable
    public static ScreenElement createFluidIngredientElement(net.minecraft.world.level.material.Fluid fluid, int amount) {
        if (!isAvailable()) return null;
        // Delegate to JeiIngredientHelper which will handle platform FluidStack creation
        return JeiIngredientHelper.createFluidScreenElement(fluid, amount);
    }

    /**
     * Resolve an ingredient ID string by searching JEI's registered ingredient types.
     * Used as a fallback when item/fluid registries don't contain the ID.
     * Returns a ScreenElement if a matching ingredient is found, null otherwise.
     */
    @Nullable
    public static ScreenElement resolveIngredientById(String id) {
        if (!isAvailable()) return null;
        return JeiIngredientHelper.resolveById(id);
    }

    /**
     * Resolve an ingredient ID string from a specific JEI ingredient kind.
     * The kind is the lower-case JEI ingredient family name, e.g. "item", "fluid", "chemical".
     */
    @Nullable
    public static ScreenElement resolveIngredientById(String id, @Nullable String kind) {
        if (!isAvailable()) return null;
        return JeiIngredientHelper.resolveById(id, kind);
    }

    /**
     * Resolve an ingredient ID string from a JEI ITypedIngredient click.
     * Handles all ingredient types (items, fluids, chemicals, etc.).
     * Returns the registry ID string, or null if unable to resolve.
     */
    @Nullable
    public static String resolveIngredientId(Object typedIngredient) {
        if (!isAvailable()) return null;
        return JeiIngredientHelper.resolveId(typedIngredient);
    }

    /**
     * Resolve a JEI ingredient into an {id, kind} descriptor for persistence.
     */
    @Nullable
    public static IngredientDescriptor resolveIngredientDescriptor(Object typedIngredient) {
        if (!isAvailable()) return null;
        return JeiIngredientHelper.resolveDescriptor(typedIngredient);
    }

    /**
     * Get all JEI ingredient entries for registry mapping.
     * Returns a list of {id, displayName, path, kind} string arrays for ALL ingredients
     * from ALL JEI-registered types (items, fluids, Mekanism chemicals, etc.).
     * The "kind" field indicates the ingredient type (e.g. "item", "fluid", "chemical").
     */
    public static List<String[]> getAllExtraIngredientEntries() {
        if (!isAvailable()) return List.of();
        return JeiIngredientHelper.getAllExtraEntries();
    }
}
