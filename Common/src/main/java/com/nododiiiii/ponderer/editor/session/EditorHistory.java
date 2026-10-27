package com.nododiiiii.ponderer.editor.session;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import javax.annotation.Nullable;

public final class EditorHistory {
    @Nullable
    private String activePonderKey;
    private final Map<String, UndoManager> sceneHistories = new HashMap<>();
    private boolean preserveOnNextPonderUiRemoval;

    public UndoManager historyFor(String ponderKey, String sceneKey) {
        switchToPonder(ponderKey);
        return sceneHistories.computeIfAbsent(sceneKey, ignored -> new UndoManager());
    }

    public void markUiToEditorTransition(String ponderKey) {
        switchToPonder(ponderKey);
        preserveOnNextPonderUiRemoval = true;
    }

    public void handlePonderUiRemoved(String ponderKey) {
        if (!Objects.equals(activePonderKey, ponderKey)) {
            return;
        }
        if (preserveOnNextPonderUiRemoval) {
            preserveOnNextPonderUiRemoval = false;
            return;
        }
        clear();
    }

    public void clearIfLeavingPonder(String ponderKey) {
        if (activePonderKey != null && !Objects.equals(activePonderKey, ponderKey)) {
            clear();
        }
    }

    public void clearPonder(String ponderKey) {
        if (Objects.equals(activePonderKey, ponderKey)) {
            sceneHistories.clear();
            preserveOnNextPonderUiRemoval = false;
        }
    }

    public void switchToPonder(String ponderKey) {
        if (Objects.equals(activePonderKey, ponderKey)) {
            return;
        }
        activePonderKey = ponderKey;
        sceneHistories.clear();
        preserveOnNextPonderUiRemoval = false;
    }

    public void clear() {
        activePonderKey = null;
        sceneHistories.clear();
        preserveOnNextPonderUiRemoval = false;
    }
}
