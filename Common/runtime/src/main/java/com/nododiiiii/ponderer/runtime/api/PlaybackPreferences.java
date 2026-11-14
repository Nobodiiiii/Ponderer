package com.nododiiiii.ponderer.runtime.api;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Host-provided viewer preference, independent of the product config schema. */
public final class PlaybackPreferences {
    private static BooleanSupplier textProgress = () -> false;
    private static Consumer<Boolean> saveTextProgress = value -> {};
    private PlaybackPreferences() {}
    public static boolean textProgress() { return textProgress.getAsBoolean(); }
    public static void textProgress(boolean enabled) { saveTextProgress.accept(enabled); }
    public static AutoCloseable install(BooleanSupplier read, Consumer<Boolean> save) {
        textProgress = read;
        saveTextProgress = save;
        return () -> { if (textProgress == read) { textProgress = () -> false; saveTextProgress = value -> {}; } };
    }
}
