package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.XyzListEntry;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface StepXyzButtonSpec {

    void attach(AbstractStepEditorScreen screen, XyzListEntry entry);

    static StepXyzButtonSpec action(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return action(20, onClick, () -> label, () -> color, tooltipText);
    }

    static StepXyzButtonSpec action(int width, Runnable onClick, Supplier<String> labelGetter,
                                    IntSupplier colorGetter, @Nullable String tooltipText) {
        return (screen, entry) -> entry.addTrailingButton(width, onClick, labelGetter, colorGetter, tooltipText);
    }

    static StepXyzButtonSpec pick(PickState.TargetField target, boolean halfOffset) {
        return (screen, entry) -> entry.addTrailingButton(
            20,
            () -> screen.startPointPickFromButton(target, halfOffset),
            () -> "+",
            () -> 0x80FFFF,
            UIText.of("ponderer.ui.pick.tooltip"));
    }

    static StepXyzButtonSpec pick(PickState.TargetField target) {
        return pick(target, false);
    }
}
