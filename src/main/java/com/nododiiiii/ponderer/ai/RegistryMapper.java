package com.nododiiiii.ponderer.ai;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds display-name-to-registry-ID mappings from vanilla registries.
 * Used to help the LLM resolve localized names to proper IDs.
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
    private record Entry(String id, String displayName, String path) {}

    /**
     * Build a targeted mapping for the names requested by the outline pass.
     *
     * @param requestedNames   names from the outline's REQUIRED_ELEMENTS line
     * @param structureBlockIds exact block IDs present in the NBT structure(s)
     */
    public static String buildMappingForDisplayNames(List<String> requestedNames,
                                                      List<String> structureBlockIds) {
        // Build lookup tables that support multiple IDs per key (different mods, same name)
        //   displayIndex — lowercase display name → [entries]
        //   pathIndex    — lowercase id path      → [entries]
        Map<String, List<Entry>> displayIndex = new LinkedHashMap<>();
        Map<String, List<Entry>> pathIndex = new LinkedHashMap<>();
        List<Entry> allEntries = new ArrayList<>();

        BuiltInRegistries.BLOCK.forEach(block -> {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String displayName = block.getName().getString().toLowerCase(Locale.ROOT);
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);
            Entry e = new Entry(id, displayName, path);
            allEntries.add(e);
            displayIndex.computeIfAbsent(displayName, k -> new ArrayList<>()).add(e);
            pathIndex.computeIfAbsent(path, k -> new ArrayList<>()).add(e);
        });
        BuiltInRegistries.ITEM.forEach(item -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String displayName = item.getDescription().getString().toLowerCase(Locale.ROOT);
            String path = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
            // Skip if this exact id was already added (block+item share the same id)
            if (allEntries.stream().anyMatch(e -> e.id().equals(id))) return;
            Entry e = new Entry(id, displayName, path);
            allEntries.add(e);
            displayIndex.computeIfAbsent(displayName, k -> new ArrayList<>()).add(e);
            pathIndex.computeIfAbsent(path, k -> new ArrayList<>()).add(e);
        });

        StringBuilder sb = new StringBuilder("Display Name → Registry ID mapping:\n");
        Set<String> addedIds = new HashSet<>();

        // Always include structure blocks (exact IDs from NBT, no lookup needed)
        for (String blockId : structureBlockIds) {
            ResourceLocation loc = ResourceLocation.tryParse(blockId);
            if (loc == null) continue;
            BuiltInRegistries.BLOCK.getOptional(loc).ifPresent(block -> {
                String displayName = block.getName().getString();
                sb.append("  ").append(displayName).append(" → ").append(blockId).append("\n");
                addedIds.add(blockId);
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
            List<String> exactIds = new ArrayList<>();
            // Check display name
            List<Entry> byDisplay = displayIndex.get(lower);
            if (byDisplay != null) {
                for (Entry e : byDisplay) {
                    if (!addedIds.contains(e.id()) && !exactIds.contains(e.id())) {
                        exactIds.add(e.id());
                    }
                }
            }
            // Check ID path (also with underscore variant)
            for (String pathKey : List.of(lower, asPath)) {
                List<Entry> byPath = pathIndex.get(pathKey);
                if (byPath != null) {
                    for (Entry e : byPath) {
                        if (!addedIds.contains(e.id()) && !exactIds.contains(e.id())) {
                            exactIds.add(e.id());
                        }
                    }
                }
            }

            if (exactIds.size() == 1) {
                // Lv1: exact unique match
                String id = exactIds.get(0);
                sb.append("  ").append(name).append(" → ").append(id)
                  .append("  [Lv1: exact match]\n");
                addedIds.add(id);
                continue;
            }
            if (exactIds.size() > 1) {
                // Lv2: exact but ambiguous (multiple mods)
                sb.append("  ").append(name).append(" → ")
                  .append(String.join(" | ", exactIds))
                  .append("  [Lv2: multiple exact matches, choose appropriate]\n");
                exactIds.forEach(addedIds::add);
                continue;
            }

            // ---- Fuzzy match phase (display name + ID path, both languages) ----
            String[] words = lower.split("\\s+");
            // Also prepare underscore-separated words for path matching
            String[] pathWords = asPath.split("_");

            List<String> fuzzyIds = new ArrayList<>();
            for (Entry e : allEntries) {
                if (fuzzyIds.size() >= 8) break;
                if (addedIds.contains(e.id())) continue;

                // Check if ALL words appear in either displayName or path
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
                // Also try path-style words (e.g. "fire_red_lotus" split as pathWords)
                if (!allMatch && pathWords.length > 1) {
                    allMatch = true;
                    for (String pw : pathWords) {
                        if (!e.displayName().contains(pw) && !e.path().contains(pw)) {
                            allMatch = false;
                            break;
                        }
                    }
                }

                if (allMatch) fuzzyIds.add(e.id());
            }

            if (!fuzzyIds.isEmpty()) {
                // Lv3: fuzzy match
                sb.append("  ").append(name).append(" → ");
                if (fuzzyIds.size() == 1) {
                    sb.append(fuzzyIds.get(0)).append("  [Lv3: fuzzy match]\n");
                } else {
                    sb.append(String.join(" | ", fuzzyIds))
                      .append("  [Lv3: fuzzy, multiple candidates]\n");
                }
                fuzzyIds.forEach(addedIds::add);
            } else {
                // Lv4: not found
                sb.append("  ").append(name)
                  .append(" → (NOT FOUND — use the exact registry ID, e.g. \"mod:block_name\")  [Lv4]\n");
            }
        }

        return sb.toString();
    }
}
