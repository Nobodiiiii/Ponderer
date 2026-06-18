package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.PackStateStore;
import com.nododiiiii.ponderer.ponder.PonderPackInfo;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.projector.client.ProjectorClientCaches;
import com.nododiiiii.ponderer.ui.catnip.AbstractReadonlyDeclarativeListScreen;
import com.nododiiiii.ponderer.ui.catnip.FullButtonListEntry;
import com.nododiiiii.ponderer.ui.catnip.SectionHeaderListEntry;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ImportPackScreen extends AbstractReadonlyDeclarativeListScreen {

    private enum PackEntryState {
        READONLY_AVAILABLE,
        IMPORTED_LOCAL,
        SOURCE_NEWER,
        LOCAL_ONLY
    }

    private record PackEntryRow(
        String packId,
        String displayName,
        @Nullable String author,
        @Nullable String sourceVersion,
        @Nullable String importedVersion,
        @Nullable Path sourcePath,
        PackEntryState state
    ) {
    }

    private final List<PackEntryRow> packEntries = new ArrayList<>();

    public ImportPackScreen() {
        super(new FunctionScreen(), "ponderer.ui.scope.editor", "ponderer.ui.function_page.import.title", UILayoutConstants.EDITOR_LIST_W);
    }

    @Override
    protected void init() {
        scanPacks();
        super.init();
    }

    @Override
    protected void collectEntries(List<ConfigScreenList.Entry> entries) {
        if (packEntries.isEmpty()) {
            entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.import.none")));
            return;
        }

        entries.add(new SectionHeaderListEntry(UIText.of("ponderer.ui.import.available")));
        for (PackEntryRow row : packEntries) {
            entries.add(new FullButtonListEntry(
                buildLabel(row),
                buildSubtitle(row),
                () -> onPackSelected(row)));
        }
    }

    @Override
    protected int getEntryHeight() {
        return UILayoutConstants.COMPACT_LIST_ENTRY_H;
    }

    private void scanPacks() {
        packEntries.clear();
        PackStateStore.load();

        Map<String, PackEntryRow> merged = new LinkedHashMap<>();
        for (PonderPackInfo info : SceneStore.scanAvailableSourcePacks()) {
            PackStateStore.ImportedPackState imported = PackStateStore.getImportedPack(info.name);
            PackEntryState state = PackStateStore.isImported(info.name)
                ? isSourceNewer(imported, info.version) ? PackEntryState.SOURCE_NEWER : PackEntryState.IMPORTED_LOCAL
                : PackEntryState.READONLY_AVAILABLE;

            merged.put(info.name, new PackEntryRow(
                info.name,
                info.name,
                info.author,
                info.version,
                imported != null ? imported.importedVersion : null,
                info.sourcePath,
                state));
        }

        for (PackStateStore.ImportedPackState imported : PackStateStore.getImportedPacks()) {
            if (imported.packId == null || imported.packId.isBlank()) {
                continue;
            }
            if (!PackStateStore.isImported(imported.packId) || merged.containsKey(imported.packId)) {
                continue;
            }

            merged.put(imported.packId, new PackEntryRow(
                imported.packId,
                imported.displayName != null && !imported.displayName.isBlank() ? imported.displayName : imported.packId,
                null,
                null,
                imported.importedVersion,
                null,
                PackEntryState.LOCAL_ONLY));
        }

        packEntries.addAll(merged.values().stream()
            .sorted(Comparator.comparing(row -> row.displayName().toLowerCase(Locale.ROOT)))
            .toList());
    }

    private boolean isSourceNewer(@Nullable PackStateStore.ImportedPackState imported, @Nullable String sourceVersion) {
        return imported != null
            && imported.importedVersion != null
            && !imported.importedVersion.isBlank()
            && sourceVersion != null
            && !sourceVersion.isBlank()
            && !sourceVersion.equals(imported.importedVersion);
    }

    private String buildLabel(PackEntryRow row) {
        StringBuilder label = new StringBuilder(row.displayName());
        if (row.sourceVersion() != null && !row.sourceVersion().isBlank()) {
            label.append(" v").append(row.sourceVersion());
        } else if (row.importedVersion() != null && !row.importedVersion().isBlank()) {
            label.append(" v").append(row.importedVersion());
        }
        if (row.author() != null && !row.author().isBlank()) {
            label.append(" | ").append(row.author());
        }
        return label.toString();
    }

    private String buildSubtitle(PackEntryRow row) {
        return switch (row.state()) {
            case READONLY_AVAILABLE -> UIText.of("ponderer.ui.import.status.readonly");
            case IMPORTED_LOCAL -> UIText.of("ponderer.ui.import.status.imported", versionLabel(row.importedVersion()));
            case SOURCE_NEWER -> UIText.of("ponderer.ui.import.status.newer_source",
                versionLabel(row.importedVersion()), versionLabel(row.sourceVersion()));
            case LOCAL_ONLY -> UIText.of("ponderer.ui.import.status.local_only", versionLabel(row.importedVersion()));
        };
    }

    private String versionLabel(@Nullable String version) {
        return version == null || version.isBlank() ? "?" : "v" + version;
    }

    private void onPackSelected(PackEntryRow row) {
        switch (row.state()) {
            case READONLY_AVAILABLE -> importPack(row);
            case IMPORTED_LOCAL -> notifyUser(UIText.of("ponderer.ui.import.already_imported", row.displayName()));
            case SOURCE_NEWER -> notifyUser(UIText.of("ponderer.ui.import.newer_source",
                row.displayName(), versionLabel(row.sourceVersion()), versionLabel(row.importedVersion())));
            case LOCAL_ONLY -> notifyUser(UIText.of("ponderer.ui.import.local_only", row.displayName()));
        }
    }

    private void importPack(PackEntryRow row) {
        if (row.sourcePath() == null) {
            notifyUser(UIText.of("ponderer.ui.import.failed", "Missing source pack"));
            return;
        }

        SceneStore.PackImportResult result = SceneStore.importPackFromResourcePack(row.sourcePath());
        if (!result.isSuccess()) {
            String key = result.uiMessageKey();
            notifyUser(key == null || key.isBlank()
                ? result.englishMessage()
                : UIText.of(key, result.uiMessageArgs()));
            return;
        }

        SceneStore.autoLoadPonderPacks();
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(ProjectorClientCaches::reloadPonderIndexAndInvalidate);
        notifyUser(UIText.of(result.uiMessageKey(), result.uiMessageArgs()));
        Minecraft.getInstance().setScreen(new ImportPackScreen());
    }

    private void notifyUser(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
        }
    }
}
