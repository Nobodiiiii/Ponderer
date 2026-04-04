package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.FormTextButtonSpec;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@FunctionalInterface
public interface StepTextButtonSpec {

    void attach(AbstractStepEditorScreen screen, PlainTextListEntry entry);

    static StepTextButtonSpec action(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return action(FormTextButtonSpec.DEFAULT_WIDTH, onClick, () -> label, () -> color, tooltipText);
    }

    static StepTextButtonSpec action(int width, Runnable onClick, Supplier<String> labelGetter,
                                     IntSupplier colorGetter, @Nullable String tooltipText) {
        return (screen, entry) -> FormTextButtonSpec.action(width, onClick, labelGetter, colorGetter, tooltipText)
            .attach(screen, entry);
    }

    static StepTextButtonSpec jei(IdFieldMode mode) {
        return (screen, entry) -> FormTextButtonSpec.jei(mode).attach(screen, entry);
    }

    static StepTextButtonSpec blockPick(String nbtSnapshotKey) {
        return (screen, entry) -> FormTextButtonSpec.action(
            "+",
            0x66FF66,
            UIText.of("ponderer.ui.block_pick.tooltip"),
            () -> screen.startNbtPickFromButton(nbtSnapshotKey, true))
            .attach(screen, entry);
    }

    static StepTextButtonSpec nbtPick(String nbtSnapshotKey) {
        return (screen, entry) -> FormTextButtonSpec.action(
            "+",
            0x66FF66,
            UIText.of("ponderer.ui.nbt_pick.tooltip"),
            () -> screen.startNbtPickFromButton(nbtSnapshotKey, false))
            .attach(screen, entry);
    }

    static StepTextButtonSpec heldItem(java.util.function.Consumer<net.minecraft.world.item.ItemStack> onItemPicked) {
        return (screen, entry) -> FormTextButtonSpec.action(
            "+",
            0x66FF66,
            UIText.of("ponderer.ui.held_item.tooltip"),
            () -> screen.useHeldItemFromButton(onItemPicked))
            .attach(screen, entry);
    }
}
