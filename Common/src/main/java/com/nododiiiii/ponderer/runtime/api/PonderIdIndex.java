package com.nododiiiii.ponderer.runtime.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registration-order identity mapping, separate from scene document storage. */
final class PonderIdIndex {
    record SceneKeyEntry(String sceneKey, int sceneIndex) {}
    final Map<String, List<SceneKeyEntry>> entries = new ConcurrentHashMap<>();
    void clear() { entries.clear(); }
    void register(String id, String key, int segment) {
        entries.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new SceneKeyEntry(key, segment));
    }
}
