package com.nododiiiii.ponderer.application.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;

public interface HintableTextWidgetBinding extends FieldBinding<String> {

    void attach(HintableTextFieldWidget widget);

    @Nullable
    HintableTextFieldWidget widget();
}
