package com.nododiiiii.ponderer.workspace.api;

import java.util.Collection;

/** Owned workspace capability; operations never publish playback or UI state. */
public interface PackState {
    public static final class ImportedPackState {
        public String packId;
        public String displayName;
        public String sourceFile;
        public String importedVersion;
        public String currentSourceVersion;
        public String lastNotifiedSourceVersion;
        public String importedAt;

        public ImportedPackState copy() {
            ImportedPackState copy = new ImportedPackState();
            copy.packId = packId;
            copy.displayName = displayName;
            copy.sourceFile = sourceFile;
            copy.importedVersion = importedVersion;
            copy.currentSourceVersion = currentSourceVersion;
            copy.lastNotifiedSourceVersion = lastNotifiedSourceVersion;
            copy.importedAt = importedAt;
            return copy;
        }
    }

    void load();

    void save();

    Collection<ImportedPackState> getImportedPacks();

    ImportedPackState getImportedPack(String packId);

    boolean hasImportedState(String packId);

    boolean isImported(String packId);

    boolean hasLocalSceneCopy(String packId);

    ImportedPackState markImported(PonderPackInfo info);

    void updateCurrentSource(PonderPackInfo info);

    void clearMissingSources(Collection<String> presentPackIds);

    boolean shouldNotifySourceUpdate(String packId, String sourceVersion);

    void markNotified(String packId, String sourceVersion);

    void removeImportedPack(String packId);
}
