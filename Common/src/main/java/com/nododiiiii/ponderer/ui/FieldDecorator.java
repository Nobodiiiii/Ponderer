package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.LocalizedTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.XyzListEntry;

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
