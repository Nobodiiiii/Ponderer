package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ui.catnip.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.ui.catnip.FormTextButtonSpec;
import com.nododiiiii.ponderer.ui.catnip.LocalizedTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import com.nododiiiii.ponderer.ui.catnip.XyzListEntry;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FieldDecorators {

    public record LangToggleSpec(Supplier<String> langGetter, Runnable onToggle) {
    }

    private FieldDecorators() {
    }

    public static FieldDecorator textAction(int width, Runnable onClick, Supplier<String> labelGetter,
                                            IntSupplier colorGetter, @Nullable String tooltipText) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                entry.addTrailingButton(width, onClick, labelGetter, colorGetter, tooltipText);
            }
        };
    }

    public static FieldDecorator textAction(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return textAction(FormTextButtonSpec.DEFAULT_WIDTH, onClick, () -> label, () -> color, tooltipText);
    }

    public static FieldDecorator xyzAction(int width, Runnable onClick, Supplier<String> labelGetter,
                                           IntSupplier colorGetter, @Nullable String tooltipText) {
        return new FieldDecorator() {
            @Override
            public void applyXyz(AbstractDeclarativeFormScreen screen, XyzListEntry entry) {
                entry.addTrailingButton(width, onClick, labelGetter, colorGetter, tooltipText);
            }
        };
    }

    public static FieldDecorator xyzAction(String label, int color, @Nullable String tooltipText, Runnable onClick) {
        return xyzAction(20, onClick, () -> label, () -> color, tooltipText);
    }

    public static FieldDecorator jei(IdFieldMode mode) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!JeiCompat.isAvailable()) {
                    return;
                }
                if (!(screen instanceof JeiTextButtonHost host)) {
                    throw new IllegalStateException("JEI decorator requires screen to implement JeiTextButtonHost");
                }
                entry.addTrailingButton(
                    FormTextButtonSpec.DEFAULT_WIDTH,
                    () -> host.toggleJeiForField((net.createmod.catnip.config.ui.HintableTextFieldWidget) entry.field(), mode),
                    () -> "J",
                    () -> host.isJeiActiveForField((net.createmod.catnip.config.ui.HintableTextFieldWidget) entry.field())
                        ? 0x55FF55
                        : 0xAAAAFF,
                    UIText.of("ponderer.ui.jei_browse.tooltip"));
            }
        };
    }

    public static FieldDecorator pointPick(PickState.TargetField target, boolean halfOffset) {
        return new FieldDecorator() {
            @Override
            public void applyXyz(AbstractDeclarativeFormScreen screen, XyzListEntry entry) {
                if (!(screen instanceof PointPickButtonHost host)) {
                    throw new IllegalStateException("Point pick decorator requires screen to implement PointPickButtonHost");
                }
                entry.addTrailingButton(
                    20,
                    () -> host.startPointPickFromButton(target, halfOffset),
                    () -> "+",
                    () -> 0x80FFFF,
                    UIText.of("ponderer.ui.pick.tooltip"));
            }
        };
    }

    public static FieldDecorator pointPick(PickState.TargetField target) {
        return pointPick(target, false);
    }

    public static FieldDecorator blockPick(String nbtSnapshotKey) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof NbtPickButtonHost host)) {
                    throw new IllegalStateException("Block pick decorator requires screen to implement NbtPickButtonHost");
                }
                entry.addTrailingButton(
                    FormTextButtonSpec.DEFAULT_WIDTH,
                    () -> host.startNbtPickFromButton(nbtSnapshotKey, true),
                    () -> "+",
                    () -> 0x66FF66,
                    UIText.of("ponderer.ui.block_pick.tooltip"));
            }
        };
    }

    public static FieldDecorator nbtPick(String nbtSnapshotKey) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof NbtPickButtonHost host)) {
                    throw new IllegalStateException("NBT pick decorator requires screen to implement NbtPickButtonHost");
                }
                entry.addTrailingButton(
                    FormTextButtonSpec.DEFAULT_WIDTH,
                    () -> host.startNbtPickFromButton(nbtSnapshotKey, false),
                    () -> "+",
                    () -> 0x66FF66,
                    UIText.of("ponderer.ui.nbt_pick.tooltip"));
            }
        };
    }

    public static FieldDecorator nbtExpand(String nbtSnapshotKey) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof NbtExpandButtonHost host)) {
                    throw new IllegalStateException("NBT expand decorator requires screen to implement NbtExpandButtonHost");
                }
                entry.addTrailingButton(
                    FormTextButtonSpec.DEFAULT_WIDTH,
                    () -> host.startNbtExpandFromButton(nbtSnapshotKey),
                    () -> "V",
                    () -> 0x80FFFF,
                    UIText.of("ponderer.ui.nbt_editor.expand.tooltip"));
            }
        };
    }

    public static FieldDecorator heldItem(java.util.function.Consumer<ItemStack> onItemPicked) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof HeldItemButtonHost host)) {
                    throw new IllegalStateException("Held item decorator requires screen to implement HeldItemButtonHost");
                }
                entry.addTrailingButton(
                    FormTextButtonSpec.DEFAULT_WIDTH,
                    () -> host.useHeldItemFromButton(onItemPicked),
                    () -> "+",
                    () -> 0x66FF66,
                    UIText.of("ponderer.ui.held_item.tooltip"));
            }
        };
    }

    public static FieldDecorator sceneSelector(Runnable onClick) {
        return textAction("S", 0x80FFFF, UIText.of("ponderer.ui.scene_desc.structure_list_title"), onClick);
    }

    public static FieldDecorator langToggle(Supplier<String> langGetter, Runnable onToggle) {
        return new FieldDecorator() {
            @Override
            public LangToggleSpec langToggle() {
                return new LangToggleSpec(langGetter, onToggle);
            }

            @Override
            public void applyLocalizedText(AbstractDeclarativeFormScreen screen, LocalizedTextListEntry entry) {
                // The localized entry already owns the language button.
            }
        };
    }
}
