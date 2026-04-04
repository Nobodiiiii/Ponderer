package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.JeiTextButtonHost;
import com.nododiiiii.ponderer.ui.UIText;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface FormTextButtonSpec {

    int DEFAULT_WIDTH = 20;

    void attach(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry);

    static FormTextButtonSpec action(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return action(DEFAULT_WIDTH, onClick, () -> label, () -> color, tooltipText);
    }

    static FormTextButtonSpec action(int width, Runnable onClick, Supplier<String> labelGetter,
                                     IntSupplier colorGetter, @Nullable String tooltipText) {
        return (screen, entry) -> entry.addTrailingButton(width, onClick, labelGetter, colorGetter, tooltipText);
    }

    static FormTextButtonSpec jei(IdFieldMode mode) {
        return (screen, entry) -> {
            if (!JeiCompat.isAvailable()) {
                return;
            }
            if (!(screen instanceof JeiTextButtonHost host)) {
                throw new IllegalStateException("JEI button requires screen to implement JeiTextButtonHost");
            }
            entry.addTrailingButton(
                DEFAULT_WIDTH,
                () -> host.toggleJeiForField(entry.field(), mode),
                () -> "J",
                () -> host.isJeiActiveForField(entry.field()) ? 0x55FF55 : 0xAAAAFF,
                UIText.of("ponderer.ui.jei_browse.tooltip"));
        };
    }
}
