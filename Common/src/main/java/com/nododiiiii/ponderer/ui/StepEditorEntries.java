package com.nododiiiii.ponderer.ui;

import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class StepEditorEntries {

    private StepEditorEntries() {
    }

    public static StepEditorEntry text(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                       String hint, int fieldWidth) {
        return text(handle, labelKey, tooltipKey, hint, fieldWidth, field -> {
        }, new StepTextButtonSpec[0]);
    }

    public static StepEditorEntry text(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                       String hint, int fieldWidth, StepTextButtonSpec... buttonSpecs) {
        return text(handle, labelKey, tooltipKey, hint, fieldWidth, field -> {
        }, buttonSpecs);
    }

    public static StepEditorEntry text(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                       String hint, int fieldWidth, Consumer<HintableTextFieldWidget> afterBuild,
                                       StepTextButtonSpec... buttonSpecs) {
        return wrap(FieldSpecs.text(
            handle,
            labelKey,
            tooltipKey,
            hint,
            fieldWidth,
            entry -> afterBuild.accept((HintableTextFieldWidget) entry.field()),
            buttonSpecs));
    }

    public static StepEditorEntry number(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                         String hint, int fieldWidth, @Nullable String unitKey) {
        return wrap(FieldSpecs.number(handle, labelKey, tooltipKey, hint, fieldWidth, unitKey));
    }

    public static StepEditorEntry localizedText(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                                String hint, int fieldWidth,
                                                Supplier<String> langGetter, Runnable onToggle) {
        return wrap(FieldSpecs.localizedText(handle, labelKey, tooltipKey, hint, fieldWidth, langGetter, onToggle, entry -> {
        }));
    }

    public static StepEditorEntry xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                      @Nullable PickState.TargetField target, boolean halfOffset) {
        StepXyzButtonSpec[] buttonSpecs = target == null
            ? new StepXyzButtonSpec[0]
            : new StepXyzButtonSpec[]{StepXyzButtonSpec.pick(target, halfOffset)};
        return xyz(handle, labelKey, tooltipKey, "X", "Y", "Z", buttonSpecs);
    }

    public static StepEditorEntry xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                      @Nullable String xHint, @Nullable String yHint, @Nullable String zHint,
                                      StepXyzButtonSpec... buttonSpecs) {
        return wrap(FieldSpecs.xyz(handle, labelKey, tooltipKey, xHint, yHint, zHint, buttonSpecs));
    }

    public static StepEditorEntry xyzWithHints(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                               @Nullable PickState.TargetField target, boolean halfOffset,
                                               @Nullable String xHint, @Nullable String yHint, @Nullable String zHint) {
        StepXyzButtonSpec[] buttonSpecs = target == null
            ? new StepXyzButtonSpec[0]
            : new StepXyzButtonSpec[]{StepXyzButtonSpec.pick(target, halfOffset)};
        return xyz(handle, labelKey, tooltipKey, xHint, yHint, zHint, buttonSpecs);
    }

    public static StepEditorEntry xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                      PickState.TargetField target) {
        return xyz(handle, labelKey, tooltipKey, target, false);
    }

    public static StepEditorEntry xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey) {
        return xyz(handle, labelKey, tooltipKey, null, false);
    }

    public static StepEditorEntry dualNumberFields(StepTextFieldHandle firstHandle, StepTextFieldHandle secondHandle,
                                                   String labelKey, @Nullable String tooltipKey,
                                                   String firstHint, int firstWidth,
                                                   String secondHint, int secondWidth) {
        return wrap(FieldSpecs.dualText(firstHandle, secondHandle, labelKey, tooltipKey, firstHint, firstWidth, secondHint, secondWidth));
    }

    public static StepEditorEntry toggle(String labelKey, @Nullable String tooltipKey,
                                         BooleanSupplier stateGetter, Runnable onToggle) {
        return wrap(FieldSpecs.toggle(labelKey, tooltipKey, stateGetter, onToggle));
    }

    public static StepEditorEntry cycleButton(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              Runnable onClick, Supplier<String> labelGetter,
                                              IntSupplier colorGetter) {
        return wrap(FieldSpecs.choice(
            labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter,
            null, 0.5f));
    }

    public static StepEditorEntry cycleButton(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              Runnable onClick, Supplier<String> labelGetter) {
        return cycleButton(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, () -> 0xFFFFFF);
    }

    public static StepEditorEntry blockProps(String labelKey, @Nullable String tooltipKey, IntSupplier rowCountSupplier) {
        return new StepEditorEntry() {
            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                screen.addFormBlockProps(labelKey, tooltipKey);
            }
        };
    }

    private static StepEditorEntry wrap(FieldSpec spec) {
        return new StepEditorEntry() {
            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                spec.build(screen);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                spec.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                spec.restore(snapshot);
            }
        };
    }
}
