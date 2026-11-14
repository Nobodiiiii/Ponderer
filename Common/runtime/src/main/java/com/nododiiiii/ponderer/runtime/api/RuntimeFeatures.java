package com.nododiiiii.ponderer.runtime.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Startup policy is deliberately independent of Minecraft and loader initialization. */
public final class RuntimeFeatures {
    public enum Policy { AUTO, ON, OFF }
    private static final Properties CONFIG = readConfig();
    private static final Policy PLAYER = policy("player", Policy.AUTO);
    private static final Policy INTERFACES = policy("interfaces", Policy.AUTO);
    private static final Set<Object> PLAYER_REQUESTS = ConcurrentHashMap.newKeySet();
    private RuntimeFeatures() {}

    /** Call before opening a player. Each request has independent ownership. */
    public static AutoCloseable requestPlayerEnhancements(String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Missing request owner");
        Object token = new Object();
        PLAYER_REQUESTS.add(token);
        return () -> PLAYER_REQUESTS.remove(token);
    }
    public static boolean playerEnabled() {
        return PLAYER == Policy.ON || PLAYER == Policy.AUTO && !PLAYER_REQUESTS.isEmpty();
    }
    /** Core bridges are installed at startup; AUTO keeps interfaces available for Java consumers. */
    public static boolean interfacesEnabled() { return INTERFACES != Policy.OFF; }
    public static void requireInterfaces(String location) {
        if (!interfacesEnabled()) throw new IllegalStateException(location + ": capability pondererlib:interfaces is disabled at startup");
    }
    public static Policy playerPolicy() { return PLAYER; }
    public static Policy interfacePolicy() { return INTERFACES; }

    private static Policy policy(String name, Policy fallback) {
        String value = System.getProperty("pondererlib." + name, CONFIG.getProperty(name, fallback.name()));
        try { return Policy.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid pondererlib " + name + " policy: " + value, error); }
    }
    private static Properties readConfig() {
        Properties values = new Properties();
        Path path = Path.of(System.getProperty("pondererlib.config", "config/pondererlib.properties"));
        if (!Files.exists(path)) return values;
        try (var input = Files.newInputStream(path)) { values.load(input); }
        catch (IOException error) { throw new IllegalStateException("Cannot read " + path.toAbsolutePath(), error); }
        return values;
    }
}
