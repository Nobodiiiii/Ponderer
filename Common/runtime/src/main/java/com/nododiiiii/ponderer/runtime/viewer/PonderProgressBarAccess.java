package com.nododiiiii.ponderer.runtime.viewer;

/** Embedded timelines can opt in without initializing or enabling the full player UI. */
public interface PonderProgressBarAccess {
    boolean ponderer$progressBarEnhanced();

    static boolean enhanced(Object owner) {
        return owner instanceof PonderProgressBarAccess access
            ? access.ponderer$progressBarEnhanced()
            : PonderUiFeaturesAccess.enhanced(owner);
    }
}
