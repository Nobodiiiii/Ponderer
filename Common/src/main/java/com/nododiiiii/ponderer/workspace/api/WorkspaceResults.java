package com.nododiiiii.ponderer.workspace.api;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** File operation results; no publication or UI side effects. */
public final class WorkspaceResults {
    private WorkspaceResults() {}

    public record SyncFileRef(String id, @javax.annotation.Nullable String pack, Path path) {
    }

    public static class PackUpdateInfo {
        public final String packName;
        public final String oldVersion;
        public final String newVersion;
        public final int totalFiles;     // total files extracted
        public final int conflictCount;  // number of user-modified files that were backed up

        public PackUpdateInfo(String packName, String oldVersion, String newVersion, int totalFiles, int conflictCount) {
            this.packName = packName;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.totalFiles = totalFiles;
            this.conflictCount = conflictCount;
        }
    }

    public static final class LocalSaveResult {
        private final boolean success;
        @javax.annotation.Nullable
        private final Path path;
        private final String englishMessage;
        @javax.annotation.Nullable
        private final String uiMessageKey;
        private final Object[] uiMessageArgs;

        private LocalSaveResult(boolean success, @javax.annotation.Nullable Path path, String englishMessage,
                                @javax.annotation.Nullable String uiMessageKey, Object[] uiMessageArgs) {
            this.success = success;
            this.path = path;
            this.englishMessage = englishMessage;
            this.uiMessageKey = uiMessageKey;
            this.uiMessageArgs = uiMessageArgs == null ? new Object[0] : Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public boolean isSuccess() {
            return success;
        }

        @javax.annotation.Nullable
        public Path path() {
            return path;
        }

        public String englishMessage() {
            return englishMessage;
        }

        @javax.annotation.Nullable
        public String uiMessageKey() {
            return uiMessageKey;
        }

        public Object[] uiMessageArgs() {
            return Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public static LocalSaveResult success(Path path, String englishMessage) {
            return new LocalSaveResult(true, path, englishMessage, null, new Object[0]);
        }

        public static LocalSaveResult failure(String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new LocalSaveResult(false, null, englishMessage, uiMessageKey, uiMessageArgs);
        }
    }

    public static final class PackExportResult {
        private final boolean success;
        @javax.annotation.Nullable
        private final Path path;
        private final String englishMessage;
        @javax.annotation.Nullable
        private final String uiMessageKey;
        private final Object[] uiMessageArgs;

        private PackExportResult(boolean success, @javax.annotation.Nullable Path path, String englishMessage,
                                 @javax.annotation.Nullable String uiMessageKey, Object[] uiMessageArgs) {
            this.success = success;
            this.path = path;
            this.englishMessage = englishMessage;
            this.uiMessageKey = uiMessageKey;
            this.uiMessageArgs = uiMessageArgs == null ? new Object[0] : Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public boolean isSuccess() {
            return success;
        }

        @javax.annotation.Nullable
        public Path path() {
            return path;
        }

        public String englishMessage() {
            return englishMessage;
        }

        @javax.annotation.Nullable
        public String uiMessageKey() {
            return uiMessageKey;
        }

        public Object[] uiMessageArgs() {
            return Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public static PackExportResult success(Path path, String englishMessage) {
            return new PackExportResult(true, path, englishMessage, null, new Object[0]);
        }

        public static PackExportResult failure(String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new PackExportResult(false, null, englishMessage, uiMessageKey, uiMessageArgs);
        }
    }

    public static final class PackImportResult {
        private final boolean success;
        private final int fileCount;
        private final String englishMessage;
        @javax.annotation.Nullable
        private final String uiMessageKey;
        private final Object[] uiMessageArgs;

        private PackImportResult(boolean success, int fileCount, String englishMessage,
                                 @javax.annotation.Nullable String uiMessageKey, Object[] uiMessageArgs) {
            this.success = success;
            this.fileCount = fileCount;
            this.englishMessage = englishMessage;
            this.uiMessageKey = uiMessageKey;
            this.uiMessageArgs = uiMessageArgs == null ? new Object[0] : Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public boolean isSuccess() {
            return success;
        }

        public int fileCount() {
            return fileCount;
        }

        public String englishMessage() {
            return englishMessage;
        }

        @javax.annotation.Nullable
        public String uiMessageKey() {
            return uiMessageKey;
        }

        public Object[] uiMessageArgs() {
            return Arrays.copyOf(uiMessageArgs, uiMessageArgs.length);
        }

        public static PackImportResult success(int fileCount, String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new PackImportResult(true, fileCount, englishMessage, uiMessageKey, uiMessageArgs);
        }

        public static PackImportResult failure(String englishMessage, String uiMessageKey, Object... uiMessageArgs) {
            return new PackImportResult(false, 0, englishMessage, uiMessageKey, uiMessageArgs);
        }
    }

    public static class AutoLoadResult {
        public final List<String> orphanedPacks;
        public final List<PackUpdateInfo> updatedPacks;

        public AutoLoadResult(List<String> orphanedPacks, List<PackUpdateInfo> updatedPacks) {
            this.orphanedPacks = orphanedPacks;
            this.updatedPacks = updatedPacks;
        }
    }
}
