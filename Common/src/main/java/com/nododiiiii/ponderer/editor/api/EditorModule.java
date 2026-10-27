package com.nododiiiii.ponderer.editor.api;

import com.nododiiiii.ponderer.editor.capture.*;
import com.nododiiiii.ponderer.runtime.api.*;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions;
import java.util.ArrayList;
import java.util.List;

/** Reversible editor installation. Capture state and subscriptions are released together. */
public final class EditorModule implements AutoCloseable {
    private static int installations;
    public static boolean isInstalled() { return installations > 0; }
    private boolean closed;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    public EditorModule() { this(IngredientPresentation.NONE); }
    public EditorModule(IngredientPresentation ingredients) {
        installations++;
        bindings.add(InterfaceExtensions.installEditing(new SlotDragController(ingredients)));
        bindings.add(InterfaceSessionEvents.onSlotsChanged(InterfaceSlotEditState::invalidatePreview));
        bindings.add(PlaybackUiExtensions.install(new EditorInputExtension()));
        bindings.add(PlaybackInputLocks.register(() -> PickState.isActive() || NbtExpandedPickState.isActive() || InterfaceSlotEditState.isActive()));
    }
    public static void resetSession() {
        com.nododiiiii.ponderer.support.lifecycle.Cleanup.all(
            PickState::reset, NbtPickState::reset, CoordPickState::reset,
            NbtExpandedPickState::reset, InterfaceSlotEditState::reset,
            InterfaceCapture::reset, EditorResources::closePreviews);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        installations--;
        RuntimeException failure = null;
        try { resetSession(); } catch (RuntimeException error) { failure = error; }
        for (int i = bindings.size() - 1; i >= 0; i--) {
            try { bindings.get(i).close(); } catch (Exception error) {
                if (failure == null) failure = new IllegalStateException("Failed to close editor binding");
                failure.addSuppressed(error);
            }
        }
        bindings.clear();
        if (failure != null) throw failure;
    }
}
