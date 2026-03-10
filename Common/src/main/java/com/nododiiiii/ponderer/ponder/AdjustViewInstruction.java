package com.nododiiiii.ponderer.ponder;

import com.nododiiiii.ponderer.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.ui.PickState;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.OutlinerElement;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.world.phys.Vec3;

public class AdjustViewInstruction extends TickingInstruction {

    private final Vec3 center;
    private final boolean useDefaultCenter;
    private final float multiplier;

    private boolean skipDueToPickMode;

    public AdjustViewInstruction(Vec3 center, boolean useDefaultCenter, float multiplier, int duration) {
        super(true, Math.max(0, duration));
        this.center = center;
        this.useDefaultCenter = useDefaultCenter;
        this.multiplier = multiplier;
    }

    @Override
    protected void firstTick(PonderScene scene) {
        skipDueToPickMode = PickState.isActive();
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

        float currentScale = accessor.ponderer$getScaleFactor();
        float defaultScale = viewOffset.ponderer$getDefaultScale();
        if (Float.isNaN(defaultScale)) {
            defaultScale = currentScale;
            viewOffset.ponderer$setDefaultScale(defaultScale);
        }
        float targetScale = Float.isNaN(multiplier) ? defaultScale : defaultScale * multiplier;

        LerpedFloat lerpX = viewOffset.ponderer$getViewOffsetX();
        LerpedFloat lerpY = viewOffset.ponderer$getViewOffsetY();
        LerpedFloat lerpZ = viewOffset.ponderer$getViewOffsetZ();
        LerpedFloat lerpScale = viewOffset.ponderer$getScaleOverride();

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

        // Initialize scale override from current scaleFactor
        if (!viewOffset.ponderer$isScaleOverrideActive()) {
            lerpScale.startWithValue(currentScale);
        }
        viewOffset.ponderer$setScaleOverrideActive(true);

        if (totalTicks == 0) {
            lerpX.startWithValue(targetX);
            lerpY.startWithValue(targetY);
            lerpZ.startWithValue(targetZ);
            lerpScale.startWithValue(targetScale);
            accessor.ponderer$setScaleFactor(targetScale);
        } else {
            int moveTicks = totalTicks / 2;
            int zoomTicks = totalTicks - moveTicks;

            // Pan phase: chase offset over first half
            if (moveTicks > 0) {
                lerpX.chase(targetX, Math.abs(targetX - lerpX.getValue(0)) / moveTicks, LerpedFloat.Chaser.LINEAR);
                lerpY.chase(targetY, Math.abs(targetY - lerpY.getValue(0)) / moveTicks, LerpedFloat.Chaser.LINEAR);
                lerpZ.chase(targetZ, Math.abs(targetZ - lerpZ.getValue(0)) / moveTicks, LerpedFloat.Chaser.LINEAR);
            } else {
                lerpX.startWithValue(targetX);
                lerpY.startWithValue(targetY);
                lerpZ.startWithValue(targetZ);
            }

            // Zoom phase starts after pan completes; keep scale chasing idle for now
            // It will be kicked off in tick() once the pan phase ends
        }
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (skipDueToPickMode) {
            return;
        }
        if (totalTicks <= 0) {
            return;
        }
        if (!(scene instanceof PonderSceneAccessor accessor) || !(scene instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }

        int elapsed = totalTicks - remainingTicks;
        int moveTicks = totalTicks / 2;
        int zoomTicks = totalTicks - moveTicks;

        // At the transition point, kick off the scale chase
        if (zoomTicks > 0 && elapsed == moveTicks + 1) {
            float currentScale = accessor.ponderer$getScaleFactor();
            float defaultScale = viewOffset.ponderer$getDefaultScale();
            float targetScale = Float.isNaN(multiplier) ? defaultScale : defaultScale * multiplier;
            LerpedFloat lerpScale = viewOffset.ponderer$getScaleOverride();
            lerpScale.startWithValue(currentScale);
            lerpScale.chase(targetScale, Math.abs(targetScale - currentScale) / zoomTicks, LerpedFloat.Chaser.LINEAR);
        }

        // Sync scaleFactor from the override so other code reading it gets reasonable values
        if (viewOffset.ponderer$isScaleOverrideActive()) {
            accessor.ponderer$setScaleFactor(viewOffset.ponderer$getScaleOverride().getValue(0));
        }
    }

    private static double alignToBlockCenter(double value) {
        return Math.abs(value - Math.rint(value)) < 1e-6 ? value + 0.5 : value;
    }
}
