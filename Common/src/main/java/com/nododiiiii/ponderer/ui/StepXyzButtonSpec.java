package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.XyzListEntry;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface StepXyzButtonSpec extends FieldDecorator {

    void attach(AbstractStepEditorScreen screen, XyzListEntry entry);

    @Override
    default void applyXyz(AbstractStepEditorScreen screen, XyzListEntry entry) {
        attach(screen, entry);
    }

    static StepXyzButtonSpec action(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return action(20, onClick, () -> label, () -> color, tooltipText);
    }

    static StepXyzButtonSpec action(int width, Runnable onClick, Supplier<String> labelGetter,
                                    IntSupplier colorGetter, @Nullable String tooltipText) {
        return (screen, entry) -> FieldDecorators.xyzAction(width, onClick, labelGetter, colorGetter, tooltipText)
            .applyXyz(screen, entry);
    }

    static StepXyzButtonSpec pick(PickState.TargetField target, boolean halfOffset) {
        return (screen, entry) -> FieldDecorators.pointPick(target, halfOffset).applyXyz(screen, entry);
    }

    static StepXyzButtonSpec pick(PickState.TargetField target) {
        return pick(target, false);
    }
}
