package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import com.nododiiiii.ponderer.sync.protocol.RemoteCatalogResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteCatalogResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteCatalogResponseHandler {
    private RemoteCatalogResponseHandler() {}

    public static RemoteCatalogResponsePayload fromSnapshot(RemoteWorkspaceService.CatalogSnapshot snapshot,
                                                            String message, boolean error) {
        return new RemoteCatalogResponsePayload(
            snapshot.scenes().stream().map(RemoteCatalogResponseHandler::fromServiceEntry).toList(),
            snapshot.structures().stream().map(RemoteCatalogResponseHandler::fromServiceEntry).toList(),
            snapshot.packs().stream().map(RemoteCatalogResponseHandler::fromServiceEntry).toList(),
            snapshot.canPull(),
            snapshot.canUpload(),
            snapshot.canManage(),
            message == null ? "" : message,
            error);
    }

    private static Entry fromServiceEntry(RemoteWorkspaceService.CatalogEntry entry) {
        return new Entry(entry.kind(), entry.id(), entry.pack(), entry.title(), entry.summary(), entry.hash(),
            entry.revision(), entry.size(), entry.updatedAt(), entry.updatedBy(), entry.dependencyCount(), entry.refCount());
    }
}
