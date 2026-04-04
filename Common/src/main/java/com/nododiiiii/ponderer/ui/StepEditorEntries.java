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
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                HintableTextFieldWidget field = screen.addFormTextField(labelKey, tooltipKey, hint, fieldWidth, buttonSpecs);
                handle.attach(field);
                afterBuild.accept(field);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                handle.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                handle.restore(snapshot);
            }
        };
    }

    public static StepEditorEntry number(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                         String hint, int fieldWidth, @Nullable String unitKey) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormNumberField(labelKey, tooltipKey, hint, fieldWidth, unitKey));
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                handle.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                handle.restore(snapshot);
            }
        };
    }

    public static StepEditorEntry localizedText(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                                String hint, int fieldWidth,
                                                Supplier<String> langGetter, Runnable onToggle) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormTextFieldWithLang(labelKey, tooltipKey, hint, fieldWidth, langGetter, onToggle).field());
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                handle.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                handle.restore(snapshot);
            }
        };
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
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                var group = screen.addFormXyzRow(labelKey, tooltipKey, xHint, yHint, zHint, buttonSpecs);
                handle.xHandle().attach(group.x());
                handle.yHandle().attach(group.y());
                handle.zHandle().attach(group.z());
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                handle.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                handle.restore(snapshot);
            }
        };
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
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                var pair = screen.addFormDualNumberField(labelKey, tooltipKey, firstHint, firstWidth, secondHint, secondWidth);
                firstHandle.attach(pair.first());
                secondHandle.attach(pair.second());
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                firstHandle.snapshot(snapshot);
                secondHandle.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                firstHandle.restore(snapshot);
                secondHandle.restore(snapshot);
            }
        };
    }

    public static StepEditorEntry toggle(String labelKey, @Nullable String tooltipKey,
                                         BooleanSupplier stateGetter, Runnable onToggle) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                screen.addFormToggle(labelKey, tooltipKey, stateGetter, onToggle);
            }
        };
    }

    public static StepEditorEntry cycleButton(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              Runnable onClick, Supplier<String> labelGetter,
                                              IntSupplier colorGetter) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                screen.addFormCycleButton(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter);
            }
        };
    }

    public static StepEditorEntry cycleButton(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              Runnable onClick, Supplier<String> labelGetter) {
        return cycleButton(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, () -> 0xFFFFFF);
    }

    public static StepEditorEntry blockProps(String labelKey, @Nullable String tooltipKey, IntSupplier rowCountSupplier) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return rowCountSupplier.getAsInt();
            }

            @Override
            public void buildStep(AbstractStepEditorScreen screen) {
                screen.addFormBlockProps(labelKey, tooltipKey);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                // Filled by the screen base because block props live on the screen itself.
            }
        };
    }
}
