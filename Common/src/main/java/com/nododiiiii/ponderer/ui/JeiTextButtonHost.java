package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;

public interface JeiTextButtonHost extends JeiAwareScreen {

    void toggleJeiForField(HintableTextFieldWidget field, IdFieldMode mode);

    boolean isJeiActiveForField(HintableTextFieldWidget field);
}
