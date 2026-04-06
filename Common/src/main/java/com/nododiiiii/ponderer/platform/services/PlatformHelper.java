package com.nododiiiii.ponderer.platform.services;

import com.nododiiiii.ponderer.ponder.DslScene;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Platform abstraction for loader-specific utilities.
 */
public interface PlatformHelper {

    /** Returns the current platform: "forge", "fabric", or "neoforge". */
    String getPlatformName();

    /** Whether this is a client-side environment. */
    boolean isClient();

    /** Whether we're running in a development environment. */
    boolean isDevelopmentEnvironment();

    /** Check if a mod is loaded. */
    boolean isModLoaded(String modId);

    /** Get the game directory (e.g., .minecraft). */
    Path getGameDir();

    /** Get the config directory. */
    Path getConfigDir();

    /** Execute a runnable only on the client side (avoids class-loading issues). */
    void executeOnClient(Supplier<Runnable> runnable);

    /**
     * Open a mirrored block interface for a scene step on the client.
     * Default is no-op for platforms without this feature.
     */
    default void showInterfaceStep(DslScene.DslStep step) {
    }

    /**
     * Simulate a click on the mirrored interface for a scene step.
     * Default is no-op for platforms without this feature.
     */
    default void clickInterfaceStep(DslScene.DslStep step) {
    }

    /**
     * Close the currently mirrored interface, if any.
     * Default is no-op for platforms without this feature.
     */
    default void closeInterfaceStep(String reason) {
    }

    /**
     * Whether this platform can render the live mirrored GUI preview inside PonderUI.
     */
    default boolean supportsEmbeddedInterfacePreview() {
        return false;
    }
}
