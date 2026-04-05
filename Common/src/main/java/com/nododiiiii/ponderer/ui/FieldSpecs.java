package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.BlockPropertyListEntry;
import com.nododiiiii.ponderer.ui.catnip.ButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.DualTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.LocalizedTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.XyzListEntry;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FieldSpecs {

    private static final float DEFAULT_CHOICE_CONTROL_SCALE = 0.5f;
    private static final int HALF_WIDTH_CONTROL_MIN = 100;

    private FieldSpecs() {
    }

    public static FieldSpec text(FieldBinding<String> binding, String labelKey, @Nullable String tooltipKey,
                                 @Nullable String hintKey, int fieldWidth,
                                 Consumer<PlainTextListEntry> afterBuild,
                                 FieldDecorator... decorators) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                FieldDecorators.LangToggleSpec langToggle = findLangToggle(decorators);
                if (langToggle != null) {
                    LocalizedTextListEntry entry = screen.createLocalizedTextEntry(
                    labelKey, tooltipKey, hintKey, safeValue(binding.get()),
                    langToggle.langGetter(), langToggle.onToggle(),
                    binding::set);
                attachWidget(binding, (HintableTextFieldWidget) entry.field());
                if (fieldWidth > 0) {
                    entry.setPreferredFieldWidth(fieldWidth);
                }
                applyLocalizedDecorators(screen, entry, decorators);
                afterBuild.accept(entry);
                return;
            }

                PlainTextListEntry entry = screen.createTextEntry(
                    labelKey, tooltipKey, hintKey, safeValue(binding.get()), binding::set);
                attachWidget(binding, (HintableTextFieldWidget) entry.field());
                if (fieldWidth > 0) {
                    entry.setPreferredFieldWidth(fieldWidth);
                }
                applyTextDecorators(screen, entry, decorators);
                afterBuild.accept(entry);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                binding.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                binding.restore(snapshot);
            }
        };
    }

    public static FieldSpec text(FieldBinding<String> binding, String labelKey, @Nullable String tooltipKey,
                                 @Nullable String hintKey, int fieldWidth, FieldDecorator... decorators) {
        return text(binding, labelKey, tooltipKey, hintKey, fieldWidth, entry -> {
        }, decorators);
    }

    public static FieldSpec localizedText(FieldBinding<String> binding, String labelKey, @Nullable String tooltipKey,
                                          @Nullable String hintKey, int fieldWidth,
                                          Supplier<String> langGetter, Runnable onToggle,
                                          Consumer<LocalizedTextListEntry> afterBuild) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                LocalizedTextListEntry entry = screen.createLocalizedTextEntry(
                    labelKey, tooltipKey, hintKey, safeValue(binding.get()), langGetter, onToggle, binding::set);
                attachWidget(binding, (HintableTextFieldWidget) entry.field());
                if (fieldWidth > 0) {
                    entry.setPreferredFieldWidth(fieldWidth);
                }
                afterBuild.accept(entry);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                binding.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                binding.restore(snapshot);
            }
        };
    }

    public static FieldSpec localizedText(FieldBinding<String> binding, String labelKey, @Nullable String tooltipKey,
                                          @Nullable String hintKey, int fieldWidth,
                                          Supplier<String> langGetter, Runnable onToggle) {
        return localizedText(binding, labelKey, tooltipKey, hintKey, fieldWidth, langGetter, onToggle, entry -> {
        });
    }

    public static FieldSpec number(FieldBinding<String> binding, String labelKey, @Nullable String tooltipKey,
                                   String hintKey, int fieldWidth, @Nullable String unitKey) {
        return text(binding, labelKey, tooltipKey, hintKey, fieldWidth, entry -> {
            if ("ponderer.ui.duration".equals(labelKey)) {
                entry.setControlWidthScale(0.5f);
                entry.setMinimumControlWidth(HALF_WIDTH_CONTROL_MIN);
            }
            if (unitKey != null) {
                entry.setUnitText(() -> UIText.of(unitKey));
            }
        });
    }

    public static FieldSpec toggle(FieldBinding<Boolean> binding, String labelKey, @Nullable String tooltipKey) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                screen.createToggleEntry(
                    labelKey,
                    tooltipKey,
                    () -> Boolean.TRUE.equals(binding.get()),
                    () -> binding.set(!Boolean.TRUE.equals(binding.get())));
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                binding.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                binding.restore(snapshot);
            }
        };
    }

    public static FieldSpec toggle(String labelKey, @Nullable String tooltipKey,
                                   BooleanSupplier stateGetter, Runnable onToggle) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                screen.createToggleEntry(labelKey, tooltipKey, stateGetter, onToggle);
            }
        };
    }

    public static FieldSpec choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                   Runnable onClick, Supplier<String> labelGetter,
                                   IntSupplier colorGetter, @Nullable String buttonTooltipText,
                                   float controlWidthScale) {
        return screen -> screen.createChoiceEntry(
            labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter, buttonTooltipText, controlWidthScale);
    }

    public static FieldSpec choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                   Runnable onClick, Supplier<String> labelGetter,
                                   IntSupplier colorGetter, @Nullable Supplier<String> buttonTooltipGetter,
                                   float controlWidthScale) {
        return screen -> screen.createChoiceEntry(
            labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter, buttonTooltipGetter, controlWidthScale);
    }

    public static FieldSpec choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                   Runnable onClick, Supplier<String> labelGetter,
                                   IntSupplier colorGetter, @Nullable String buttonTooltipText) {
        return choice(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter,
            buttonTooltipText, DEFAULT_CHOICE_CONTROL_SCALE);
    }

    public static FieldSpec choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                   Runnable onClick, Supplier<String> labelGetter,
                                   IntSupplier colorGetter) {
        return choice(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter, (String) null,
            DEFAULT_CHOICE_CONTROL_SCALE);
    }

    public static FieldSpec choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                   Runnable onClick, Supplier<String> labelGetter) {
        return choice(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, () -> 0xFFFFFF, (String) null,
            DEFAULT_CHOICE_CONTROL_SCALE);
    }

    public static FieldSpec cycle(FieldBinding<Integer> binding, String labelKey, @Nullable String tooltipKey,
                                  int buttonWidth, int optionCount, Runnable afterCycle,
                                  Supplier<String> labelGetter, IntSupplier colorGetter) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                screen.createChoiceEntry(
                    labelKey,
                    tooltipKey,
                    buttonWidth,
                    () -> {
                        int current = binding.get() == null ? 0 : binding.get();
                        binding.set((current + 1 + optionCount) % optionCount);
                        afterCycle.run();
                    },
                    labelGetter,
                    colorGetter,
                    (String) null,
                    0.5f);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                binding.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                binding.restore(snapshot);
            }
        };
    }

    public static FieldSpec fullButton(String label, @Nullable String tooltipText, Runnable onClick) {
        return screen -> screen.createFullButtonEntry(label, tooltipText, onClick);
    }

    public static FieldSpec fullButton(Supplier<String> labelGetter, @Nullable Supplier<String> tooltipGetter,
                                       Runnable onClick, IntSupplier colorGetter, BooleanSupplier activeGetter) {
        return screen -> screen.createFullButtonEntry(labelGetter, tooltipGetter, onClick, colorGetter, activeGetter);
    }

    public static FieldSpec labeledButton(String labelKey, @Nullable String tooltipKey,
                                          Runnable onClick, Supplier<String> buttonLabelGetter,
                                          @Nullable Supplier<String> buttonTooltipGetter) {
        return choice(labelKey, tooltipKey, 0, onClick, buttonLabelGetter, () -> 0xFFFFFF,
            buttonTooltipGetter, 1.0f);
    }

    public static FieldSpec sectionHeader(String title) {
        return screen -> screen.createSectionHeaderEntry(title);
    }

    public static FieldSpec sectionHeader(Supplier<String> titleGetter) {
        return screen -> screen.createSectionHeaderEntry(titleGetter);
    }

    public static FieldSpec xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                @Nullable String xHint, @Nullable String yHint, @Nullable String zHint,
                                FieldDecorator... decorators) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                XyzListEntry entry = new XyzListEntry(labelKey, tooltipKey, xHint, yHint, zHint);
                attachWidget(handle.xHandle(), (HintableTextFieldWidget) entry.xField());
                attachWidget(handle.yHandle(), (HintableTextFieldWidget) entry.yField());
                attachWidget(handle.zHandle(), (HintableTextFieldWidget) entry.zField());
                applyXyzDecorators(screen, entry, decorators);
                screen.appendBuiltEntry(entry);
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

    public static FieldSpec xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                @Nullable PickState.TargetField target, boolean halfOffset) {
        FieldDecorator[] decorators = target == null
            ? new FieldDecorator[0]
            : new FieldDecorator[]{FieldDecorators.pointPick(target, halfOffset)};
        return xyz(handle, labelKey, tooltipKey, "X", "Y", "Z", decorators);
    }

    public static FieldSpec xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey,
                                PickState.TargetField target) {
        return xyz(handle, labelKey, tooltipKey, target, false);
    }

    public static FieldSpec xyz(StepXyzFieldHandle handle, String labelKey, @Nullable String tooltipKey) {
        return xyz(handle, labelKey, tooltipKey, "X", "Y", "Z");
    }

    public static FieldSpec dualText(FieldBinding<String> firstBinding, FieldBinding<String> secondBinding,
                                     String labelKey, @Nullable String tooltipKey,
                                     String firstHint, int firstWidth, String secondHint, int secondWidth) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                if (!(screen instanceof AbstractStepEditorScreen)) {
                    throw new IllegalStateException("Dual text fields require an AbstractStepEditorScreen");
                }
                DualTextListEntry entry = new DualTextListEntry(labelKey, tooltipKey, firstHint, secondHint);
                entry.setPreferredWidths(firstWidth, secondWidth);
                attachWidget(firstBinding, (HintableTextFieldWidget) entry.firstField());
                attachWidget(secondBinding, (HintableTextFieldWidget) entry.secondField());
                entry.firstField().setResponder(firstBinding::set);
                entry.secondField().setResponder(secondBinding::set);
                entry.firstField().setValue(safeValue(firstBinding.get()));
                entry.secondField().setValue(safeValue(secondBinding.get()));
                screen.appendBuiltEntry(entry);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                firstBinding.snapshot(snapshot);
                secondBinding.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                firstBinding.restore(snapshot);
                secondBinding.restore(snapshot);
            }
        };
    }

    public static FieldSpec blockProperties(KeyValueListState state, String labelKey, @Nullable String tooltipKey) {
        return new FieldSpec() {
            @Override
            public void build(AbstractDeclarativeFormScreen screen) {
                for (int i = 0; i < state.size(); i++) {
                    final int index = i;
                    KeyValueListState.KeyValue pair = state.get(i);
                    BlockPropertyListEntry entry = new BlockPropertyListEntry(
                        i == 0 ? labelKey : "",
                        i == 0 ? tooltipKey : null,
                        pair.key(),
                        pair.value(),
                        () -> {
                            state.remove(index);
                            if (screen instanceof AbstractStepEditorScreen stepScreen) {
                                stepScreen.rebuildFormPreservingState();
                            }
                        });
                    entry.keyField().setResponder(value -> state.updateKey(index, value));
                    entry.valueField().setResponder(value -> state.updateValue(index, value));
                    screen.appendBuiltEntry(entry);
                }

                ButtonListEntry addEntry = new ButtonListEntry(
                    "",
                    null,
                    40,
                    () -> {
                        state.addEmpty();
                        if (screen instanceof AbstractStepEditorScreen stepScreen) {
                            stepScreen.rebuildFormPreservingState();
                        }
                    },
                    () -> "+",
                    () -> 0x80FF80,
                    UIText.of("ponderer.ui.block_properties"));
                screen.appendBuiltEntry(addEntry);
            }

            @Override
            public void snapshot(Map<String, String> snapshot) {
                state.snapshot(snapshot);
            }

            @Override
            public void restore(Map<String, String> snapshot) {
                state.restore(snapshot);
            }
        };
    }

    private static void applyTextDecorators(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry,
                                            FieldDecorator... decorators) {
        for (FieldDecorator decorator : decorators) {
            if (decorator != null) {
                decorator.applyText(screen, entry);
            }
        }
    }

    private static void applyLocalizedDecorators(AbstractDeclarativeFormScreen screen, LocalizedTextListEntry entry,
                                                 FieldDecorator... decorators) {
        for (FieldDecorator decorator : decorators) {
            if (decorator != null) {
                decorator.applyLocalizedText(screen, entry);
            }
        }
    }

    private static void applyXyzDecorators(AbstractDeclarativeFormScreen screen, XyzListEntry entry,
                                           FieldDecorator... decorators) {
        for (FieldDecorator decorator : decorators) {
            if (decorator != null) {
                decorator.applyXyz(screen, entry);
            }
        }
    }

    @Nullable
    private static FieldDecorators.LangToggleSpec findLangToggle(FieldDecorator... decorators) {
        for (FieldDecorator decorator : decorators) {
            if (decorator != null && decorator.langToggle() != null) {
                return decorator.langToggle();
            }
        }
        return null;
    }

    private static void attachWidget(FieldBinding<String> binding, HintableTextFieldWidget widget) {
        if (binding instanceof HintableTextWidgetBinding widgetBinding) {
            widgetBinding.attach(widget);
        } else {
            String value = safeValue(binding.get());
            if (!value.equals(widget.getValue())) {
                widget.setValue(value);
            }
        }
    }

    private static String safeValue(@Nullable String value) {
        return value == null ? "" : value;
    }
}
