package com.nododiiiii.ponderer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

/**
 * Keeps Ponderer's own screens at a roughly constant physical size across
 * monitor resolutions by temporarily overriding the window's GUI scale.
 *
 * <p>The layout in {@link UILayoutConstants} was tuned against 4K (3840px wide)
 * at GUI Scale 4. At lower resolutions the same constants take up a much larger
 * share of the screen, hurting readability. We compute a target scale so that
 * each logical pixel occupies a similar number of physical pixels as the
 * reference setup, and apply it via {@code Window#setGuiScale} for the
 * duration of a Ponderer screen session. The original scale is restored once
 * the active screen is no longer a {@link ScaledScreen}.
 */
public final class PondererUiScaling {

    private static final int REF_PHYSICAL_WIDTH = 3840;
    private static final int REF_SCALE = 4;

    private static boolean active = false;

    private PondererUiScaling() {}

    /** Marker interface for screens that should render at the Ponderer target scale. */
    public interface ScaledScreen {}

    /**
     * Apply the target scale for the given screen if needed. Idempotent: if the
     * window is already at the target scale (either because we already applied,
     * or because the user's own preference matches), this is a no-op aside from
     * keeping the screen's logical dimensions in sync.
     */
    public static void apply(@Nullable Minecraft mc, @Nullable Screen screen) {
        if (mc == null || screen == null) {
            return;
        }

        int target = computeTargetScale(mc);
        if (target < 1) {
            return;
        }

        int current = (int) mc.getWindow().getGuiScale();
        if (target == current) {
            if (active) {
                syncScreenDimensions(mc, screen);
            }
            return;
        }

        active = true;
        mc.getWindow().setGuiScale(target);
        syncScreenDimensions(mc, screen);
    }

    /**
     * Schedule a check on the next tick: if the active screen is no longer a
     * Ponderer-scaled screen, restore the user's preferred GUI scale. Safe to
     * call from {@code removed()} / {@code onClose()} — we don't yet know what
     * the next screen will be when those run.
     */
    public static void scheduleRestore(@Nullable Minecraft mc) {
        if (mc == null || !active) {
            return;
        }
        // Always defer past the current setScreen() call. Minecraft may run
        // execute() immediately on the render thread while mc.screen still
        // points at the removed ScaledScreen, causing the restore check to be
        // consumed too early.
        mc.tell(PondererUiScaling::maybeRestore);
    }

    private static int computeTargetScale(Minecraft mc) {
        int physical = mc.getWindow().getWidth();
        if (physical <= 0) {
            return -1;
        }
        int maxScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
        int ideal = (int) Math.round(physical * (double) REF_SCALE / REF_PHYSICAL_WIDTH);
        return Math.max(1, Math.min(maxScale, ideal));
    }

    private static void syncScreenDimensions(Minecraft mc, Screen screen) {
        screen.width = mc.getWindow().getGuiScaledWidth();
        screen.height = mc.getWindow().getGuiScaledHeight();
    }

    private static void maybeRestore() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) {
            return;
        }
        if (mc.screen instanceof ScaledScreen) {
            return;
        }

        active = false;
        int requested = mc.options.guiScale().get();
        int restored = mc.getWindow().calculateScale(requested, mc.isEnforceUnicode());
        mc.getWindow().setGuiScale(restored);

        if (mc.screen != null) {
            mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }
}
