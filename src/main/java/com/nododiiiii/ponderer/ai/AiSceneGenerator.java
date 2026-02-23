package com.nododiiiii.ponderer.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Orchestrator: assembles the prompt, calls the LLM provider in two passes,
 * validates the result, writes the scene JSON to disk and triggers reload.
 *
 * <p>Pass 1 — outline: LLM designs the scene narrative and declares required game elements
 * (REQUIRED_ELEMENTS line). No JSON output, no registry mapping needed.
 *
 * <p>Pass 2 — JSON: LLM generates the full scene JSON, guided by the outline and a
 * targeted registry mapping built from the elements declared in Pass 1.
 */
public class AiSceneGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .setLenient()
        .create();
    private static final Gson GSON_PRETTY = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    /**
     * Generate a ponder scene from structure + user prompt using two LLM passes.
     *
     * @param structurePaths  list of NBT file paths for the structures
     * @param carrierItemId   the item ID this scene is for (e.g. "minecraft:piston")
     * @param userPrompt      user's natural language prompt
     * @param referenceUrls   optional reference web page URLs
     * @param existingJson    existing JSON for adjustment mode (null for new generation)
     * @param onSuccess       callback on main thread with the saved file path
     * @param onError         callback on main thread with error message
     * @param onStatus        callback on main thread with intermediate status messages
     */
    public static void generate(List<Path> structurePaths, String carrierItemId, String userPrompt,
                                 List<String> referenceUrls, String existingJson,
                                 Consumer<String> onSuccess, Consumer<String> onError,
                                 Consumer<String> onStatus) {
        String apiKey = Config.AI_API_KEY.get().trim();
        if (apiKey.isEmpty()) {
            onError.accept("API key not configured. Set it in Settings > AI Configuration.");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Parse structures (fall back to built-in basic if none specified)
                List<StructureDescriber.StructureInfo> structures = new ArrayList<>();
                List<String> structureNames = new ArrayList<>();
                List<String> allBlockTypes = new ArrayList<>();

                if (structurePaths.isEmpty()) {
                    try (var is = SceneStore.openBuiltinStructure("basic")) {
                        if (is != null) {
                            StructureDescriber.StructureInfo info = StructureDescriber.describe(is);
                            structures.add(info);
                            structureNames.add("ponderer:basic");
                            allBlockTypes.addAll(info.blockTypes());
                        }
                    }
                } else {
                    for (Path nbtPath : structurePaths) {
                        StructureDescriber.StructureInfo info = StructureDescriber.describe(nbtPath);
                        structures.add(info);
                        String name = nbtPath.getFileName().toString();
                        if (name.endsWith(".nbt")) name = name.substring(0, name.length() - 4);
                        structureNames.add("ponderer:" + name);
                        for (String bt : info.blockTypes()) {
                            if (!allBlockTypes.contains(bt)) allBlockTypes.add(bt);
                        }
                    }
                }

                // 2. Build structure description text (shared between both passes)
                StringBuilder structDescBuf = new StringBuilder();
                structDescBuf.append("=== Structure Information ===\n");
                structDescBuf.append("Structure pool:\n");
                for (int i = 0; i < structureNames.size(); i++) {
                    structDescBuf.append("  ").append(i + 1).append(". ").append(structureNames.get(i)).append("\n");
                }
                structDescBuf.append("\n");
                for (int i = 0; i < structures.size(); i++) {
                    structDescBuf.append("--- Structure: ").append(structureNames.get(i)).append(" ---\n");
                    structDescBuf.append(structures.get(i).textDescription()).append("\n");
                }
                String structDesc = structDescBuf.toString();

                // 3. Fetch web pages (if any)
                List<LlmProvider.ContentBlock> webContent = new ArrayList<>();
                StringBuilder webLogBuf = new StringBuilder();
                for (String url : referenceUrls) {
                    if (url == null || url.isBlank()) continue;
                    try {
                        WebPageFetcher.WebPageContent page = WebPageFetcher.fetch(url);
                        webContent.add(new LlmProvider.ContentBlock.Text(
                            "=== Reference web page: " + url + " ===\n" + page.text()));
                        webLogBuf.append("=== ").append(url).append(" ===\n");
                        webLogBuf.append("Text length: ").append(page.text().length())
                            .append(" chars, Images: ").append(page.images().size()).append("\n\n");
                        webLogBuf.append(page.text()).append("\n\n");
                        for (WebPageFetcher.ImageData img : page.images()) {
                            webContent.add(new LlmProvider.ContentBlock.Image(img.base64(), img.mediaType()));
                            webLogBuf.append("[Image: ").append(img.mediaType())
                                .append(", base64 length: ").append(img.base64().length()).append("]\n");
                        }
                        webLogBuf.append("\n");
                    } catch (Exception e) {
                        LOGGER.warn("Failed to fetch reference URL: {}", url, e);
                        webContent.add(new LlmProvider.ContentBlock.Text(
                            "(Failed to fetch " + url + ": " + e.getMessage() + ")"));
                        webLogBuf.append("=== FAILED: ").append(url).append(" ===\n")
                            .append(e.getMessage()).append("\n\n");
                    }
                }
                if (webLogBuf.length() > 0) {
                    writeLog("last_web_content.log", webLogBuf.toString());
                }

                // Create LLM provider (reused for both passes)
                String provider = Config.AI_PROVIDER.get();
                LlmProvider llm = "anthropic".equals(provider)
                    ? new AnthropicProvider() : new OpenAiCompatProvider();
                String baseUrl = Config.getEffectiveBaseUrl();
                String model = Config.getEffectiveModel();
                String structuresStr = String.join(", ", structureNames);

                // ---- PASS 1: Generate scene outline ----
                notifyStatus(onStatus, "Generating outline (1/2)...");

                List<LlmProvider.ContentBlock> outlineContent = new ArrayList<>();
                outlineContent.add(new LlmProvider.ContentBlock.Text(structDesc));
                outlineContent.addAll(webContent);
                if (existingJson != null && !existingJson.isBlank()) {
                    outlineContent.add(new LlmProvider.ContentBlock.Text(
                        "=== Current scene JSON (adjust based on instruction below) ===\n" + existingJson));
                }
                outlineContent.add(new LlmProvider.ContentBlock.Text(
                    "=== User instruction ===\n" + userPrompt +
                    "\n\nTarget item ID: " + carrierItemId +
                    "\nStructures: " + structuresStr));

                String outline = llm.generate(buildOutlineSystemPrompt(), outlineContent,
                    baseUrl, apiKey, model).join();
                LOGGER.info("Scene outline generated ({} chars)", outline.length());
                writeLog("last_outline.log", outline);

                // ---- Parse required elements and build targeted registry mapping ----
                List<String> requiredElements = parseRequiredElements(outline);
                LOGGER.info("Required elements from outline: {}", requiredElements);
                String registryMapping = RegistryMapper.buildMappingForDisplayNames(requiredElements, allBlockTypes);
                writeLog("last_registry_mapping.log", registryMapping);

                // ---- PASS 2: Generate JSON ----
                notifyStatus(onStatus, "Generating JSON (2/2)...");

                List<LlmProvider.ContentBlock> jsonContent = new ArrayList<>();
                jsonContent.add(new LlmProvider.ContentBlock.Text(structDesc));
                jsonContent.add(new LlmProvider.ContentBlock.Text(registryMapping));
                jsonContent.addAll(webContent);
                if (existingJson != null && !existingJson.isBlank()) {
                    jsonContent.add(new LlmProvider.ContentBlock.Text(
                        "=== Current scene JSON (modify based on instruction below) ===\n" + existingJson));
                }
                jsonContent.add(new LlmProvider.ContentBlock.Text(
                    "=== Scene design outline (follow this plan) ===\n" + outline));
                jsonContent.add(new LlmProvider.ContentBlock.Text(
                    "=== User instruction ===\n" + userPrompt +
                    "\n\nTarget item ID: " + carrierItemId +
                    "\nStructures: " + structuresStr));

                String response = llm.generate(buildSystemPrompt(), jsonContent,
                    baseUrl, apiKey, model).join();

                // 7. Extract and clean JSON from response
                LOGGER.info("Raw LLM response generated ({} chars)", response.length());
                writeLog("last_json_response.log", response);
                String json = extractJson(response);
                json = cleanJson(json);
                writeLog("last_extracted_json.log", json);

                // 8. Validate by parsing
                DslScene scene;
                try {
                    scene = GSON.fromJson(json, DslScene.class);
                } catch (Exception parseEx) {
                    LOGGER.error("JSON parse failed. Extracted JSON ({} chars, first 200: {})",
                        json.length(), json.substring(0, Math.min(200, json.length())));
                    writeLog("last_parse_error.log",
                        "Error: " + parseEx.getMessage() + "\n\nExtracted JSON:\n" + json);
                    throw new RuntimeException("JSON parse failed: " + parseEx.getMessage(), parseEx);
                }
                if (scene == null || scene.id == null || scene.id.isBlank()) {
                    throw new RuntimeException("Generated scene has no ID");
                }

                // 9. Pretty-print and save
                String prettyJson = GSON_PRETTY.toJson(scene);
                Path sceneDir = SceneStore.getSceneDir();
                Files.createDirectories(sceneDir);
                String fileName = scene.id.replace(":", "/") + ".json";
                if (scene.id.startsWith("ponderer:")) {
                    fileName = scene.id.substring("ponderer:".length()) + ".json";
                }
                Path filePath = sceneDir.resolve(fileName);
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, prettyJson, StandardCharsets.UTF_8);

                return filePath.toString();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).thenAcceptAsync(filePath -> {
            PondererClientCommands.reloadLocal();
            onSuccess.accept(filePath);
        }, Minecraft.getInstance()).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            LOGGER.error("AI scene generation failed", cause);
            String msg = cause.getMessage();
            if (msg == null) msg = cause.getClass().getSimpleName();
            String finalMsg = msg;
            Minecraft.getInstance().execute(() -> onError.accept(finalMsg));
            return null;
        });
    }

    /** Schedule a status notification on the main thread. */
    private static void notifyStatus(Consumer<String> onStatus, String message) {
        Minecraft.getInstance().execute(() -> onStatus.accept(message));
    }

    /** Write content to a log file under config/ponderer/prompts/ for debugging. */
    private static void writeLog(String fileName, String content) {
        try {
            Path file = getPromptsDir().resolve(fileName);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to write log file {}: {}", fileName, e.getMessage());
        }
    }

    /**
     * Parse the REQUIRED_ELEMENTS line from the outline.
     * Expected format (on its own line): {@code REQUIRED_ELEMENTS: Piston, Lever, minecraft:zombie}
     * Returns an empty list if the line is absent.
     */
    private static List<String> parseRequiredElements(String outline) {
        for (String line : outline.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("REQUIRED_ELEMENTS:")) {
                String rest = trimmed.substring("REQUIRED_ELEMENTS:".length()).trim();
                if (rest.isEmpty()) return List.of();
                List<String> result = new ArrayList<>();
                for (String part : rest.split(",")) {
                    String s = part.trim();
                    if (!s.isEmpty()) result.add(s);
                }
                return result;
            }
        }
        return List.of();
    }

    /** Extract JSON object from LLM response, stripping markdown code fences and surrounding text. */
    private static String extractJson(String response) {
        String trimmed = response.trim();

        // Strip markdown code fences: ```json ... ```, ```JSON ... ```, ``` ... ```
        int fenceStart = trimmed.indexOf("```json");
        if (fenceStart < 0) fenceStart = trimmed.indexOf("```JSON");
        if (fenceStart < 0) fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = trimmed.indexOf('\n', fenceStart);
            if (contentStart >= 0) {
                contentStart++;
                int fenceEnd = trimmed.indexOf("```", contentStart);
                if (fenceEnd > contentStart) {
                    trimmed = trimmed.substring(contentStart, fenceEnd).trim();
                }
            }
        }

        // Find the outermost JSON object by matching braces
        int braceStart = trimmed.indexOf('{');
        if (braceStart < 0) return trimmed;

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = braceStart; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return trimmed.substring(braceStart, i + 1);
                    }
                }
            }
        }

        // Fallback: first { to last }
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }

    /** Clean common LLM JSON issues: trailing commas, single-line comments outside strings. */
    private static String cleanJson(String json) {
        json = json.replaceAll(",\\s*}", "}");
        json = json.replaceAll(",\\s*]", "]");
        return json;
    }

    // -------------------------------------------------------------------------
    // System prompts — hot-reloadable from config/ponderer/prompts/
    // -------------------------------------------------------------------------

    private static final String OUTLINE_PROMPT_FILE = "outline_system.txt";
    private static final String JSON_PROMPT_FILE = "json_system.txt";

    private static Path getPromptsDir() {
        return FMLPaths.CONFIGDIR.get().resolve("ponderer").resolve("prompts");
    }

    /**
     * Load a prompt from an external file. If the file does not exist yet,
     * write the built-in default so the user has a starting point to edit.
     * The file is re-read on every generation call — no restart needed.
     */
    private static String loadOrCreatePrompt(String fileName, String defaultContent) {
        Path file = getPromptsDir().resolve(fileName);
        try {
            if (Files.exists(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultContent, StandardCharsets.UTF_8);
            LOGGER.info("Created default prompt file: {}", file);
        } catch (Exception e) {
            LOGGER.warn("Failed to load/create prompt file {}: {}", fileName, e.getMessage());
        }
        return defaultContent;
    }

    private static String buildOutlineSystemPrompt() {
        return loadOrCreatePrompt(OUTLINE_PROMPT_FILE, getDefaultOutlineSystemPrompt());
    }

    private static String buildSystemPrompt() {
        return loadOrCreatePrompt(JSON_PROMPT_FILE, getDefaultJsonSystemPrompt());
    }

    private static String getDefaultOutlineSystemPrompt() {
        return """
            You are a Minecraft Ponder scene designer. Your task is to PLAN a scene — write a plain-text outline, NOT JSON.

            A Ponder scene is an animated tutorial demonstrating a Minecraft mechanic using a 3D structure.
            It is composed of scene segments (pages), each with sequential animation steps.

            Based on the structure info and user instruction, write a concise outline covering:
            - How many segments and what each one demonstrates
            - For each segment: narrative flow, which step types to use, key visual moments
            - Emphasize visual actions over text: set_block, toggle_redstone_power, show_controls, create_item_entity, indicate_success, etc.
            - Keep text steps short (one sentence each) — they annotate what's happening visually

            Content focus by subject type:
            - **Single blocks/items**: Focus on FUNCTION and KEY NUMBERS (e.g., generation rate, capacity, burn time). Show core functionality with examples. Operational steps are secondary (e.g., how to place/use it, but don't over-detail).
            - **Machines/multi-block structures**: Focus on OPERATIONAL STEPS (input → processing → output). Show how to use it step-by-step. Technical numbers are secondary (mention key values only if they directly affect the workflow).
            - Note: This is a relative emphasis, not absolute — blocks may still show basic usage, machines may still mention important metrics. The split is about which aspect matters most to the player learning the content.

            Scene segmentation rules:
            - A scene segment is a "page" with its own show_structure + step sequence. Only create a NEW segment when there is a major topic shift or scene reset (e.g. switching from "introduction" to "advanced usage", or resetting the structure to show a different configuration).
            - Do NOT create a new segment for every small idea. Continuous flow within one topic should stay in one segment with many steps.
            - Typical scene: 1-3 segments, each with 8-20+ steps. Avoid many short segments (e.g. 5 segments with 3 steps each).

            Visual-first ordering:
            - When introducing a block or item, FIRST place it (set_block) or spawn it (create_item_entity), THEN show the text annotation.
            - Pattern: set_block/create_item_entity → idle (short) → text → idle (text duration)

            Key constraints to respect in your plan:
            - Floor awareness: read the structure's Y-layer layout to identify the floor Y. Plan set_block placements at floor_Y + 1 or higher — never on the floor itself.
            - Text overlap: each text label lasts for its duration. Plan idles between consecutive text steps (≥ previous text's duration), or use vertically separated points so labels don't collide.
            - Default structure (ponderer:basic): if no user NBT is provided, plan replace_blocks/set_block steps early to adapt the plain stone platform to the scene's theme.

            Available step types:
            show_structure, idle, text, show_controls, play_sound, set_block, destroy_block, replace_blocks,
            hide_section, show_section_and_merge, toggle_redstone_power, modify_block_entity_nbt,
            create_entity, create_item_entity, rotate_section, move_section, indicate_redstone,
            indicate_success, encapsulate_bounds, rotate_camera_y

            Coordinate system: X=east(+)/west(-), Y=up(+)/down(-), Z=south(+)/north(-). [0,0,0] = bottom-north-west corner.

            At the very end of your response, on its own line, list every block, item, entity, and sound you plan to reference:
            REQUIRED_ELEMENTS: <comma-separated list>

            Use display names for blocks/items in the SAME LANGUAGE as the user instruction (e.g. if the user writes Chinese, use "活塞", "红石灯"; if English, use "Piston", "Redstone Lamp").
            For modded content you may also use the ID path without namespace (e.g. "endoflame" instead of full "botania:endoflame") — this is more reliable than guessing display names.
            For entities use registry IDs (e.g. "minecraft:zombie", "minecraft:villager").
            For sounds use registry IDs (e.g. "minecraft:block.piston.extend", "minecraft:block.lever.click").

            CRITICAL: In the outline, ONLY use display names or ID paths. Do NOT guess or suggest:
            - Full registry IDs (e.g. do NOT write "botania:specialflower")
            - Block state properties or variant selectors (e.g. do NOT write "with endoflame variant" or "type=endoflame")
            - Implementation details about how to achieve a block — just name it (e.g. write "endoflame" not "specialflower with endoflame variant")
            The correct registry IDs will be resolved automatically in the next step. Your job is only to NAME what is needed, not to specify HOW to place it.
            """;
    }

    private static String getDefaultJsonSystemPrompt() {
        return """
            You are a Minecraft Ponder scene designer. You create JSON scene files for the Ponderer mod.

            A Ponder scene is an interactive tutorial that demonstrates game mechanics using a 3D structure.
            The scene is composed of sequential steps that show structures, add text annotations, manipulate blocks, spawn entities, and control the camera.

            ## JSON Schema

            Top-level fields:
            - "id": string (required) - Unique scene identifier, format "ponderer:<name>"
            - "items": string[] (required) - Item IDs that trigger this scene (the item the player looks at in JEI/creative to see this tutorial)
            - "title": {"en_us": "...", "zh_cn": "..."} - Localized title displayed in the scene list
            - "structures": string[] - Structure pool; each entry is a structure resource id (e.g. "ponderer:my_build"). Structures referenced by show_structure steps must be listed here.
            - "tags": string[] - Tags for categorization (usually empty)
            - "steps": [] - MUST be empty array; all steps go inside "scenes" segments instead
            - "scenes": array of scene segments (each segment is one "page" the player can navigate between)

            Each scene segment:
            - "id": string - Segment identifier (unique within this scene)
            - "title": {"en_us": "...", "zh_cn": "..."} - Segment title shown in the scene navigation bar
            - "steps": array of step objects executed sequentially

            ## Step Types — Detailed Reference

            Every step is a JSON object with a "type" field and type-specific parameters.
            The optional field "attachKeyFrame" (bool) can be added to ANY step to mark it as a timeline keyframe (the player can click the timeline to jump here).

            ---
            ### 1. show_structure
            **Purpose**: Renders the 3D structure into the scene. This is the FIRST step of every scene segment — without it the player sees nothing.
            **When to use**: At the beginning of every scene segment. Also use it when switching to a different structure mid-scene.
            **Fields**:
            - structure (string, required): resource id referencing an entry in the top-level "structures" array, e.g. "ponderer:my_build"
            - height (int, optional): if set (≥0), only shows blocks from this Y-layer upward (useful for revealing a build layer by layer). Omit to show the entire structure at once.
            **Example**: {"type":"show_structure", "structure":"ponderer:redstone_demo", "attachKeyFrame":true}

            ---
            ### 2. idle
            **Purpose**: Pauses the scene for a given number of ticks. Gives the player time to read text or observe animations.
            **When to use**: Between almost every other step. Without idle steps, everything would happen instantaneously. Use 20-40 ticks (1-2 seconds) after text, 5-10 ticks between quick operations.
            **Fields**:
            - duration (int, optional, default 20): pause length in game ticks (20 ticks = 1 second)
            **Example**: {"type":"idle", "duration":30}

            ---
            ### 3. text
            **Purpose**: Shows a floating text tooltip in the 3D scene, pointing at a specific location. This is the primary way to explain things to the player.
            **When to use**: Whenever you need to annotate, explain, or call attention to a block or area. Combine with placeNearTarget to make the text float near the pointed block.
            **IMPORTANT — overlap**: a text label stays visible for exactly "duration" ticks. If a second text step starts before the first one expires (idle gap < first duration), both labels render simultaneously and collide on screen. Prevent this by: (a) inserting an idle ≥ previous text's duration before the next text step, or (b) using a "point" with ≥ 2-block Y difference so the two labels are vertically separated.
            **Fields**:
            - text (LocalizedText, required): {"en_us":"English text", "zh_cn":"中文文本"}
            - point (float[3], required): the 3D coordinate the text points at. Use x.5 values to point at block centers (e.g. [2.5, 1.5, 3.5] = center of block at [2,1,3])
            - duration (int, optional, default 40): how long the text stays visible (ticks)
            - color (string, optional): text color theme. Values: "green" (general info), "blue" (highlight), "red" (warning/danger), "cyan" (secondary info), "input" (player interaction), "output" (result), "slow"/"medium"/"fast" (speed indicators). Default is white.
            - placeNearTarget (bool, optional): if true, the text label floats near the pointed position instead of at a fixed screen location. Almost always set to true.
            **Example**: {"type":"text", "text":{"en_us":"This lever controls the piston","zh_cn":"这个拉杆控制活塞"}, "point":[2.5,2.5,3.5], "duration":60, "color":"green", "placeNearTarget":true, "attachKeyFrame":true}

            ---
            ### 4. shared_text
            **Purpose**: Like text, but references a pre-defined lang key instead of inline text. Used for reusable/standardized messages.
            **When to use**: When referencing a built-in or shared localization key that already exists in the lang files. (Not Suggested)
            **Fields**:
            - key (string, required): lang file key (e.g. "ponderer.scene.redstone.power_on")
            - point (float[3], required): 3D coordinate the text points at
            - duration (int, optional, default 40): display duration in ticks
            - color (string, optional): same color options as text
            - placeNearTarget (bool, optional): float near target position
            **Example**: {"type":"shared_text", "key":"ponderer.scene.tip.right_click", "point":[3.5,1.5,2.5], "duration":40, "placeNearTarget":true}

            ---
            ### 5. rotate_camera_y
            **Purpose**: Smoothly rotates the camera around the Y (vertical) axis. Gives the player a different viewing angle of the structure.
            **When to use**: When you want to show the build from a different side, or create a cinematic rotation effect. Use after showing an initial view and before explaining something on the other side.
            **Fields**:
            - degrees (float, optional, default 90): rotation angle in degrees. Positive = clockwise when viewed from above. Use 90 for quarter turn, 180 for half turn.
            **Example**: {"type":"rotate_camera_y", "degrees":-90, "attachKeyFrame":true}

            ---
            ### 6. show_controls
            **Purpose**: Shows an on-screen control hint icon (mouse button, scroll wheel) to tell the player what input action to perform on a block.
            **When to use**: When teaching the player to interact with a block — e.g. "right-click this lever", "scroll on the wrench", "sneak + left-click".
            **Fields**:
            - point (float[3], required): position in the scene where the control indicator appears
            - direction (string, required): which side of the point the icon appears. Values: "up", "down", "left", "right"
            - action (string, required): input type. Values: "left" (left-click), "right" (right-click), "scroll" (scroll wheel)
            - duration (int, optional, default 40): how long the indicator is shown
            - item (string, optional): item id to display alongside the control (e.g. "minecraft:stick" shows the item icon)
            - whileSneaking (bool, optional): if true, shows a "while sneaking" modifier
            - whileCTRL (bool, optional): if true, shows a "while holding CTRL" modifier
            **Example**: {"type":"show_controls", "point":[2.5,2.5,3.5], "direction":"down", "action":"right", "duration":40, "item":"minecraft:lever"}

            ---
            ### 7. play_sound
            **Purpose**: Plays a sound effect during the scene. Adds audio feedback to important moments.
            **When to use**: When a block activates (piston extending, door opening, explosion), to make the tutorial feel alive. Use sparingly.
            **Fields**:
            - sound (string, required): sound event id (e.g. "minecraft:block.piston.extend", "minecraft:block.lever.click", "minecraft:entity.experience_orb.pickup")
            - soundVolume (float, optional, default 1.0): volume multiplier
            - pitch (float, optional, default 1.0): pitch multiplier (>1 = higher, <1 = lower)
            - source (string, optional, default "master"): sound category. Values: "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"
            **Example**: {"type":"play_sound", "sound":"minecraft:block.lever.click", "soundVolume":0.8, "pitch":1.2}

            ---
            ### 8. encapsulate_bounds
            **Purpose**: Overrides the visible scene bounding box. By default the scene bounds match the structure size. Use this to shrink or expand the visible area.
            **When to use**: Rarely needed. Useful when you want to focus on a sub-region of a large structure, or expand bounds to show entities that move outside the structure.
            **Fields**:
            - bounds (int[3], required): new scene dimensions [sizeX, sizeY, sizeZ]
            **Example**: {"type":"encapsulate_bounds", "bounds":[5, 4, 5]}

            ---
            ### 9. set_block
            **Purpose**: Places a block at a position (or fills a region) in the scene. The block appears instantly with optional particle effects.
            **When to use**: When demonstrating block placement, showing "before/after" states, or dynamically building up a structure during the tutorial.
            **IMPORTANT**: Check the structure's Y-layer layout in the description to identify the floor Y level. Always place new blocks at floor_Y + 1 or above — never overwrite floor/base blocks unintentionally.
            **Fields**:
            - block (string, required): block id (e.g. "minecraft:redstone_lamp", "minecraft:oak_planks")
            - blockPos (int[3], required): position to place the block [x, y, z]
            - blockPos2 (int[3], optional): if provided, fills the entire region from blockPos to blockPos2
            - blockProperties (object, optional): block state properties as key-value pairs, e.g. {"facing":"north", "powered":"true"}
            - spawnParticles (bool, optional, default false): if true, shows block break particles when placing
            **Example**: {"type":"set_block", "block":"minecraft:redstone_lamp", "blockPos":[2,1,3], "blockProperties":{"lit":"true"}, "spawnParticles":true}

            ---
            ### 10. destroy_block
            **Purpose**: Removes a block from the scene, optionally showing destruction particles. The block position becomes air.
            **When to use**: When showing what happens when a block is broken, or when clearing blocks for the next step of a tutorial.
            **Fields**:
            - blockPos (int[3], required): position of the block to destroy
            - destroyParticles (bool, optional, default true): if true, shows block breaking particle effect
            **Example**: {"type":"destroy_block", "blockPos":[2,1,3], "destroyParticles":true}

            ---
            ### 11. replace_blocks
            **Purpose**: Replaces all blocks in a region with a new block type. Similar to set_block with a range, but semantically represents "replacing existing blocks".
            **When to use**: When swapping materials in a region, or showing an upgrade/transformation (e.g. replacing cobblestone with stone bricks).
            **Fields**:
            - block (string, required): the new block id
            - blockPos (int[3], required): one corner of the region
            - blockPos2 (int[3], required): opposite corner of the region
            - blockProperties (object, optional): block state properties
            - spawnParticles (bool, optional, default false): show particles during replacement
            **Example**: {"type":"replace_blocks", "block":"minecraft:stone_bricks", "blockPos":[0,0,0], "blockPos2":[4,0,4], "spawnParticles":true}

            ---
            ### 12. hide_section
            **Purpose**: Hides a rectangular section of blocks with a slide-out animation. The blocks visually slide away in the specified direction and disappear.
            **When to use**: When you want to remove part of the structure to reveal what's behind/underneath, or to clear the view before showing a new configuration.
            **Fields**:
            - blockPos (int[3], required): one corner of the section to hide
            - blockPos2 (int[3], required): opposite corner
            - direction (string, required): slide-out direction. Values: "up", "down", "north", "south", "east", "west"
            **Example**: {"type":"hide_section", "blockPos":[0,2,0], "blockPos2":[4,3,4], "direction":"up"}

            ---
            ### 13. show_section_and_merge
            **Purpose**: Shows a rectangular section of blocks with a slide-in animation from the given direction, then merges it into the main scene. Used to dramatically reveal parts of a build.
            **When to use**: When building up a structure piece by piece, or revealing a hidden mechanism. The linkId lets you track this section for later move_section/rotate_section operations.
            **Fields**:
            - blockPos (int[3], required): one corner of the section
            - blockPos2 (int[3], required): opposite corner
            - direction (string, required): direction the section slides IN from. Values: "up", "down", "north", "south", "east", "west"
            - linkId (string, optional, default "default"): identifier for this section. Use unique ids if you want to animate sections independently later.
            **Example**: {"type":"show_section_and_merge", "blockPos":[0,1,0], "blockPos2":[4,1,4], "direction":"down", "linkId":"floor"}

            ---
            ### 14. toggle_redstone_power
            **Purpose**: Toggles redstone power state for blocks in a region. Activates or deactivates redstone-powered blocks (levers, repeaters, lamps, pistons, etc.).
            **When to use**: When demonstrating redstone circuits — flipping a lever, showing a piston extending, lighting a redstone lamp. This simulates the player interacting with redstone.
            **Fields**:
            - blockPos (int[3], required): one corner of the region
            - blockPos2 (int[3], optional): opposite corner. If omitted, affects only the single block at blockPos.
            **Example**: {"type":"toggle_redstone_power", "blockPos":[2,1,3]}

            ---
            ### 15. modify_block_entity_nbt
            **Purpose**: Directly modifies the NBT data of a block entity (chest, sign, command block, etc.). Allows changing container contents, sign text, and other tile entity data.
            **When to use**: When you need to show specific block entity states that can't be achieved through normal block placement — e.g. items inside a chest, text on a sign.
            **Fields**:
            - blockPos (int[3], required): position of the block entity
            - blockPos2 (int[3], optional): opposite corner for a region
            - nbt (string, required): SNBT string to merge into the block entity's data, e.g. "{Items:[{id:'minecraft:diamond',Count:1b,Slot:0b}]}"
            - reDrawBlocks (bool, optional, default false): if true, forces the block to re-render after NBT change (needed for visual updates on some blocks)
            **Example**: {"type":"modify_block_entity_nbt", "blockPos":[2,1,3], "nbt":"{Items:[{id:\\"minecraft:diamond\\",Count:1b,Slot:0b}]}", "reDrawBlocks":true}

            ---
            ### 16. create_entity
            **Purpose**: Spawns a living entity (mob) into the scene. The entity has AI and gravity disabled by default, so it stays in place as a visual prop.
            **When to use**: When the tutorial involves mobs — e.g. showing a mob farm, demonstrating a trap, or illustrating mob behavior.
            **Fields**:
            - entity (string, required): entity type id (e.g. "minecraft:zombie", "minecraft:villager", "minecraft:cow")
            - pos (float[3], required): spawn position (can also use "point" field)
            - yaw (float, optional): horizontal rotation in degrees (facing direction)
            - pitch (float, optional): vertical head tilt in degrees
            - lookAt (float[3], optional): alternative to yaw/pitch — entity will face this position
            **Example**: {"type":"create_entity", "entity":"minecraft:villager", "pos":[2.5, 1.0, 3.5], "yaw":180}

            ---
            ### 17. create_item_entity
            **Purpose**: Spawns a floating item entity (dropped item) in the scene. Can have initial velocity to simulate items being thrown or ejected.
            **When to use**: When showing item drops, hopper transport, or illustrating crafting results. Use motion to make items fly out of machines.
            **Fields**:
            - item (string, required): item id (e.g. "minecraft:diamond", "minecraft:iron_ingot")
            - pos (float[3], required): spawn position (can also use "point" field)
            - motion (float[3], optional): initial velocity [vx, vy, vz]. Use small values like [0, 0.2, 0] for gentle upward pop.
            - count (int, optional, default 1): number of items to display in the stack
            **Example**: {"type":"create_item_entity", "item":"minecraft:iron_ingot", "pos":[2.5, 2.0, 3.5], "motion":[0, 0.15, 0]}

            ---
            ### 18. rotate_section
            **Purpose**: Smoothly rotates a section of blocks around its center over a duration. Creates a spinning/tilting animation effect.
            **When to use**: When demonstrating mechanical rotation, or for dramatic visual effect. The section must have been previously created with show_section_and_merge with a matching linkId.
            **Fields**:
            - linkId (string, required): the section to rotate (must match a previous show_section_and_merge linkId)
            - duration (int, optional, default 20): animation duration in ticks
            - rotX (float, optional): rotation around X axis in degrees
            - rotY (float, optional): rotation around Y axis in degrees (if omitted, uses the "degrees" field)
            - rotZ (float, optional): rotation around Z axis in degrees
            - degrees (float, optional): shorthand for rotY if rotY is not specified
            **Example**: {"type":"rotate_section", "linkId":"gear", "duration":40, "rotY":90}

            ---
            ### 19. move_section
            **Purpose**: Smoothly translates a section of blocks by an offset over a duration. Creates a sliding animation.
            **When to use**: When showing pistons pushing blocks, doors opening, or any translational movement of a group of blocks.
            **Fields**:
            - linkId (string, required): the section to move (must match a previous show_section_and_merge linkId)
            - duration (int, optional, default 20): animation duration in ticks
            - offset (float[3], required): movement offset [dx, dy, dz] in blocks
            **Example**: {"type":"move_section", "linkId":"piston_head", "duration":10, "offset":[1.0, 0, 0]}

            ---
            ### 20. indicate_redstone
            **Purpose**: Shows a pulsing red particle indicator at a block position. Visual cue to draw attention to a redstone-related block.
            **When to use**: When you want to highlight that a specific block is receiving or emitting redstone power, without changing any block state.
            **Fields**:
            - blockPos (int[3], required): position to show the indicator
            **Example**: {"type":"indicate_redstone", "blockPos":[2,1,3]}

            ---
            ### 21. indicate_success
            **Purpose**: Shows a pulsing green checkmark/success particle indicator at a block position. Visual cue for "this is correct" or "this is complete".
            **When to use**: Sparingly. Use only at true conclusion points (end of tutorial, final configuration achieved, or major milestone reached). Avoid spamming it at every small action — overuse dilutes its impact. Use 1-2 times per scene segment, not once per step.
            **Fields**:
            - blockPos (int[3], required): position to show the indicator
            **Example**: {"type":"indicate_success", "blockPos":[2,1,3]}

            ## Coordinate System
            - X: east(+)/west(-), Y: up(+)/down(-), Z: south(+)/north(-)
            - [0,0,0] is the bottom-north-west corner of the structure
            - For "point" fields (text, show_controls, entity pos): use float values. Use x.5 to target block centers (e.g. [2.5, 1.5, 3.5] = center of block [2,1,3])
            - For "blockPos" fields (set_block, destroy_block, etc.): use integer values [x, y, z]
            - All coordinates must be within the structure bounds

            ## CRITICAL: Show, Don't Tell
            Ponder scenes are VISUAL DEMONSTRATIONS, not text lectures.
            The player watches a 3D scene — they want to SEE things happen, not just read descriptions.

            **Rules for visual storytelling:**
            - When mentioning a block/item, SHOW it: use set_block to place it, or create_item_entity to spawn it. Use indicate_redstone for power flows; save indicate_success for major conclusions only.
            - When explaining a crafting recipe or result, use create_item_entity to spawn the items visually
            - When explaining player interaction, use show_controls to display the mouse action (left-click, right-click, scroll)
            - When explaining redstone mechanics, use toggle_redstone_power to actually toggle levers/lamps/pistons so the player sees the state change
            - When explaining a building process, use set_block step-by-step to place blocks one at a time or layer by layer, rather than describing it in text
            - Text steps should ANNOTATE what the player is seeing, not replace visual demonstration. Keep text SHORT (one sentence).
            - A good ratio: for every 1 text step, have at least 1-2 visual action steps (set_block, toggle_redstone_power, create_item_entity, show_controls, etc.)

            **Bad example** (text-only, avoid this):
            1. show_structure → 2. text "Place a lever on the block" → 3. idle → 4. text "The lever powers the lamp" → 5. idle → 6. text "The lamp turns on"

            **Good example** (visual-first demonstration):
            1. show_structure → 2. set_block lever → 3. idle → 4. text "Place a lever here" → 5. idle → 6. show_controls right-click → 7. toggle_redstone_power lever → 8. idle → 9. text "The lamp turns on!" → 10. indicate_success lamp_pos

            ## Scene Design Guidelines
            1. ALWAYS start each scene segment with show_structure — nothing is visible without it
            2. Use idle steps between content steps (20-40 ticks for reading text, 5-10 ticks between quick block operations)
            3. Set placeNearTarget: true on text steps so labels float near the relevant blocks
            4. Use attachKeyFrame: true on important steps — these become clickable markers on the timeline
            5. Provide both en_us and zh_cn text in all text steps
            6. Scene segmentation: only create a new segment for major topic shifts or scene resets. Keep continuous flow within one segment. Typical: 1-3 segments with 8-20+ steps each. Avoid many short segments.
            7. Color conventions: "green" = general info, "red" = warning/important, "blue" = highlight, "input" = player action required
            8. Visual-first ordering: when introducing a block or item, FIRST show it visually (set_block / create_item_entity), THEN add the text annotation. Pattern: visual action → idle (10-20 ticks) → text → idle (≥ text duration). The player should SEE the thing before reading about it.
            9. Typical scene flow: show_structure → visual action (set_block/create_item_entity) → idle → text (brief annotation) → idle → more visual actions → ... → (only at the very end) indicate_success
            9. Use rotate_camera_y when the interesting part is on a different side of the structure
            10. When the tutorial involves multiple blocks, place them one at a time with short idles between each, so the player can follow along
            11. Use destroy_block + set_block pairs to show "replace this block with that block" visually
            12. Use create_item_entity to show items that a machine produces, a mob drops, or a player needs to use
            13. Floor awareness: read the structure description's Y-layer layout to find the floor Y. Always place new blocks at floor_Y + 1 or higher — never accidentally overwrite the floor or base layer with set_block.
            14. Text overlap prevention: each text label persists for its "duration" ticks. Before starting a new text step, ensure the previous text has had time to expire. Either add idle ≥ previous text's duration, or point the new text at a position with ≥ 2-block Y difference from the previous one so labels don't visually collide.
            15. Default structure adaptation: when using ponderer:basic (no user-provided NBT), the scene starts as a plain stone platform. Use set_block or replace_blocks early in the scene to adapt the floor and surroundings to match the tutorial's theme (e.g. replace the floor with dirt/farmland for farming, with sand for desert, or add context walls/props to make the demonstration feel natural).

            ## Important Rules
            - Output ONLY valid JSON. No markdown fences, no comments, no trailing commas, no explanation text
            - The JSON must be a single, complete DslScene object
            - **CRITICAL — Block/Item ID resolution**: The "block" field in set_block/replace_blocks and all item/entity IDs MUST use the EXACT registry ID from the "Display Name → Registry ID mapping" provided above. Do NOT use any other ID, even if the outline suggests one.
              - If the mapping says `endoflame → botania:endoflame`, use `"block": "botania:endoflame"` directly.
              - NEVER use a generic parent block + blockProperties/variant to select a sub-type (e.g. do NOT use `"block": "botania:specialflower"` with `"blockProperties": {"type": "endoflame"}`).
              - The mapping is authoritative — it overrides your training knowledge and any suggestions from the outline.
            - The "steps" array at the top level MUST be empty []; put all steps inside "scenes" segments
            - Screenshots from reference URLs are for understanding the workflow only; use the NBT structure data for accurate block positions
            """;
    }
}
