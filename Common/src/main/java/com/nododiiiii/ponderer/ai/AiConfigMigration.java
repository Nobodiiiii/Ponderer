package com.nododiiiii.ponderer.ai;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Migrates the raw file before the loader can correct or discard legacy settings. */
public final class AiConfigMigration {
    public static final int CURRENT_VERSION = 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    private AiConfigMigration() {
    }

    public static void migrate(Path path) {
        if (!Files.exists(path)) return;

        try (CommentedFileConfig config = CommentedFileConfig.builder(path)
                .sync()
                .preserveInsertionOrder()
                .writingMode(WritingMode.REPLACE)
                .build()) {
            config.load();
            Object savedVersion = config.get("ai.configVersion");
            if (savedVersion instanceof Number version && version.longValue() >= CURRENT_VERSION) return;

            boolean hasProtocol = config.contains("ai.wireApi");
            Object protocol = config.get("ai.wireApi");
            Object provider = config.get("ai.provider");
            if (!hasProtocol || "auto".equals(protocol)) {
                config.set("ai.wireApi", provider instanceof String name
                    && "anthropic".equalsIgnoreCase(name.trim()) ? "anthropic" : "auto");
            }
            config.remove("ai.provider");
            config.removeComment("ai.provider");

            // The original providers appended /v1 themselves. Preserve that endpoint
            // when upgrading a file from before the explicit protocol/base-path settings.
            if (!hasProtocol && config.get("ai.apiBaseUrl") instanceof String value && !value.isBlank()) {
                String baseUrl = value.trim();
                while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    baseUrl = "https://" + baseUrl;
                }
                if (!baseUrl.endsWith("/v1")) baseUrl += "/v1";
                config.set("ai.apiBaseUrl", baseUrl);
            }

            Object maxTokens = config.get("ai.maxTokens");
            if (!(maxTokens instanceof Number limit) || limit.longValue() < AiDefaults.MAX_TOKENS) {
                config.set("ai.maxTokens", AiDefaults.MAX_TOKENS);
            }
            Object maxTurns = config.get("ai.maxAgentTurns");
            if (maxTurns == null || maxTurns instanceof Number turns && turns.intValue() == 6) {
                config.set("ai.maxAgentTurns", AiDefaults.MAX_AGENT_TURNS);
            }
            if (!config.contains("ai.reasoningEffort")) config.set("ai.reasoningEffort", "");
            if (!config.contains("ai.requestTimeoutSeconds")) {
                config.set("ai.requestTimeoutSeconds", AiDefaults.REQUEST_TIMEOUT_SECONDS);
            }
            config.set("ai.configVersion", CURRENT_VERSION);

            Path backup = path.resolveSibling(path.getFileName() + ".pre-ai-v" + CURRENT_VERSION + ".bak");
            if (!Files.exists(backup)) Files.copy(path, backup);
            config.save();
            LOGGER.info("[Ponderer] Migrated AI configuration to version {}: {} (backup: {})",
                CURRENT_VERSION, path, backup);
        } catch (IOException | RuntimeException exception) {
            // Do not let config-spec correction erase legacy fields after a failed migration.
            throw new IllegalStateException("Failed to migrate Ponderer AI configuration: " + path, exception);
        }
    }
}
