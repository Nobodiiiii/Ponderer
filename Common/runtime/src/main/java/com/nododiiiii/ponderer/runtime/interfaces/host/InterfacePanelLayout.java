package com.nododiiiii.ponderer.runtime.interfaces.host;

import com.mojang.math.Axis;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.enums.PonderGuiTextures;
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
    private final double offsetX, offsetY, scale;
    private final List<Double> crop;
    private final boolean background;
    private int[] source = {0, 0, 176, 166};
    private int[] display = {0, 0, 176, 166};

    InterfacePanelLayout(Vec3 anchor, Pointing direction, List<Double> transform, List<Double> crop, boolean background) {
        this.anchor = anchor;
        this.direction = direction;
        this.offsetX = transform.get(0);
        this.offsetY = transform.get(1);
        this.scale = transform.get(2);
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
        double x = point.x, y = point.y;
        switch (direction) {
            case DOWN -> { x -= w / 2; y -= h + 10; }
            case UP -> { x -= w / 2; y += 10; }
            case LEFT -> { x += 10; y -= h / 2; }
            case RIGHT -> { x -= w + 10; y -= h / 2; }
        }
        x = Mth.clamp(x + offsetX, 4, Math.max(4, window.getGuiScaledWidth() - w - 4));
        y = Mth.clamp(y + offsetY, 20, Math.max(20, window.getGuiScaledHeight() - h - 40));
        display = new int[]{(int) Math.round(x), (int) Math.round(y), w, h};
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
        // SpeechBox uses BoxElement's default two-pixel inset. Use the same primitives with no inset.
        int w = display[2], h = display[3];
        int x = display[0], y = display[1];
        new BoxElement().withBackground(PonderUI.BACKGROUND_FLAT).gradientBorder(PonderUI.COLOR_IDLE)
            .withBorderOffset(0).at(x, y, 100).withBounds(w, h).render(graphics);
        int arrowX, arrowY, rotation;
        Color color;
        switch (direction) {
            case DOWN -> { arrowX = x + w / 2 - 4; arrowY = y + h + 1; rotation = 0; color = PonderUI.COLOR_IDLE.getSecond(); }
            case UP -> { arrowX = x + w / 2 - 4; arrowY = y - 9; rotation = 180; color = PonderUI.COLOR_IDLE.getFirst(); }
            case LEFT -> { arrowX = x - 9; arrowY = y + h / 2 - 4; rotation = 90; color = Color.mixColors(PonderUI.COLOR_IDLE, 0.5f); }
            case RIGHT -> { arrowX = x + w + 1; arrowY = y + h / 2 - 4; rotation = 270; color = Color.mixColors(PonderUI.COLOR_IDLE, 0.5f); }
            default -> throw new IllegalStateException("Unsupported panel direction: " + direction);
        }
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(arrowX + 4, arrowY + 4, 110);
            pose.mulPose(Axis.ZP.rotationDegrees(rotation));
            pose.translate(-4, -4, 0);
            PonderGuiTextures.SPEECH_TOOLTIP_BACKGROUND.render(graphics, 0, 0);
            PonderGuiTextures.SPEECH_TOOLTIP_COLOR.render(graphics, 0, 0, color);
        } finally {
            pose.popPose();
        }
    }
}
