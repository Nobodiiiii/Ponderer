package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

public class JeiFieldController {

    private final BiConsumer<JeiAwareScreen, IdFieldMode> activateAction;
    private boolean active = false;
    @Nullable
    private HintableTextFieldWidget targetField = null;

    public JeiFieldController(BiConsumer<JeiAwareScreen, IdFieldMode> activateAction) {
        this.activateAction = activateAction;
    }

    @Nullable
    public HintableTextFieldWidget targetField() {
        return targetField;
    }

    public boolean isActiveFor(HintableTextFieldWidget field) {
        return active && targetField == field;
    }

    public void toggle(JeiAwareScreen host, HintableTextFieldWidget field, IdFieldMode mode) {
        if (!JeiCompat.isAvailable()) {
            return;
        }
        if (active && targetField == field) {
            deactivate();
            return;
        }
        active = true;
        targetField = field;
        activateAction.accept(host, mode);
    }

    public void deactivate() {
        active = false;
        targetField = null;
        JeiCompat.clearActiveEditor();
    }

    public boolean isActive() {
        return active;
    }
}
