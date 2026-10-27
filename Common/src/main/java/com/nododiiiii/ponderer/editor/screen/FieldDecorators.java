package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.editor.capture.PickState;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.editor.screen.widgets.AbstractDeclarativeFormScreen;
import com.nododiiiii.ponderer.editor.screen.widgets.FormTextButtonSpec;
import com.nododiiiii.ponderer.editor.screen.widgets.LocalizedTextListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.PlainTextListEntry;
import com.nododiiiii.ponderer.editor.screen.widgets.PondererGuiIcons;
import com.nododiiiii.ponderer.editor.screen.widgets.XyzListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class FieldDecorators {

    public record LangToggleSpec(Supplier<String> langGetter, Runnable onToggle) {
    }

    private FieldDecorators() {
    }

    public static FieldDecorator type(FieldType type) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                entry.setSharedField(type);
            }

            @Override
            public void applyXyz(AbstractDeclarativeFormScreen screen, XyzListEntry entry) {
                entry.setSharedField(type);
            }
        };
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

    public static FieldDecorator iconAction(PondererGuiIcons icon, @Nullable String tooltipText, Runnable onClick) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                entry.addTrailingIcon(icon, onClick, tooltipText);
            }
        };
    }

    public static FieldDecorator xyzIconAction(PondererGuiIcons icon, @Nullable String tooltipText, Runnable onClick) {
        return new FieldDecorator() {
            @Override
            public void applyXyz(AbstractDeclarativeFormScreen screen, XyzListEntry entry) {
                entry.addTrailingIcon(icon, onClick, tooltipText);
            }
        };
    }

    public static FieldDecorator jei(IdFieldMode mode) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof JeiAwareScreen)) {
                    throw new IllegalStateException("JEI decorator requires screen to implement JeiAwareScreen");
                }
                entry.setSharedField(FieldType.forId(mode));
                entry.addIngredientSlot(
                    mode,
                    JeiCompat.isAvailable() ? UIText.of("ponderer.ui.jei_browse.tooltip") : null);
            }
        };
    }

    public static FieldDecorator itemTagDropdown(String fieldId) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof AbstractJeiAwareFormScreen form)) {
                    throw new IllegalStateException("Item tag dropdown requires a JEI-aware form screen");
                }
                entry.setSharedField(FieldType.ITEM_TAG);
                form.attachItemTagDropdown(fieldId, entry);
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
                entry.setSharedField(host.pointPickFieldType(target, halfOffset));
                entry.addTrailingIcon(
                    PondererGuiIcons.PICK_POSITION,
                    () -> host.startPointPickFromButton(target, halfOffset),
                    host.pointPickTooltip(target));
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
                entry.setSharedField(FieldType.BLOCK);
                entry.addTrailingIcon(
                    PondererGuiIcons.PICK_BLOCK,
                    () -> host.startNbtPickFromButton(nbtSnapshotKey, true),
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
                entry.addTrailingIcon(
                    PondererGuiIcons.PICK_NBT,
                    () -> host.startNbtPickFromButton(nbtSnapshotKey, false),
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
                entry.setSharedField(FieldType.NBT);
                entry.addTrailingIcon(
                    PondererGuiIcons.EDIT_NBT,
                    () -> host.startNbtExpandFromButton(nbtSnapshotKey),
                    UIText.of("ponderer.ui.nbt_editor.expand.tooltip"));
            }
        };
    }

    /** Every NBT input has an expanded editor; forms with scene context also support picking. */
    public static FieldDecorator nbtEditor(String snapshotKey) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (screen instanceof NbtPickButtonHost) {
                    nbtPick(snapshotKey).applyText(screen, entry);
                }
                nbtExpand(snapshotKey).applyText(screen, entry);
            }
        };
    }

    public static FieldDecorator heldItem(StepTextFieldHandle item, @Nullable StepTextFieldHandle nbt) {
        return heldItem(item, nbt, null);
    }

    public static FieldDecorator heldItem(StepTextFieldHandle item, @Nullable StepTextFieldHandle nbt,
                                           @Nullable StepTextFieldHandle count) {
        return heldItem(stack -> {
            item.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (nbt != null) nbt.setValue(itemCustomData(stack));
            if (count != null) count.setValue(String.valueOf(stack.getCount()));
        });
    }

    public static String itemCustomData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? "" : data.copyTag().toString();
    }

    public static FieldDecorator heldItem(java.util.function.Consumer<ItemStack> onItemPicked) {
        return new FieldDecorator() {
            @Override
            public void applyText(AbstractDeclarativeFormScreen screen, PlainTextListEntry entry) {
                if (!(screen instanceof HeldItemButtonHost host)) {
                    throw new IllegalStateException("Held item decorator requires screen to implement HeldItemButtonHost");
                }
                entry.addTrailingIcon(
                    PondererGuiIcons.PICK_ITEM,
                    () -> host.openInventoryPickerFromButton(entry, onItemPicked),
                    UIText.of("ponderer.ui.inventory_pick.tooltip"));
            }
        };
    }

    public static FieldDecorator sceneSelector(Runnable onClick) {
        return iconAction(PondererGuiIcons.STRUCTURE, UIText.of("ponderer.ui.scene_desc.structure_list_title"), onClick);
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
