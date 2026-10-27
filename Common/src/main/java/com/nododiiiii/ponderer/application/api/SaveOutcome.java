package com.nododiiiii.ponderer.application.api;

import com.nododiiiii.ponderer.workspace.api.WorkspaceResults.LocalSaveResult;
import javax.annotation.Nullable;

/** Persistence success remains distinct from a subsequent catalog/refresh failure. */
public record SaveOutcome(LocalSaveResult persistence, long catalogVersion,
                          @Nullable RuntimeException refreshFailure) {
    public boolean persisted() { return persistence.isSuccess(); }
}
