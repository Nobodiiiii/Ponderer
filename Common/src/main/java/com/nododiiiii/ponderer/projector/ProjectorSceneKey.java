package com.nododiiiii.ponderer.projector;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class ProjectorSceneKey {

    private static final String NATIVE_PREFIX = "native|";

    private ProjectorSceneKey() {
    }

    public static String nativeKey(ResourceLocation componentId, ResourceLocation sceneId, int occurrence) {
        return NATIVE_PREFIX + componentId + "|" + sceneId + "|" + Math.max(0, occurrence);
    }

    @Nullable
    public static Native parseNative(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(NATIVE_PREFIX)) {
            return null;
        }

        String[] parts = rawKey.split("\\|", -1);
        if (parts.length != 4 || !"native".equals(parts[0])) {
            return null;
        }

        ResourceLocation componentId = ResourceLocation.tryParse(parts[1]);
        ResourceLocation sceneId = ResourceLocation.tryParse(parts[2]);
        if (componentId == null || sceneId == null) {
            return null;
        }

        try {
            return new Native(componentId, sceneId, Math.max(0, Integer.parseInt(parts[3])));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record Native(ResourceLocation componentId, ResourceLocation sceneId, int occurrence) {
    }
}
