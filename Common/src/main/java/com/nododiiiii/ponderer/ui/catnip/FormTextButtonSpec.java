package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ui.FieldDecorator;
import com.nododiiiii.ponderer.ui.FieldDecorators;
import com.nododiiiii.ponderer.ui.IdFieldMode;
import com.nododiiiii.ponderer.ui.JeiTextButtonHost;
import com.nododiiiii.ponderer.ui.UIText;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface FormTextButtonSpec extends FieldDecorator {

    int DEFAULT_WIDTH = 20;

    void attach(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry);

    @Override
    default void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
        attach(screen, entry);
    }

    static FormTextButtonSpec action(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return action(DEFAULT_WIDTH, onClick, () -> label, () -> color, tooltipText);
    }

    static FormTextButtonSpec action(int width, Runnable onClick, Supplier<String> labelGetter,
                                     IntSupplier colorGetter, @Nullable String tooltipText) {
        FieldDecorator decorator = FieldDecorators.textAction(width, onClick, labelGetter, colorGetter, tooltipText);
        return decorator::applyText;
    }

    static FormTextButtonSpec jei(IdFieldMode mode) {
        return FieldDecorators.jei(mode)::applyText;
    }
}
