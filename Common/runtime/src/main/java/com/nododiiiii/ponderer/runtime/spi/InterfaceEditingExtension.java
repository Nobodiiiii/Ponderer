package com.nododiiiii.ponderer.runtime.spi;

import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.runtime.interfaces.sandbox.SandboxSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.util.Map;

public interface InterfaceEditingExtension {
    default boolean isActive() { return false; }
    default void applyPreview(SandboxSession session) {}
    default boolean pressed(SandboxSession session, double x, double y, int button) { return false; }
    default boolean released(SandboxSession session, double x, double y, int button) { return false; }
    default void cancelDrag() {}
    default void resetInput() {}
    default void renderDragged(GuiGraphics graphics, int x, int y) {}
    default void renderHint(GuiGraphics graphics, Screen screen, int x, int y) {}
    default Map<Integer, DslScene.InterfaceSlotBinding> ghostBindings() { return Map.of(); }
    InterfaceEditingExtension NONE = new InterfaceEditingExtension() {};
}
