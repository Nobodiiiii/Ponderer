package com.nododiiiii.ponderer.ui.catnip;

import com.nododiiiii.ponderer.ui.UILayoutConstants;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public abstract class AbstractDeclarativeFormScreen extends AbstractDeclarativeListScreen {

    protected static final float DEFAULT_CHOICE_CONTROL_SCALE = 0.5f;
    private final List<DeclarativeFormEntry> formEntries = new ArrayList<>();
    @Nullable
    private List<ConfigScreenList.Entry> currentEntries;

    protected AbstractDeclarativeFormScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        this(parent, scopeKey, titleKey, UILayoutConstants.EDITOR_LIST_W);
    }

    protected AbstractDeclarativeFormScreen(@Nullable Screen parent, String scopeKey, String titleKey,
                                            int preferredListWidth) {
        super(parent, scopeKey, titleKey, preferredListWidth);
    }

    @Override
    protected int getEntryHeight() {
        return 40;
    }

    @Override
    protected final void collectEntries(List<ConfigScreenList.Entry> entries) {
        currentEntries = entries;
        formEntries.clear();
        collectFormEntries(formEntries);
        for (DeclarativeFormEntry entry : formEntries) {
            entry.build(this);
        }
        currentEntries = null;
    }

    protected abstract void collectFormEntries(List<DeclarativeFormEntry> entries);

    protected final List<DeclarativeFormEntry> builtFormEntries() {
        return formEntries;
    }

    protected final PlainTextListEntry addTextFormEntry(String labelKey,
                                                        @Nullable String tooltipKey, @Nullable String hintKey,
                                                        String initialValue, Consumer<String> responder) {
        return addTextFormEntry(labelKey, tooltipKey, hintKey, initialValue, responder,
            new FormTextButtonSpec[0]);
    }

    protected final PlainTextListEntry addTextFormEntry(String labelKey,
                                                        @Nullable String tooltipKey, @Nullable String hintKey,
                                                        String initialValue, Consumer<String> responder,
                                                        FormTextButtonSpec... buttonSpecs) {
        PlainTextListEntry entry = textEntry(labelKey, tooltipKey, hintKey, initialValue, responder);
        applyTextButtonSpecs(entry, buttonSpecs);
        appendEntry(entry);
        return entry;
    }

    protected final LocalizedTextListEntry addLocalizedTextFormEntry(String labelKey, @Nullable String tooltipKey,
                                                                     @Nullable String hintKey, String initialValue,
                                                                     Supplier<String> langGetter,
                                                                     Runnable onToggle,
                                                                     Consumer<String> responder) {
        LocalizedTextListEntry entry = localizedTextEntry(
            labelKey, tooltipKey, hintKey, initialValue, langGetter, onToggle, responder);
        appendEntry(entry);
        return entry;
    }

    protected final ToggleListEntry addToggleFormEntry(String labelKey, @Nullable String tooltipKey,
                                                       BooleanSupplier stateGetter, Runnable onToggle) {
        ToggleListEntry entry = new ToggleListEntry(labelKey, tooltipKey, stateGetter, onToggle);
        appendEntry(entry);
        return entry;
    }

    protected final ButtonListEntry addChoiceFormEntry(String labelKey,
                                                       @Nullable String tooltipKey, int buttonWidth,
                                                       Runnable onClick, Supplier<String> labelGetter,
                                                       IntSupplier colorGetter, @Nullable String buttonTooltipText) {
        return addChoiceFormEntry(labelKey, tooltipKey, buttonWidth, onClick, labelGetter,
            colorGetter, buttonTooltipText, DEFAULT_CHOICE_CONTROL_SCALE);
    }

    protected final ButtonListEntry addChoiceFormEntry(String labelKey,
                                                       @Nullable String tooltipKey, int buttonWidth,
                                                       Runnable onClick, Supplier<String> labelGetter,
                                                       IntSupplier colorGetter, @Nullable String buttonTooltipText,
                                                       float controlWidthScale) {
        ButtonListEntry entry = new ButtonListEntry(
            labelKey, tooltipKey, buttonWidth, onClick, labelGetter, colorGetter, buttonTooltipText)
            .setControlWidthScale(controlWidthScale);
        appendEntry(entry);
        return entry;
    }

    protected final FullButtonListEntry addFullButtonFormEntry(String label,
                                                               @Nullable String tooltipText, Runnable onClick) {
        FullButtonListEntry entry = new FullButtonListEntry(label, tooltipText, onClick);
        appendEntry(entry);
        return entry;
    }

    protected final FullButtonListEntry addFullButtonFormEntry(Supplier<String> labelGetter,
                                                               @Nullable Supplier<String> tooltipGetter,
                                                               Runnable onClick, IntSupplier colorGetter,
                                                               BooleanSupplier activeGetter) {
        FullButtonListEntry entry = new FullButtonListEntry(labelGetter, tooltipGetter, onClick, colorGetter, activeGetter);
        appendEntry(entry);
        return entry;
    }

    protected final SectionHeaderListEntry addSectionHeaderEntry(String title) {
        SectionHeaderListEntry entry = new SectionHeaderListEntry(title);
        appendEntry(entry);
        return entry;
    }

    protected final SectionHeaderListEntry addSectionHeaderEntry(Supplier<String> titleGetter) {
        SectionHeaderListEntry entry = new SectionHeaderListEntry(titleGetter);
        appendEntry(entry);
        return entry;
    }

    protected final void applyTextButtonSpecs(PlainTextListEntry entry, FormTextButtonSpec... buttonSpecs) {
        if (buttonSpecs == null || buttonSpecs.length == 0) {
            return;
        }
        if (buttonSpecs.length > 2) {
            throw new IllegalArgumentException("Text form entries support at most 2 trailing buttons");
        }
        for (FormTextButtonSpec buttonSpec : buttonSpecs) {
            if (buttonSpec != null) {
                buttonSpec.attach(this, entry);
            }
        }
    }

    protected final void appendEntry(ConfigScreenList.Entry entry) {
        if (currentEntries == null) {
            throw new IllegalStateException("Form entries can only be appended during collectEntries()");
        }
        currentEntries.add(entry);
    }
}
