package com.nododiiiii.ponderer.ui;

/**
 * Shared conversions for UI anchor coordinates.
 * Canonical format is centered normalized coordinates in [-2, 2],
 * where (0, 0) is the center of the UI.
 */
public final class UiAnchorCoords {

    private UiAnchorCoords() {
    }

    public static double normalizeX(double mouseX, int width) {
        if (width <= 0) {
            return 0.0;
        }
        double centered = (mouseX / (double) width - 0.5) * 2.0;
        return clamp(centered, -2.0, 2.0);
    }

    public static double normalizeY(double mouseY, int height) {
        if (height <= 0) {
            return 0.0;
        }
        double centered = (0.5 - mouseY / (double) height) * 2.0;
        return clamp(centered, -2.0, 2.0);
    }

    /**
     * Decode a centered anchor X value into GUI pixel space (top-left origin).
     * Uses normalized range [-2..2]. Values outside this range are clamped.
     */
    public static double decodeToPixelX(double anchorX, int guiWidth) {
        if (guiWidth <= 0) {
            return 0;
        }

        double normalized = clamp(anchorX, -2.0, 2.0);
        return (normalized * 0.5 + 0.5) * guiWidth;
    }

    /**
    * Decode a centered anchor Y value into GUI pixel space (top-left origin).
     * Uses normalized range [-2..2]. Values outside this range are clamped.
    * Positive values move upward.
     */
    public static double decodeToPixelYTopLeft(double anchorY, int guiHeight) {
        if (guiHeight <= 0) {
            return 0;
        }

        double normalized = clamp(anchorY, -2.0, 2.0);
        return (0.5 - normalized * 0.5) * guiHeight;
    }

    /**
     * Convert top-left GUI X into centered transform X.
     * Ponder's transform-space input is centered around the middle of the screen.
     */
    public static double topLeftToTransformX(double xTopLeft, int guiWidth) {
        return xTopLeft - guiWidth * 0.5;
    }

    /**
     * Convert top-left GUI Y into transform-space Y expected by screenToScene.
     */
    public static double topLeftToTransformY(double yTopLeft, int guiHeight) {
        return yTopLeft - guiHeight * 0.5;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
