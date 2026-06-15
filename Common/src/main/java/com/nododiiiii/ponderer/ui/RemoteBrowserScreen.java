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
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.RemoteWorkspaceService;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ponder.SyncMeta;
import com.nododiiiii.ponderer.ui.catnip.ActionStripListEntry;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.DynamicActionRowListEntry;
import com.nododiiiii.ponderer.ui.catnip.PonderIconStencils;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import com.nododiiiii.ponderer.ui.catnip.WorkspaceHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class RemoteBrowserScreen extends AbstractReadonlyDeclarativeListScreen {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private enum Tab {
        SCENES, STRUCTURES, PACKS, HISTORY
    }

    private record LocalComparison(boolean priority, String detail) {
        static LocalComparison none() {
            return new LocalComparison(false, "");
        }
    }

    private record RemoteRow(RemoteCatalogResponsePayload.Entry entry, LocalComparison comparison, ItemStack iconStack) {
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
            setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.catalog_loaded", scenes.size(), structures.size()));
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
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.history_loaded", displayKey(historyId, historyPack)));
        rebuildListAtTop();
    }

    @Override
    protected void collectHeaderEntries(List<ConfigScreenList.Entry> entries) {
        entries.add(new WorkspaceHeaderListEntry(
            () -> UIText.of("ponderer.ui.function_page.remote_browser.title"),
            this::subtitle,
            List.of(WorkspaceHeaderListEntry.iconButton(
                PonderIconStencils.centered(PonderGuiTextures.ICON_CONFIG_RESET),
                this::requestCatalog,
                tooltip("ponderer.ui.remote_browser.refresh.tooltip"),
                () -> true))));
        entries.add(new ActionStripListEntry(List.of(
            ActionStripListEntry.button(() -> UIText.of("ponderer.ui.remote_browser.tab.scenes"), null, () -> switchTab(Tab.SCENES), () -> 0xFFFFFF, () -> true),
            ActionStripListEntry.button(() -> UIText.of("ponderer.ui.remote_browser.tab.structures"), null, () -> switchTab(Tab.STRUCTURES), () -> 0xFFFFFF, () -> true),
            ActionStripListEntry.button(() -> UIText.of("ponderer.ui.remote_browser.tab.packs"), null, () -> switchTab(Tab.PACKS), () -> 0xFFFFFF, () -> true),
            ActionStripListEntry.button(() -> UIText.of("ponderer.ui.remote_browser.tab.history"), null, () -> switchTab(Tab.HISTORY), () -> 0xFFFFFF, () -> true))));
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (waitingForServer) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.loading")));
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
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.empty.scenes")));
            return;
        }
        for (RemoteRow row : remoteRows(scenes)) {
            RemoteCatalogResponsePayload.Entry entry = row.entry();
            entries.add(new DynamicActionRowListEntry(
                () -> sceneTitle(entry),
                () -> resourceDetail(entry, row.comparison()),
                () -> searchText(entry),
                List.of(
                    actionButton("ponderer.ui.remote_browser.action.pull", "ponderer.ui.remote_browser.action.pull.tooltip",
                        () -> pull(entry, true), 0xA8E6FF, () -> canPull),
                    actionButton("ponderer.ui.remote_browser.action.upload", "ponderer.ui.remote_browser.action.upload.tooltip",
                        () -> uploadLocal(entry), 0xB5F5A8, () -> canUpload && hasLocalScene(entry)),
                    actionButton("ponderer.ui.remote_browser.action.history", "ponderer.ui.remote_browser.action.history.tooltip",
                        () -> requestHistory(entry), 0xFFFFFF, () -> true),
                    actionButton("ponderer.ui.remote_browser.action.delete", "ponderer.ui.remote_browser.action.delete.tooltip",
                        () -> delete(entry), 0xFF9A9A, () -> canManage)),
                row::iconStack,
                row.comparison()::priority));
        }
    }

    private void collectStructureEntries(List<ConfigScreenList.Entry> entries) {
        if (structures.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.empty.structures")));
            return;
        }
        for (RemoteRow row : remoteRows(structures)) {
            RemoteCatalogResponsePayload.Entry entry = row.entry();
            entries.add(new DynamicActionRowListEntry(
                () -> displayKey(entry.id(), entry.pack()),
                () -> resourceDetail(entry, row.comparison()),
                () -> searchText(entry),
                List.of(
                    actionButton("ponderer.ui.remote_browser.action.pull", "ponderer.ui.remote_browser.action.pull.tooltip",
                        () -> pull(entry, false), 0xA8E6FF, () -> canPull),
                    actionButton("ponderer.ui.remote_browser.action.history", "ponderer.ui.remote_browser.action.history.tooltip",
                        () -> requestHistory(entry), 0xFFFFFF, () -> true),
                    actionButton("ponderer.ui.remote_browser.action.delete", "ponderer.ui.remote_browser.action.delete.tooltip",
                        () -> delete(entry), 0xFF9A9A, () -> canManage)),
                row::iconStack,
                row.comparison()::priority));
        }
    }

    private void collectPackEntries(List<ConfigScreenList.Entry> entries) {
        if (packs.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.empty.packs")));
            return;
        }
        for (RemoteRow row : remoteRows(packs)) {
            RemoteCatalogResponsePayload.Entry entry = row.entry();
            entries.add(new DynamicActionRowListEntry(
                entry::id,
                () -> resourceDetail(entry, row.comparison()),
                () -> entry.id() + " " + entry.summary(),
                List.of(actionButton("ponderer.ui.remote_browser.action.pull", "ponderer.ui.remote_browser.action.pull_pack.tooltip",
                    () -> pullPack(entry.id()), 0xA8E6FF, () -> canPull)),
                row::iconStack,
                row.comparison()::priority));
        }
    }

    private void collectHistoryEntries(List<ConfigScreenList.Entry> entries) {
        if (historyId.isBlank()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.history.select")));
            return;
        }
        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.history.title",
            displayKey(historyId, historyPack))));
        if (history.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.remote_browser.history.empty")));
            return;
        }
        for (RemoteHistoryResponsePayload.Entry entry : history) {
            entries.add(new DynamicActionRowListEntry(
                () -> UIText.of("ponderer.ui.remote_browser.history.row", entry.revision(), entry.action()),
                () -> historyDetail(entry),
                () -> entry.revision() + " " + entry.action() + " " + entry.actor(),
                List.of(actionButton("ponderer.ui.remote_browser.action.rollback", "ponderer.ui.remote_browser.action.rollback.tooltip",
                    () -> rollback(entry.revision()), 0xFFE08A, () -> historyCanManage))));
        }
    }

    private void switchTab(Tab next) {
        tab = next;
        rebuildListAtTop();
    }

    private void requestCatalog() {
        requestSent = true;
        waitingForServer = true;
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.loading"));
        PondererServices.NETWORK.sendToServer(new RemoteCatalogRequestPayload());
        if (list != null) {
            rebuildListPreservingScroll();
        }
    }

    private void pull(RemoteCatalogResponsePayload.Entry entry, boolean includeDependencies) {
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.pulling", displayKey(entry.id(), entry.pack())));
        PondererServices.NETWORK.sendToServer(
            new RemotePullRequestPayload(entry.kind(), entry.id(), entry.pack(), includeDependencies));
    }

    private void pullPack(String pack) {
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.pulling_pack", pack));
        PondererServices.NETWORK.sendToServer(
            new RemotePullRequestPayload(RemoteWorkspaceService.KIND_PACK, pack, null, true));
    }

    private void uploadLocal(RemoteCatalogResponsePayload.Entry entry) {
        if (!hasLocalScene(entry)) {
            setErrorMessage(UIText.of("ponderer.ui.remote_browser.status.no_local_scene",
                displayKey(entry.id(), entry.pack())));
            return;
        }
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.uploading", displayKey(entry.id(), entry.pack())));
        PondererClientCommands.pushByKey(displayKey(entry.id(), entry.pack()), "check");
    }

    private void delete(RemoteCatalogResponsePayload.Entry entry) {
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.deleting", displayKey(entry.id(), entry.pack())));
        PondererServices.NETWORK.sendToServer(new RemoteDeleteRequestPayload(entry.kind(), entry.id(), entry.pack()));
    }

    private void requestHistory(RemoteCatalogResponsePayload.Entry entry) {
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.loading_history",
            displayKey(entry.id(), entry.pack())));
        PondererServices.NETWORK.sendToServer(new RemoteHistoryRequestPayload(entry.kind(), entry.id(), entry.pack()));
    }

    private void rollback(int revision) {
        setInfoMessage(UIText.of("ponderer.ui.remote_browser.status.rolling_back", displayKey(historyId, historyPack)));
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

    private String resourceDetail(RemoteCatalogResponsePayload.Entry entry, LocalComparison comparison) {
        List<String> parts = new ArrayList<>();
        if (!comparison.detail().isBlank()) {
            parts.add(comparison.detail());
        }
        if (RemoteWorkspaceService.KIND_PACK.equals(entry.kind())) {
            parts.add(UIText.of("ponderer.ui.remote_browser.detail.pack_counts",
                entry.dependencyCount(), entry.refCount()));
            return String.join(" | ", parts);
        }
        if (entry.revision() > 0) {
            parts.add(UIText.of("ponderer.ui.remote_browser.detail.revision", entry.revision()));
        }
        if (entry.size() > 0) {
            parts.add(formatBytes(entry.size()));
        }
        if (entry.updatedAt() > 0) {
            parts.add(TIME_FORMAT.format(Instant.ofEpochMilli(entry.updatedAt())));
        }
        if (entry.dependencyCount() > 0) {
            parts.add(UIText.of("ponderer.ui.remote_browser.detail.dependencies", entry.dependencyCount()));
        }
        if (entry.refCount() > 0) {
            parts.add(UIText.of("ponderer.ui.remote_browser.detail.references", entry.refCount()));
        }
        if (parts.isEmpty() && entry.summary() != null && !entry.summary().isBlank()) {
            parts.add(entry.summary());
        } else if (parts.isEmpty() && RemoteWorkspaceService.KIND_SCENE.equals(entry.kind())) {
            parts.add(UIText.of("ponderer.ui.remote_browser.detail.no_bound_items"));
        }
        return String.join(" | ", parts);
    }

    private String historyDetail(RemoteHistoryResponsePayload.Entry entry) {
        String time = entry.createdAt() <= 0 ? "" : TIME_FORMAT.format(Instant.ofEpochMilli(entry.createdAt()));
        String actor = entry.actor() == null || entry.actor().isBlank()
            ? UIText.of("ponderer.ui.remote_browser.actor.server")
            : entry.actor();
        return actor + (time.isBlank() ? "" : " | " + time) + " | " + formatBytes(entry.size());
    }

    private String subtitle() {
        return switch (tab) {
            case SCENES -> UIText.of("ponderer.ui.remote_browser.subtitle.scenes", scenes.size());
            case STRUCTURES -> UIText.of("ponderer.ui.remote_browser.subtitle.structures", structures.size());
            case PACKS -> UIText.of("ponderer.ui.remote_browser.subtitle.packs", packs.size());
            case HISTORY -> historyId.isBlank()
                ? UIText.of("ponderer.ui.remote_browser.subtitle.no_history")
                : displayKey(historyId, historyPack);
        };
    }

    private List<RemoteRow> remoteRows(List<RemoteCatalogResponsePayload.Entry> entries) {
        List<RemoteRow> rows = new ArrayList<>();
        for (RemoteCatalogResponsePayload.Entry entry : entries) {
            rows.add(new RemoteRow(entry, compareLocal(entry), iconStackFor(entry)));
        }
        rows.sort(Comparator
            .comparing((RemoteRow row) -> !row.comparison().priority())
            .thenComparing(row -> Objects.toString(row.entry().pack(), ""), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(row -> row.entry().id(), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private LocalComparison compareLocal(RemoteCatalogResponsePayload.Entry entry) {
        if (RemoteWorkspaceService.KIND_SCENE.equals(entry.kind())) {
            return compareHash(entry, SceneStore.findLocalSceneFile(entry.id(), entry.pack()));
        }
        if (RemoteWorkspaceService.KIND_STRUCTURE.equals(entry.kind())) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.id());
            Path path = loc == null ? null : SceneStore.resolveLocalSyncStructurePath(loc, entry.pack());
            return compareHash(entry, path);
        }
        return LocalComparison.none();
    }

    private LocalComparison compareHash(RemoteCatalogResponsePayload.Entry entry, @Nullable Path localPath) {
        String remoteHash = clean(entry.hash());
        if (remoteHash.isBlank()) {
            return LocalComparison.none();
        }
        if (localPath == null || !Files.exists(localPath)) {
            return new LocalComparison(true,
                UIText.of("ponderer.ui.remote_browser.diff.local_missing", shortHash(remoteHash)));
        }
        String localHash = clean(SyncMeta.hashLocalFile(localPath));
        if (localHash.isBlank()) {
            return LocalComparison.none();
        }
        if (!localHash.equalsIgnoreCase(remoteHash)) {
            return new LocalComparison(true,
                UIText.of("ponderer.ui.remote_browser.diff.hash", shortHash(localHash), shortHash(remoteHash)));
        }
        return LocalComparison.none();
    }

    private ItemStack iconStackFor(RemoteCatalogResponsePayload.Entry entry) {
        ResourceLocation itemId = firstBoundItem(entry);
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    @Nullable
    private ResourceLocation firstBoundItem(RemoteCatalogResponsePayload.Entry entry) {
        DslScene localScene = SceneRuntime.findByKey(displayKey(entry.id(), entry.pack()));
        ResourceLocation localItem = firstValidItem(localScene == null ? null : localScene.items);
        if (localItem != null) {
            return localItem;
        }
        if (!RemoteWorkspaceService.KIND_SCENE.equals(entry.kind()) || entry.summary() == null) {
            return null;
        }
        for (String part : entry.summary().split(",")) {
            ResourceLocation parsed = parseItemId(part);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    @Nullable
    private ResourceLocation firstValidItem(@Nullable List<String> items) {
        if (items == null) {
            return null;
        }
        for (String item : items) {
            ResourceLocation parsed = parseItemId(item);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    @Nullable
    private ResourceLocation parseItemId(@Nullable String raw) {
        String itemId = clean(raw);
        if (itemId.isBlank()) {
            return null;
        }
        int nbtStart = itemId.indexOf('{');
        if (nbtStart >= 0) {
            itemId = itemId.substring(0, nbtStart).trim();
        }
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.AIR);
        return item == Items.AIR ? null : loc;
    }

    private static ActionStripListEntry.ButtonModel actionButton(String labelKey, @Nullable String tooltipKey,
                                                                 Runnable action, int color,
                                                                 BooleanSupplier activeGetter) {
        return ActionStripListEntry.button(
            () -> UIText.of(labelKey),
            tooltipKey == null ? null : tooltip(tooltipKey),
            action,
            () -> color,
            activeGetter);
    }

    private static Supplier<List<Component>> tooltip(String key) {
        return () -> List.of(Component.literal(UIText.of(key)));
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

    private static String shortHash(String hash) {
        String clean = clean(hash);
        return clean.length() <= 8 ? clean : clean.substring(0, 8);
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
