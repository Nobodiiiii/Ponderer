package com.nododiiiii.ponderer.workspace.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Owned workspace capability; operations never publish playback or UI state. */
public interface PackRegistry {
    public static class PackEntry {
        public String name;
        public String version;
        public String author;
        public String description;
        public String sourceFile;      // relative to resourcepacks/
        public String fileHash;         // SHA-256 of zip
        public String loadedAt;         // ISO 8601 timestamp
        public String packPrefix;       // [PackName]

        public PackEntry() {
        }

        public PackEntry(PonderPackInfo info, String fileHash) {
            this.name = info.name;
            this.version = info.version;
            this.author = info.author;
            this.description = info.description;
            this.sourceFile = info.sourcePath.getFileName().toString();
            this.fileHash = fileHash;
            this.loadedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
            this.packPrefix = info.packPrefix;
        }
    }

    void load();

    void save();

    void addOrUpdatePack(String displayName, PonderPackInfo info, String fileHash);

    void removePack(String displayName);

    PackEntry getPack(String displayName);

    Map<String, PackEntry> getAllPacks();

    boolean hasPack(String displayName);

    String getDisplayName(String packName);
}
