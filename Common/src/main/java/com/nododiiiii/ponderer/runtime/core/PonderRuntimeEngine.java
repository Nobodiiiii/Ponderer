package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.*;
import com.nododiiiii.ponderer.runtime.spi.*;
import net.createmod.ponder.api.registration.PonderPlugin;

/** Minimal runtime assembly. A host may supply memory documents and stream-only structures. */
public final class PonderRuntimeEngine implements RuntimeEngine {
    private final SceneCatalog catalog = new SceneCatalog();
    private final SceneCompiler compiler;
    private final PonderPlugin plugin;
    public PonderRuntimeEngine(String namespace, StructureSource structures,
                               IngredientResolver ingredients, RegistrationContribution contribution,
                               java.util.function.BooleanSupplier defaultEditable) {
        compiler = new SceneCompiler(namespace, structures, ingredients, defaultEditable);
        plugin = new DynamicPonderPlugin(catalog, compiler, contribution);
    }
    @Override public SceneCatalog catalog() { return catalog; }
    @Override public void replaceCatalog(SceneSource source) {
        catalog.setScenes(source.scenes().stream().map(snapshot -> snapshot.document()).toList());
    }
    @Override public CompilationResult compile(CompileRequest request) {
        return compiler.compile(request.scene().document(), request.segmentIndex());
    }
    @Override public PonderPlugin plugin() { return plugin; }
    @Override public void close() { catalog.setScenes(java.util.List.of()); catalog.clearPonderIdMapping(); }
}
