package com.nododiiiii.ponderer.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.Config;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.LocalizedText;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.ui.UIText;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
                                 boolean buildTutorial, boolean includeImages,
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
                Set<String> allBlockTypesSet = new LinkedHashSet<>();

                if (structurePaths.isEmpty()) {
                    try (var is = SceneStore.openBuiltinStructure("basic")) {
                        if (is != null) {
                            StructureDescriber.StructureInfo info = StructureDescriber.describe(is);
                            structures.add(info);
                            structureNames.add("ponderer:basic");
                            allBlockTypesSet.addAll(info.blockTypes());
                        }
                    }
                } else {
                    for (Path nbtPath : structurePaths) {
                        StructureDescriber.StructureInfo info = StructureDescriber.describe(nbtPath);
                        structures.add(info);
                        String name = nbtPath.getFileName().toString();
                        if (name.endsWith(".nbt")) name = name.substring(0, name.length() - 4);
                        structureNames.add("ponderer:" + name);
                        allBlockTypesSet.addAll(info.blockTypes());
                    }
                }
                List<String> allBlockTypes = new ArrayList<>(allBlockTypesSet);

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
                if (referenceUrls.stream().anyMatch(u -> u != null && !u.isBlank())) {
                    notifyStatus(onStatus, UIText.of("ponderer.ui.ai_generate.status.fetching_web"));
                }
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
                        if (includeImages) {
                            for (WebPageFetcher.ImageData img : page.images()) {
                                webContent.add(new LlmProvider.ContentBlock.Image(img.base64(), img.mediaType()));
                                webLogBuf.append("[Image: ").append(img.mediaType())
                                    .append(", base64 length: ").append(img.base64().length()).append("]\n");
                            }
                        } else {
                            webLogBuf.append("[Images skipped (includeImages=false), count: ")
                                .append(page.images().size()).append("]\n");
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
                notifyStatus(onStatus, UIText.of("ponderer.ui.ai_generate.status.outline"));

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

                String outline = llm.generate(buildOutlineSystemPrompt(buildTutorial), outlineContent,
                    baseUrl, apiKey, model).join();
                LOGGER.info("Scene outline generated ({} chars)", outline.length());
                writeLog("last_outline.log", outline);

                // ---- Parse required elements and build targeted registry mapping ----
                List<String> requiredElements = parseRequiredElements(outline);
                LOGGER.info("Required elements from outline: {}", requiredElements);
                String registryMapping = RegistryMapper.buildMappingForDisplayNames(requiredElements, allBlockTypes);
                writeLog("last_registry_mapping.log", registryMapping);

                // ---- PASS 2: Generate JSON (with retry on parse failure) ----
                notifyStatus(onStatus, UIText.of("ponderer.ui.ai_generate.status.json"));

                DslScene scene = null;
                String json = null;
                int parseAttempt = 0;

                while (parseAttempt < 2 && scene == null) {
                    parseAttempt++;
                    if (parseAttempt == 2) {
                        notifyStatus(onStatus, UIText.of("ponderer.ui.ai_generate.status.retry"));
                    }

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

                    // On retry, add error context
                    if (parseAttempt == 2 && json != null) {
                        jsonContent.add(new LlmProvider.ContentBlock.Text(
                            "=== PREVIOUS ATTEMPT FAILED ===\n" +
                            "Your previous JSON response was invalid or incomplete. Please generate a complete, " +
                            "valid JSON DslScene object that can be parsed correctly.\n" +
                            "First 500 chars of previous attempt:\n" + json.substring(0, Math.min(500, json.length()))));
                    }

                    jsonContent.add(new LlmProvider.ContentBlock.Text(
                        "=== User instruction ===\n" + userPrompt +
                        "\n\nTarget item ID: " + carrierItemId +
                        "\nStructures: " + structuresStr));

                    String response = llm.generate(buildSystemPrompt(buildTutorial), jsonContent,
                        baseUrl, apiKey, model).join();

                    // Extract and clean JSON from response
                    LOGGER.info("Pass 2 attempt {} - Raw LLM response generated ({} chars)", parseAttempt, response.length());
                    writeLog("last_json_response_attempt_" + parseAttempt + ".log", response);
                    json = extractJson(response);
                    json = cleanJson(json);
                    writeLog("last_extracted_json_attempt_" + parseAttempt + ".log", json);

                    // Try to parse
                    try {
                        scene = GSON.fromJson(json, DslScene.class);
                        if (scene == null || scene.id == null || scene.id.isBlank()) {
                            scene = null; // Force retry
                            LOGGER.warn("Pass 2 attempt {}: Scene parsed but has no ID or is null", parseAttempt);
                        } else {
                            LOGGER.info("Pass 2 attempt {}: JSON parsed successfully", parseAttempt);
                        }
                    } catch (Exception parseEx) {
                        LOGGER.warn("Pass 2 attempt {}: JSON parse failed - {}", parseAttempt, parseEx.getMessage());
                        if (parseAttempt < 2) {
                            LOGGER.info("Retrying JSON generation...");
                        } else {
                            LOGGER.error("Both JSON generation attempts failed. Last error: {}", parseEx.getMessage());
                            writeLog("last_parse_error.log",
                                "Both attempts failed.\n\n" +
                                "Attempt 1 error: (check last_extracted_json_attempt_1.log)\n" +
                                "Attempt 2 error: " + parseEx.getMessage() + "\n\nFinal extracted JSON:\n" + json);
                            throw new RuntimeException("JSON generation failed after 2 attempts. Last error: " + parseEx.getMessage(), parseEx);
                        }
                    }
                }

                if (scene == null) {
                    throw new RuntimeException("Failed to generate valid scene after 2 attempts");
                }

                // 9a. Post-process: auto-add attachKeyFrame to all non-text/idle steps
                autoAddKeyFrames(scene);

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
            Throwable cause = ex;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
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

    /** Write content to a log file under config/ponderer/logs/ for debugging. */
    private static void writeLog(String fileName, String content) {
        try {
            Path file = getLogsDir().resolve(fileName);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to write log file {}: {}", fileName, e.getMessage());
        }
    }

    /**
     * Parse the REQUIRED_ELEMENTS line from the outline.
     * Expected format (on its own line): {@code REQUIRED_ELEMENTS: Piston, Lever, minecraft:zombie}
     * Tolerates common LLM formatting variants: bold markers (**), extra spaces,
     * underscores vs spaces, mixed case, trailing punctuation, etc.
     * Returns an empty list if the line is absent.
     */
    private static List<String> parseRequiredElements(String outline) {
        String[] lines = outline.split("\n");
        for (int i = 0; i < lines.length; i++) {
            // Strip markdown bold/italic markers and leading/trailing whitespace
            // NOTE: do NOT strip underscores — they appear in "REQUIRED_ELEMENTS" and registry IDs
            String cleaned = lines[i].trim().replaceAll("[*`#>]", "").trim();
            // Normalize to uppercase, collapse spaces, strip possible dash prefix ("- REQUIRED_ELEMENTS:")
            String upper = cleaned.toUpperCase(Locale.ROOT).replaceAll("^[-\\s]+", "");
            // Match "REQUIRED_ELEMENTS" or "REQUIRED ELEMENTS" with optional underscore/space,
            // followed by optional colon/equals
            if (upper.startsWith("REQUIRED_ELEMENTS") || upper.startsWith("REQUIRED ELEMENTS")) {
                // Find where the key ends — skip past the label and separator
                int idx = cleaned.indexOf(':');
                if (idx < 0) idx = cleaned.indexOf('=');
                if (idx < 0) {
                    // Try to find the split after "REQUIRED_ELEMENTS" or "REQUIRED ELEMENTS"
                    idx = upper.startsWith("REQUIRED_ELEMENTS") ? "REQUIRED_ELEMENTS".length()
                        : "REQUIRED ELEMENTS".length();
                    // Skip trailing whitespace/punctuation after the label
                    while (idx < cleaned.length() && ":= \t".indexOf(cleaned.charAt(idx)) >= 0) idx++;
                    idx--; // will be incremented below
                }
                String rest = cleaned.substring(idx + 1).trim();
                // If the elements are on the next line(s), read ahead
                if (rest.isEmpty()) {
                    StringBuilder buf = new StringBuilder();
                    for (int j = i + 1; j < lines.length; j++) {
                        String nextLine = lines[j].trim();
                        if (nextLine.isEmpty()) break; // stop at blank line
                        if (buf.length() > 0) buf.append(", ");
                        buf.append(nextLine);
                    }
                    rest = buf.toString().trim();
                }
                if (rest.isEmpty()) return List.of();
                List<String> result = new ArrayList<>();
                for (String part : rest.split("[,;、]")) {
                    String s = part.trim();
                    if (!s.isEmpty()) result.add(s);
                }
                return result;
            }
        }
        return List.of();
    }

    /**
     * Post-process: auto-add attachKeyFrame to the first non-text/idle step
     * in each consecutive run. Resets when a text or idle step is encountered.
     */
    private static void autoAddKeyFrames(DslScene scene) {
        if (scene.scenes != null) {
            for (DslScene.SceneSegment seg : scene.scenes) {
                if (seg.steps != null) addKeyFramesToSteps(seg.steps);
            }
        }
    }

    private static void addKeyFramesToSteps(List<DslScene.DslStep> steps) {
        boolean needKeyFrame = true; // start true so first action step gets a keyframe
        for (DslScene.DslStep step : steps) {
            if (step.type == null) continue;
            String t = step.type.toLowerCase(java.util.Locale.ROOT);
            if ("text".equals(t) || "shared_text".equals(t) || "idle".equals(t)) {
                needKeyFrame = true;
            } else {
                if (needKeyFrame) {
                    step.attachKeyFrame = true;
                    needKeyFrame = false;
                }
            }
        }
    }

    /** Extract JSON object from LLM response, stripping markdown code fences and surrounding text. */
    private static String extractJson(String response) {
        String trimmed = response.trim();

        // Strip markdown code fences: ```json ... ```, ```JSON ... ```, ``` ... ```
        // Use regex to match ```json (case-insensitive) or plain ``` at line start,
        // avoiding false matches like ```javascript or ```jsonl
        java.util.regex.Matcher fenceMatcher = java.util.regex.Pattern.compile(
            "```(?:json|JSON)?\\s*\\n", java.util.regex.Pattern.MULTILINE
        ).matcher(trimmed);
        if (fenceMatcher.find()) {
            int contentStart = fenceMatcher.end();
            int fenceEnd = trimmed.indexOf("```", contentStart);
            if (fenceEnd > contentStart) {
                trimmed = trimmed.substring(contentStart, fenceEnd).trim();
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

    /** Clean common LLM JSON issues: trailing commas before } or ] (string-aware). */
    private static String cleanJson(String json) {
        StringBuilder sb = new StringBuilder(json.length());
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                sb.append(c);
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                sb.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }
            if (!inString && c == ',') {
                // Look ahead: skip whitespace, check if next non-ws char is } or ]
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
                if (j < json.length() && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    // Skip this trailing comma (don't append it)
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // System prompts — hot-reloadable from config/ponderer/prompts/
    // -------------------------------------------------------------------------

    private static final String OUTLINE_PROMPT_FILE = "outline_system.txt";
    private static final String JSON_PROMPT_FILE = "json_system.txt";

    private static Path getPromptsDir() {
        return FMLPaths.CONFIGDIR.get().resolve("ponderer").resolve("prompts");
    }

    private static Path getLogsDir() {
        return FMLPaths.CONFIGDIR.get().resolve("ponderer").resolve("logs");
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

    private static final String BUILD_TUTORIAL_PLACEHOLDER = "{{BUILD_TUTORIAL}}";

    private static final String BUILD_TUTORIAL_CONTENT = """

              ## MANDATORY: Build Tutorial as First Scene Segment
              The FIRST scene segment MUST be a step-by-step layer-by-layer build tutorial. This is NOT optional. Do NOT skip this. Do NOT merge it into other segments.

              **Structure of the first scene segment (BUILD TUTORIAL):**
              The first scene segment's ONLY purpose is to show the structure being built layer by layer from bottom to top.
              1. show_structure — render the FULL structure
              2. (Optional) text — a brief overview sentence about the structure while it's fully visible (e.g. "This is a large multiblock structure"), followed by idle
              3. hide_section covering ALL blocks from Y=1 to Y=max (direction "up") — so ONLY the Y=0 base layer is visible
              4. text explaining the base layer (Y=0) → idle
              5. show_section_and_merge for Y=1 layer (direction "down") → text explaining Y=1 → idle
              6. show_section_and_merge for Y=2 layer (direction "down") → text explaining Y=2 → idle
              7. Continue for EVERY remaining Y layer until the full structure is visible again

              **CRITICAL RULES:**
              - You MUST hide ALL layers above Y=0 first, then reveal them ONE BY ONE from bottom to top
              - EVERY distinct Y level MUST get its own show_section_and_merge step. Do NOT skip any Y level.
              - If two adjacent Y layers have identical block patterns, you may combine them into ONE show_section_and_merge covering both Y levels, but you must still show them — never skip.
              - Each layer reveal MUST be followed by text annotation explaining what blocks are at that layer, then idle
              - Since the default camera sees the north+west faces, start annotations on those faces. Use rotate_camera_y (positive = clockwise from above) if ports/details are on the hidden south/east side.
              - The first scene segment contains ONLY the build tutorial. All other content (operation, usage, etc.) goes in subsequent scene segments.

              **Example for a 6-layer structure (Y=0 to Y=5):**
              Scene segment 1 ("Build Tutorial" / "搭建教程"):
                step 1: show_structure (full)
                step 2: text "This is a large multiblock structure" (optional overview)
                step 3: idle 60
                step 4: hide_section blockPos=[0,1,0] blockPos2=[maxX, 5, maxZ] direction="up"  ← hides Y=1 through Y=5
                step 5: text "Base layer: ..." pointing at Y=0
                step 6: idle 60
                step 7: show_section_and_merge blockPos=[0,1,0] blockPos2=[maxX,1,maxZ] direction="down"  ← reveals Y=1
                step 8: text "Y=1 layer: ..." pointing at Y=1
                step 9: idle 60
                step 10: show_section_and_merge blockPos=[0,2,0] blockPos2=[maxX,2,maxZ] direction="down"  ← reveals Y=2
                step 11: text "Y=2 layer: ..."
                step 12: idle 60
                ... continue for Y=3, Y=4, Y=5 ...

              Then scene segment 2, 3, etc. contain the normal operation/usage tutorial.

              ## MANDATORY: Input/Output Overview Segment
              The scene segment IMMEDIATELY AFTER the build tutorial MUST be an **Input/Output Overview**. This segment visually demonstrates ALL inputs and outputs of the machine/structure.

              **How to create this segment:**
              1. Scan ALL input text (user instruction, reference material) and extract every input and output: items, fluids, gases, chemicals, energy, etc.
              2. For each input/output, identify:
                 - WHAT substance it is (e.g. steam, water, energy, items)
                 - WHERE on the structure it enters/exits (which block/port)
              3. Use show_controls at each port location to display the substance, followed by text explaining it.

              **Pattern for each input/output:**
              - show_controls position=[port_block_pos] item="substance_name" → idle → text "Input: ..." or "Output: ..." at the same position → idle

              **Example:**
              Scene segment 2 ("Input/Output Overview" / "输入与输出"):
                step 1: show_structure
                step 2: text "This machine has the following inputs and outputs."
                step 3: idle 60
                step 4: show_controls position=[valve_pos] item="蒸汽" — show steam input
                step 5: text "Input: Steam enters through turbine valves" (point at valve_pos)
                step 6: idle 60
                step 7: show_controls position=[output_valve_pos] item="能量" — show energy output (use the closest matching item/block)
                step 8: text "Output: Electrical energy exits through turbine valves" (point at output_valve_pos)
                step 9: idle 60
                step 10: show_controls position=[vent_pos] item="水" — show water output
                step 11: text "Output: Condensed water exits through turbine vents (optional)" (point at vent_pos)
                step 12: idle 60

              **CRITICAL**: Every show_controls in this segment MUST have an "item" field with the specific substance's display name (the JSON pass will resolve it to registry ID). Try to find the most specific item/fluid/gas — e.g. use "蒸汽" not "气体", use "水" not "液体".
            """;

    private static final String EXAMPLE_PLACEHOLDER = "{{EXAMPLE}}";

    /** Example for multi-block structures with build tutorial (buildTutorial=true) */
    private static final String MULTIBLOCK_EXAMPLE = """
            ## Example Output
            Below is a real example showing correct JSON structure for a Mekanism fission reactor with build tutorial.
            Adapt the pattern to your actual structure.
            ```json
            {
              "id": "ponderer:fission_reactor",
              "items": ["mekanismgenerators:fission_reactor_port"],
              "title": {"en_us": "Fission Reactor", "zh_cn": "裂变反应堆"},
              "structures": ["ponderer:gen"],
              "tags": [],
              "steps": [],
              "scenes": [
                {
                  "id": "build_tutorial",
                  "title": {"en_us": "Construction Guide", "zh_cn": "搭建教程"},
                  "steps": [
                    {"type": "show_structure", "structure": "ponderer:gen", "attachKeyFrame": true},
                    {"type": "text", "duration": 60, "text": {"en_us": "The fission reactor is a large multiblock structure", "zh_cn": "裂变反应堆是一个大型多方块结构"}, "point": [1.5,3.5,1.0], "color": "blue", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "hide_section", "direction": "up", "blockPos": [0,1,0], "blockPos2": [6,5,6]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Base layer: grass blocks and dirt frame", "zh_cn": "地基层：草方块和泥土"}, "point": [0.5,0.0,0.5], "color": "green", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_section_and_merge", "direction": "down", "linkId": "layer_y1", "blockPos": [0,1,0], "blockPos2": [6,1,6], "attachKeyFrame": true},
                    {"type": "text", "duration": 50, "text": {"en_us": "Y=1 layer: reactor casing frame and creative tanks", "zh_cn": "Y=1 层：裂变反应堆外壳框架和存储介质"}, "point": [1.0,1.5,1.5], "color": "green", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_section_and_merge", "direction": "down", "linkId": "layer_y2", "blockPos": [0,2,0], "blockPos2": [6,2,6]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Y=2 layer: reactor ports, fuel assemblies, reactor glass, and pipe connections", "zh_cn": "Y=2 层：裂变反应堆端口、燃料组件、强化玻璃和管道接口"}, "point": [1.0,2.5,1.5], "color": "blue", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_section_and_merge", "direction": "down", "linkId": "layer_y3", "blockPos": [0,3,0], "blockPos2": [6,3,6]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Y=3 layer: fuel assemblies and reactor glass (middle layer)", "zh_cn": "Y=3 层：裂变燃料组件和强化玻璃（中层）"}, "point": [1.0,3.5,1.5], "color": "green", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_section_and_merge", "direction": "down", "linkId": "layer_y4", "blockPos": [0,4,0], "blockPos2": [6,4,6]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Y=4 layer: control rod assemblies and reactor glass (above fuel)", "zh_cn": "Y=4 层：控制棒组件和强化玻璃（控制棒顶部）"}, "point": [1.0,4.5,1.5], "color": "blue", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_section_and_merge", "direction": "down", "linkId": "layer_y5", "blockPos": [0,5,0], "blockPos2": [6,5,6]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Y=5 layer: reactor casing roof (construction complete)", "zh_cn": "Y=5 层：裂变反应堆外壳顶部（完成搭建）"}, "point": [1.0,5.5,1.5], "color": "green", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60}
                  ]
                },
                {
                  "id": "port_modes",
                  "title": {"en_us": "Port Modes & Fluid Transfer", "zh_cn": "端口模式与流体传输"},
                  "steps": [
                    {"type": "show_structure", "structure": "ponderer:gen", "attachKeyFrame": true},
                    {"type": "rotate_camera_y", "degrees": -90},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 40, "text": {"en_us": "Fission reactor ports have three working modes", "zh_cn": "裂变反应堆端口有三种工作模式"}, "point": [1.0,2.5,2.5], "color": "blue", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_controls", "duration": 40, "point": [1.0,2.5,2.0], "direction": "right", "action": "right", "item": "mekanism:configurator", "attachKeyFrame": true},
                    {"type": "text", "duration": 40, "text": {"en_us": "Right-click with configurator to switch port mode", "zh_cn": "右键用扳手点击端口可切换模式"}, "point": [1.0,2.5,2.5], "color": "input", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 50, "text": {"en_us": "Input mode: receives fission fuel and coolant", "zh_cn": "输入模式：接收裂变燃料和冷却液"}, "point": [1.0,2.5,4.5], "color": "green", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "show_controls", "duration": 50, "point": [1.0,2.5,4.5], "direction": "down", "item": "minecraft:water"},
                    {"type": "show_controls", "duration": 50, "point": [1.0,2.5,2.5], "direction": "down", "item": "mekanism:fissile_fuel"},
                    {"type": "idle", "duration": 60},
                    {"type": "rotate_camera_y", "degrees": 90},
                    {"type": "text", "duration": 50, "text": {"en_us": "Output waste mode: outputs nuclear waste", "zh_cn": "输出废料模式：输出核废料"}, "point": [2.5,2.5,1.0], "color": "green", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "show_controls", "duration": 50, "point": [2.5,2.5,1.0], "direction": "down", "item": "mekanism:nuclear_waste"},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 50, "text": {"en_us": "Output coolant mode: outputs heated coolant", "zh_cn": "输出冷却剂模式：输出冷却剂"}, "point": [4.5,2.5,1.0], "color": "blue", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "show_controls", "duration": 50, "point": [4.5,2.5,1.0], "direction": "down", "item": "mekanism:water_vapor"},
                    {"type": "idle", "duration": 60}
                  ]
                }
              ]
            }
            ```
            Note the key patterns in this example:
            - Build tutorial: show_structure → overview text → hide upper layers → reveal layer by layer with text annotations
            - Optional overview text BEFORE hide_section to give context about the structure
            - show_controls WITH action="right" + item for player interaction (configurator right-click)
            - show_controls WITHOUT action for machine I/O indicator (water, fissile_fuel, nuclear_waste as port indicators)
            - Multiple show_controls can run simultaneously to show multiple I/O ports at once
            - Default idle duration is 60 ticks; text/show_controls duration is typically 50 ticks
            - zh_cn text is natural Chinese, not translated from English
            """;

    /** Example for single block / item introduction (buildTutorial=false) */
    private static final String SINGLE_BLOCK_EXAMPLE = """
            ## Example Output
            Below is a real example showing correct JSON structure for a Botania Entropinnyum tutorial (no build tutorial).
            Adapt the pattern to your actual block/item. Pay close attention to how different step types are used for different kinds: set_block for [block], create_entity for [entity], show_controls for player interactions.
            ```json
            {
              "id": "ponderer:entropinnyum_tutorial",
              "items": ["botania:entropinnyum"],
              "title": {"en_us": "Entropinnyum - Explosion to Mana", "zh_cn": "热爆花 - 爆炸转魔力"},
              "structures": ["ponderer:basic"],
              "tags": [],
              "steps": [],
              "scenes": [
                {
                  "id": "entropinnyum_introduction",
                  "title": {"en_us": "Introduction & Setup", "zh_cn": "介绍与设置"},
                  "steps": [
                    {"type": "show_structure", "structure": "ponderer:basic", "attachKeyFrame": true},
                    {"type": "replace_blocks", "block": "minecraft:grass_block", "blockPos": [0,0,0], "blockPos2": [4,0,4], "spawnParticles": false},
                    {"type": "set_block", "block": "botania:entropinnyum", "blockPos": [2,1,2], "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 50, "text": {"en_us": "The Entropinnyum is a generating flower that converts TNT explosions into mana.", "zh_cn": "热爆花是一种产能花，利用TNT爆炸转换成魔力。"}, "point": [2.5,1.5,2.0], "color": "green", "placeNearTarget": true, "attachKeyFrame": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_controls", "duration": 40, "point": [2.5,1.5,2.0], "direction": "up", "item": "botania:mana_bottle"},
                    {"type": "idle", "duration": 20},
                    {"type": "text", "duration": 50, "text": {"en_us": "It stores up to 6500 mana maximum, and one TNT explosion fills the entire buffer.", "zh_cn": "最大可储存6500点魔力，1个TNT可以充满。"}, "point": [2.5,1.5,2.0], "color": "blue", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 60, "text": {"en_us": "The Entropinnyum's detection range is 25×33×25 blocks: 12 blocks horizontally and 16 blocks vertically.", "zh_cn": "热爆花的检测范围是25×33×25，水平距离12格，竖直距离16格。"}, "point": [2.5,1.5,2.0], "color": "green", "placeNearTarget": true},
                    {"type": "idle", "duration": 60}
                  ]
                },
                {
                  "id": "entropinnyum_tnt_mechanics",
                  "title": {"en_us": "TNT Detection & Mana Generation", "zh_cn": "TNT检测与魔力生成"},
                  "steps": [
                    {"type": "show_structure", "structure": "ponderer:basic", "attachKeyFrame": true},
                    {"type": "replace_blocks", "block": "minecraft:grass_block", "blockPos": [0,0,0], "blockPos2": [4,0,4], "spawnParticles": false},
                    {"type": "set_block", "block": "botania:entropinnyum", "blockPos": [2,1,2]},
                    {"type": "idle", "duration": 20},
                    {"type": "set_block", "block": "minecraft:tnt", "blockPos": [1,1,2]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Place TNT within the Entropinnyum's detection range.", "zh_cn": "放置TNT在热爆花的范围内。"}, "point": [2.5,2.0,2.5], "color": "blue", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "show_controls", "duration": 40, "point": [2.5,2.0,2.5], "direction": "right", "action": "right", "item": "minecraft:flint_and_steel", "attachKeyFrame": true},
                    {"type": "idle", "duration": 20},
                    {"type": "text", "duration": 50, "text": {"en_us": "Use Flint and Steel to ignite the TNT.", "zh_cn": "用打火石点燃TNT。"}, "point": [2.5,2.0,2.5], "color": "input", "placeNearTarget": true},
                    {"type": "destroy_block", "blockPos": [1,1,2], "destroyParticles": false},
                    {"type": "create_entity", "entity": "minecraft:tnt", "pos": [1.5,1.0,2.5]},
                    {"type": "idle", "duration": 80},
                    {"type": "indicate_redstone", "blockPos": [2,1,2], "attachKeyFrame": true},
                    {"type": "text", "duration": 50, "text": {"en_us": "The Entropinnyum absorbs the explosion and converts it into mana.", "zh_cn": "热爆花吸收爆炸并转换成魔力。"}, "point": [2.5,1.5,2.0], "color": "green", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 50, "text": {"en_us": "Critical: If the mana buffer has remaining space, the TNT will explode normally without being absorbed.", "zh_cn": "重要：魔力存储有剩余时，TNT将继续爆炸而不被吸收。"}, "point": [2.5,1.5,2.0], "color": "red", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 50, "text": {"en_us": "The Entropinnyum only absorbs TNT explosions. It cannot absorb creeper explosions or other blast effects.", "zh_cn": "热爆花无法吸收苦力怕或其他爆炸效果，只吸收TNT。"}, "point": [2.5,1.5,2.0], "color": "red", "placeNearTarget": true},
                    {"type": "create_entity", "entity": "minecraft:creeper", "pos": [1.0,1.5,2.5]},
                    {"type": "idle", "duration": 60},
                    {"type": "clear_entities", "fullScene": true},
                    {"type": "text", "duration": 50, "text": {"en_us": "Since version 1.16, TNT duplicators using pistons, slime blocks, or rails are detected and rejected.", "zh_cn": "在1.16版本后，活塞、粘液块、铁轨制造的TNT复制机会被检测到。"}, "point": [2.5,1.5,2.0], "color": "red", "placeNearTarget": true},
                    {"type": "set_block", "block": "minecraft:slime_block", "blockPos": [1,1,1], "attachKeyFrame": true},
                    {"type": "set_block", "block": "minecraft:rail", "blockPos": [2,1,1]},
                    {"type": "set_block", "block": "minecraft:piston", "blockPos": [3,1,1]},
                    {"type": "idle", "duration": 60},
                    {"type": "text", "duration": 50, "text": {"en_us": "Detected duplicated TNT yields only 3 mana and causes no block destruction.", "zh_cn": "被检测到的复制TNT只产生3点魔力且不破坏方块。"}, "point": [2.5,1.5,2.0], "color": "red", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "destroy_block", "blockPos": [3,1,1]},
                    {"type": "destroy_block", "blockPos": [2,1,1]},
                    {"type": "destroy_block", "blockPos": [1,1,1]},
                    {"type": "text", "duration": 50, "text": {"en_us": "Enchanted Soil has no effect on the Entropinnyum and cannot increase its mana generation.", "zh_cn": "蕴魔土对热爆花无效，无法增加产魔效率。"}, "point": [2.5,1.5,2.0], "color": "red", "placeNearTarget": true},
                    {"type": "idle", "duration": 60},
                    {"type": "indicate_success", "blockPos": [2,1,2], "attachKeyFrame": true},
                    {"type": "idle", "duration": 60}
                  ]
                }
              ]
            }
            ```
            Note the key patterns in this example:
            - No build tutorial — starts directly with functional demonstration
            - replace_blocks with blockPos + blockPos2 to replace floor, then set_block to place the target block — adapts ponderer:basic structure for the scene
            - **Step type matches registry kind**: TNT is [entity/block/item] — first shown as set_block (placed block), then destroy_block + create_entity (ignited TNT entity). Creeper is [entity] → create_entity. Flint and Steel is [item] → show_controls (player interaction). This is the correct pattern: use the FIRST kind tag to choose step type.
            - show_controls with action="right" and item for player interactions (right-click with flint and steel)
            - Specific numbers in text (6500 mana, 25×33×25 range) — focus on KEY NUMBERS for single blocks
            - Default idle duration is 60 ticks; text duration is typically 50 ticks
            - Multiple scene segments when content has clear topic transitions
            - zh_cn text is natural Chinese, not translated from English
            """;

    private static String buildOutlineSystemPrompt(boolean buildTutorial) {
        String prompt = loadOrCreatePrompt(OUTLINE_PROMPT_FILE, getDefaultOutlineSystemPrompt());
        return prompt.replace(BUILD_TUTORIAL_PLACEHOLDER,
            buildTutorial ? BUILD_TUTORIAL_CONTENT : "");
    }

    private static String buildSystemPrompt(boolean buildTutorial) {
        String prompt = loadOrCreatePrompt(JSON_PROMPT_FILE, getDefaultJsonSystemPrompt());
        return prompt.replace(EXAMPLE_PLACEHOLDER,
            buildTutorial ? MULTIBLOCK_EXAMPLE : SINGLE_BLOCK_EXAMPLE);
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

            ## CORE RULE: Faithful to Source Material
            You MUST only include information that is explicitly stated in the provided inputs (user instruction, reference material, structure info). Do NOT:
            - Invent facts, numbers, or mechanics not mentioned in the inputs
            - Add real-world knowledge or analogies (e.g. do NOT compare a turbine to a real power plant)
            - Speculate about how something works if the reference material doesn't explain it
            - Add gameplay tips or advice not present in the source material
            If the inputs don't cover a topic, simply don't include it. The scene should teach exactly what the inputs describe — nothing more, nothing less.

            Content focus by subject type:
            - **Single blocks/items**: Focus on FUNCTION and KEY NUMBERS (e.g., generation rate, capacity, burn time). Show core functionality with examples. Operational steps are secondary (e.g., how to place/use it, but don't over-detail).
            - **Machines/multi-block structures**: Focus on OPERATIONAL STEPS (input → processing → output). Show how to use it step-by-step. Technical numbers are secondary (mention key values only if they directly affect the workflow).
            - Note: This is a relative emphasis, not absolute — blocks may still show basic usage, machines may still mention important metrics. The split is about which aspect matters most to the player learning the content.

            ## Version Handling
            When reference material (e.g. wiki pages) contains descriptions for MULTIPLE versions of the same mechanic (e.g. "Before 1.18" vs "After 1.18", or "Legacy" vs "Current"), always use the LATEST / MOST RECENT version's description. Ignore outdated version info unless the user explicitly asks about an older version.

            Scene segmentation rules:
            - A scene segment is a "page" with its own show_structure + step sequence. Only create a NEW segment when there is a major topic shift or scene reset (e.g. switching from "introduction" to "advanced usage", or resetting the structure to show a different configuration).
            - Do NOT create a new segment for every small idea. Continuous flow within one topic should stay in one segment with many steps.
            - Typical scene: 1-4 segments, each with 8-20+ steps. Avoid many short segments (e.g. 5 segments with 3 steps each). Simple blocks may need only 1-2 segments; complex multiblock machines can justify 3-4 segments.
            - **Input/Output segment**: If the subject has inputs and/or outputs (items, fluids, gases, energy, etc.), the FIRST content segment (i.e., the first segment for single blocks, or the segment right after the build tutorial for multiblock structures) MUST be an **Input/Output Overview**. Extract ALL inputs and outputs from the reference material, and for each one, use show_controls with the substance's item/fluid/gas display name at the corresponding port/block position, followed by text annotation. This gives the player an immediate visual overview of what goes in and what comes out before diving into details.
            """ + BUILD_TUTORIAL_PLACEHOLDER + """

            ## Duration Guidelines
            - Default idle duration: 60 ticks (3 seconds). Use this between most content steps.
            - Default text/show_controls duration: 50 ticks.
            - Short idle (10-20 ticks) is allowed for rapid consecutive small operations (e.g. between show_section_and_merge and its layer text).
            - **No idle needed between set_block and the text that immediately annotates it.** The block appears instantly, so the text can follow directly. Pattern: set_block → text → idle 60.

            Visual-first ordering:
            - When introducing a block or item, FIRST place it (set_block) or spawn it (create_item_entity), THEN show the text annotation.
            - Pattern: set_block → text → idle 60 (no idle between set_block and text)

            ## CRITICAL: Show, Don't Tell
            Ponder scenes are VISUAL DEMONSTRATIONS, not text lectures.
            The player watches a 3D scene — they want to SEE things happen, not just read descriptions.

            **Step 1 — Extract all items**: Before writing the outline, scan ALL input text (user instruction, reference material, structure info) and extract every concrete item mentioned: blocks, items, fluids, gases, chemicals, entities, tools, etc. Plan to VISUALLY SHOW each extracted item in the scene using a concrete step type:
            - Blocks (things that exist as placed blocks in the world) → use **set_block** to place them
            - Items (tools, ingredients, craftable things) → use **create_item_entity** to spawn as dropped item, or **show_controls** with action for player interaction
            - Fluids/chemicals/gases → use **show_controls** pointing at the machine port
            - Entities → use **create_entity** to spawn them
            - Player interactions (right-click, left-click) → use **show_controls** with action field
            Only omit an extracted item if you are confident it is irrelevant noise (e.g. unrelated sidebar content from a wiki page). When in doubt, show it.
            Note: The JSON generation pass will adjust the step type based on the registry mapping's [kind] tag priority. So use your best guess for now — the JSON pass will correct it if needed.

            **Step 2 — Visual storytelling rules:**
            - When mentioning a block/item, SHOW it visually with a concrete step type (set_block, create_item_entity, show_controls, indicate_redstone, etc.). Use indicate_redstone for power flows; save indicate_success for major conclusions only.
            - When explaining player interaction, use show_controls to display the mouse action (left-click, right-click, scroll)
            - **CRITICAL**: When your text mentions "use [item] with left/right click" (e.g. "右键使用扳手", "left-click with pickaxe"), you MUST use show_controls with BOTH the "action" field ("left"/"right") AND the "item" field. The action field is REQUIRED for player interactions — do NOT omit it.
            - When explaining redstone mechanics, use toggle_redstone_power to actually toggle levers/lamps/pistons so the player sees the state change
            - When explaining a building process, place blocks step-by-step or layer by layer, rather than describing it in text
            - **Machine I/O**: When a machine inputs/outputs substances through a port, use show_controls pointing at the port. Then add a text step labeling it as "Input: ..." or "Output: ...".
            - **CRITICAL — Text must point at the relevant block**: When a text step describes a specific block or component, its "point" coordinate MUST target that block's position, NOT the center of the structure. Pair the text with a show_controls or indicate_redstone on the SAME block so the player sees the visual indicator AND the text annotation together at that location.
              Example: "阀门输入水" → show_controls at the valve block (showing water icon) → text at the same valve block position explaining the input.
              **BAD**: text "阀门输入水" with point=[3.5, 4.5, 3.5] (center of structure, nowhere near the valve)
              **GOOD**: show_controls at valve [1.0, 2.5, 2.0] showing water → text at [1.0, 2.5, 2.0] "阀门输入水"
            - **Dropped items**: When items exist physically in the world (dropped items, items on the ground), spawn them visually with create_item_entity, and clean up with clear_item_entities when consumed. The JSON pass will correct the step type based on registry kind tags if needed.
            - Text steps should ANNOTATE what the player is seeing, not replace visual demonstration. Keep text SHORT (one sentence).
            - **HARD RULE — No naked text**: EVERY text step MUST be preceded by (or paired with) a visual action step that shows what the text is describing. A text step that simply appears after idle → text → idle with no visual is FORBIDDEN. If the text mentions an item/block/substance, spawn it (create_item_entity, set_block, create_entity) or point at it (show_controls, indicate_redstone) BEFORE the text. If the text describes a number/stat/property with no physical object, use show_controls pointing at the relevant block with an appropriate item icon.
              **BAD pattern** (naked text wall):
              text "最大燃烧耗时16000 tick" → idle → text "产魔速度1.5 mana/tick" → idle → text "最大产魔24000 mana" → idle
              **GOOD pattern** (every text has a visual):
              show_controls at flower item="煤块" → text "最大燃烧耗时16000 tick" → idle → indicate_redstone at flower → text "产魔速度1.5 mana/tick" → idle → show_controls at flower item="魔力" → text "最大产魔24000 mana" → idle
            - Use rotate_camera_y when the interesting part is on a different side of the structure
            - **Item entity lifecycle**: When showing multiple items in sequence, ALWAYS clean up before spawning the next.
            - **show_controls + text explanation pattern**: After a show_controls step, follow with idle → text step explaining what the control does.
            - **show_controls MUST have item**: Every show_controls step MUST specify what item/block to display. When pointing at a block/component, use that block's display name (the JSON pass will resolve it to registry ID). A show_controls without an item displays nothing and is useless. Example: show_controls pointing at electromagnetic coil → item="电磁线圈".

            **Bad example** (text-only, avoid this):
            1. show_structure → 2. text "Place a lever on the block" → 3. idle → 4. text "The lever powers the lamp" → 5. idle → 6. text "The lamp turns on"

            **Good example** (visual-first demonstration):
            1. show_structure → 2. set_block lever → 3. idle → 4. text "Place a lever here" → 5. idle → 6. show_controls right-click → 7. toggle_redstone_power lever → 8. idle → 9. text "The lamp turns on!" → 10. indicate_success lamp_pos

            Key constraints to respect in your plan:
            - Floor awareness: read the structure's Y-layer layout to identify the floor Y. Plan set_block placements at floor_Y + 1 or higher — never on the floor itself.
            - Default structure (ponderer:basic): if no user NBT is provided, plan replace_blocks/set_block steps early to adapt the plain stone platform to the scene's theme. For example, if demonstrating a flower or plant, replace the floor (or at least the block beneath it) with grass_block; if demonstrating a Nether item, replace with netherrack; if demonstrating an ocean-related item, replace with sand or prismarine, etc. The floor material should visually match the subject.

            Available step types:
            show_structure, idle, text, show_controls, play_sound, set_block, destroy_block, replace_blocks,
            hide_section, show_section_and_merge, toggle_redstone_power, modify_block_entity_nbt,
            create_entity, create_item_entity, clear_entities, clear_item_entities, rotate_section, move_section, indicate_redstone,
            indicate_success, encapsulate_bounds, rotate_camera_y

            Coordinate system: X=east(+)/west(-), Y=up(+)/down(-), Z=south(+)/north(-). [0,0,0] = bottom-north-west corner (the corner CLOSEST to the default camera).

            ## Camera Awareness
            The default camera looks from the NORTHWEST corner toward the SOUTHEAST.
            The player sees the **north face (Z=0 side)** and the **west face (X=0 side)** of the structure.
            The **south face (high Z)** and **east face (high X)** are HIDDEN from view.

            **CRITICAL — Annotation Positioning Rules**:
            1. For show_controls and text "point" coordinates, you MUST target a VISIBLE face of the block.
               - The VISIBLE face of a block is the face with the LOWEST X or LOWEST Z coordinate.
               - To target the **north face** (visible) of block [bx, by, bz]: use point [bx+0.5, by+0.5, bz] (Z = integer = north face).
               - To target the **west face** (visible) of block [bx, by, bz]: use point [bx, by+0.5, bz+0.5] (X = integer = west face).
               - NEVER use [bx+1, by+0.5, bz+0.5] (east face) or [bx+0.5, by+0.5, bz+1] (south face) — those faces are HIDDEN.
            2. **Example**: A 7×9×7 structure has a valve at block position [0,2,3].
               - CORRECT: show_controls position = [0, 2.5, 3.5] — targets the WEST face (X=0, visible).
               - WRONG: show_controls position = [1, 2.5, 3.5] — targets the EAST face (hidden).
               Similarly, a valve at [3,2,0]: position = [3.5, 2.5, 0] — targets the NORTH face (Z=0, visible).
            3. **When choosing which block to annotate**: If multiple equivalent blocks exist (e.g. valves on different faces), ALWAYS pick the one on the NORTH or WEST face (lowest Z or lowest X).
               - Example: If turbine valves exist at X=0 (west), X=6 (east), Z=0 (north), Z=6 (south) — choose the one at X=0 or Z=0.
            4. If you MUST annotate a block on the hidden side, insert rotate_camera_y BEFORE the annotation:
               - +90° reveals the east face (high X). -90° reveals the south face (high Z). +180° shows the back (southeast corner).
               - After rotating, remember subsequent annotations are from the new angle. Rotate back when done.
            5. **Default rule**: When no camera rotation has been applied, ALL show_controls and text annotations MUST point to blocks with X=0 or Z=0 face visible. If you find yourself writing a position where both X and Z are interior values and no rotate_camera_y precedes it, you are probably wrong.

            At the very end of your response, on its own line, list every block, item, fluid, chemical, entity, and sound you plan to reference:
            REQUIRED_ELEMENTS: <comma-separated list>

            **CRITICAL**: The REQUIRED_ELEMENTS list MUST include ALL substances mentioned in your outline:
            - Blocks you plan to place/reference (e.g. 活塞, Piston)
            - Items shown as drops or in show_controls (e.g. 铁锭, Iron Ingot)
            - **Fluids** piped in/out of machines (e.g. 水, Water, 熔岩, Lava)
            - **Gases and chemicals** from mods like Mekanism (e.g. 氢气, Hydrogen, 氧气, Oxygen, 蒸汽, Steam)
            - Entities spawned (e.g. minecraft:zombie)
            - Sounds played (e.g. minecraft:block.piston.extend)
            If your outline mentions a machine that processes/inputs/outputs a fluid, gas, or chemical, that substance MUST appear in REQUIRED_ELEMENTS. Missing elements will cause ID resolution to fail silently.
            **REQUIRED_ELEMENTS naming — THIS IS CRITICAL**:
            - Look at the reference material and user instruction. If they are in Chinese, ALL element names MUST be in Chinese. If in English, use English.
            - Use the EXACT display names as they appear in the reference material or game wiki. For example, if the wiki says "活塞", write "活塞", NOT "Piston".
            - NEVER mix languages: do NOT write English names when the reference material is Chinese. English names will FAIL to resolve to registry IDs on a Chinese game client.
            - Only use registry ID format (mod:name) for entities (minecraft:zombie), sounds (minecraft:block.piston.extend), and vanilla blocks that have no localized display name in the reference (e.g. minecraft:grass_block).
            - Example (Chinese context): REQUIRED_ELEMENTS: 活塞, 红石灯, TNT, 打火石, 水, minecraft:grass_block
            - Example (English context): REQUIRED_ELEMENTS: Piston, Redstone Lamp, TNT, Flint and Steel, Water

            ## Reference Material Coverage
            When reference material (wiki pages, user notes) is provided, you MUST read ALL sections thoroughly and cover every significant topic in your outline. Do NOT cherry-pick only the basic mechanics — if the reference has sections on advanced usage, edge cases, caveats, historical changes, special interactions, or warnings, you should include them in your scene plan (as text annotations or visual demonstrations).
            - Scan the entire reference for distinct topics/sections and plan at least one visual step or text annotation for each
            - Especially do NOT skip: warnings, restrictions, special notes, advanced techniques, version-specific behavior, and interaction caveats
            - If a reference section is very long and detailed (e.g. multi-version history), summarize the most relevant points for the LATEST version rather than ignoring it entirely

            ## Localization Quality Rules
            When generating zh_cn (Chinese) text content:
            - Write NATURAL, fluent Chinese directly. Do NOT think in English first and then translate — this produces unnatural "翻译腔" (translation-ese).
            - **Preserve proper nouns exactly**: Block names, item names, fluid names, chemical names, entity names, and mod names must use their ORIGINAL Chinese display names from the game (e.g. "活塞" not "推动装置", "红石灯" not "由红石供能的灯").
            - If the user prompt or reference material contains Chinese names for blocks/items, use those EXACT names in your text — do NOT paraphrase or re-translate them.
            - Keep text concise and game-appropriate. Use the tone of Minecraft wiki or in-game tutorials, not academic or literary Chinese.
            - **Do NOT use any emoji** in text content. No ⚠️, ✅, ❌, 🔥, etc. Use plain text only.

            Use display names for blocks/items/fluids/chemicals in the SAME LANGUAGE as the reference material and user instruction. If the reference wiki page is in Chinese, use Chinese display names everywhere — in outline text, show_controls items, AND REQUIRED_ELEMENTS.
            **Do NOT use English display names when the reference material is in Chinese** — they will fail registry lookup. For example, if the wiki says "活塞", use "活塞" everywhere, NOT "Piston".
            **Do NOT use registry IDs (e.g. minecraft:tnt, minecraft:flint_and_steel) for blocks/items in the outline text or REQUIRED_ELEMENTS** — always prefer display names. The registry ID resolution happens automatically in the next step.
            For modded content whose display name you are unsure of, use the ID path without namespace (e.g. "endoflame" instead of full "botania:endoflame").
            For entities use registry IDs (e.g. "minecraft:zombie", "minecraft:villager").
            For sounds use registry IDs (e.g. "minecraft:block.piston.extend", "minecraft:block.lever.click").

            CRITICAL: In the outline, ONLY use display names or ID paths. Do NOT guess or suggest:
            - Full registry IDs (e.g. do NOT write "botania:specialflower")
            - Block state properties or variant selectors (e.g. do NOT write "with endoflame variant" or "type=endoflame")
            - Implementation details about how to achieve a block — just name it (e.g. write "endoflame" not "specialflower with endoflame variant")
            The correct registry IDs will be resolved automatically in the next step. Your job is only to NAME what is needed, not to specify HOW to place it.

            ## SELF-CHECK: Scan for consecutive text walls
            After writing your complete outline, re-read it and check for **3 or more consecutive text steps** (where text steps are separated only by idle). If you find such a sequence:
            1. Identify the items/blocks/substances mentioned in each text
            2. Insert a visual step BEFORE every 2nd text onward: show_controls with the relevant item, create_item_entity, set_block, indicate_redstone, etc.
            3. Make sure no 3 consecutive text steps remain without a visual step between them
            This is your LAST step before outputting the outline — do NOT skip it.
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

            ## Step Types — Detailed Reference (ordered by frequency of use)

            Every step is a JSON object with a "type" field and type-specific parameters.
            The optional field "attachKeyFrame" (bool) can be added to ANY step to mark it as a timeline keyframe (the player can click the timeline to jump here).

            ---
            ### 1. show_structure
            **Purpose**: Renders the 3D structure into the scene. This is the FIRST step of every scene segment — without it the player sees nothing.
            **When to use**: At the beginning of every scene segment. Also use it when switching to a different structure mid-scene.
            **Fields**:
            - structure (string, required): resource id referencing an entry in the top-level "structures" array, e.g. "ponderer:my_build"
            - height (int, optional): if set (≥0), only shows blocks from this Y-layer upward (useful for revealing a build layer by layer). Omit to show the entire structure at once.
            - scale (float, optional): scene zoom factor. >1 = zoom in (larger), <1 = zoom out (smaller), omit for default (1.0). Useful when the structure is very large and needs to be shown smaller to fit the view.
            **Example**: {"type":"show_structure", "structure":"ponderer:redstone_demo", "attachKeyFrame":true}
            **Example with scale**: {"type":"show_structure", "structure":"ponderer:large_build", "scale":0.75, "attachKeyFrame":true}

            ---
            ### 2. idle
            **Purpose**: Pauses the scene for a given number of ticks. Gives the player time to read text or observe animations.
            **When to use**: Between almost every other step. Without idle steps, everything would happen instantaneously. Default idle is 60 ticks (3 seconds). Use shorter idles (10-20 ticks) for rapid consecutive small operations (e.g. between a block placement and its text annotation).
            **Fields**:
            - duration (int, optional, default 20): pause length in game ticks (20 ticks = 1 second)
            **Example**: {"type":"idle", "duration":60}

            ---
            ### 3. text
            **Purpose**: Shows a floating text tooltip in the 3D scene, pointing at a specific location. This is the primary way to explain things to the player.
            **When to use**: Whenever you need to annotate, explain, or call attention to a block or area. Combine with placeNearTarget to make the text float near the pointed block.
            **IMPORTANT — overlap**: a text label stays visible for exactly "duration" ticks. With the standard 50-tick text duration and 60-tick idle, there is a natural 10-tick gap preventing overlap. If using non-standard durations, ensure the idle between consecutive text steps is at least as long as the previous text's duration, or use "point" coordinates with ≥ 2-block Y difference so labels are vertically separated.
            **Fields**:
            - text (LocalizedText, required): {"en_us":"English text", "zh_cn":"中文文本"}
            - point (float[3], required): the 3D coordinate the text points at. Use x.5 values to point at block centers (e.g. [2.5, 1.5, 3.5] = center of block at [2,1,3])
            - duration (int, optional, default 40): how long the text stays visible (ticks)
            - color (string, optional): text color theme. Values: "green" (general info), "blue" (highlight), "red" (warning/danger), "cyan" (secondary info), "input" (player interaction), "output" (result), "slow"/"medium"/"fast" (speed indicators). Default is white.
            - placeNearTarget (bool, optional): if true, the text label floats near the pointed position instead of at a fixed screen location. Almost always set to true.
            **Example**: {"type":"text", "text":{"en_us":"This lever controls the piston","zh_cn":"这个拉杆控制活塞"}, "point":[2.5,2.5,3.5], "duration":60, "color":"green", "placeNearTarget":true, "attachKeyFrame":true}

            ---
            ### 4. set_block
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
            ### 5. show_controls
            **Purpose**: Shows an on-screen control hint icon with an ingredient display. Has TWO use cases:
            1. **Player interaction hint**: Show a mouse action (left-click, right-click, scroll) to teach the player how to interact with a block.
            2. **Machine input/output indicator**: Show an item, fluid, or chemical at a machine's port to indicate what goes in/out. In this case, set the "item" field to the substance's registry ID and omit the "action" field.
            **CRITICAL**: show_controls WITHOUT an "item" field displays NOTHING — it is an empty, invisible indicator. You MUST ALWAYS set the "item" field. When pointing at a block/component, use that block's registry ID as the item. When showing I/O, use the substance's registry ID.
            **When to use**:
            - Teaching player interaction: "right-click this lever", "scroll on the wrench" — you MUST set the "action" field ("left"/"right"/"scroll") AND the "item" field. NEVER omit action for player interaction hints.
            - Showing machine I/O: "this port accepts Water", "this outputs Iron Ingots" — use show_controls with item field pointing at the port block, then follow with a text step labeling it as input/output. Omit "action" field for I/O indicators.
            - Pointing at a component to explain it: set "item" to that component's registry ID so it displays the block icon. Example: pointing at an electromagnetic coil → item="mekanismgenerators:electromagnetic_coil".
            **Fields**:
            - point (float[3], required): position in the scene where the indicator appears
            - direction (string, required): which side of the point the icon appears. Values: "up", "down", "left", "right"
            - action (string, optional): input type. Values: "left" (left-click), "right" (right-click), "scroll" (scroll wheel). Omit when using as I/O indicator.
            - duration (int, optional, default 40): how long the indicator is shown
            - item (string, REQUIRED): registry ID of the item, block, fluid, or substance to display. Supports ALL registry types: items (e.g. "minecraft:iron_ingot"), fluids (e.g. "minecraft:water"), blocks (e.g. "minecraft:lever"), modded chemicals (e.g. "mekanism:hydrogen"), etc. NEVER omit this field — a show_controls without item is invisible and useless.
            - whileSneaking (bool, optional): if true, shows a "while sneaking" modifier
            - whileCTRL (bool, optional): if true, shows a "while holding CTRL" modifier
            **Example (player interaction)**: {"type":"show_controls", "point":[2.5,2.5,3.5], "direction":"down", "action":"right", "duration":40, "item":"minecraft:lever"}
            **Example (machine I/O)**: {"type":"show_controls", "point":[3.5,1.5,0.5], "direction":"down", "duration":40, "item":"minecraft:water"}

            ---
            ### 6. create_item_entity
            **Purpose**: Spawns a floating item entity (dropped item) in the scene. Can have initial velocity to simulate items being thrown or ejected.
            **When to use**: When showing item drops, hopper transport, or illustrating crafting results. Use motion to make items fly out of machines.
            **Fields**:
            - item (string, required): item id (e.g. "minecraft:diamond", "minecraft:iron_ingot")
            - pos (float[3], required): spawn position (can also use "point" field)
            - motion (float[3], REQUIRED): initial velocity [vx, vy, vz]. ALWAYS include this field — omitting it causes items to fly upward uncontrollably. Use [0, 0, 0] for stationary items (most common). Only use non-zero values when you need the item to visually fly/pop (e.g. [0, 0.15, 0] for gentle upward pop).
            - count (int, optional, default 1): number of items to display in the stack
            **Example**: {"type":"create_item_entity", "item":"minecraft:iron_ingot", "pos":[2.5, 2.0, 3.5], "motion":[0, 0, 0]}

            ---
            ### 7. clear_item_entities
            **Purpose**: Removes item entities (dropped items) from the scene. Can clear all item entities or filter by item type, and can target the full scene or a specific region.
            **When to use**: When cleaning up after demonstrating item drops, hopper mechanics, or machine output — to reset the scene before the next demonstration step. Also use when an item is visually consumed (picked up, inserted into a machine, smelted, crafted, or otherwise used up) to show it disappearing.
            **Fields**:
            - item (string, optional): item id to filter (e.g. "minecraft:diamond"). If omitted, clears ALL item entities.
            - fullScene (bool, optional): if true, clears item entities in the entire scene. If false/omitted, uses blockPos/blockPos2 region.
            - blockPos (int[3], optional): one corner of the region to clear (required if fullScene is not true)
            - blockPos2 (int[3], optional): opposite corner of the region
            **Example**: {"type":"clear_item_entities", "fullScene":true}
            **Example (filtered)**: {"type":"clear_item_entities", "item":"minecraft:iron_ingot", "blockPos":[0,0,0], "blockPos2":[4,3,4]}

            ---
            ### 8. rotate_camera_y
            **Purpose**: Smoothly rotates the camera around the Y (vertical) axis. Gives the player a different viewing angle of the structure.
            **When to use**: When you want to show the build from a different side, or create a cinematic rotation effect. Use after showing an initial view and before explaining something on the other side.
            **Fields**:
            - degrees (float, optional, default 90): rotation angle in degrees. Positive = clockwise when viewed from above. Use 90 for quarter turn, 180 for half turn.
            **Example**: {"type":"rotate_camera_y", "degrees":-90, "attachKeyFrame":true}

            ---
            ### 9. play_sound
            **Purpose**: Plays a sound effect during the scene. Adds audio feedback to important moments.
            **When to use**: When a block activates (piston extending, door opening, explosion), to make the tutorial feel alive. Use sparingly.
            **Fields**:
            - sound (string, required): sound event id (e.g. "minecraft:block.piston.extend", "minecraft:block.lever.click", "minecraft:entity.experience_orb.pickup")
            - soundVolume (float, optional, default 1.0): volume multiplier
            - pitch (float, optional, default 1.0): pitch multiplier (>1 = higher, <1 = lower)
            - source (string, optional, default "master"): sound category. Values: "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"
            **Example**: {"type":"play_sound", "sound":"minecraft:block.lever.click", "soundVolume":0.8, "pitch":1.2}

            ---
            ### 10. hide_section
            **Purpose**: Hides a rectangular section of blocks with a slide-out animation. The blocks visually slide away in the specified direction and disappear.
            **When to use**: When you want to remove part of the structure to reveal what's behind/underneath, or to clear the view before showing a new configuration.
            **Fields**:
            - blockPos (int[3], required): one corner of the section to hide
            - blockPos2 (int[3], required): opposite corner
            - direction (string, required): slide-out direction. Values: "up", "down", "north", "south", "east", "west"
            **Example**: {"type":"hide_section", "blockPos":[0,2,0], "blockPos2":[4,3,4], "direction":"up"}

            ---
            ### 11. show_section_and_merge
            **Purpose**: Shows a rectangular section of blocks with a slide-in animation from the given direction, then merges it into the main scene. Used to dramatically reveal parts of a build.
            **When to use**: When building up a structure piece by piece, or revealing a hidden mechanism. The linkId lets you track this section for later move_section/rotate_section operations.
            **Fields**:
            - blockPos (int[3], required): one corner of the section
            - blockPos2 (int[3], required): opposite corner
            - direction (string, required): direction the section slides IN from. Values: "up", "down", "north", "south", "east", "west"
            - linkId (string, optional, default "default"): identifier for this section. Use unique ids if you want to animate sections independently later.
            **Example**: {"type":"show_section_and_merge", "blockPos":[0,1,0], "blockPos2":[4,1,4], "direction":"down", "linkId":"floor"}

            ---
            ### 12. destroy_block
            **Purpose**: Removes a block from the scene, optionally showing destruction particles. The block position becomes air.
            **When to use**: When showing what happens when a block is broken, or when clearing blocks for the next step of a tutorial.
            **Fields**:
            - blockPos (int[3], required): position of the block to destroy
            - destroyParticles (bool, optional, default true): if true, shows block breaking particle effect
            **Example**: {"type":"destroy_block", "blockPos":[2,1,3], "destroyParticles":true}

            ---
            ### 13. replace_blocks
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
            ### 14. toggle_redstone_power
            **Purpose**: Toggles redstone power state for blocks in a region. Activates or deactivates redstone-powered blocks (levers, repeaters, lamps, pistons, etc.).
            **When to use**: When demonstrating redstone circuits — flipping a lever, showing a piston extending, lighting a redstone lamp. This simulates the player interacting with redstone.
            **Fields**:
            - blockPos (int[3], required): one corner of the region
            - blockPos2 (int[3], optional): opposite corner. If omitted, affects only the single block at blockPos.
            **Example**: {"type":"toggle_redstone_power", "blockPos":[2,1,3]}

            ---
            ### 15. indicate_success
            **Purpose**: Shows a pulsing green checkmark/success particle indicator at a block position. Visual cue for "this is correct" or "this is complete".
            **When to use**: Sparingly. Use only at true conclusion points (end of tutorial, final configuration achieved, or major milestone reached). Avoid spamming it at every small action — overuse dilutes its impact. Use 1-2 times per scene segment, not once per step.
            **Fields**:
            - blockPos (int[3], required): position to show the indicator
            **Example**: {"type":"indicate_success", "blockPos":[2,1,3]}

            ---
            ### 16. indicate_redstone
            **Purpose**: Shows a pulsing red particle indicator at a block position. Visual cue to draw attention to a redstone-related block.
            **When to use**: When you want to highlight that a specific block is receiving or emitting redstone power, without changing any block state.
            **Fields**:
            - blockPos (int[3], required): position to show the indicator
            **Example**: {"type":"indicate_redstone", "blockPos":[2,1,3]}

            ---
            ## Less Common Step Types
            The following step types are rarely needed. Use them only when the specific situation calls for it.

            ---
            ### 17. shared_text
            Like text, but references a pre-defined lang key. Rarely used — prefer inline text.
            **Fields**: key (string), point (float[3]), duration (int), color (string), placeNearTarget (bool)

            ---
            ### 18. encapsulate_bounds
            Overrides the visible scene bounding box. Rarely used.
            **Fields**: bounds (int[3]) — new scene dimensions [sizeX, sizeY, sizeZ]

            ---
            ### 19. modify_block_entity_nbt
            **Purpose**: Directly modifies the NBT data of a block entity (chest, sign, command block, etc.). Allows changing container contents, sign text, and other tile entity data.
            **When to use**: When you need to show specific block entity states that can't be achieved through normal block placement — e.g. items inside a chest, text on a sign.
            **Fields**:
            - blockPos (int[3], required): position of the block entity
            - blockPos2 (int[3], optional): opposite corner for a region
            - nbt (string, required): SNBT string to merge into the block entity's data, e.g. "{Items:[{id:'minecraft:diamond',Count:1b,Slot:0b}]}"
            - reDrawBlocks (bool, optional, default false): if true, forces the block to re-render after NBT change (needed for visual updates on some blocks)
            **Example**: {"type":"modify_block_entity_nbt", "blockPos":[2,1,3], "nbt":"{Items:[{id:\\"minecraft:diamond\\",Count:1b,Slot:0b}]}", "reDrawBlocks":true}

            ---
            ### 20. create_entity
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
            ### 21. clear_entities
            **Purpose**: Removes living entities (mobs) from the scene. Can clear all entities or filter by entity type, and can target the full scene or a specific region. Does NOT affect item entities.
            **Fields**:
            - entity (string, optional): entity type id to filter. If omitted, clears ALL non-item entities.
            - fullScene (bool, optional): if true, clears entities in the entire scene. If false/omitted, uses blockPos/blockPos2 region.
            - blockPos (int[3], optional): one corner of the region to clear (required if fullScene is not true)
            - blockPos2 (int[3], optional): opposite corner of the region
            **Example**: {"type":"clear_entities", "fullScene":true}

            ---
            ### 22. rotate_section
            **Purpose**: Smoothly rotates a section of blocks around its center over a duration. Creates a spinning/tilting animation effect.
            **When to use**: When demonstrating mechanical rotation. The section must have been previously created with show_section_and_merge with a matching linkId.
            **Fields**:
            - linkId (string, required): the section to rotate (must match a previous show_section_and_merge linkId)
            - duration (int, optional, default 20): animation duration in ticks
            - rotX (float, optional): rotation around X axis in degrees
            - rotY (float, optional): rotation around Y axis in degrees (if omitted, uses the "degrees" field)
            - rotZ (float, optional): rotation around Z axis in degrees
            - degrees (float, optional): shorthand for rotY if rotY is not specified
            **Example**: {"type":"rotate_section", "linkId":"gear", "duration":40, "rotY":90}

            ---
            ### 23. move_section
            **Purpose**: Smoothly translates a section of blocks by an offset over a duration. Creates a sliding animation.
            **When to use**: When showing pistons pushing blocks, doors opening, or any translational movement of a group of blocks.
            **Fields**:
            - linkId (string, required): the section to move (must match a previous show_section_and_merge linkId)
            - duration (int, optional, default 20): animation duration in ticks
            - offset (float[3], required): movement offset [dx, dy, dz] in blocks
            **Example**: {"type":"move_section", "linkId":"piston_head", "duration":10, "offset":[1.0, 0, 0]}

            ## Coordinate System
            - X: east(+)/west(-), Y: up(+)/down(-), Z: south(+)/north(-)
            - [0,0,0] is the bottom-north-west corner of the structure (the corner closest to the camera)
            - For "point" fields (text, show_controls, entity pos): use float values.
              - Block center: [bx+0.5, by+0.5, bz+0.5]
              - **Block face targeting** (for annotations): use an INTEGER value on one axis to select a face:
                - West face (visible): X = bx (integer), e.g. block [2,1,3] → west face = [2.0, 1.5, 3.5]
                - North face (visible): Z = bz (integer), e.g. block [2,1,3] → north face = [2.5, 1.5, 3.0]
                - East face (hidden): X = bx+1 — AVOID unless camera is rotated
                - South face (hidden): Z = bz+1 — AVOID unless camera is rotated
            - For "blockPos" fields (set_block, destroy_block, etc.): use integer values [x, y, z]
            - All coordinates must be within the structure bounds

            ## Camera Awareness
            The default camera looks from the NORTHWEST corner toward the SOUTHEAST.
            The player sees the **north face (Z=0 side)** and the **west face (X=0 side)**.
            The **south face (high Z)** and **east face (high X)** are HIDDEN.

            **CRITICAL — Point Coordinate Rules**:
            1. For show_controls "position" and text "point", target a VISIBLE face:
               - North face of block [bx,by,bz]: point = [bx+0.5, by+0.5, bz] (Z = integer = north face, visible).
               - West face of block [bx,by,bz]: point = [bx, by+0.5, bz+0.5] (X = integer = west face, visible).
               - NEVER use east face [bx+1, by+0.5, bz+0.5] or south face [bx+0.5, by+0.5, bz+1] — those are HIDDEN.
            2. **Choose which block to annotate**: If equivalent blocks exist on multiple faces, pick the one at X=0 or Z=0 (visible faces).
               - Example: valves at X=0, X=6, Z=0, Z=6 → choose X=0 or Z=0 valve.
            3. If the outline says rotate_camera_y before an annotation, insert the rotation step first, then annotate.
            4. **Default rule**: Without a preceding rotate_camera_y, ALL show_controls positions and text points MUST have either X as an integer (west face) or Z as an integer (north face). Both X.5 and Z.5 = block center = wrong for annotations.

            ## Scene Design Guidelines
            Follow the outline provided — it already contains the scene design plan.
            Your job is to translate the outline into valid JSON, not to redesign the scene.
            **Faithful to source**: Only include information from the outline and reference material. Do NOT invent facts, add real-world analogies, or include content not present in the inputs. Translate the outline faithfully — do not add extra text steps with information the outline didn't mention.
            Additional formatting rules:
            1. ALWAYS start each scene segment with show_structure
            2. **Duration guidelines**: Default idle is 60 ticks. Default text/show_controls duration is 50 ticks. Use shorter idles (10-20 ticks) only for rapid consecutive small operations (e.g. after rotate_camera_y). **No idle needed between set_block and the text that immediately annotates it** — the block appears instantly, so the text can follow directly: set_block → text → idle 60.
            3. Set placeNearTarget: true on text steps
            4. Use attachKeyFrame: true on important steps
            5. Provide both en_us and zh_cn text in all text steps
            6. The "steps" array at the top level MUST be empty []; put all steps inside "scenes" segments
            7. Color conventions: "green" = general info, "red" = warning/important, "blue" = highlight, "input" = player action required
            8. **Item entity lifecycle**: When demonstrating multiple items in sequence, ALWAYS use clear_item_entities before spawning the next. Pattern: create_item_entity → idle → show_controls/text → idle → clear_item_entities → (next create_item_entity or continue). Always clean up with clear_item_entities after the last item demo too.
            9. **show_controls explanation pattern**: After a show_controls step, always follow with idle → text explaining what the control hint means. Never leave show_controls without a follow-up text annotation.
            10. **Text points to the relevant block**: When a text step describes a specific block or component (e.g. a valve, a coil, a vent), its "point" MUST be at or near that block's position — NOT at the center of the structure. The text should visually appear next to the block it's describing. Pair it with a preceding show_controls or indicate_redstone on the SAME block.

            ## Localization Quality Rules
            When writing zh_cn (Chinese) text in text steps:
            - Write NATURAL, fluent Chinese directly. Do NOT translate from English — avoid "翻译腔" (translation-ese).
            - **Preserve proper nouns exactly**: Use the ORIGINAL Chinese display names from the game for blocks, items, fluids, chemicals, etc. (e.g. "活塞" not "推动装置").
            - If the user prompt or outline contains Chinese names, copy them verbatim into your zh_cn text.
            - Keep text concise and game-appropriate, matching the tone of Minecraft in-game tutorials.
            - **Do NOT use any emoji** in text content. No ⚠️, ✅, ❌, 🔥, etc. Use plain text only.

            ## Important Rules
            - Output ONLY valid JSON. Do NOT wrap it in markdown code fences (no ```json```). No comments, no trailing commas, no explanation text before or after the JSON.
            - The JSON must be a single, complete DslScene object
            - **CRITICAL — ID resolution**: The "block" field in set_block/replace_blocks, the "item" field in show_controls/create_item_entity, and all entity IDs MUST use the EXACT registry ID from the "Display Name → Registry ID mapping" provided above. This mapping covers blocks, items, fluids, chemicals, entities, and all other registered types. Do NOT use any other ID, even if the outline suggests one.
              - If the mapping says `endoflame → botania:endoflame`, use `"block": "botania:endoflame"` directly.
              - NEVER use a generic parent block + blockProperties/variant to select a sub-type (e.g. do NOT use `"block": "botania:specialflower"` with `"blockProperties": {"type": "endoflame"}`).
              - The mapping is authoritative — it overrides your training knowledge and any suggestions from the outline.
            - **CRITICAL — Step type selection based on [kind] tag**: The registry mapping labels each ID with one or more kind tags (e.g. [block], [item], [fluid/block], [entity/block/item]). When multiple kinds are listed, they are sorted by priority — the **first kind is the most important** and determines the step type. The outline already specifies visual step types — use the kind tag to VERIFY and CORRECT them:
              **As part of the world (placing/spawning in the scene) — use the FIRST kind tag:**
              - First kind is **entity** → use **create_entity** (NOT create_item_entity!) to spawn it
              - First kind is **block** → use **set_block** to place it as a block
              - First kind is **item** → use **create_item_entity** to spawn it as a dropped item
              **CORRECTION EXAMPLE**: If the outline writes `create_item_entity TNT` but registry says `TNT → minecraft:tnt [entity/block/item]`, the first kind is "entity", so you MUST output `{"type": "create_entity", "entity": "minecraft:tnt", ...}` instead of `{"type": "create_item_entity", ...}`. The example scene uses create_item_entity for coal because coal is [item] — do NOT copy that pattern for non-item things.
              **As machine input/output (showing at a port/interface):**
              - [item] → use **show_controls** with optional action="left"/"right" (for player interaction like inserting items)
              - [fluid], [chemical], or any other non-item kind → use **show_controls** WITHOUT action field (just an I/O indicator, no click action)
              **IMPORTANT**: If the outline uses the wrong step type (e.g. create_item_entity for something whose first kind is entity or block), you MUST correct it based on the first kind tag. But NEVER downgrade a visual step to a text-only step — always keep or improve the visual richness.
            - **CRITICAL — No naked text**: Every text step MUST be preceded by (or paired with) a visual action step. If the outline has consecutive text → idle → text → idle sequences with no visual steps between them, you MUST insert visual steps: use show_controls to point at relevant blocks with an item icon, use indicate_redstone to highlight components, use create_item_entity to spawn items being discussed, etc. A text step that appears without any preceding visual in the same "block" is FORBIDDEN. When the text describes a stat/number, use show_controls at the relevant block with an appropriate item to give the player something to look at.
            - Screenshots from reference URLs are for understanding the workflow only; use the NBT structure data for accurate block positions

            ## SELF-CHECK: Before outputting the final JSON
            After composing the complete JSON, mentally scan each scene's steps array for **3 or more consecutive text steps** (where text steps are separated only by idle). If you find such a sequence:
            1. Extract the item/block/substance mentioned in each text
            2. Insert a visual step (show_controls with item, create_item_entity, indicate_redstone, etc.) BEFORE every 2nd consecutive text onward
            3. Ensure no 3 consecutive text steps remain without a visual step between them
            This self-check overrides the outline — if the outline has text walls, FIX them in the JSON by adding visual steps.

            {{EXAMPLE}}
            """;
    }
}
