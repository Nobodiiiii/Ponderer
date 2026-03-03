package com.nododiiiii.ponderer.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Snapshot-based undo/redo manager for the scene editor.
 * Captures full step list state as JSON before each mutation.
 */
public class UndoManager {
    private static final int MAX_HISTORY = 50;
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();
    private static final Type STEP_LIST_TYPE = new TypeToken<List<DslScene.DslStep>>(){}.getType();

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    /** Call before any mutation to capture the current state. */
    public void saveState(List<DslScene.DslStep> steps) {
        String json = GSON.toJson(steps, STEP_LIST_TYPE);
        undoStack.push(json);
        if (undoStack.size() > MAX_HISTORY) {
            ((ArrayDeque<String>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    /** Undo: restore previous state, push current to redo stack. Returns null if nothing to undo. */
    public List<DslScene.DslStep> undo(List<DslScene.DslStep> currentSteps) {
        if (undoStack.isEmpty()) return null;
        String currentJson = GSON.toJson(currentSteps, STEP_LIST_TYPE);
        redoStack.push(currentJson);
        String previousJson = undoStack.pop();
        return GSON.fromJson(previousJson, STEP_LIST_TYPE);
    }

    /** Redo: restore next state, push current to undo stack. Returns null if nothing to redo. */
    public List<DslScene.DslStep> redo(List<DslScene.DslStep> currentSteps) {
        if (redoStack.isEmpty()) return null;
        String currentJson = GSON.toJson(currentSteps, STEP_LIST_TYPE);
        undoStack.push(currentJson);
        String nextJson = redoStack.pop();
        return GSON.fromJson(nextJson, STEP_LIST_TYPE);
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
