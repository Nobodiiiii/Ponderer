package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.FeatureAvailability;

/**
 * Utility class for resolving whether Ponderer's Projector feature is enabled.
 */
public final class ProjectorFeature {

    private ProjectorFeature() {
    }

    /**
     * Returns true when the server config enables Ponderer's Projector blocks.
     */
    public static boolean isProjectorEnabled() {
        return FeatureAvailability.isProjectorEnabled();
    }

    /**
     * Returns true if the Projector block items should be shown in creative tabs.
     */
    public static boolean shouldShowProjectorsInCreativeTab() {
        return isProjectorEnabled();
    }
}
