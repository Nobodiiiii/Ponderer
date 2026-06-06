package com.nododiiiii.ponderer.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Reader for Claude Code's local settings. It supports the API-key style
 * environment fields commonly stored under ~/.claude/settings.json.
 */
public final class ClaudeCodeAiConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClaudeCodeAiConfig() {
    }

    public static Optional<AiRuntimeConfig> loadDefault(int maxTokens) {
        Path claudeHome = defaultClaudeHome();
        return load(claudeHome, System.getenv(), maxTokens);
    }

    static Optional<AiRuntimeConfig> load(Path claudeHome, Map<String, String> environment, int maxTokens) {
        Path settingsFile = claudeHome.resolve("settings.json");
        Path configFile = claudeHome.resolve("config.json");

        try {
            JsonObject settings = readObject(settingsFile);
            JsonObject env = settings.has("env") && settings.get("env").isJsonObject()
                ? settings.getAsJsonObject("env") : new JsonObject();
            JsonObject config = readObject(configFile);

            String authToken = firstNonBlank(
                environment.get("ANTHROPIC_AUTH_TOKEN"),
                stringValue(env, "ANTHROPIC_AUTH_TOKEN")
            );
            String apiKey = firstNonBlank(
                environment.get("ANTHROPIC_API_KEY"),
                stringValue(env, "ANTHROPIC_API_KEY"),
                stringValue(config, "primaryApiKey")
            );
            String baseUrl = normalizeBaseUrl(firstNonBlank(
                environment.get("ANTHROPIC_BASE_URL"),
                stringValue(env, "ANTHROPIC_BASE_URL"),
                "https://api.anthropic.com"
            ));
            String model = firstNonBlank(
                environment.get("ANTHROPIC_MODEL"),
                stringValue(env, "ANTHROPIC_MODEL"),
                stringValue(env, "ANTHROPIC_DEFAULT_OPUS_MODEL"),
                stringValue(env, "ANTHROPIC_DEFAULT_SONNET_MODEL"),
                stringValue(settings, "model"),
                "claude-sonnet-4-20250514"
            );

            return Optional.of(new AiRuntimeConfig(
                "anthropic",
                "anthropic",
                baseUrl,
                authToken.isBlank() ? apiKey : bearerToken(authToken),
                model,
                maxTokens,
                "claude-code:" + settingsFile
            ));
        } catch (Exception e) {
            LOGGER.warn("Failed to read Claude Code AI config from {}", claudeHome, e);
            return Optional.empty();
        }
    }

    private static Path defaultClaudeHome() {
        String envHome = System.getenv("CLAUDE_CONFIG_DIR");
        if (envHome != null && !envHome.isBlank()) {
            return Path.of(envHome);
        }
        return Path.of(System.getProperty("user.home"), ".claude");
    }

    private static JsonObject readObject(Path file) {
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("Failed to read Claude Code config file {}", file, e);
            return new JsonObject();
        }
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
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
}
