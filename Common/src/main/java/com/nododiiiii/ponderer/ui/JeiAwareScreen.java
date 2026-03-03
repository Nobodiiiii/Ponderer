package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;

/**
 * Interface for screens that support JEI item browsing integration.
 * Both {@link AbstractStepEditorScreen} and {@link CommandParamScreen} implement this.
 */
public interface JeiAwareScreen {

    /** Returns the text field that should receive the JEI-selected ID. */
    @Nullable
    HintableTextFieldWidget getJeiTargetField();

    /** Deactivate JEI overlay (called after successful selection). */
    void deactivateJei();

    /** Show an error message when an incompatible item is clicked in JEI. */
    void showJeiIncompatibleWarning(IdFieldMode mode);

    /** Public getters for JEI compat layer (IGuiProperties). */
    int getGuiLeft();
    int getGuiTop();
    int getGuiWidth();
    int getGuiHeight();
}
