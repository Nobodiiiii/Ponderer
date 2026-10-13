package com.nododiiiii.ponderer.interfaces.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxSession;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
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

    InterfaceOverlayElement(PonderScene scene, SandboxSession session) {
        this.scene = scene;
        this.session = session;
        // Not setFade(1): LerpedFloat.setValue keeps previousValue at 0 and nothing ticks this
        // element, so getFade(pt) would equal partialTicks forever and the < 1/32 cutoff below
        // would drop frames at a rate that beats against the frame rate (visible flicker).
        fade.startWithValue(1.0f);
    }

    public PonderScene scene() {
        return scene;
    }

    public SandboxSession session() {
        return session;
    }

    /** Swaps in a rebuilt session (try-mode reset); the caller destroys the old one. */
    void replaceSession(SandboxSession session) {
        this.session = session;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks, float fade) {
        // Interface code overrides shader colour, so fading is all-or-nothing (scene fade-out hides it).
        if (fade < 1 / 32f || scene != screen.getActiveScene() || session.isDestroyed() || !InterfaceHost.isCurrent(this)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int mouseX = InterfaceHost.mouseX(mc);
        int mouseY = InterfaceHost.mouseY(mc);
        InterfaceHost.beforeRender(session, mouseX, mouseY);
        boolean hoverAllowed = InterfaceHost.shouldForwardHover(screen, mouseX, mouseY);
        int hoverX = hoverAllowed ? mouseX : -1;
        int hoverY = hoverAllowed ? mouseY : -1;
        double[] ghost = InterfaceHost.cursorPosition(session, partialTicks);
        if (ghost != null) {
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
            session.render(graphics, hoverX, hoverY, partialTicks);
            if (session.currentScreen() != null) {
                InterfaceSlotOverlayRenderer.render(graphics, session.currentScreen());
            }
            InterfaceHost.renderChrome(graphics, session, mouseX, mouseY, partialTicks);
            InterfaceHost.renderDraggedBinding(graphics, mouseX, mouseY);
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
        if (hoverAllowed && ghost == null && session.currentScreen() != null) {
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
