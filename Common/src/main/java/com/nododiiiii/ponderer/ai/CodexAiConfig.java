package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny reader for Codex's config.toml/auth.json. It intentionally supports only
 * the simple scalar TOML shapes Codex uses for model provider settings.
 */
public final class CodexAiConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CodexAiConfig() {
    }

    public static Optional<AiRuntimeConfig> loadDefault(int maxTokens) {
        Path codexHome = defaultCodexHome();
        return load(codexHome, System.getenv(), maxTokens);
    }

    static Optional<AiRuntimeConfig> load(Path codexHome, Map<String, String> environment, int maxTokens) {
        Path configFile = codexHome.resolve("config.toml");
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }

        try {
            TomlLite toml = TomlLite.parse(Files.readString(configFile, StandardCharsets.UTF_8));
            String providerName = valueOrDefault(toml.get("", "model_provider"), "openai");
            String model = valueOrDefault(toml.get("", "model"), "");
            String providerTable = "model_providers." + providerName;

            String wireApi = valueOrDefault(toml.get(providerTable, "wire_api"), "chat");
            String baseUrl = valueOrDefault(toml.get(providerTable, "base_url"), "");
            boolean requiresOpenAiAuth = Boolean.parseBoolean(
                valueOrDefault(toml.get(providerTable, "requires_openai_auth"), "false"));
            String providerKind = inferProviderKind(providerName, wireApi, baseUrl, requiresOpenAiAuth);

            if (baseUrl.isBlank()) {
                baseUrl = "anthropic".equals(providerKind) ? "https://api.anthropic.com" : "https://api.openai.com";
            }
            if (model.isBlank()) {
                model = "anthropic".equals(providerKind) ? "claude-sonnet-4-20250514" : "gpt-4o";
            }

            String apiKey = resolveApiKey(codexHome, toml, environment, providerKind, requiresOpenAiAuth);
            return Optional.of(new AiRuntimeConfig(
                providerKind,
                wireApi,
                normalizeBaseUrl(baseUrl, wireApi),
                apiKey,
                model,
                maxTokens,
                "codex:" + configFile
            ));
        } catch (Exception e) {
            LOGGER.warn("Failed to read Codex AI config from {}", configFile, e);
            return Optional.empty();
        }
    }

    private static Path defaultCodexHome() {
        String envHome = System.getenv("CODEX_HOME");
        if (envHome != null && !envHome.isBlank()) {
            return Path.of(envHome);
        }
        return Path.of(System.getProperty("user.home"), ".codex");
    }

    private static String inferProviderKind(String providerName, String wireApi, String baseUrl,
                                            boolean requiresOpenAiAuth) {
        String haystack = (providerName + " " + wireApi + " " + baseUrl).toLowerCase(Locale.ROOT);
        if (haystack.contains("anthropic") || haystack.contains("claude")) {
            return "anthropic";
        }
        if (requiresOpenAiAuth || "responses".equalsIgnoreCase(wireApi)) {
            return "openai";
        }
        return "openai";
    }

    private static String resolveApiKey(Path codexHome, TomlLite toml, Map<String, String> environment,
                                        String providerKind, boolean requiresOpenAiAuth) {
        if ("anthropic".equals(providerKind)) {
            String token = firstNonBlank(
                environment.get("ANTHROPIC_AUTH_TOKEN"),
                toml.get("shell_environment_policy.set", "ANTHROPIC_AUTH_TOKEN")
            );
            if (!token.isBlank()) {
                return bearerToken(token);
            }
            String apiKey = firstNonBlank(
                environment.get("ANTHROPIC_API_KEY"),
                toml.get("shell_environment_policy.set", "ANTHROPIC_API_KEY")
            );
            if (!apiKey.isBlank()) {
                return apiKey;
            }
        }

        String openAiKey = firstNonBlank(
            environment.get("OPENAI_API_KEY"),
            toml.get("shell_environment_policy.set", "OPENAI_API_KEY"),
            readAuthJsonValue(codexHome.resolve("auth.json"), "OPENAI_API_KEY")
        );
        if (!openAiKey.isBlank()) {
            return openAiKey;
        }

        if (requiresOpenAiAuth) {
            return "";
        }
        String token = firstNonBlank(
            toml.get("shell_environment_policy.set", "ANTHROPIC_AUTH_TOKEN"),
            environment.get("ANTHROPIC_AUTH_TOKEN")
        );
        return token.isBlank() ? "" : bearerToken(token);
    }

    private static String readAuthJsonValue(Path authFile, String key) {
        if (!Files.isRegularFile(authFile)) {
            return "";
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(authFile, StandardCharsets.UTF_8))
                .getAsJsonObject();
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
        } catch (Exception e) {
            LOGGER.warn("Failed to read Codex auth file {}", authFile, e);
            return "";
        }
    }

    private static String normalizeBaseUrl(String baseUrl, String wireApi) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (!"responses".equalsIgnoreCase(wireApi) && url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String bearerToken(String token) {
        return token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length()) ? token : "Bearer " + token;
    }

    static final class TomlLite {
        private final Map<String, Map<String, String>> values = new LinkedHashMap<>();

        static TomlLite parse(String raw) throws IOException {
            TomlLite toml = new TomlLite();
            String section = "";
            String[] lines = raw.split("\\R");
            for (String originalLine : lines) {
                String line = stripComment(originalLine).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = unquoteDottedSection(line.substring(1, line.length() - 1).trim());
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = line.substring(0, equals).trim();
                String value = parseScalar(line.substring(equals + 1).trim());
                toml.values.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(key, value);
            }
            return toml;
        }

        String get(String section, String key) {
            Map<String, String> sectionValues = values.get(section == null ? "" : section);
            return sectionValues == null ? null : sectionValues.get(key);
        }

        private static String stripComment(String line) {
            boolean inSingle = false;
            boolean inDouble = false;
            boolean escape = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\' && inDouble) {
                    escape = true;
                    continue;
                }
                if (c == '\'' && !inDouble) {
                    inSingle = !inSingle;
                    continue;
                }
                if (c == '"' && !inSingle) {
                    inDouble = !inDouble;
                    continue;
                }
                if (c == '#' && !inSingle && !inDouble) {
                    return line.substring(0, i);
                }
            }
            return line;
        }

        private static String parseScalar(String raw) {
            String value = raw.trim();
            if (value.length() >= 2) {
                char first = value.charAt(0);
                char last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    value = value.substring(1, value.length() - 1);
                    if (first == '"') {
                        value = value.replace("\\\"", "\"").replace("\\\\", "\\");
                    }
                }
            }
            return value;
        }

        private static String unquoteDottedSection(String section) {
            StringBuilder out = new StringBuilder(section.length());
            boolean inSingle = false;
            boolean inDouble = false;
            boolean escape = false;
            for (int i = 0; i < section.length(); i++) {
                char c = section.charAt(i);
                if (escape) {
                    out.append(c);
                    escape = false;
                    continue;
                }
                if (c == '\\' && inDouble) {
                    escape = true;
                    continue;
                }
                if (c == '\'' && !inDouble) {
                    inSingle = !inSingle;
                    continue;
                }
                if (c == '"' && !inSingle) {
                    inDouble = !inDouble;
                    continue;
                }
                out.append(c);
            }
            return out.toString();
        }
    }
}
