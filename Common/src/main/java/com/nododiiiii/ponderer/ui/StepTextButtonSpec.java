package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.FormTextButtonSpec;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface StepTextButtonSpec extends FieldDecorator {

    void attach(AbstractStepEditorScreen screen, PlainTextListEntry entry);

    @Override
    default void applyText(com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
        attach((AbstractStepEditorScreen) screen, entry);
    }

    static StepTextButtonSpec action(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return action(FormTextButtonSpec.DEFAULT_WIDTH, onClick, () -> label, () -> color, tooltipText);
    }

    static StepTextButtonSpec action(int width, Runnable onClick, Supplier<String> labelGetter,
                                     IntSupplier colorGetter, @Nullable String tooltipText) {
        return (screen, entry) -> FieldDecorators.textAction(width, onClick, labelGetter, colorGetter, tooltipText)
            .applyText(screen, entry);
    }

    static StepTextButtonSpec jei(IdFieldMode mode) {
        return (screen, entry) -> FieldDecorators.jei(mode).applyText(screen, entry);
    }

    static StepTextButtonSpec blockPick(String nbtSnapshotKey) {
        return (screen, entry) -> FieldDecorators.blockPick(nbtSnapshotKey).applyText(screen, entry);
    }

    static StepTextButtonSpec nbtPick(String nbtSnapshotKey) {
        return (screen, entry) -> FieldDecorators.nbtPick(nbtSnapshotKey).applyText(screen, entry);
    }

    static StepTextButtonSpec heldItem(java.util.function.Consumer<net.minecraft.world.item.ItemStack> onItemPicked) {
        return (screen, entry) -> FieldDecorators.heldItem(onItemPicked).applyText(screen, entry);
    }
}
