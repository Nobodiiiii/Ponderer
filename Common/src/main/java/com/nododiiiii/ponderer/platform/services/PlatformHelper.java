package com.nododiiiii.ponderer.platform.services;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Platform abstraction for loader-specific utilities.
 */
public interface PlatformHelper {

    /** Returns the current platform: "neoforge" or "fabric". */
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
}
