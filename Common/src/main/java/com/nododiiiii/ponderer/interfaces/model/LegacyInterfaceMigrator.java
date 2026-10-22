package com.nododiiiii.ponderer.interfaces.model;

import com.nododiiiii.ponderer.ponder.DslScene;

import java.util.List;
import java.util.Locale;

/**
 * Maps a {@code show_interface} step to an {@link InterfaceDescriptor}.
 *
 * <p>A step carrying a schema 2 {@code "interface"} object is parsed as is; otherwise the
 * v1 fields ({@code interfaceSource}, {@code block}, {@code nbt}, {@code uiId}, ...) are
 * migrated. Pure function: no game state is read.
 */
public final class LegacyInterfaceMigrator {

    private LegacyInterfaceMigrator() {
    }

    public static InterfaceDescriptor fromStep(DslScene.DslStep step) {
        if (step.interfaceSpec != null) {
            return InterfaceDescriptorJson.parse(step.interfaceSpec);
        }

        InterfaceDescriptor d = new InterfaceDescriptor();
        String source = step.interfaceSource == null ? "block" : step.interfaceSource.trim().toLowerCase(Locale.ROOT);
        boolean nbtEnabled = !Boolean.FALSE.equals(step.enableNbt);
        switch (source) {
            case "held_item" -> {
                d.origin = "legacy:held_item";
                d.targetKind = InterfaceDescriptor.TargetKind.ITEM;
                d.itemMode = InterfaceDescriptor.ItemMode.AUTO;
                if (step.item != null && !step.item.isBlank()) {
                    d.item = new InterfaceDescriptor.ItemSpec(step.item.trim(), 1, null,
                        nbtEnabled ? blankToNull(step.nbt) : null);
                }
                // v1 stored "use while sneaking" for items in whileSneaking.
                d.player.sneaking = Boolean.TRUE.equals(step.whileSneaking);
            }
            case "ui_id" -> {
                d.origin = "legacy:ui_id";
                d.targetKind = InterfaceDescriptor.TargetKind.MENU;
                d.menuType = blankToNull(step.uiId);
            }
            default -> {
                d.origin = "block".equals(source) ? "legacy:block" : "legacy:" + source;
                d.targetKind = InterfaceDescriptor.TargetKind.BLOCK;
                d.block = blankToNull(step.block);
                if (step.blockProperties != null) {
                    d.blockProperties.putAll(step.blockProperties);
                }
                d.nbt = nbtEnabled ? blankToNull(step.nbt) : null;
                // v1 stored BlockHitResult#isInside in whileSneaking for block sources (K7).
                d.hit = new InterfaceDescriptor.Hit(
                    step.direction == null || step.direction.isBlank() ? "up" : step.direction.trim().toLowerCase(Locale.ROOT),
                    point(step.point),
                    Boolean.TRUE.equals(step.whileSneaking));
            }
        }
        return d;
    }

    private static double[] point(List<Double> raw) {
        if (raw == null || raw.size() < 3 || raw.get(0) == null || raw.get(1) == null || raw.get(2) == null) {
            return null;
        }
        return new double[]{raw.get(0), raw.get(1), raw.get(2)};
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
