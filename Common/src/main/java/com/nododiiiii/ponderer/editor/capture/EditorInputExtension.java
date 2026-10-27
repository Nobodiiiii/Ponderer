package com.nododiiiii.ponderer.editor.capture;

import com.nododiiiii.ponderer.runtime.spi.PlaybackUiExtension;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import com.nododiiiii.ponderer.editor.capture.PickState;
import com.nododiiiii.ponderer.editor.capture.NbtExpandedPickState;
import com.nododiiiii.ponderer.editor.capture.InterfaceSlotEditState;

public final class EditorInputExtension implements PlaybackUiExtension {
    @Override public boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        if (InterfaceSlotEditState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                InterfaceSlotEditState.finishAndReopenEditor();
                return true;
            }
        }
        if (PickState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                PickState.cancelPick();
                return true;
            }
        }
        if (NbtExpandedPickState.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                NbtExpandedPickState.cancelPick();
                return true;
            }
        }
        return false;
    }
}
