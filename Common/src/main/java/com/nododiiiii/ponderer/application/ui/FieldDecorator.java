package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.application.ui.widgets.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.application.ui.widgets.LocalizedTextListEntry;
import com.nododiiiii.ponderer.application.ui.widgets.PlainTextListEntry;
import com.nododiiiii.ponderer.application.ui.widgets.XyzListEntry;

import javax.annotation.Nullable;

public interface FieldDecorator {

    default void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
    }

    default void applyLocalizedText(AbstractDeclarativeFormScreen screen, LocalizedTextListEntry entry) {
        applyText(screen, entry);
    }

    default void applyXyz(AbstractDeclarativeFormScreen screen, XyzListEntry entry) {
    }

    @Nullable
    default FieldDecorators.LangToggleSpec langToggle() {
        return null;
    }
}
