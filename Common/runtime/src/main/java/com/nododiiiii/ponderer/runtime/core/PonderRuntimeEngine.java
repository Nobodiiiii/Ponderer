package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.*;
import com.nododiiiii.ponderer.runtime.spi.*;
import net.createmod.ponder.api.registration.PonderPlugin;

/** Minimal runtime assembly. A host may supply memory documents and stream-only structures. */
public final class PonderRuntimeEngine implements RuntimeEngine {
    private final java.util.Set<OwnedSession> sessions = new java.util.HashSet<>();
    private boolean closed;
    private final JavaFragments fragments = new JavaFragments();
    private final StepRegistry steps = new StepRegistry();
    private final SceneCatalog catalog = new SceneCatalog();
    private final SceneCompiler compiler;
    private final DynamicPonderPlugin plugin;
    public PonderRuntimeEngine(String namespace, StructureSource structures,
                               IngredientResolver ingredients, RegistrationContribution contribution,
                               java.util.function.BooleanSupplier defaultEditable) {
        compiler = new SceneCompiler(namespace, structures, ingredients, defaultEditable, steps, fragments);
        plugin = new DynamicPonderPlugin(catalog, compiler, contribution, structures);
    }
    @Override public SceneCatalog catalog() { return catalog; }
    @Override public StepRegistry steps() { return steps; }
    @Override public JavaFragments fragments() { return fragments; }
    @Override public net.createmod.ponder.api.scene.PonderStoryBoard storyboard(com.nododiiiii.ponderer.scene.SceneSnapshot snapshot, int segmentIndex) {
        if (closed) throw new IllegalStateException("Runtime engine is closed");
        var document = snapshot.document();
        SceneRequirements.require(document, steps);
        var segments = compiler.normalizeScenes(document);
        return compiler.createStoryBoard(document, segments.get(segmentIndex), segmentIndex, segments.size());
    }
    @Override public SceneProgram program(String sceneId, String segmentId, net.createmod.ponder.api.scene.SceneBuilder builder, net.createmod.ponder.api.scene.SceneBuildingUtil util) {
        if (closed) throw new IllegalStateException("Runtime engine is closed");
        return compiler.program(sceneId, segmentId, builder, util);
    }
    @Override public void replaceCatalog(SceneSource source) {
        if (closed) throw new IllegalStateException("Runtime engine is closed");
        com.nododiiiii.ponderer.runtime.support.lifecycle.Cleanup.all(new java.util.ArrayList<>(sessions).stream().<Runnable>map(session -> session::close).toArray(Runnable[]::new));
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
            com.nododiiiii.ponderer.runtime.support.lifecycle.Cleanup.all(new java.util.ArrayList<>(sessions).stream()
                .<Runnable>map(session -> session::close).toArray(Runnable[]::new));
        } finally {
            sessions.clear();
            steps.clear();
            fragments.clear();
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
            com.nododiiiii.ponderer.runtime.support.lifecycle.Cleanup.all(
                result.playback()::close,
                () -> { if (RuntimeFeatures.interfacesEnabled()) com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost.hide(result.scene()); },
                () -> com.nododiiiii.ponderer.runtime.render.PonderRenderCache.releaseDiscardedScene(result.scene()));
        }
    }
}
