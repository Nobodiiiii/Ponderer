package com.nododiiiii.ponderer.runtime.viewer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Draw complete native text windows after the progress panel, independent of other overlay GL state. */
public final class PonderSceneTextLayer {
    private static Frame pending;

    private PonderSceneTextLayer() {}

    public static void beforeRender(Screen screen) {
        cancelPending();
        if (!(screen instanceof PonderUI ui)) return;
        boolean panelShown = screen.children().stream().anyMatch(child ->
            child instanceof PonderTextListWidget panel && panel.hasVisibleEntries());
        if (!panelShown) return;

        Frame frame = new Frame(ui);
        pending = frame;
        try {
            for (PonderScene scene : ((PonderUIAccessor) ui).ponderer$getScenes()) {
                List<TextWindowElement> texts = new ArrayList<>();
                scene.forEach(TextWindowElement.class, text -> {
                    if (text.isVisible()) texts.add(text);
                });
                if (texts.isEmpty()) continue;
                TextPass pass = new TextPass(frame, scene, texts);
                frame.passes.add(pass);
                texts.forEach(text -> text.setVisible(false));
                scene.addElement(pass);
            }
        } catch (RuntimeException | Error e) {
            cancelPending();
            throw e;
        }
    }

    public static void afterRender(Screen screen, GuiGraphics graphics) {
        Frame frame = pending;
        if (frame == null || frame.ui != screen) return;
        try {
            for (TextPass pass : frame.rendered) {
                graphics.flush();
                graphics.pose().pushPose();
                try {
                    graphics.pose().last().pose().set(pass.pose);
                    for (TextWindowElement text : pass.texts) {
                        // Input hints can leave depth testing disabled, and embedded interfaces clear depth.
                        // Establish it for every complete background/text pair instead of relying on iteration order.
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthMask(true);
                        text.render(pass.scene, frame.ui, graphics, pass.partialTicks);
                        graphics.flush();
                    }
                } finally {
                    graphics.pose().popPose();
                }
            }
        } finally {
            cancelPending();
        }
    }

    /** Also runs before client ticks so a cancelled render cannot leave native elements hidden. */
    public static void cancelPending() {
        Frame frame = pending;
        if (frame == null) return;
        pending = null;
        for (TextPass pass : frame.passes) {
            pass.scene.getElements().remove(pass);
            pass.texts.forEach(text -> text.setVisible(true));
        }
    }

    private static final class Frame {
        private final PonderUI ui;
        private final List<TextPass> passes = new ArrayList<>();
        private final List<TextPass> rendered = new ArrayList<>();

        private Frame(PonderUI ui) {
            this.ui = ui;
        }
    }

    private static final class TextPass extends PonderElementBase implements PonderOverlayElement {
        private final Frame frame;
        private final PonderScene scene;
        private final List<TextWindowElement> texts;
        private Matrix4f pose;
        private float partialTicks;

        private TextPass(Frame frame, PonderScene scene, List<TextWindowElement> texts) {
            this.frame = frame;
            this.scene = scene;
            this.texts = texts;
        }

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
            if (screen != frame.ui) return;
            if (pose == null) frame.rendered.add(this);
            pose = new Matrix4f(graphics.pose().last().pose());
            this.partialTicks = partialTicks;
            // Let the native overlay call decide whether text is shown and supply its exact transform/timing.
            // Q, T, seeking and scene transitions therefore retain their native behavior.
        }
    }
}
