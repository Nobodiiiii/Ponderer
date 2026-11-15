package com.nododiiiii.ponderer.runtime.interfaces.host;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.scene.interfaces.InterfacePanelSpec;
import java.util.List;

/** Keeps native menu coordinates separate from the panel's projected screen rectangle. */
final class InterfacePanelLayout {
    private final Vec3 anchor;
    private final Pointing direction;
    private final float scale;
    private final List<Double> crop;
    private final boolean background;
    private int[] source = {0, 0, 176, 166};
    private int[] display = {0, 0, 176, 166};

    InterfacePanelLayout(Vec3 anchor, Pointing direction, float scale, List<Double> crop, boolean background) {
        this.anchor = anchor;
        this.direction = direction;
        this.scale = scale;
        this.crop = crop == null ? null : List.copyOf(crop);
        this.background = background;
    }

    int[] sourceRect(SandboxSession session) {
        return InterfacePanelSpec.cropRect(session.panelBounds(), crop);
    }

    boolean containsNativeRect(int x, int y, int width, int height) {
        return x >= source[0] && y >= source[1] && x + width <= source[0] + source[2] && y + height <= source[1] + source[3];
    }

    boolean containsDisplay(double x, double y) {
        return x >= display[0] && y >= display[1] && x < display[0] + display[2] && y < display[1] + display[3];
    }

    void update(PonderScene scene, int[] rect, float partialTicks) {
        if (rect == null) return;
        source = rect;
        var window = Minecraft.getInstance().getWindow();
        double fittedScale = Math.min(scale, Math.min(Math.max(1, window.getGuiScaledWidth() - 32) / (double) rect[2],
            Math.max(1, window.getGuiScaledHeight() - 80) / (double) rect[3]));
        int w = Math.max(1, (int) Math.round(rect[2] * fittedScale));
        int h = Math.max(1, (int) Math.round(rect[3] * fittedScale));
        var point = scene.getTransform().sceneToScreen(anchor, partialTicks);
        int x = Math.round(point.x), y = Math.round(point.y);
        switch (direction) {
            case DOWN -> { x -= (w + 8) / 2; y -= h + 18; }
            case UP -> { x -= (w + 8) / 2; y += 10; }
            case LEFT -> { x += 10; y -= (h + 8) / 2; }
            case RIGHT -> { x -= w + 18; y -= (h + 8) / 2; }
        }
        x = Mth.clamp(x, 4, Math.max(4, window.getGuiScaledWidth() - w - 12));
        y = Mth.clamp(y, 20, Math.max(20, window.getGuiScaledHeight() - h - 48));
        display = new int[]{x + 4, y + 4, w, h};
    }

    int[] rect() { return display; }

    double[] toNative(double x, double y) {
        return new double[]{source[0] + (x - display[0]) * source[2] / display[2],
            source[1] + (y - display[1]) * source[3] / display[3]};
    }

    double[] toDisplay(double x, double y) {
        return new double[]{display[0] + (x - source[0]) * display[2] / source[2],
            display[1] + (y - source[1]) * display[3] / source[3]};
    }

    void renderFrame(GuiGraphics graphics) {
        if (!background) return;
        // Ponder already draws this frame procedurally at arbitrary sizes, including its pointer.
        int w = display[2] + 8, h = display[3] + 8;
        int x = display[0] - 4, y = display[1] - 4;
        switch (direction) {
            case DOWN -> { x += w / 2; y += h + 10; }
            case UP -> { x += w / 2; y -= 10; }
            case LEFT -> { x -= 10; y += h / 2; }
            case RIGHT -> { x += w + 10; y += h / 2; }
        }
        PonderUI.renderSpeechBox(graphics, x, y, w, h, false, direction, false);
    }
}
