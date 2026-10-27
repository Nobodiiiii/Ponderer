package com.nododiiiii.ponderer.features.projector;

import com.nododiiiii.ponderer.features.projector.spi.ProjectorPlatform;

public final class ProjectorPlatforms {
    private static ProjectorPlatform platform;
    private ProjectorPlatforms() {}
    public static void install(ProjectorPlatform value) { platform = java.util.Objects.requireNonNull(value); }
    public static ProjectorPlatform get() {
        if (platform == null) throw new IllegalStateException("Projector platform has not been installed");
        return platform;
    }
}
