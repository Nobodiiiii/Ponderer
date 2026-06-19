package com.nododiiiii.ponderer;

import java.util.function.BooleanSupplier;

/**
 * Captures server feature toggles at server start.
 * Server config changes are intentionally not applied during the same run.
 */
public final class FeatureAvailability {

    private static boolean captured;
    private static boolean blueprintEnabled = true;
    private static boolean projectorEnabled = true;

    private FeatureAvailability() {
    }

    public static void captureFromConfig() {
        blueprintEnabled = readBlueprintConfig();
        projectorEnabled = readProjectorConfig();
        captured = true;
    }

    public static void captureFromServer(boolean enableBlueprint, boolean enableProjector) {
        blueprintEnabled = enableBlueprint;
        projectorEnabled = enableProjector;
        captured = true;
    }

    public static void reset() {
        captured = false;
        blueprintEnabled = true;
        projectorEnabled = true;
    }

    public static boolean isBlueprintEnabled() {
        return captured ? blueprintEnabled : readBlueprintConfig();
    }

    public static boolean isBlueprintEnabled(BooleanSupplier uncapturedReader) {
        return captured ? blueprintEnabled : readUncaptured(uncapturedReader);
    }

    public static boolean isProjectorEnabled() {
        return captured ? projectorEnabled : readProjectorConfig();
    }

    public static boolean isProjectorEnabled(BooleanSupplier uncapturedReader) {
        return captured ? projectorEnabled : readUncaptured(uncapturedReader);
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
