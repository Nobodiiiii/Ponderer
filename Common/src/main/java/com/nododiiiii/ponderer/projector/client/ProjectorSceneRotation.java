package com.nododiiiii.ponderer.projector.client;

import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.phys.Vec3;

final class ProjectorSceneRotation {
    // Ponder starts yRotation at 55 + 90; miniature projection keeps the built-in +90 scene yaw.
    private static final float DEFAULT_Y_ROTATION = 55.0F;

    static final ProjectorSceneRotation NONE = new ProjectorSceneRotation(0.0F);

    private final float yDegrees;

    private ProjectorSceneRotation(float yDegrees) {
        this.yDegrees = sanitize(yDegrees);
    }

    static ProjectorSceneRotation from(PonderScene scene, float partialTick) {
        if (scene == null) {
            return NONE;
        }

        float y = scene.getTransform().yRotation.getValue(partialTick) - DEFAULT_Y_ROTATION;
        if (Math.abs(y) < 0.001F) {
            return NONE;
        }
        return new ProjectorSceneRotation(y);
    }

    float yDegrees() {
        return yDegrees;
    }

    Vec3 apply(Vec3 vec) {
        return rotateY(vec, yDegrees);
    }

    static Vec3 rotateY(Vec3 vec, float rotationDegrees) {
        if (Math.abs(rotationDegrees) < 0.001F) {
            return vec;
        }
        double radians = Math.toRadians(rotationDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = vec.x * cos + vec.z * sin;
        double z = vec.z * cos - vec.x * sin;
        return new Vec3(x, vec.y, z);
    }

    private static float sanitize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || Math.abs(value) < 0.001F) {
            return 0.0F;
        }
        return value;
    }
}
