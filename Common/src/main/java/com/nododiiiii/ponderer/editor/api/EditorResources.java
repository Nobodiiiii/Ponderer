package com.nododiiiii.ponderer.editor.api;

import java.util.*;

/** Weak ownership avoids retaining closed screens; explicit reset releases live previews. */
public final class EditorResources {
    private static final Set<AutoCloseable> previews = Collections.newSetFromMap(new WeakHashMap<>());
    private EditorResources() {}
    public static void track(AutoCloseable preview) { previews.add(preview); }
    public static void release(AutoCloseable preview) { previews.remove(preview); }
    public static void closePreviews() {
        RuntimeException failure = null;
        for (AutoCloseable preview : new ArrayList<>(previews)) {
            try { preview.close(); } catch (Exception error) {
                if (failure == null) failure = new IllegalStateException("Failed to release editor preview");
                failure.addSuppressed(error);
            }
        }
        previews.clear();
        if (failure != null) throw failure;
    }
}
