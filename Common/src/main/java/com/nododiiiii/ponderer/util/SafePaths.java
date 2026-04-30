package com.nododiiiii.ponderer.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SafePaths {
    private static final Pattern INVALID_WINDOWS_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");
    private static final Pattern INVALID_ID_LIKE_CHARS = Pattern.compile("[^a-z0-9._-]");
    private static final Set<String> RESERVED_WINDOWS_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL", "CLOCK$", "CONIN$", "CONOUT$",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    public enum FileNameValidationErrorCode {
        EMPTY,
        DOT_SEGMENT,
        TRAILING_SPACE_OR_DOT,
        INVALID_CHARACTER,
        RESERVED_NAME
    }

    public static final class FileNameValidationError {
        private final FileNameValidationErrorCode code;
        private final String input;
        @Nullable
        private final String offendingText;

        private FileNameValidationError(FileNameValidationErrorCode code, String input, @Nullable String offendingText) {
            this.code = code;
            this.input = input;
            this.offendingText = offendingText;
        }

        public FileNameValidationErrorCode code() {
            return code;
        }

        public String input() {
            return input;
        }

        @Nullable
        public String offendingText() {
            return offendingText;
        }
    }

    private SafePaths() {
    }

    public static boolean isValidWindowsFileNameSegment(String segment) {
        return diagnoseWindowsFileNameSegment(segment) == null;
    }

    @Nullable
    public static String validateWindowsFileNameSegment(String segment) {
        return diagnoseWindowsFileNameSegment(segment) == null ? segment : null;
    }

    @Nullable
    public static FileNameValidationError diagnoseWindowsFileNameSegment(String segment) {
        if (segment == null || segment.isEmpty() || segment.isBlank()) {
            return new FileNameValidationError(FileNameValidationErrorCode.EMPTY, segment == null ? "" : segment, null);
        }
        if (".".equals(segment) || "..".equals(segment)) {
            return new FileNameValidationError(FileNameValidationErrorCode.DOT_SEGMENT, segment, null);
        }
        if (segment.endsWith(" ") || segment.endsWith(".")) {
            return new FileNameValidationError(FileNameValidationErrorCode.TRAILING_SPACE_OR_DOT, segment, null);
        }
        java.util.regex.Matcher matcher = INVALID_WINDOWS_CHARS.matcher(segment);
        if (matcher.find()) {
            return new FileNameValidationError(
                FileNameValidationErrorCode.INVALID_CHARACTER,
                segment,
                printableCharacter(matcher.group().charAt(0))
            );
        }
        if (isReservedWindowsName(segment)) {
            return new FileNameValidationError(FileNameValidationErrorCode.RESERVED_NAME, segment, null);
        }
        return null;
    }

    @Nullable
    public static FileNameValidationError diagnosePortableAssetName(String segment) {
        String normalized = segment == null ? "" : segment.trim();
        FileNameValidationError baseError = diagnoseWindowsFileNameSegment(normalized);
        if (baseError != null) {
            return baseError;
        }
        java.util.regex.Matcher matcher = INVALID_ID_LIKE_CHARS.matcher(normalized);
        if (matcher.find()) {
            return new FileNameValidationError(
                FileNameValidationErrorCode.INVALID_CHARACTER,
                normalized,
                printableCharacter(matcher.group().charAt(0))
            );
        }
        return null;
    }

    public static String sanitizeWindowsFileName(String raw, String fallback) {
        String safeFallback = fallback == null || fallback.isBlank() ? "file" : fallback;
        String candidate = raw == null ? "" : raw.trim();
        candidate = INVALID_WINDOWS_CHARS.matcher(candidate).replaceAll("_");
        candidate = stripTrailingDotsAndSpaces(candidate);
        if (candidate.isEmpty() || candidate.isBlank() || ".".equals(candidate) || "..".equals(candidate)) {
            candidate = safeFallback;
        }
        if (isReservedWindowsName(candidate)) {
            candidate = candidate + "_";
        }
        candidate = stripTrailingDotsAndSpaces(candidate);
        if (!isValidWindowsFileNameSegment(candidate)) {
            candidate = safeFallback;
            if (isReservedWindowsName(candidate)) {
                candidate = candidate + "_";
            }
            candidate = stripTrailingDotsAndSpaces(candidate);
        }
        return isValidWindowsFileNameSegment(candidate) ? candidate : "file";
    }

    @Nullable
    public static List<String> splitValidatedRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty() || relativePath.isBlank()) {
            return null;
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            return null;
        }
        if (relativePath.startsWith("//") || relativePath.startsWith("\\\\")) {
            return null;
        }
        if (relativePath.length() >= 2
            && Character.isLetter(relativePath.charAt(0))
            && relativePath.charAt(1) == ':') {
            return null;
        }

        String normalized = relativePath.replace('\\', '/');
        if (normalized.endsWith("/")) {
            return null;
        }

        String[] rawSegments = normalized.split("/");
        if (rawSegments.length == 0) {
            return null;
        }

        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String rawSegment : rawSegments) {
            String validated = validateWindowsFileNameSegment(rawSegment);
            if (validated == null) {
                return null;
            }
            segments.add(validated);
        }
        return segments;
    }

    @Nullable
    public static Path resolveRelativePath(Path root, String relativePath) {
        List<String> segments = splitValidatedRelativePath(relativePath);
        if (segments == null) {
            return null;
        }
        return resolveRelativePath(root, segments);
    }

    @Nullable
    public static Path resolveRelativePath(Path root, List<String> relativeSegments) {
        if (root == null || relativeSegments == null || relativeSegments.isEmpty()) {
            return null;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot;
        for (String segment : relativeSegments) {
            if (validateWindowsFileNameSegment(segment) == null) {
                return null;
            }
            resolved = resolved.resolve(segment);
        }
        Path normalizedResolved = resolved.normalize();
        return normalizedResolved.startsWith(normalizedRoot) ? normalizedResolved : null;
    }

    @Nullable
    public static Path resolveFileName(Path root, String fileName) {
        String validated = validateWindowsFileNameSegment(fileName);
        if (validated == null) {
            return null;
        }
        return resolveRelativePath(root, List.of(validated));
    }

    @Nullable
    public static Path resolveNamespacedPath(Path root, ResourceLocation id, String defaultNamespace, String extension) {
        if (root == null || id == null || extension == null) {
            return null;
        }
        String relativePath = id.getNamespace().equals(defaultNamespace)
            ? id.getPath() + extension
            : id.getNamespace() + "/" + id.getPath() + extension;
        return resolveRelativePath(root, relativePath);
    }

    private static boolean isReservedWindowsName(String segment) {
        String base = segment;
        int dot = base.indexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        base = stripTrailingDotsAndSpaces(base).toUpperCase(Locale.ROOT);
        return !base.isEmpty() && RESERVED_WINDOWS_NAMES.contains(base);
    }

    private static String stripTrailingDotsAndSpaces(String value) {
        int end = value.length();
        while (end > 0) {
            char ch = value.charAt(end - 1);
            if (ch != ' ' && ch != '.') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    private static String printableCharacter(char ch) {
        if (Character.isISOControl(ch)) {
            return String.format(Locale.ROOT, "U+%04X", (int) ch);
        }
        return Character.toString(ch);
    }
}
