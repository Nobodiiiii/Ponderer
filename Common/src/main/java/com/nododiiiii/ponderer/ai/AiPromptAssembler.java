package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.scene.step.SceneStepCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import com.mojang.logging.LogUtils;

/** Versioned built-in contracts; legacy disk prompts are archived and never loaded. */
final class AiPromptAssembler {
    static final int VERSION = 9;
    private static final String ROOT = "/assets/ponderer/ai/prompts/";
    private AiPromptAssembler() {}

    static String build(Path directory, boolean building, boolean adjusting, String tools) {
        archiveLegacyPrompts(directory);
        StringBuilder prompt = new StringBuilder("Ponderer prompt version: " + VERSION + "\n\n");
        prompt.append(resource("core.txt")).append("\n\n");
        prompt.append(resource(adjusting ? "editing.txt" : "authoring.txt")).append("\n\n");
        if (building && !adjusting) prompt.append(resource("building.txt")).append("\n\n");
        prompt.append("<tool_contracts>\n").append(tools).append("\n</tool_contracts>\n");
        prompt.append("<step_index>\n").append(SceneStepCatalog.compactIndex()).append("</step_index>\n");
        return prompt.toString();
    }

    private static synchronized void archiveLegacyPrompts(Path directory) {
        try {
            Files.createDirectories(directory);
            Path backup = null;
            for (String name : List.of("user_instructions.txt", "agent_system.txt", "json_system.txt", "outline_system.txt")) {
                Path legacy = directory.resolve(name);
                if (!Files.isRegularFile(legacy)) continue;
                if (backup == null) {
                    Path backups = Files.createDirectories(directory.resolve("backups"));
                    backup = Files.createTempDirectory(backups, "pre-v" + VERSION + "-");
                }
                Files.move(legacy, backup.resolve(name));
            }
            Files.writeString(directory.resolve("prompt-version.txt"), Integer.toString(VERSION), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LogUtils.getLogger().warn("Could not archive legacy AI prompts; using built-in prompts only: {}", e.getMessage());
        }
    }

    private static String resource(String name) {
        try (var stream = AiPromptAssembler.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) throw new IllegalStateException("Missing built-in AI prompt: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) { throw new IllegalStateException("Could not read AI prompt " + name, e); }
    }
}
