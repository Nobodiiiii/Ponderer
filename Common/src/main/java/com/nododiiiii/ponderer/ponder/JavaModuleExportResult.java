package com.nododiiiii.ponderer.ponder;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaModuleExportResult {
    public boolean success;
    @Nullable
    public String errorMessage;
    @Nullable
    public Path reportPath;
    public int addedCount;
    public int replacedCount;
    public int partialCount;
    public int blankCount;
    public int skippedCount;
    public List<SceneExportOutcome> sceneOutcomes = new ArrayList<>();

    public static JavaModuleExportResult failure(String message) {
        JavaModuleExportResult result = new JavaModuleExportResult();
        result.success = false;
        result.errorMessage = message;
        return result;
    }
}
