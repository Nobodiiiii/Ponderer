package com.nododiiiii.ponderer.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class TomlBooleanReader {
    public static Optional<Boolean> readBoolean(Path path, String key) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                Optional<Boolean> value = parseLine(line, key);
                if (value.isPresent()) {
                    return value;
                }
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static Optional<Boolean> parseLine(String line, String key) {
        int commentStart = line.indexOf('#');
        String withoutComment = commentStart >= 0 ? line.substring(0, commentStart) : line;
        int equals = withoutComment.indexOf('=');
        if (equals < 0) {
            return Optional.empty();
        }

        String name = withoutComment.substring(0, equals).trim();
        if (!key.equals(name)) {
            return Optional.empty();
        }

        String value = withoutComment.substring(equals + 1).trim();
        if ("true".equalsIgnoreCase(value)) {
            return Optional.of(true);
        }
        if ("false".equalsIgnoreCase(value)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private TomlBooleanReader() {
    }
}
