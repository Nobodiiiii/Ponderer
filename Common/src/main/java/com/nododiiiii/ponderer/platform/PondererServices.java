package com.nododiiiii.ponderer.platform;

import com.nododiiiii.ponderer.platform.services.NetworkHelper;
import com.nododiiiii.ponderer.platform.services.PlatformHelper;
import com.nododiiiii.ponderer.platform.services.RegistrationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Central service locator using Java SPI (ServiceLoader).
 * Each platform (NeoForge/Fabric) provides implementations via META-INF/services.
 */
public final class PondererServices {
    private static final Logger LOGGER = LoggerFactory.getLogger("Ponderer");

    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final NetworkHelper NETWORK = load(NetworkHelper.class);
    public static final RegistrationHelper REGISTRATION = load(RegistrationHelper.class);

    public static <T> T load(Class<T> clazz) {
        final T service = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        LOGGER.debug("Loaded {} for service {}", service, clazz);
        return service;
    }

    private PondererServices() {}
}
