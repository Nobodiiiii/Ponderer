package com.nododiiiii.ponderer.runtime.interfaces.platform;

/** Host installation is explicit and client-only; callers cannot load product service locators. */
public final class InterfacePlatform {
    private static InterfaceSandboxPlatform platform;
    private InterfacePlatform() {}
    public static InterfaceSandboxPlatform get() {
        if (platform == null) throw new IllegalStateException("Interface platform has not been installed by the host");
        return platform;
    }
    public static AutoCloseable install(InterfaceSandboxPlatform adapter) {
        platform = adapter;
        return () -> { if (platform == adapter) platform = null; };
    }
}
