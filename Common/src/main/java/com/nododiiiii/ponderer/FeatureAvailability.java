package com.nododiiiii.ponderer;

import java.util.function.BooleanSupplier;

/**
 * Captures server feature toggles at server start.
 * Server config changes are intentionally not applied during the same run.
 */
public final class FeatureAvailability {

    public record Snapshot(boolean blueprintEnabled, boolean projectorEnabled) {}
    private static volatile Snapshot captured;

    private FeatureAvailability() {
    }

    public static void captureFromConfig() {
        captured = new Snapshot(readBlueprintConfig(), readProjectorConfig());
    }

    public static void captureFromServer(boolean enableBlueprint, boolean enableProjector) {
        captured = new Snapshot(enableBlueprint, enableProjector);
    }

    public static void reset() { captured = null; }

    public static boolean isBlueprintEnabled() {
        Snapshot snapshot = captured;
        return snapshot != null ? snapshot.blueprintEnabled() : readBlueprintConfig();
    }

    public static boolean isBlueprintEnabled(BooleanSupplier uncapturedReader) {
        Snapshot snapshot = captured;
        return snapshot != null ? snapshot.blueprintEnabled() : readUncaptured(uncapturedReader);
    }

    public static boolean isProjectorEnabled() {
        Snapshot snapshot = captured;
        return snapshot != null ? snapshot.projectorEnabled() : readProjectorConfig();
    }

    public static boolean isProjectorEnabled(BooleanSupplier uncapturedReader) {
        Snapshot snapshot = captured;
        return snapshot != null ? snapshot.projectorEnabled() : readUncaptured(uncapturedReader);
    }

    private static boolean readUncaptured(BooleanSupplier reader) {
        try {
            return reader.getAsBoolean();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean readBlueprintConfig() {
        try {
            return Config.ENABLE_BLUEPRINT_ITEM.get();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean readProjectorConfig() {
        try {
            return Config.ENABLE_PROJECTOR.get();
        } catch (Exception e) {
            return true;
        }
    }
}
