package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.*;
import com.nododiiiii.ponderer.runtime.spi.*;
import net.createmod.ponder.api.registration.PonderPlugin;

/** Minimal runtime assembly. A host may supply memory documents and stream-only structures. */
public final class PonderRuntimeEngine implements RuntimeEngine {
    private final java.util.Set<OwnedSession> sessions = new java.util.HashSet<>();
    private boolean closed;
    private final SceneCatalog catalog = new SceneCatalog();
    private final SceneCompiler compiler;
    private final DynamicPonderPlugin plugin;
    public PonderRuntimeEngine(String namespace, StructureSource structures,
                               IngredientResolver ingredients, RegistrationContribution contribution,
                               java.util.function.BooleanSupplier defaultEditable) {
        compiler = new SceneCompiler(namespace, structures, ingredients, defaultEditable);
        plugin = new DynamicPonderPlugin(catalog, compiler, contribution, structures);
    }
    @Override public SceneCatalog catalog() { return catalog; }
    @Override public void replaceCatalog(SceneSource source) {
        if (closed) throw new IllegalStateException("Runtime engine is closed");
        catalog.setScenes(source.scenes().stream().map(snapshot -> snapshot.document()).toList());
    }
    @Override public CompilationResult compile(CompileRequest request) {
        if (closed) throw new IllegalStateException("Runtime engine is closed");
        CompilationResult result = compiler.compile(request.scene().document(), request.segmentIndex());
        if (result == null) return null;
        OwnedSession session = new OwnedSession(result);
        sessions.add(session);
        return new CompilationResult(result.scene(), result.stepTicks(), session);
    }
    @Override public PonderPlugin plugin() { return plugin; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            com.nododiiiii.ponderer.support.lifecycle.Cleanup.all(new java.util.ArrayList<>(sessions).stream()
                .<Runnable>map(session -> session::close).toArray(Runnable[]::new));
        } finally {
            sessions.clear();
            catalog.setScenes(java.util.List.of());
            catalog.clearPonderIdMapping();
            plugin.close();
        }
    }
    private final class OwnedSession implements PlaybackSession {
        private final CompilationResult result;
        private boolean released;
        private OwnedSession(CompilationResult result) { this.result = result; }
        @Override public void previewStep(int index) { result.playback().previewStep(index); }
        @Override public boolean isPreviewingStep() { return result.playback().isPreviewingStep(); }
        @Override public boolean isStepComplete() { return result.playback().isStepComplete(); }
        @Override public void resumeTimeline() { result.playback().resumeTimeline(); }
        @Override public void close() {
            if (released) return;
            released = true;
            sessions.remove(this);
            com.nododiiiii.ponderer.support.lifecycle.Cleanup.all(
                result.playback()::close,
                () -> com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost.hide(result.scene()),
                () -> com.nododiiiii.ponderer.runtime.render.PonderRenderCache.releaseDiscardedScene(result.scene()));
        }
    }
}
