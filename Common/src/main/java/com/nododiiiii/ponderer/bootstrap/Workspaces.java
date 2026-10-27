package com.nododiiiii.ponderer.bootstrap;

import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.workspace.local.WorkspaceFiles;

/** Product composition root. Runtime hosts never use the product filesystem. */
public final class Workspaces {
    private Workspaces() {}
    private static final class Holder {
        static final WorkspaceFiles FILES = new WorkspaceFiles(
            PondererServices.PLATFORM.getConfigDir(), PondererServices.PLATFORM.getGameDir(),
            () -> Config.DEFAULT_EDITABLE.get(), com.nododiiiii.ponderer.sync.client.SyncMeta::recordLocalSceneDeletion);
    }
    public static WorkspaceFiles files() { return Holder.FILES; }
}
