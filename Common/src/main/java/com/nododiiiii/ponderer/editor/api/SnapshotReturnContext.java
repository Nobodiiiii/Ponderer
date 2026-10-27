package com.nododiiiii.ponderer.editor.api;

import java.util.Map;

public interface SnapshotReturnContext {

    void reopenEditor(Map<String, String> snapshot);
}
