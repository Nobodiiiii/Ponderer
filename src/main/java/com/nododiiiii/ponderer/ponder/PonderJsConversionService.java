package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PonderJsConversionService {
    private static final String BRIDGE_ID_PREFIX = "// PONDERER_BRIDGE_ID ";
    private static final String BRIDGE_JSON_PREFIX = "// PONDERER_BRIDGE_JSON_BASE64 ";
    private static final Pattern PONDER_REGISTRY_PATTERN = Pattern.compile("Ponder\\s*\\.\\s*registry\\s*\\(\\s*\\(?\\s*event\\s*\\)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_PATTERN = Pattern.compile("event\\s*\\.\\s*create\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENE_PATTERN = Pattern.compile("\\.\\s*scene\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]*)['\"](?:\\s*,\\s*['\"]([^'\"]*)['\"])?", Pattern.CASE_INSENSITIVE);

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    private PonderJsConversionService() {
    }

    private static Path getClientScriptsDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("kubejs").resolve("client_scripts").resolve("ponder");
    }

    public static int convertToPonderJs(ResourceLocation id) {
        Optional<DslScene> scene = SceneRuntime.getScenes().stream()
            .filter(s -> id.toString().equals(s.id))
            .findFirst();
        if (scene.isEmpty()) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.scene_not_found", id.toString()));
            return 0;
        }

        Path scriptsDir = getClientScriptsDir();
        String flatName = id.getPath().replace('/', '_') + ".ponderer.js";
        Path out = scriptsDir.resolve(flatName);
        try {
            Files.createDirectories(out.getParent());
            String json = GSON.toJson(scene.get());
            String b64 = Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String content = buildPonderJsScript(scene.get(), id.toString(), b64);
            Files.writeString(out, content, StandardCharsets.UTF_8);
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_done", out.toString()));
            return 1;
        } catch (Exception e) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_failed", id.toString()));
            return 0;
        }
    }

    public static int convertAllToPonderJs() {
        int count = 0;
        for (DslScene scene : SceneRuntime.getScenes()) {
            if (scene == null || scene.id == null || scene.id.isBlank()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(scene.id);
            if (id == null) {
                continue;
            }
            count += convertToPonderJs(id);
        }
        notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.to_all_done", count));
        return count;
    }

    public static int convertFromPonderJs(ResourceLocation id) {
        Path scriptsDir = getClientScriptsDir();
        if (!Files.exists(scriptsDir)) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.dir_not_found"));
            return 0;
        }
        int count = 0;
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path p : paths.filter(path -> path.toString().endsWith(".js")).toList()) {
                DslScene scene = parseAnyPonderJsFile(p);
                if (scene == null || !id.toString().equals(scene.id)) {
                    continue;
                }
                if (SceneStore.saveSceneToLocal(scene)) {
                    count++;
                }
            }
        } catch (Exception e) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.scan_failed"));
            return 0;
        }
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);
        notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.from_done", count, id.toString()));
        return count;
    }

    public static int convertAllFromPonderJs() {
        Path scriptsDir = getClientScriptsDir();
        if (!Files.exists(scriptsDir)) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.dir_not_found"));
            return 0;
        }
        int count = 0;
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            for (Path p : paths.filter(path -> path.toString().endsWith(".js")).toList()) {
                DslScene scene = parseAnyPonderJsFile(p);
                if (scene == null) {
                    continue;
                }
                if (SceneStore.saveSceneToLocal(scene)) {
                    count++;
                }
            }
        } catch (Exception e) {
            notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.scan_failed"));
            return 0;
        }
        SceneStore.reloadFromDisk();
        Minecraft.getInstance().execute(PonderIndex::reload);
        notifyClient(net.minecraft.network.chat.Component.translatable("ponderer.cmd.convert.from_all_done", count));
        return count;
    }

    private record BridgeExtract(String id, String base64) {
    }

    private static BridgeExtract extractBridge(Path file) {
        try {
            String id = null;
            String b64 = null;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith(BRIDGE_ID_PREFIX)) {
                    id = line.substring(BRIDGE_ID_PREFIX.length()).trim();
                } else if (line.startsWith(BRIDGE_JSON_PREFIX)) {
                    b64 = line.substring(BRIDGE_JSON_PREFIX.length()).trim();
                }
            }
            if (id == null || b64 == null) {
                return null;
            }
            return new BridgeExtract(id, b64);
        } catch (Exception e) {
            return null;
        }
    }

    private static DslScene parseBridgeScene(BridgeExtract extracted) {
        try {
            byte[] raw = Base64.getDecoder().decode(extracted.base64());
            String json = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            DslScene scene = GSON.fromJson(json, DslScene.class);
            if (scene == null || scene.id == null || scene.id.isBlank()) {
                return null;
            }
            return scene;
        } catch (Exception e) {
            return null;
        }
    }

    private static DslScene parseAnyPonderJsFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);

            BridgeExtract extracted = extractBridge(file);
            if (extracted != null) {
                DslScene decoded = parseBridgeScene(extracted);
                if (decoded != null) {
                    return decoded;
                }
            }

            if (!PONDER_REGISTRY_PATTERN.matcher(content).find()) {
                return null;
            }

            String defaultId = "ponderer:imported/" + stripExt(file.getFileName().toString()).replaceAll("[^a-zA-Z0-9_/-]", "_");

            String firstItem = "minecraft:stone";
            Matcher createMatcher = CREATE_PATTERN.matcher(content);
            if (createMatcher.find()) {
                firstItem = createMatcher.group(1);
            }

            List<DslScene.SceneSegment> segments = new ArrayList<>();
            java.util.LinkedHashSet<String> schematicSet = new java.util.LinkedHashSet<>();
            List<String> segmentSchematics = new ArrayList<>();
            Matcher sceneMatcher = SCENE_PATTERN.matcher(content);
            int index = 0;
            while (sceneMatcher.find()) {
                DslScene.SceneSegment seg = new DslScene.SceneSegment();
                String sid = sceneMatcher.group(1);
                String stitle = sceneMatcher.group(2);
                String schematic = sceneMatcher.group(3);
                seg.id = sid == null || sid.isBlank() ? ("scene_" + (++index)) : sid;
                seg.title = LocalizedText.of(stitle == null || stitle.isBlank() ? seg.id : stitle);

                if (schematic != null && !schematic.isBlank()) {
                    schematicSet.add(schematic);
                    segmentSchematics.add(schematic);
                } else {
                    segmentSchematics.add(null);
                }

                // Try to extract the scene body and parse steps semantically
                String body = extractSceneBody(content, sceneMatcher.end());
                if (body != null && !body.isBlank()) {
                    seg.steps = PonderJsParsers.parseSceneBody(body);
                } else {
                    seg.steps = List.of();
                }
                segments.add(seg);
            }

            // Populate structures pool from .scene() schematic arguments
            List<String> structurePool = new ArrayList<>(schematicSet);
            String defaultSchematic = structurePool.isEmpty() ? null : structurePool.get(0);
            for (int i = 0; i < segments.size(); i++) {
                String segSchematic = i < segmentSchematics.size() ? segmentSchematics.get(i) : null;
                if (segSchematic != null && !segSchematic.equals(defaultSchematic)) {
                    DslScene.SceneSegment seg = segments.get(i);
                    if (seg.steps != null) {
                        for (DslScene.DslStep step : seg.steps) {
                            if ("show_structure".equalsIgnoreCase(step.type)) {
                                step.structure = segSchematic;
                                break;
                            }
                        }
                    }
                }
            }

            DslScene scene = new DslScene();
            scene.id = defaultId;
            scene.items = List.of(firstItem);
            scene.title = LocalizedText.of("Imported from PonderJS");
            scene.structures = structurePool;
            scene.scenes = segments.isEmpty() ? List.of() : segments;
            scene.steps = List.of();
            return scene;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract the function body of a scene callback starting after .scene("id", "title", (scene, util) => {
     * Finds the matching closing brace by tracking brace depth.
     */
    private static String extractSceneBody(String content, int searchStart) {
        // Find the opening of the callback body: (scene, util) => { or (scene) => { or function(scene, util) {
        Pattern callbackOpen = Pattern.compile("\\(\\s*scene\\s*(?:,\\s*util\\s*)?\\)\\s*=>\\s*\\{");
        Matcher m = callbackOpen.matcher(content);
        if (!m.find(searchStart)) {
            Pattern funcOpen = Pattern.compile("function\\s*\\(\\s*scene\\s*(?:,\\s*util\\s*)?\\)\\s*\\{");
            m = funcOpen.matcher(content);
            if (!m.find(searchStart)) return null;
        }
        int braceStart = m.end();
        int depth = 1;
        boolean inString = false;
        char stringChar = 0;
        for (int i = braceStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < content.length()) { i++; continue; }
                if (c == stringChar) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; stringChar = c; continue; }
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(braceStart, i);
                }
            }
        }
        return null;
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String buildPonderJsScript(DslScene scene, String id, String b64) {
        StringBuilder sb = new StringBuilder();
        String item = (scene.items != null && !scene.items.isEmpty()) ? scene.items.get(0) : "minecraft:stone";
        sb.append("// Auto-generated by Ponderer bridge\n");
        sb.append("// NOTE: When converting back to JSON, the BRIDGE_JSON_BASE64 below takes priority.\n");
        sb.append("//       Edits to the JS code will be IGNORED unless you delete the two BRIDGE lines.\n");
        sb.append("// 注意：转回JSON时，下方的 BRIDGE_JSON_BASE64 优先生效。\n");
        sb.append("//       对JS代码的修改会被忽略，除非删除下面两行 BRIDGE 注释。\n");
        sb.append(BRIDGE_ID_PREFIX).append(id).append("\n");
        sb.append(BRIDGE_JSON_PREFIX).append(b64).append("\n\n");
        sb.append("Ponder.registry((event) => {\n");

        List<DslScene.SceneSegment> segments = (scene.scenes != null && !scene.scenes.isEmpty())
            ? scene.scenes
            : List.of();

        // Open a single event.create() builder and chain all .scene() calls
        sb.append("  event.create(\"").append(escapeJs(item)).append("\")\n");

        if (segments.isEmpty()) {
            String schematic = resolveDefaultSchematic(scene);
            emitSceneCall(sb, scene.id, scene.title, scene.steps, schematic, true);
        } else {
            List<String> schematics = resolveSceneSchematics(scene, segments);
            for (int i = 0; i < segments.size(); i++) {
                DslScene.SceneSegment seg = segments.get(i);
                String sid = seg.id == null || seg.id.isBlank() ? "scene" : seg.id;
                String schematic = i < schematics.size() ? schematics.get(i) : resolveDefaultSchematic(scene);
                boolean isLast = (i == segments.size() - 1);
                emitSceneCall(sb, sid, seg.title, seg.steps, schematic, isLast);
            }
        }

        sb.append("});\n");
        return sb.toString();
    }

    private static void emitSceneCall(StringBuilder sb, String sceneId, LocalizedText title,
                                       List<DslScene.DslStep> steps, String schematic, boolean isLast) {
        String sid = safeSceneId(sceneId);
        String titleText = title == null ? sid : escapeJs(title.resolve());
        sb.append("    .scene(\"").append(escapeJs(sid)).append("\", \"").append(titleText)
          .append("\", \"").append(escapeJs(schematic)).append("\", (scene, util) => {\n");

        PonderJsEmitters.EmitContext ctx = new PonderJsEmitters.EmitContext();
        boolean hasSteps = steps != null && !steps.isEmpty();

        // Step types where attachKeyFrame is emitted as a chained call inside the emitter
        java.util.Set<String> keyFrameChainedTypes = java.util.Set.of("text", "shared_text");

        if (hasSteps) {
            for (DslScene.DslStep step : steps) {
                if (step == null || step.type == null) continue;
                // Emit standalone addKeyframe() for non-text steps with attachKeyFrame
                if (Boolean.TRUE.equals(step.attachKeyFrame)
                        && !keyFrameChainedTypes.contains(step.type.toLowerCase(java.util.Locale.ROOT))) {
                    sb.append("      scene.addKeyframe();\n");
                }
                String emitted = PonderJsEmitters.emit(step, ctx);
                if (emitted != null) {
                    for (String line : emitted.split("\n")) {
                        sb.append("      ").append(line).append("\n");
                    }
                } else {
                    sb.append("      // TODO_UNSUPPORTED_STEP: ").append(step.type).append("\n");
                }
            }
        } else {
            sb.append("      scene.showStructure();\n");
            sb.append("      scene.idle(20);\n");
        }

        if (isLast) {
            sb.append("    });\n");
        } else {
            sb.append("    })\n");
        }
    }

    // ---- Schematic resolution (mirrors DynamicPonderPlugin logic) ----

    private static String resolveDefaultSchematic(DslScene scene) {
        List<String> pool = getStructurePool(scene);
        if (!pool.isEmpty()) {
            String resolved = resolveSchematicString(pool.get(0));
            if (resolved != null) return resolved;
        }
        return "ponder:debug/scene_1";
    }

    private static List<String> resolveSceneSchematics(DslScene scene, List<DslScene.SceneSegment> segments) {
        List<String> resolved = new ArrayList<>();
        String current = resolveDefaultSchematic(scene);
        for (DslScene.SceneSegment seg : segments) {
            String explicit = extractExplicitStructureRef(seg);
            if (explicit != null) {
                String next = resolveStructureReference(scene, explicit);
                if (next != null) current = next;
            }
            resolved.add(current);
        }
        return resolved;
    }

    private static List<String> getStructurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) return scene.structures;
        if (scene.structure != null && !scene.structure.isBlank()) return List.of(scene.structure);
        return List.of();
    }

    private static String extractExplicitStructureRef(DslScene.SceneSegment seg) {
        if (seg == null || seg.steps == null) return null;
        for (DslScene.DslStep step : seg.steps) {
            if (step == null || step.type == null) continue;
            if (!"show_structure".equalsIgnoreCase(step.type)) continue;
            if (step.structure != null && !step.structure.isBlank()) return step.structure.trim();
            return null;
        }
        return null;
    }

    private static String resolveStructureReference(DslScene scene, String ref) {
        if (ref == null || ref.isBlank()) return null;
        List<String> pool = getStructurePool(scene);
        try {
            int parsed = Integer.parseInt(ref);
            int index = -1;
            if (parsed >= 1 && parsed <= pool.size()) index = parsed - 1;
            else if (parsed >= 0 && parsed < pool.size()) index = parsed;
            if (index >= 0) return resolveSchematicString(pool.get(index));
        } catch (NumberFormatException ignored) {}
        return resolveSchematicString(ref);
    }

    private static String resolveSchematicString(String structure) {
        if (structure == null || structure.isBlank()) return "ponder:debug/scene_1";
        if (structure.contains(":")) return structure;
        return "ponder:" + structure;
    }

    private static String safeSceneId(String fullId) {
        if (fullId == null || fullId.isBlank()) {
            return "scene";
        }
        int idx = fullId.indexOf(':');
        String path = idx >= 0 ? fullId.substring(idx + 1) : fullId;
        return path.replace('/', '_');
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void notifyClient(net.minecraft.network.chat.Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(message, false);
        }
    }
}
