package com.nododiiiii.ponderer.workspace.api;

public interface Workspace {
    WorkspaceLayoutView layout();
    SceneRepository scenes();
    StructureRepository structures();
    PackService packs();
    PackState packState();
    PackRegistry registry();
}
