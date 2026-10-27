package com.nododiiiii.ponderer.workspace.local;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** A product workspace with explicit roots and normalization policy. */
public final class WorkspaceFiles {
    final Path configDir;
    final Path gameDir;
    final BooleanSupplier defaultEditable;
    final WorkspaceLayout layout;
    final LocalSceneRepository scenes;
    final LocalStructureRepository structures;
    final LocalPackService packs;
    final PackStateStore packState;
    final PonderPackRegistry registry;

    public WorkspaceFiles(Path configDir, Path gameDir, BooleanSupplier defaultEditable) {
        this.configDir = configDir;
        this.gameDir = gameDir;
        this.defaultEditable = defaultEditable;
        layout = new WorkspaceLayout(this);
        scenes = new LocalSceneRepository(this);
        structures = new LocalStructureRepository(this);
        packs = new LocalPackService(this);
        packState = new PackStateStore(this);
        registry = new PonderPackRegistry(this);
    }
    public WorkspaceLayout layout() { return layout; }
    public LocalSceneRepository scenes() { return scenes; }
    public LocalStructureRepository structures() { return structures; }
    public LocalPackService packs() { return packs; }
    public PackStateStore packState() { return packState; }
    public PonderPackRegistry registry() { return registry; }
}
