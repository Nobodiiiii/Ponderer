package com.nododiiiii.ponderer.editor.api;

import com.nododiiiii.ponderer.editor.capture.*;
import com.nododiiiii.ponderer.runtime.api.*;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions;
import java.util.ArrayList;
import java.util.List;

/** Reversible editor installation. Capture state and subscriptions are released together. */
public final class EditorModule implements AutoCloseable {
    private final List<AutoCloseable> bindings = new ArrayList<>();
    public EditorModule() {
        bindings.add(InterfaceExtensions.installEditing(new SlotDragController()));
        bindings.add(InterfaceSessionEvents.onSlotsChanged(InterfaceSlotEditState::invalidatePreview));
        bindings.add(PlaybackUiExtensions.install(new EditorInputExtension()));
        bindings.add(PlaybackInputLocks.register(() -> PickState.isActive() || NbtExpandedPickState.isActive() || InterfaceSlotEditState.isActive()));
    }
    @Override public void close() {
        PickState.reset();
        NbtExpandedPickState.reset();
        InterfaceSlotEditState.reset();
        for (int i = bindings.size() - 1; i >= 0; i--) {
            try { bindings.get(i).close(); } catch (Exception error) { throw new IllegalStateException("Failed to close editor binding", error); }
        }
        bindings.clear();
    }
}
