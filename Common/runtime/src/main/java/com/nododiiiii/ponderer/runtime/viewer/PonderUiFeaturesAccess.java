package com.nododiiiii.ponderer.runtime.viewer;

/** Player policy is captured when a screen initializes so an in-flight render cannot change policy. */
public interface PonderUiFeaturesAccess {
    boolean ponderer$playerEnhanced();
    static boolean enhanced(Object screen) {
        return screen instanceof PonderUiFeaturesAccess access && access.ponderer$playerEnhanced();
    }
}
