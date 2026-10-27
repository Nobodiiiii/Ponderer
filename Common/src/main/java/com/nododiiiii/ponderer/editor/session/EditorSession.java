package com.nododiiiii.ponderer.editor.session;

import com.nododiiiii.ponderer.editor.api.EditCommand;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.SceneSnapshot;
import java.util.ArrayList;
import java.util.List;

/** Owns a detached mutable document and its selected segment; runtime sees only snapshots. */
public final class EditorSession {
    private final DslScene draft;
    private int segment;
    private long version;
    public EditorSession(DslScene draft, int segment) { this.draft = draft; this.segment = segment; }
    public DslScene draft() { return draft; }
    public long version() { return version; }
    public SceneSnapshot snapshot() { return SceneSnapshot.of(draft); }
    public void selectSegment(int segment) { this.segment = segment; }
    public List<DslScene.DslStep> steps() {
        if (draft.scenes == null || segment < 0 || segment >= draft.scenes.size()) return List.of();
        var steps = draft.scenes.get(segment).steps;
        return steps == null ? List.of() : steps;
    }
    public List<DslScene.DslStep> mutableSteps() {
        if (draft.scenes == null || segment < 0 || segment >= draft.scenes.size()) return new ArrayList<>();
        var selected = draft.scenes.get(segment);
        if (!(selected.steps instanceof ArrayList)) selected.steps = new ArrayList<>(steps());
        return selected.steps;
    }
    public void execute(EditCommand command, UndoManager history) {
        history.saveState(steps());
        command.apply(mutableSteps());
        version++;
    }
    public void replaceSteps(List<DslScene.DslStep> steps) {
        if (draft.scenes != null && segment >= 0 && segment < draft.scenes.size()) {
            draft.scenes.get(segment).steps = new ArrayList<>(steps);
            version++;
        }
    }
    public boolean undo(UndoManager history) {
        var restored = history.undo(steps());
        if (restored == null) return false;
        replaceSteps(restored);
        return true;
    }
    public boolean redo(UndoManager history) {
        var restored = history.redo(steps());
        if (restored == null) return false;
        replaceSteps(restored);
        return true;
    }
}
