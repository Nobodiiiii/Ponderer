package com.nododiiiii.ponderer.application.ui;

/** Formatting of persistence outcomes for product screens. */
public final class WorkspaceMessages {
    private WorkspaceMessages() {}
    public static String saveError(com.nododiiiii.ponderer.workspace.api.WorkspaceResults.LocalSaveResult result) {
        if (result == null) {
            return com.nododiiiii.ponderer.support.ui.UIText.of("ponderer.ui.save_error.io", "I/O error");
        }
        String key = result.uiMessageKey();
        if (key == null || key.isBlank()) {
            return com.nododiiiii.ponderer.support.ui.UIText.of("ponderer.ui.save_error.io", result.englishMessage());
        }
        return com.nododiiiii.ponderer.support.ui.UIText.of(key, result.uiMessageArgs());
    }
}
