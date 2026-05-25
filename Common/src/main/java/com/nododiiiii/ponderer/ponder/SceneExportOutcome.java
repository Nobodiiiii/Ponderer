package com.nododiiiii.ponderer.ponder;

import java.util.ArrayList;
import java.util.List;

public class SceneExportOutcome {

    public enum Status {
        COMPLETE,
        PARTIAL,
        BLANK,
        SKIPPED_SCENE
    }

    public String sceneId;
    public String sceneKey;
    public String className;
    public Status status = Status.COMPLETE;
    public boolean replaced;
    public int exportedSegments;
    public int blankSegments;
    public int omittedSteps;
    public List<String> ownedFiles = new ArrayList<>();
    public List<String> messages = new ArrayList<>();
}
