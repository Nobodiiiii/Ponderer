package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.runtime.api.PlaybackInputLocks;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.OutlinerElement;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.phys.Vec3;

public class AdjustViewInstruction extends TickingInstruction {

    // PonderUI changes scenes with EXP at .25f. Normalize the gentler .1 curve
    // over each phase so it reaches its target within the configured duration.
    private static final double EASE_OUT_DECAY = 0.9;

    private final Vec3 center;
    private final boolean useDefaultCenter;
    private final float multiplier;

    private boolean skipDueToPickMode;
    private boolean animationReady;
    private Vec3 startOffset;
    private Vec3 targetOffset;
    private float startScale;
    private float targetScale;

    public AdjustViewInstruction(Vec3 center, boolean useDefaultCenter, float multiplier, int duration) {
        super(true, Math.max(0, duration));
        this.center = center;
        this.useDefaultCenter = useDefaultCenter;
        this.multiplier = multiplier;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        animationReady = false;
        skipDueToPickMode = PlaybackInputLocks.isLocked();
        if (skipDueToPickMode) {
            return;
        }

        // Brute-force mitigation: clear active text overlays before moving view.
        scene.forEach(TextWindowElement.class, e -> {
            e.setVisible(false);
            e.setFade(0);
        });
        scene.forEach(OutlinerElement.class, e -> {
            e.setVisible(false);
            e.setFade(0);
        });

        if (!(scene instanceof PonderSceneAccessor accessor) || !(scene instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }

        float currentScale = viewOffset.ponderer$isScaleOverrideActive()
                ? viewOffset.ponderer$getScaleOverride().getValue()
                : accessor.ponderer$getScaleFactor();
        float defaultScale = viewOffset.ponderer$getDefaultScale();
        if (Float.isNaN(defaultScale)) {
            defaultScale = currentScale;
            viewOffset.ponderer$setDefaultScale(defaultScale);
        }
        startScale = currentScale;
        targetScale = Float.isNaN(multiplier) ? defaultScale : defaultScale * multiplier;

        LerpedFloat lerpX = viewOffset.ponderer$getViewOffsetX();
        LerpedFloat lerpY = viewOffset.ponderer$getViewOffsetY();
        LerpedFloat lerpZ = viewOffset.ponderer$getViewOffsetZ();
        LerpedFloat lerpScale = viewOffset.ponderer$getScaleOverride();
        startOffset = new Vec3(lerpX.getValue(), lerpY.getValue(), lerpZ.getValue());

        double targetX, targetY, targetZ;

        int basePlateSize = accessor.ponderer$getBasePlateSize();
        int baseOffX = accessor.ponderer$getBasePlateOffsetX();
        int baseOffZ = accessor.ponderer$getBasePlateOffsetZ();
        double baseTranslationX = -basePlateSize / 2.0 - baseOffX;
        double baseTranslationZ = -basePlateSize / 2.0 - baseOffZ;
        double baseTranslationY = -1.0 + scene.getYOffset();

        if (useDefaultCenter) {
            targetX = 0;
            targetY = 0;
            targetZ = 0;
        } else {
            double centerX = alignToBlockCenter(center.x);
            double centerY = center.y;
            double centerZ = alignToBlockCenter(center.z);
            targetX = -centerX - baseTranslationX;
            targetY = -centerY - baseTranslationY;
            targetZ = -centerZ - baseTranslationZ;
        }
        targetOffset = new Vec3(targetX, targetY, targetZ);

        // Initialize scale override from current scaleFactor
        if (!viewOffset.ponderer$isScaleOverrideActive()) {
            lerpScale.startWithValue(currentScale);
        }
        viewOffset.ponderer$setScaleOverrideActive(true);
        animationReady = true;

        if (totalTicks == 0) {
            lerpX.startWithValue(targetX);
            lerpY.startWithValue(targetY);
            lerpZ.startWithValue(targetZ);
            lerpScale.startWithValue(targetScale);
            accessor.ponderer$setScaleFactor(targetScale);
        } else {
            // Stop any earlier chase. tick() owns the values until both phases finish.
            lerpX.chase(startOffset.x, 0, LerpedFloat.Chaser.IDLE);
            lerpY.chase(startOffset.y, 0, LerpedFloat.Chaser.IDLE);
            lerpZ.chase(startOffset.z, 0, LerpedFloat.Chaser.IDLE);
            lerpScale.chase(currentScale, 0, LerpedFloat.Chaser.IDLE);
        }
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (skipDueToPickMode || !animationReady || totalTicks <= 0) {
            return;
        }
        if (!(scene instanceof PonderSceneAccessor accessor) || !(scene instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }

        int elapsed = totalTicks - remainingTicks;
        int moveTicks = totalTicks / 2;
        int zoomTicks = totalTicks - moveTicks;

        double moveProgress = easeOutProgress(elapsed, moveTicks);
        Vec3 offset = moveProgress == 1 ? targetOffset : startOffset.lerp(targetOffset, moveProgress);
        setAnimatedValue(viewOffset.ponderer$getViewOffsetX(), offset.x);
        setAnimatedValue(viewOffset.ponderer$getViewOffsetY(), offset.y);
        setAnimatedValue(viewOffset.ponderer$getViewOffsetZ(), offset.z);

        double zoomProgress = easeOutProgress(elapsed - moveTicks, zoomTicks);
        double scale = zoomProgress == 1 ? targetScale : startScale + (targetScale - startScale) * zoomProgress;
        setAnimatedValue(viewOffset.ponderer$getScaleOverride(), scale);
        accessor.ponderer$setScaleFactor(viewOffset.ponderer$getScaleOverride().getValue());
    }

    private static double easeOutProgress(int elapsed, int duration) {
        if (duration <= 0 || elapsed >= duration) {
            return 1;
        }
        if (elapsed <= 0) {
            return 0;
        }
        return (1 - Math.pow(EASE_OUT_DECAY, elapsed)) / (1 - Math.pow(EASE_OUT_DECAY, duration));
    }

    private static void setAnimatedValue(LerpedFloat lerp, double value) {
        // Preserve interpolation from the previous tick, with no chase left after completion.
        lerp.chase(value, 0, LerpedFloat.Chaser.IDLE);
        lerp.setValue(value);
    }

    private static double alignToBlockCenter(double value) {
        return Math.abs(value - Math.rint(value)) < 1e-6 ? value + 0.5 : value;
    }
}
