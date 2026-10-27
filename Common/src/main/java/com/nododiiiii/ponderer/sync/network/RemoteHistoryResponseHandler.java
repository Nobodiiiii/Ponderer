package com.nododiiiii.ponderer.sync.network;

import com.nododiiiii.ponderer.sync.server.RemoteWorkspaceService;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import com.nododiiiii.ponderer.sync.protocol.RemoteHistoryResponsePayload;
import static com.nododiiiii.ponderer.sync.protocol.RemoteHistoryResponsePayload.*;

/** Business dispatch; the payload remains a codec-only transport value. */
public final class RemoteHistoryResponseHandler {
    private RemoteHistoryResponseHandler() {}

    public static RemoteHistoryResponsePayload fromEntries(String kind, String id, @Nullable String pack,
                                                           List<RemoteWorkspaceService.HistoryEntry> entries,
                                                           boolean canManage) {
        return new RemoteHistoryResponsePayload(kind, id, pack,
            entries.stream()
                .map(entry -> new Entry(entry.revision(), entry.action(), entry.actor(), entry.createdAt(), entry.hash(), entry.size()))
                .toList(),
            canManage);
    }


}
