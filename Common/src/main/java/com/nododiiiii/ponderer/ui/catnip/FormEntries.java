package com.nododiiiii.ponderer.ui.catnip;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FormEntries {

    private FormEntries() {
    }

    public static DeclarativeFormEntry text(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                                            String initialValue, Consumer<String> responder,
                                            FormTextButtonSpec... buttonSpecs) {
        return text(labelKey, tooltipKey, hintKey, initialValue, responder, entry -> {
        }, buttonSpecs);
    }

    public static DeclarativeFormEntry text(String labelKey, @Nullable String tooltipKey, @Nullable String hintKey,
                                            String initialValue, Consumer<String> responder,
                                            Consumer<PlainTextListEntry> afterBuild,
                                            FormTextButtonSpec... buttonSpecs) {
        return screen -> afterBuild.accept(
            screen.addTextFormEntry(labelKey, tooltipKey, hintKey, initialValue, responder, buttonSpecs));
    }

    public static DeclarativeFormEntry localizedText(String labelKey, @Nullable String tooltipKey,
                                                     @Nullable String hintKey, String initialValue,
                                                     Supplier<String> langGetter, Runnable onToggle,
                                                     Consumer<String> responder) {
        return localizedText(labelKey, tooltipKey, hintKey, initialValue, langGetter, onToggle, responder, entry -> {
        });
    }

    public static DeclarativeFormEntry localizedText(String labelKey, @Nullable String tooltipKey,
                                                     @Nullable String hintKey, String initialValue,
                                                     Supplier<String> langGetter, Runnable onToggle,
                                                     Consumer<String> responder,
                                                     Consumer<LocalizedTextListEntry> afterBuild) {
        return screen -> afterBuild.accept(
            screen.addLocalizedTextFormEntry(labelKey, tooltipKey, hintKey, initialValue, langGetter, onToggle, responder));
    }

    public static DeclarativeFormEntry toggle(String labelKey, @Nullable String tooltipKey,
                                              BooleanSupplier stateGetter, Runnable onToggle) {
        return screen -> screen.addToggleFormEntry(labelKey, tooltipKey, stateGetter, onToggle);
    }

    public static DeclarativeFormEntry choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              Runnable onClick, Supplier<String> labelGetter,
                                              IntSupplier colorGetter, @Nullable String buttonTooltipText) {
        return choice(labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter,
            buttonTooltipText, AbstractDeclarativeFormScreen.DEFAULT_CHOICE_CONTROL_SCALE);
    }

    public static DeclarativeFormEntry choice(String labelKey, @Nullable String tooltipKey, int buttonWidth,
                                              Runnable onClick, Supplier<String> labelGetter,
                                              IntSupplier colorGetter, @Nullable String buttonTooltipText,
                                              float controlWidthScale) {
        return screen -> screen.addChoiceFormEntry(
            labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter, buttonTooltipText, controlWidthScale);
    }

    public static DeclarativeFormEntry fullButton(String label, @Nullable String tooltipText, Runnable onClick) {
        return screen -> screen.addFullButtonFormEntry(label, tooltipText, onClick);
    }

    public static DeclarativeFormEntry fullButton(Supplier<String> labelGetter,
                                                  @Nullable Supplier<String> tooltipGetter,
                                                  Runnable onClick, IntSupplier colorGetter,
                                                  BooleanSupplier activeGetter) {
        return screen -> screen.addFullButtonFormEntry(labelGetter, tooltipGetter, onClick, colorGetter, activeGetter);
    }

    public static DeclarativeFormEntry sectionHeader(String title) {
        return screen -> screen.addSectionHeaderEntry(title);
    }

    public static DeclarativeFormEntry sectionHeader(Supplier<String> titleGetter) {
        return screen -> screen.addSectionHeaderEntry(titleGetter);
    }
}
