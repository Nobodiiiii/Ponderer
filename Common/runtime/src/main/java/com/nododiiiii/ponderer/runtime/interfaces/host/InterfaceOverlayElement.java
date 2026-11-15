package com.nododiiiii.ponderer.runtime.interfaces.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceSlotOverlayRenderer;
import net.createmod.ponder.api.element.PonderElement;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a sandbox interface in PonderUI's scene overlay pass (design doc 10). No PonderUI
 * injection: the element lives in the scene like any text window, and the scene's reset
 * destroys the session, so seeking and replay rebuild it deterministically from the timeline.
 */
public final class InterfaceOverlayElement extends AnimatedOverlayElementBase {

    private final PonderScene scene;
    private SandboxSession session;
    private InterfacePanelLayout panel;
    private boolean panelBlocking = true;
    private int[] previewSource = {0, 0, 1, 1};
    private int[] previewDisplay = {0, 0, 1, 1};

    InterfaceOverlayElement(PonderScene scene, SandboxSession session) {
        this.scene = scene;
        this.session = session;
        // Initialize both interpolation samples so standalone interfaces are fully visible immediately.
        fade.startWithValue(1.0f);
    }

    public PonderScene scene() {
        return scene;
    }

    public SandboxSession session() {
        return session;
    }

    void setPanel(InterfacePanelLayout panel, boolean blocking, boolean animation) {
        this.panel = panel;
        this.panelBlocking = blocking;
        fade.startWithValue(animation ? 0 : 1);
    }

    @Override public void tick(PonderScene scene) {
        // Settle the previous sample while idle, so interpolation cannot repeat the last movement.
        fade.tickChaser();
    }

    boolean isPanel() { return panel != null; }

    boolean blocksTimeline() { return panel == null || panelBlocking; }

    int[] displayRect() {
        var preview = InterfaceHost.previewViewport(scene);
        if (panel == null) {
            int[] nativeRect = session.guiRect();
            if (preview == null) return nativeRect;
            updatePreviewRect(preview);
            double[] start = previewToDisplay(nativeRect[0], nativeRect[1]);
            double[] end = previewToDisplay(nativeRect[0] + nativeRect[2], nativeRect[1] + nativeRect[3]);
            return new int[]{(int) Math.round(start[0]), (int) Math.round(start[1]),
                Math.max(1, (int) Math.round(end[0] - start[0])), Math.max(1, (int) Math.round(end[1] - start[1]))};
        }
        float partialTicks = preview == null
            ? Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false) : preview.partialTicks();
        panel.update(scene, panel.sourceRect(session), partialTicks, getFade(partialTicks));
        return panel.rect();
    }

    private void updatePreviewRect(InterfaceHost.PreviewViewport preview) {
        previewSource = session.panelBounds();
        var bounds = preview.bounds();
        double scale = Math.min(1, Math.min(Math.max(1, bounds.getWidth() - 16) / (double) previewSource[2],
            Math.max(1, bounds.getHeight() - 32) / (double) previewSource[3]));
        int width = Math.max(1, (int) Math.round(previewSource[2] * scale));
        int height = Math.max(1, (int) Math.round(previewSource[3] * scale));
        previewDisplay = new int[]{bounds.getX() + (bounds.getWidth() - width) / 2,
            bounds.getY() + (bounds.getHeight() - height) / 2, width, height};
    }

    double[] toNative(double x, double y) {
        if (panel != null) {
            displayRect();
            return panel.toNative(x, y);
        }
        var preview = InterfaceHost.previewViewport(scene);
        if (preview == null) return new double[]{x, y};
        updatePreviewRect(preview);
        return new double[]{previewSource[0] + (x - previewDisplay[0]) * previewSource[2] / previewDisplay[2],
            previewSource[1] + (y - previewDisplay[1]) * previewSource[3] / previewDisplay[3]};
    }

    double[] toDisplay(double x, double y) {
        if (panel != null) {
            displayRect();
            return panel.toDisplay(x, y);
        }
        var preview = InterfaceHost.previewViewport(scene);
        if (preview == null) return new double[]{x, y};
        updatePreviewRect(preview);
        return previewToDisplay(x, y);
    }

    private double[] previewToDisplay(double x, double y) {
        return new double[]{previewDisplay[0] + (x - previewSource[0]) * previewDisplay[2] / previewSource[2],
            previewDisplay[1] + (y - previewSource[1]) * previewDisplay[3] / previewSource[3]};
    }

    boolean isNativeRectVisible(int x, int y, int width, int height) {
        if (panel == null) return true;
        displayRect();
        return panel.containsNativeRect(x, y, width, height);
    }

    boolean clipsInputAt(double x, double y) {
        if (panel != null) {
            displayRect();
            return !panel.containsDisplay(x, y);
        }
        var preview = InterfaceHost.previewViewport(scene);
        if (preview == null) return false;
        updatePreviewRect(preview);
        return x < previewDisplay[0] || y < previewDisplay[1]
            || x >= previewDisplay[0] + previewDisplay[2] || y >= previewDisplay[1] + previewDisplay[3];
    }

    /** Swaps in a rebuilt session (try-mode reset); the caller destroys the old one. */
    void replaceSession(SandboxSession session) {
        this.session = session;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        // Match InputWindowElement's cutoff; the panel and its native GUI slide as one rectangle.
        if (fade < (panel == null ? 1 / 32f : 1 / 16f)
            || scene != screen.getActiveScene() || session.isDestroyed() || !InterfaceHost.isCurrent(this)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var preview = InterfaceHost.previewViewport(scene);
        int mouseX = preview == null ? InterfaceHost.mouseX(mc) : preview.mouseX();
        int mouseY = preview == null ? InterfaceHost.mouseY(mc) : preview.mouseY();
        double[] localMouse = toNative(mouseX, mouseY);
        if (preview == null) InterfaceHost.beforeRender(session, mouseX, mouseY);
        boolean hoverAllowed = InterfaceHost.shouldForwardHover(screen, mouseX, mouseY);
        int hoverX = hoverAllowed ? (int) localMouse[0] : -1;
        int hoverY = hoverAllowed ? (int) localMouse[1] : -1;
        double[] ghost = InterfaceHost.cursorPosition(session, partialTicks);
        if (ghost != null && isNativeRectVisible((int) ghost[0], (int) ghost[1], 1, 1)) {
            // While a scripted action animates, hover (tooltips, carried stack) follows the ghost cursor.
            hoverX = (int) ghost[0];
            hoverY = (int) ghost[1];
        }

        // The 3D scene leaves depth behind that would clip GUI geometry; start from a clean buffer.
        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.pose().pushPose();
        try {
            if (panel != null) {
                int[] source = panel.sourceRect(session);
                panel.update(scene, source, partialTicks, fade);
                panel.renderFrame(graphics);
                session.renderPanel(graphics, hoverX, hoverY, partialTicks, source, panel.rect());
            } else if (preview != null) {
                updatePreviewRect(preview);
                session.renderPanel(graphics, hoverX, hoverY, partialTicks, previewSource, previewDisplay);
            } else {
                session.render(graphics, hoverX, hoverY, partialTicks);
                if (session.currentScreen() != null) InterfaceSlotOverlayRenderer.render(graphics, session.currentScreen());
            }
            if (preview == null) {
                InterfaceHost.renderChrome(graphics, session, mouseX, mouseY, partialTicks);
                InterfaceHost.renderDraggedBinding(graphics, mouseX, mouseY);
            } else {
                InterfaceHost.renderCursor(graphics, session, partialTicks);
            }
        } finally {
            graphics.pose().popPose();
            graphics.flush();
            // Everything drawn after the interface must end up on top of it.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
        }
        redrawEarlierOverlays(scene, screen, graphics, partialTicks);
        if (panel == null && preview == null && hoverAllowed && ghost == null && session.currentScreen() != null) {
            InterfaceSlotOverlayRenderer.renderJeiVisibilityTooltip(graphics, session.currentScreen(), mouseX, mouseY);
        }
    }

    /**
     * Overlay elements are kept in a HashSet, so text windows iterated before this element were
     * drawn underneath the interface backdrop. Draw those once more on top. {@link #hashCode()}
     * places this element in bucket 0, so in practice the list is almost always empty.
     */
    private void redrawEarlierOverlays(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        List<PonderOverlayElement> earlier = new ArrayList<>();
        for (PonderElement element : scene.getElements()) {
            if (element == this) {
                break;
            }
            if (element instanceof PonderOverlayElement overlay && !(element instanceof InterfaceOverlayElement)
                && element.isVisible()) {
                earlier.add(overlay);
            }
        }
        for (PonderOverlayElement overlay : earlier) {
            graphics.pose().pushPose();
            overlay.render(scene, screen, graphics, partialTicks);
            graphics.pose().popPose();
        }
    }

    @Override
    public void reset(PonderScene scene) {
        InterfaceHost.onElementReset(this);
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}
