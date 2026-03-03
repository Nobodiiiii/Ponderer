package com.nododiiiii.ponderer.ai;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds display-name-to-registry-ID mappings from all available registries.
 * Indexes: Block, Entity Type (from vanilla registries), plus ALL JEI ingredient types
 * (items, fluids, chemicals, etc.) when JEI is available.
 * Falls back to Block + Item registries only when JEI is not available.
 *
 * <p>An ID can appear with multiple kinds (e.g. mekanism:steam as both [block] and [fluid]).
 * All kinds are preserved in the output so the AI can choose the appropriate type.
 *
 * <p>Lookup chain (per requested name):
 * <ol>
 *   <li>Pass-through: name contains ":" → already a registry ID.</li>
 *   <li>Lv1 — exact unique: display name or ID path matches exactly, single result.</li>
 *   <li>Lv2 — exact ambiguous: display name or ID path matches exactly, multiple results (different mods).</li>
 *   <li>Lv3 — fuzzy: all words appear in candidate display name or ID path (both languages), up to 8 results.</li>
 *   <li>Lv4 — not found: no match at all.</li>
 * </ol>
 */
public class RegistryMapper {

    /** An entry in the combined lookup index. */
    private record Entry(String id, String displayName, String path, String kind) {}

    /**
     * Add an entry. Unlike before, the same ID can be added multiple times with different kinds.
     * Deduplication is by (id + kind) pair, not by id alone.
     */
    private static void addEntry(List<Entry> allEntries, Set<String> indexedIdKinds,
                                  Map<String, List<Entry>> displayIndex,
                                  Map<String, List<Entry>> pathIndex,
                                  String id, String displayName, String path, String kind) {
        String key = id + "|" + kind;
        if (indexedIdKinds.contains(key)) return;
        Entry e = new Entry(id, displayName, path, kind);
        allEntries.add(e);
        indexedIdKinds.add(key);
        displayIndex.computeIfAbsent(displayName, k -> new ArrayList<>()).add(e);
        pathIndex.computeIfAbsent(path, k -> new ArrayList<>()).add(e);
    }

    /**
     * Kind priority: fluid/chemical/gas etc. > block > item.
     * Lower number = higher priority (appears first in output).
     */
    private static int kindPriority(String kind) {
        if ("item".equals(kind)) return 2;
        if ("block".equals(kind)) return 1;
        // fluid, chemical, gas, entity, and anything else → highest priority
        return 0;
    }

    /**
     * Collect all distinct kinds for a given ID from a list of entries, sorted by priority.
     */
    private static String formatKinds(String id, List<Entry> entries) {
        Set<String> kinds = new LinkedHashSet<>();
        for (Entry e : entries) {
            if (e.id().equals(id)) kinds.add(e.kind());
        }
        return kinds.stream()
            .sorted(Comparator.comparingInt(RegistryMapper::kindPriority))
            .collect(Collectors.joining("/"));
    }

    /**
     * Collect all distinct kinds for a given ID from the full entry list, sorted by priority.
     */
    private static String formatKindsFromAll(String id, List<Entry> allEntries) {
        Set<String> kinds = new LinkedHashSet<>();
        for (Entry e : allEntries) {
            if (e.id().equals(id)) kinds.add(e.kind());
        }
        return kinds.isEmpty() ? "block" : kinds.stream()
            .sorted(Comparator.comparingInt(RegistryMapper::kindPriority))
            .collect(Collectors.joining("/"));
    }

    /**
     * Build a targeted mapping for the names requested by the outline pass.
     *
     * @param requestedNames   names from the outline's REQUIRED_ELEMENTS line
     * @param structureBlockIds exact block IDs present in the NBT structure(s)
     */
    public static String buildMappingForDisplayNames(List<String> requestedNames,
                                                      List<String> structureBlockIds) {
        Map<String, List<Entry>> displayIndex = new LinkedHashMap<>();
        Map<String, List<Entry>> pathIndex = new LinkedHashMap<>();
        List<Entry> allEntries = new ArrayList<>();
        Set<String> indexedIdKinds = new HashSet<>();

        // --- Block registry (needed for set_block/replace_blocks ID resolution) ---
        BuiltInRegistries.BLOCK.forEach(block -> {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String displayName = block.getName().getString().toLowerCase(Locale.ROOT);
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);
            addEntry(allEntries, indexedIdKinds, displayIndex, pathIndex, id, displayName, path, "block");
        });

        // --- Entity type registry (needed for create_entity) ---
        BuiltInRegistries.ENTITY_TYPE.forEach(entityType -> {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
            String displayName = entityType.getDescription().getString().toLowerCase(Locale.ROOT);
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getPath().toLowerCase(Locale.ROOT);
            addEntry(allEntries, indexedIdKinds, displayIndex, pathIndex, id, displayName, path, "entity");
        });

        // --- All JEI ingredient types (items, fluids, chemicals, etc. — unified) ---
        if (JeiCompat.isAvailable()) {
            for (String[] entry : JeiCompat.getAllExtraIngredientEntries()) {
                // entry = {id, displayName, path, kind}
                addEntry(allEntries, indexedIdKinds, displayIndex, pathIndex, entry[0], entry[1], entry[2], entry[3]);
            }
        } else {
            // Fallback: index Item registry when JEI is not available
            BuiltInRegistries.ITEM.forEach(item -> {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String displayName = item.getDescription().getString().toLowerCase(Locale.ROOT);
                String path = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
                addEntry(allEntries, indexedIdKinds, displayIndex, pathIndex, id, displayName, path, "item");
            });
        }

        StringBuilder sb = new StringBuilder("Display Name → Registry ID mapping:\n");
        // Track which IDs have been output (to avoid duplicate lines in final output)
        Set<String> outputIds = new HashSet<>();

        // Always include structure blocks (exact IDs from NBT, no lookup needed)
        for (String blockId : structureBlockIds) {
            ResourceLocation loc = ResourceLocation.tryParse(blockId);
            if (loc == null) continue;
            BuiltInRegistries.BLOCK.getOptional(loc).ifPresent(block -> {
                String displayName = block.getName().getString();
                String kinds = formatKindsFromAll(blockId, allEntries);
                sb.append("  ").append(displayName).append(" → ").append(blockId)
                  .append(" [").append(kinds).append("]\n");
                outputIds.add(blockId);
            });
        }

        // Look up each name requested by the outline
        for (String name : requestedNames) {
            if (name == null || name.isBlank()) continue;

            // Pass-through: already a registry ID
            if (name.contains(":")) {
                sb.append("  ").append(name).append(" → ").append(name).append("\n");
                continue;
            }

            String lower = name.toLowerCase(Locale.ROOT).trim();
            String asPath = lower.replace(' ', '_');

            // ---- Exact match phase (display name + ID path combined) ----
            // Collect unique IDs (not filtered by outputIds — we still want to resolve them)
            Map<String, List<Entry>> exactById = new LinkedHashMap<>();
            List<Entry> byDisplay = displayIndex.get(lower);
            if (byDisplay != null) {
                for (Entry e : byDisplay) {
                    exactById.computeIfAbsent(e.id(), k -> new ArrayList<>()).add(e);
                }
            }
            for (String pathKey : List.of(lower, asPath)) {
                List<Entry> byPath = pathIndex.get(pathKey);
                if (byPath != null) {
                    for (Entry e : byPath) {
                        exactById.computeIfAbsent(e.id(), k -> new ArrayList<>()).add(e);
                    }
                }
            }

            // Remove IDs already output by structureBlockIds
            Set<String> alreadyOutput = new HashSet<>();
            for (String id : exactById.keySet()) {
                if (outputIds.contains(id)) alreadyOutput.add(id);
            }

            if (!alreadyOutput.isEmpty() && alreadyOutput.size() == exactById.size()) {
                // All matches were already output in structure blocks section — skip silently
                continue;
            }
            // Remove already-output IDs from candidates
            alreadyOutput.forEach(exactById::remove);

            if (exactById.size() == 1) {
                // Lv1: exact unique match
                Map.Entry<String, List<Entry>> me = exactById.entrySet().iterator().next();
                String id = me.getKey();
                String kinds = formatKinds(id, me.getValue());
                sb.append("  ").append(name).append(" → ").append(id)
                  .append(" [").append(kinds).append("]")
                  .append("  [Lv1: exact match]\n");
                outputIds.add(id);
                continue;
            }
            if (exactById.size() > 1) {
                // Lv2: exact but ambiguous (multiple IDs)
                sb.append("  ").append(name).append(" → ");
                sb.append(exactById.entrySet().stream()
                    .map(me -> me.getKey() + " [" + formatKinds(me.getKey(), me.getValue()) + "]")
                    .reduce((a, b) -> a + " | " + b).orElse(""));
                sb.append("  [Lv2: multiple exact matches, choose appropriate]\n");
                exactById.keySet().forEach(outputIds::add);
                continue;
            }

            // ---- Fuzzy match phase (display name + ID path, both languages) ----
            String[] words = lower.split("\\s+");
            String[] pathWords = asPath.split("_");

            Map<String, List<Entry>> fuzzyById = new LinkedHashMap<>();
            for (Entry e : allEntries) {
                if (fuzzyById.size() >= 8) break;
                if (outputIds.contains(e.id())) continue;

                boolean allMatch = true;
                for (String word : words) {
                    String wordUnder = word.replace(' ', '_');
                    if (!e.displayName().contains(word)
                        && !e.path().contains(word)
                        && !e.path().contains(wordUnder)) {
                        allMatch = false;
                        break;
                    }
                }
                if (!allMatch && pathWords.length > 1) {
                    allMatch = true;
                    for (String pw : pathWords) {
                        if (!e.displayName().contains(pw) && !e.path().contains(pw)) {
                            allMatch = false;
                            break;
                        }
                    }
                }

                if (allMatch) {
                    fuzzyById.computeIfAbsent(e.id(), k -> new ArrayList<>()).add(e);
                }
            }

            if (!fuzzyById.isEmpty()) {
                sb.append("  ").append(name).append(" → ");
                if (fuzzyById.size() == 1) {
                    Map.Entry<String, List<Entry>> me = fuzzyById.entrySet().iterator().next();
                    String id = me.getKey();
                    String kinds = formatKinds(id, me.getValue());
                    sb.append(id).append(" [").append(kinds).append("]")
                      .append("  [Lv3: fuzzy match]\n");
                } else {
                    sb.append(fuzzyById.entrySet().stream()
                        .map(me -> me.getKey() + " [" + formatKinds(me.getKey(), me.getValue()) + "]")
                        .reduce((a, b) -> a + " | " + b).orElse(""));
                    sb.append("  [Lv3: fuzzy, multiple candidates]\n");
                }
                fuzzyById.keySet().forEach(outputIds::add);
            } else {
                // Lv4: not found
                sb.append("  ").append(name)
                  .append(" → (NOT FOUND — use the exact registry ID, e.g. \"mod:block_name\")  [Lv4]\n");
            }
        }

        return sb.toString();
    }
}
