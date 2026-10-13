package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.mixin.AnimatedOverlayElementAccessor;
import com.nododiiiii.ponderer.mixin.AnimatedSceneElementAccessor;
import com.nododiiiii.ponderer.mixin.LerpedFloatAccessor;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

/** Keeps the render interval spanning both logical ticks of a double-speed update. */
public final class PonderInterpolationSnapshot {
    private static final ThreadLocal<PonderInterpolationSnapshot> ACTIVE = new ThreadLocal<>();

    private final Map<LerpedFloat, Float> floats = new IdentityHashMap<>();
    private final Map<WorldSectionElementImpl, SectionStart> sections = new IdentityHashMap<>();

    private PonderInterpolationSnapshot() {
    }

    public static void tickTwice(PonderScene scene) {
        PonderInterpolationSnapshot snapshot = new PonderInterpolationSnapshot();
        PonderInterpolationSnapshot previous = ACTIVE.get();
        ACTIVE.set(snapshot);
        try {
            snapshot.capture(scene, false);
            scene.tick();
            // Newly added elements retain their first tick's native interpolation start.
            snapshot.capture(scene, true);
            scene.tick();
            snapshot.capture(scene, true);
            snapshot.restore();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private void capture(PonderScene scene, boolean addedDuringTick) {
        captureFloat(scene.getTransform().xRotation, addedDuringTick);
        captureFloat(scene.getTransform().yRotation, addedDuringTick);
        if (scene instanceof PonderSceneViewOffsetAccess view) {
            captureFloat(view.ponderer$getViewOffsetX(), addedDuringTick);
            captureFloat(view.ponderer$getViewOffsetY(), addedDuringTick);
            captureFloat(view.ponderer$getViewOffsetZ(), addedDuringTick);
            captureFloat(view.ponderer$getScaleOverride(), addedDuringTick);
        }
        scene.forEach(element -> {
            if (element instanceof AnimatedSceneElementAccessor animated) {
                captureFloat(animated.ponderer$getFade(), addedDuringTick);
            }
            if (element instanceof AnimatedOverlayElementAccessor overlay) {
                captureFloat(overlay.ponderer$getFade(), addedDuringTick);
            }
            if (element instanceof WorldSectionElementImpl section && !sections.containsKey(section)) {
                SectionAccess access = (SectionAccess) section;
                sections.put(section, new SectionStart(
                    addedDuringTick ? access.ponderer$getPreviousOffset() : section.getAnimatedOffset(),
                    addedDuringTick ? access.ponderer$getPreviousRotation() : section.getAnimatedRotation()));
            }
        });
    }

    private void captureFloat(LerpedFloat value, boolean addedDuringTick) {
        if (!floats.containsKey(value)) {
            LerpedFloatAccessor access = (LerpedFloatAccessor) value;
            floats.put(value, addedDuringTick ? access.ponderer$getPreviousValue() : access.ponderer$getValue());
        }
    }

    private void restore() {
        floats.forEach((value, start) -> ((LerpedFloatAccessor) value).ponderer$setPreviousValue(start));
        sections.forEach((section, start) -> {
            SectionAccess access = (SectionAccess) section;
            access.ponderer$setPreviousOffset(start.offset());
            access.ponderer$setPreviousRotation(start.rotation());
        });
    }

    /** Explicit resets establish a new start rather than blending from before the reset. */
    public static void resetFloat(LerpedFloat value) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null && snapshot.floats.containsKey(value)) {
            snapshot.floats.put(value, ((LerpedFloatAccessor) value).ponderer$getValue());
        }
    }

    public static void resetSectionOffset(WorldSectionElementImpl section, Vec3 offset) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null) {
            SectionStart start = snapshot.sections.get(section);
            if (start != null) {
                snapshot.sections.put(section, new SectionStart(offset, start.rotation()));
            }
        }
    }

    public static void resetSectionRotation(WorldSectionElementImpl section, Vec3 rotation) {
        PonderInterpolationSnapshot snapshot = ACTIVE.get();
        if (snapshot != null) {
            SectionStart start = snapshot.sections.get(section);
            if (start != null) {
                snapshot.sections.put(section, new SectionStart(start.offset(), rotation));
            }
        }
    }

    private record SectionStart(Vec3 offset, Vec3 rotation) {
    }

    public interface SectionAccess {
        Vec3 ponderer$getPreviousOffset();
        void ponderer$setPreviousOffset(Vec3 offset);
        Vec3 ponderer$getPreviousRotation();
        void ponderer$setPreviousRotation(Vec3 rotation);
    }
}
