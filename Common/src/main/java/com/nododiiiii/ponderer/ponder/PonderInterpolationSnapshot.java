package com.nododiiiii.ponderer.ponder;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

/** Keeps the render interval spanning both logical ticks of a double-speed update. */
public final class PonderInterpolationSnapshot {
    private static final ThreadLocal<PonderInterpolationSnapshot> ACTIVE = new ThreadLocal<>();

    private final Map<LerpedFloat, Float> floats = new IdentityHashMap<>();
    private final Map<WorldSectionElement, Vec3> offsets = new IdentityHashMap<>();
    private final Map<WorldSectionElement, Vec3> rotations = new IdentityHashMap<>();
    private boolean restoring;

    private PonderInterpolationSnapshot() {
    }

    public static void tickTwice(PonderScene scene) {
        PonderInterpolationSnapshot snapshot = new PonderInterpolationSnapshot();
        PonderInterpolationSnapshot previous = ACTIVE.get();
        ACTIVE.set(snapshot);
        try {
            scene.tick();
            scene.tick();
            snapshot.restore();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private void restore() {
        restoring = true;
        try {
            floats.forEach((value, start) -> {
                float end = value.getValue(1);
                // setValue preserves the chase target/speed while establishing the previous value.
                value.setValue(start);
                value.setValue(end);
            });
            offsets.forEach((section, start) -> {
                Vec3 end = section.getAnimatedOffset();
                section.setAnimatedOffset(start, true);
                section.setAnimatedOffset(end, false);
            });
            rotations.forEach((section, start) -> {
                Vec3 end = section.getAnimatedRotation();
                section.setAnimatedRotation(start, true);
                section.setAnimatedRotation(end, false);
            });
        } finally {
            restoring = false;
        }
    }

    /** Observe the first public mutation, including animations created during either tick. */
    public static void captureFloat(LerpedFloat value) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && !snapshot.restoring && !snapshot.floats.containsKey(value)) {
            snapshot.floats.put(value, value.getValue(1));
        }
    }

    /** Explicit resets establish a new start rather than blending from before the reset. */
    public static void resetFloat(LerpedFloat value) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && !snapshot.restoring) {
            snapshot.floats.put(value, value.getValue(1));
        }
    }

    public static void captureSectionOffset(WorldSectionElement section) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && !snapshot.restoring && !snapshot.offsets.containsKey(section)) {
            snapshot.offsets.put(section, section.getAnimatedOffset());
        }
    }

    public static void captureSectionRotation(WorldSectionElement section) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && !snapshot.restoring && !snapshot.rotations.containsKey(section)) {
            snapshot.rotations.put(section, section.getAnimatedRotation());
        }
    }

    public static void resetSectionOffset(WorldSectionElement section) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && !snapshot.restoring) {
            snapshot.offsets.put(section, section.getAnimatedOffset());
        }
    }

    public static void resetSectionRotation(WorldSectionElement section) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && !snapshot.restoring) {
            snapshot.rotations.put(section, section.getAnimatedRotation());
        }
    }
}
