package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.RemoteActionResponsePayload;
import com.nododiiiii.ponderer.network.RemoteCatalogRequestPayload;
import com.nododiiiii.ponderer.network.RemoteCatalogResponsePayload;
import com.nododiiiii.ponderer.network.RemoteDeleteRequestPayload;
import com.nododiiiii.ponderer.network.RemoteHistoryRequestPayload;
import com.nododiiiii.ponderer.network.RemoteHistoryResponsePayload;
import com.nododiiiii.ponderer.network.RemotePullRequestPayload;
import com.nododiiiii.ponderer.network.RemoteRollbackRequestPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.DynamicActionRowListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.ui.catnip.WorkspaceHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class RemoteBrowserScreen extends AbstractReadonlyDeclarativeListScreen {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private enum Tab {
        SCENES, STRUCTURES, PACKS, HISTORY
    }

    private Tab tab = Tab.SCENES;
    private boolean requestSent;
    private boolean waitingForServer = true;
    private boolean canPull;
    private boolean canUpload;
    private boolean canManage;
    private List<RemoteCatalogResponsePayload.Entry> scenes = List.of();
    private List<RemoteCatalogResponsePayload.Entry> structures = List.of();
    private List<RemoteCatalogResponsePayload.Entry> packs = List.of();
    private List<RemoteHistoryResponsePayload.Entry> history = List.of();
    private String historyKind = "";
    private String historyId = "";
    @Nullable
    private String historyPack;
    private boolean historyCanManage;

    public RemoteBrowserScreen(@Nullable Screen parent) {
        super(parent, "ponderer.ui.scope.server", "ponderer.ui.function_page.remote_browser.title",
            UILayoutConstants.EDITOR_LIST_W + 80);
    }

    @Override
    protected void init() {
        super.init();
        if (!requestSent) {
            requestCatalog();
        }
    }

    public void receiveCatalog(RemoteCatalogResponsePayload payload) {
        waitingForServer = false;
        scenes = payload.scenes();
        structures = payload.structures();
        packs = payload.packs();
        canPull = payload.canPull();
        canUpload = payload.canUpload();
        canManage = payload.canManage();
        if (payload.message() != null && !payload.message().isBlank()) {
            if (payload.error()) {
                setErrorMessage(payload.message());
            } else {
                setInfoMessage(payload.message());
            }
        } else {
            setInfoMessage("Remote catalog loaded: " + scenes.size() + " scenes, " + structures.size() + " structures");
        }
        rebuildListPreservingScroll();
    }

    public void receiveAction(RemoteActionResponsePayload payload) {
        if (payload.success()) {
            setInfoMessage(payload.message());
        } else {
            setErrorMessage(payload.message());
        }
        if (payload.refreshCatalog()) {
            requestCatalog();
        } else {
            rebuildListPreservingScroll();
        }
    }

    public void receiveUploadResult(boolean success, String message) {
        if (success) {
            setInfoMessage(message);
            requestCatalog();
        } else {
            setErrorMessage(message);
            rebuildListPreservingScroll();
        }
    }

    public void receiveHistory(RemoteHistoryResponsePayload payload) {
        historyKind = payload.kind();
        historyId = payload.id();
        historyPack = payload.pack();
        history = payload.entries();
        historyCanManage = payload.canManage();
        tab = Tab.HISTORY;
        setInfoMessage("History loaded: " + displayKey(historyId, historyPack));
        rebuildListAtTop();
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new WorkspaceHeaderListEntry(
            () -> "Remote Browser",
            this::subtitle,
            List.of(WorkspaceHeaderListEntry.button("R", this::requestCatalog, "Refresh remote catalog", () -> true))));
        entries.add(new ActionStripListEntry(List.of(
            ActionStripListEntry.button("Scenes", null, () -> switchTab(Tab.SCENES)),
            ActionStripListEntry.button("Structs", null, () -> switchTab(Tab.STRUCTURES)),
            ActionStripListEntry.button("Packs", null, () -> switchTab(Tab.PACKS)),
            ActionStripListEntry.button("History", null, () -> switchTab(Tab.HISTORY)))));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (waitingForServer) {
            entries.add(new SectionHeaderListEntry("Loading remote catalog..."));
            return;
        }

        switch (tab) {
            case SCENES -> collectSceneEntries(entries);
            case STRUCTURES -> collectStructureEntries(entries);
            case PACKS -> collectPackEntries(entries);
            case HISTORY -> collectHistoryEntries(entries);
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.LIST_ENTRY_H;
    }

    private void collectSceneEntries(List<ConfigScreenList.Entry> entries) {
        if (scenes.isEmpty()) {
            entries.add(new SectionHeaderListEntry("No remote scenes"));
            return;
        }
        for (RemoteCatalogResponsePayload.Entry entry : scenes) {
            entries.add(new DynamicActionRowListEntry(
                () -> sceneTitle(entry),
                () -> resourceDetail(entry),
                () -> searchText(entry),
                List.of(
                    ActionStripListEntry.button(() -> "Pull", null, () -> pull(entry, true), () -> 0xA8E6FF, () -> canPull),
                    ActionStripListEntry.button(() -> "Upload", null, () -> uploadLocal(entry), () -> 0xB5F5A8,
                        () -> canUpload && hasLocalScene(entry)),
                    ActionStripListEntry.button(() -> "Hist", null, () -> requestHistory(entry), () -> 0xFFFFFF, () -> true),
                    ActionStripListEntry.button(() -> "Del", null, () -> delete(entry), () -> 0xFF9A9A, () -> canManage))));
        }
    }

    private void collectStructureEntries(List<ConfigScreenList.Entry> entries) {
        if (structures.isEmpty()) {
            entries.add(new SectionHeaderListEntry("No remote structures"));
            return;
        }
        for (RemoteCatalogResponsePayload.Entry entry : structures) {
            entries.add(new DynamicActionRowListEntry(
                () -> displayKey(entry.id(), entry.pack()),
                () -> resourceDetail(entry),
                () -> searchText(entry),
                List.of(
                    ActionStripListEntry.button(() -> "Pull", null, () -> pull(entry, false), () -> 0xA8E6FF, () -> canPull),
                    ActionStripListEntry.button(() -> "Hist", null, () -> requestHistory(entry), () -> 0xFFFFFF, () -> true),
                    ActionStripListEntry.button(() -> "Del", null, () -> delete(entry), () -> 0xFF9A9A, () -> canManage))));
        }
    }

    private void collectPackEntries(List<ConfigScreenList.Entry> entries) {
        if (packs.isEmpty()) {
            entries.add(new SectionHeaderListEntry("No remote packs"));
            return;
        }
        for (RemoteCatalogResponsePayload.Entry entry : packs) {
            entries.add(new DynamicActionRowListEntry(
                entry::id,
                entry::summary,
                () -> entry.id() + " " + entry.summary(),
                List.of(ActionStripListEntry.button(() -> "Pull", null,
                    () -> pullPack(entry.id()), () -> 0xA8E6FF, () -> canPull))));
        }
    }

    private void collectHistoryEntries(List<ConfigScreenList.Entry> entries) {
        if (historyId.isBlank()) {
            entries.add(new SectionHeaderListEntry("Select a remote resource and press Hist"));
            return;
        }
        entries.add(new SectionHeaderListEntry("History: " + displayKey(historyId, historyPack)));
        if (history.isEmpty()) {
            entries.add(new SectionHeaderListEntry("No history yet. Upload, delete, or rollback will create revisions."));
            return;
        }
        for (RemoteHistoryResponsePayload.Entry entry : history) {
            entries.add(new DynamicActionRowListEntry(
                () -> "#" + entry.revision() + " " + entry.action(),
                () -> historyDetail(entry),
                () -> entry.revision() + " " + entry.action() + " " + entry.actor(),
                List.of(ActionStripListEntry.button(() -> "Rollback", null,
                    () -> rollback(entry.revision()), () -> 0xFFE08A, () -> historyCanManage))));
        }
    }

    private void switchTab(Tab next) {
        tab = next;
        rebuildListAtTop();
    }

    private void requestCatalog() {
        requestSent = true;
        waitingForServer = true;
        setInfoMessage("Loading remote catalog...");
        PondererServices.NETWORK.sendToServer(new RemoteCatalogRequestPayload());
        if (list != null) {
            rebuildListPreservingScroll();
        }
    }

    private void pull(RemoteCatalogResponsePayload.Entry entry, boolean includeDependencies) {
        setInfoMessage("Pulling " + displayKey(entry.id(), entry.pack()) + "...");
        PondererServices.NETWORK.sendToServer(
            new RemotePullRequestPayload(entry.kind(), entry.id(), entry.pack(), includeDependencies));
    }

    private void pullPack(String pack) {
        setInfoMessage("Pulling remote pack " + pack + "...");
        PondererServices.NETWORK.sendToServer(
            new RemotePullRequestPayload(RemoteWorkspaceService.KIND_PACK, pack, null, true));
    }

    private void uploadLocal(RemoteCatalogResponsePayload.Entry entry) {
        if (!hasLocalScene(entry)) {
            setErrorMessage("No matching local scene: " + displayKey(entry.id(), entry.pack()));
            return;
        }
        setInfoMessage("Uploading local scene " + displayKey(entry.id(), entry.pack()) + "...");
        PondererClientCommands.pushByKey(displayKey(entry.id(), entry.pack()), "check");
    }

    private void delete(RemoteCatalogResponsePayload.Entry entry) {
        setInfoMessage("Deleting " + displayKey(entry.id(), entry.pack()) + "...");
        PondererServices.NETWORK.sendToServer(new RemoteDeleteRequestPayload(entry.kind(), entry.id(), entry.pack()));
    }

    private void requestHistory(RemoteCatalogResponsePayload.Entry entry) {
        setInfoMessage("Loading history for " + displayKey(entry.id(), entry.pack()) + "...");
        PondererServices.NETWORK.sendToServer(new RemoteHistoryRequestPayload(entry.kind(), entry.id(), entry.pack()));
    }

    private void rollback(int revision) {
        setInfoMessage("Rolling back " + displayKey(historyId, historyPack) + "...");
        PondererServices.NETWORK.sendToServer(new RemoteRollbackRequestPayload(historyKind, historyId, historyPack, revision));
    }

    private boolean hasLocalScene(RemoteCatalogResponsePayload.Entry entry) {
        return RemoteWorkspaceService.KIND_SCENE.equals(entry.kind())
            && SceneRuntime.findByKey(displayKey(entry.id(), entry.pack())) != null;
    }

    private String sceneTitle(RemoteCatalogResponsePayload.Entry entry) {
        String title = entry.title() == null || entry.title().isBlank() ? entry.id() : entry.title();
        return displayKey(entry.id(), entry.pack()) + "  " + title;
    }

    private String resourceDetail(RemoteCatalogResponsePayload.Entry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.revision() > 0) {
            parts.add("rev " + entry.revision());
        }
        if (entry.size() > 0) {
            parts.add(formatBytes(entry.size()));
        }
        if (entry.updatedAt() > 0) {
            parts.add(TIME_FORMAT.format(Instant.ofEpochMilli(entry.updatedAt())));
        }
        if (entry.dependencyCount() > 0) {
            parts.add(entry.dependencyCount() + " deps");
        }
        if (entry.refCount() > 0) {
            parts.add(entry.refCount() + " refs");
        }
        if (parts.isEmpty() && entry.summary() != null && !entry.summary().isBlank()) {
            parts.add(entry.summary());
        }
        return String.join(" | ", parts);
    }

    private String historyDetail(RemoteHistoryResponsePayload.Entry entry) {
        String time = entry.createdAt() <= 0 ? "" : TIME_FORMAT.format(Instant.ofEpochMilli(entry.createdAt()));
        String actor = entry.actor() == null || entry.actor().isBlank() ? "server" : entry.actor();
        return actor + (time.isBlank() ? "" : " | " + time) + " | " + formatBytes(entry.size());
    }

    private String subtitle() {
        return switch (tab) {
            case SCENES -> scenes.size() + " remote scenes";
            case STRUCTURES -> structures.size() + " remote structures";
            case PACKS -> packs.size() + " remote packs";
            case HISTORY -> historyId.isBlank() ? "No history selected" : displayKey(historyId, historyPack);
        };
    }

    private static String searchText(RemoteCatalogResponsePayload.Entry entry) {
        return String.join(" ",
            List.of(
                Objects.toString(entry.id(), ""),
                Objects.toString(entry.pack(), ""),
                Objects.toString(entry.title(), ""),
                Objects.toString(entry.summary(), ""))).toLowerCase(Locale.ROOT);
    }

    private static String displayKey(String id, @Nullable String pack) {
        if (pack == null || pack.isBlank()) {
            return id;
        }
        return "[" + pack + "] " + id;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format(Locale.ROOT, "%.1f KiB", kib);
        }
        return String.format(Locale.ROOT, "%.1f MiB", kib / 1024.0);
    }
}
