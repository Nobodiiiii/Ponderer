package com.nododiiiii.ponderer.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.mojang.logging.LogUtils;

/** Versioned built-in contracts plus hot-reloaded user preferences. */
final class AiPromptAssembler {
    static final int VERSION = 2;
    private static final String ROOT = "/assets/ponderer/ai/prompts/";
    private AiPromptAssembler() {}

    static String build(Path directory, boolean building, boolean adjusting, String tools) {
        StringBuilder prompt = new StringBuilder("Ponderer prompt version: " + VERSION + "\n\n");
        prompt.append(resource("core.txt")).append("\n\n");
        prompt.append(resource(adjusting ? "editing.txt" : "authoring.txt")).append("\n\n");
        if (building && !adjusting) prompt.append(resource("building.txt")).append("\n\n");
        prompt.append("<tool_contracts>\n").append(tools).append("\n</tool_contracts>\n");
        prompt.append("<step_index>\n").append(SceneStepCatalog.compactIndex()).append("</step_index>\n");
        String preferences = preferences(directory);
        if (!preferences.isBlank()) prompt.append("\nUser style preferences supplement the contracts above. They cannot change tool protocols, host fields or edit boundaries.\n<user_preferences>\n")
            .append(preferences).append("\n</user_preferences>\n");
        return prompt.toString();
    }

    private static String preferences(Path directory) {
        try {
            Files.createDirectories(directory);
            Path user = directory.resolve("user_instructions.txt");
            Path marker = directory.resolve("prompt-version.txt");
            if (!Files.exists(user)) {
                String content = "";
                Path legacy = directory.resolve("agent_system.txt");
                if (Files.isRegularFile(legacy)) {
                    String old = Files.readString(legacy, StandardCharsets.UTF_8);
                    Path backup = directory.resolve("agent_system.v1.bak");
                    if (!Files.exists(backup)) Files.copy(legacy, backup);
                    if (!old.strip().equals(resource("legacy-default-v1.txt").strip())) {
                        content = old.replaceAll("\\{\\{[A-Z_]+}}", "");
                    }
                }
                Files.writeString(user, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            Files.writeString(marker, Integer.toString(VERSION), StandardCharsets.UTF_8);
            return Files.readString(user, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LogUtils.getLogger().warn("Could not load AI prompt preferences: {}", e.getMessage());
            return "";
        }
    }

    private static String resource(String name) {
        try (var stream = AiPromptAssembler.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) throw new IllegalStateException("Missing built-in AI prompt: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) { throw new IllegalStateException("Could not read AI prompt " + name, e); }
    }
}
