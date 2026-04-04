package com.nododiiiii.ponderer.ui;

import net.minecraft.world.item.ItemStack;

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
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormTextField(labelKey, tooltipKey, hint, fieldWidth));
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
            public void build(AbstractStepEditorScreen screen) {
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
            public void build(AbstractStepEditorScreen screen) {
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

    public static StepEditorEntry textWithJei(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                              String hint, int fieldWidth, IdFieldMode mode) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormTextFieldWithJei(labelKey, tooltipKey, hint, fieldWidth, mode).field());
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

    public static StepEditorEntry textWithJeiAndBlockPick(StepTextFieldHandle handle, String labelKey,
                                                          @Nullable String tooltipKey, String hint,
                                                          IdFieldMode mode, String nbtSnapshotKey) {
        return textWithJeiAndBlockPick(handle, labelKey, tooltipKey, hint, mode, nbtSnapshotKey, row -> {
        });
    }

    public static StepEditorEntry textWithJeiAndBlockPick(StepTextFieldHandle handle, String labelKey,
                                                          @Nullable String tooltipKey, String hint,
                                                          IdFieldMode mode, String nbtSnapshotKey,
                                                          Consumer<AbstractStepEditorScreen.FieldWithJeiAndBlockPick> afterBuild) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                var row = screen.addFormTextFieldWithJeiAndBlockPick(labelKey, tooltipKey, hint, mode, nbtSnapshotKey);
                handle.attach(row.field());
                afterBuild.accept(row);
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

    public static StepEditorEntry textWithJeiAndNbtPick(StepTextFieldHandle handle, String labelKey,
                                                        @Nullable String tooltipKey, String hint,
                                                        IdFieldMode mode, String nbtSnapshotKey) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormTextFieldWithJeiAndNbtPick(labelKey, tooltipKey, hint, mode, nbtSnapshotKey).field());
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

    public static StepEditorEntry textWithJeiAndHeldItem(StepTextFieldHandle handle, String labelKey,
                                                         @Nullable String tooltipKey, String hint,
                                                         IdFieldMode mode, Consumer<ItemStack> onItemPicked) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormTextFieldWithJeiAndHeldItem(labelKey, tooltipKey, hint, mode, onItemPicked).field());
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

    public static StepEditorEntry textWithButton(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                                 String hint, int fieldWidth, Runnable onClick,
                                                 Supplier<String> buttonLabelGetter, IntSupplier buttonColorGetter,
                                                 @Nullable String buttonTooltip) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormTextFieldWithButton(
                    labelKey, tooltipKey, hint, fieldWidth, onClick, buttonLabelGetter, buttonColorGetter, buttonTooltip).field());
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

    public static StepEditorEntry nbtText(StepTextFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                          String hint, int fieldWidth, String nbtSnapshotKey) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                handle.attach(screen.addFormNbtField(labelKey, tooltipKey, hint, fieldWidth, nbtSnapshotKey));
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
        return xyz(handle, labelKey, tooltipKey, target, halfOffset, null, null, null);
    }

    public static StepEditorEntry xyzWithHints(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                               @Nullable PickState.TargetField target, boolean halfOffset,
                                               @Nullable String xHint, @Nullable String yHint, @Nullable String zHint) {
        return xyz(handle, labelKey, tooltipKey, target, halfOffset, xHint, yHint, zHint);
    }

    private static StepEditorEntry xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                       @Nullable PickState.TargetField target, boolean halfOffset,
                                       @Nullable String xHint, @Nullable String yHint, @Nullable String zHint) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                var group = screen.addFormXyzRow(labelKey, tooltipKey, target, halfOffset);
                handle.xHandle().attach(group.x());
                handle.yHandle().attach(group.y());
                handle.zHandle().attach(group.z());
                if (xHint != null) {
                    group.x().setHint(xHint);
                }
                if (yHint != null) {
                    group.y().setHint(yHint);
                }
                if (zHint != null) {
                    group.z().setHint(zHint);
                }
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
                                      PickState.TargetField target) {
        return xyz(handle, labelKey, tooltipKey, target, false, null, null, null);
    }

    public static StepEditorEntry xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey) {
        return xyz(handle, labelKey, tooltipKey, null, false, null, null, null);
    }

    public static StepEditorEntry xyzWithButton(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                                Runnable onClick, Supplier<String> buttonLabelGetter,
                                                IntSupplier buttonColorGetter, @Nullable String buttonTooltip) {
        return new StepEditorEntry() {
            @Override
            public int rows() {
                return 1;
            }

            @Override
            public void build(AbstractStepEditorScreen screen) {
                var group = screen.addFormXyzRowWithButton(
                    labelKey, tooltipKey, onClick, buttonLabelGetter, buttonColorGetter, buttonTooltip);
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
            public void build(AbstractStepEditorScreen screen) {
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
            public void build(AbstractStepEditorScreen screen) {
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
            public void build(AbstractStepEditorScreen screen) {
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
            public void build(AbstractStepEditorScreen screen) {
                screen.addFormBlockProps(labelKey, tooltipKey);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                // Filled by the screen base because block props live on the screen itself.
            }
        };
    }
}
