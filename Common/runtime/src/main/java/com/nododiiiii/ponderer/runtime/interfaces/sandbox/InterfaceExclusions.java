package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Client-resource rules shared by embedded widgets, bounds, input and loader compatibility switches. */
public final class InterfaceExclusions {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> FIELDS = Set.of("format_version", "mod_id", "enabled", "classes", "packages", "exclude_own_screens");
    private static volatile Rules current = new Rules(List.of());

    private InterfaceExclusions() {}

    /** Normal resource-pack precedence replaces files with the same namespace/path. */
    public static void reload(ResourceManager resources) {
        List<Rule> rules = new ArrayList<>();
        var files = resources.listResources("ponderer/interface_exclusions", id -> id.getPath().endsWith(".json"));
        for (var entry : new TreeMap<>(files).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (String field : json.keySet()) {
                    if (!FIELDS.contains(field)) throw new IllegalArgumentException("Unknown field " + field);
                }
                if (json.has("format_version") && json.get("format_version").getAsInt() != 1)
                    throw new IllegalArgumentException("Unsupported format_version");
                if (!flag(json, "enabled", true)) continue;
                String modId = string(json.get("mod_id"));
                ResourceLocation.fromNamespaceAndPath(modId, "interface_exclusions");
                List<String> classes = strings(json, "classes");
                List<String> packages = strings(json, "packages");
                if (packages.stream().anyMatch(value -> !value.endsWith(".")))
                    throw new IllegalArgumentException("Package prefixes must end with '.'");
                rules.add(new Rule(modId, classes, packages, flag(json, "exclude_own_screens", false)));
            } catch (Exception error) {
                LOGGER.error("Invalid interface exclusion rule {}: {}", entry.getKey(), error.toString());
            }
        }
        // Replacing the snapshot also discards ownership/matcher caches on F3+T.
        current = new Rules(List.copyOf(rules));
        LOGGER.info("Ponderer Lib loaded {} interface exclusion rules", rules.size());
    }

    /** Host screens and all calls outside a sandbox are unaffected. Unknown controls stay visible. */
    public static boolean isExcluded(Object control, @Nullable Screen screen) {
        SandboxSession session = SandboxScope.activeSession();
        if (session == null || screen == null || session.currentScreen() != screen) return false;
        Rules rules = current;
        for (Rule rule : rules.matches.get(control.getClass())) {
            if (rule.excludeOwnScreens || !rules.owners.get(screen.getClass()).contains(rule.modId)) return true;
        }
        return false;
    }

    /** Loader compatibility switch for a whole-mod rule; class/package selectors stay widget-only. */
    public static boolean isWholeModExcluded(String modId) {
        return current.entries.stream().anyMatch(rule -> rule.modId.equals(modId)
            && rule.classes.isEmpty() && rule.packages.isEmpty());
    }

    private static boolean flag(JsonObject json, String name, boolean fallback) {
        if (!json.has(name)) return fallback;
        JsonElement value = json.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(name + " must be a boolean");
        return value.getAsBoolean();
    }

    private static String string(@Nullable JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
            throw new IllegalArgumentException("Expected a nonempty string");
        return value.getAsString();
    }

    private static List<String> strings(JsonObject json, String name) {
        if (!json.has(name)) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray(name)) values.add(string(value));
        return List.copyOf(values);
    }

    private record Rule(String modId, List<String> classes, List<String> packages, boolean excludeOwnScreens) {
        boolean matches(Class<?> type, Set<String> owners) {
            if (classes.isEmpty() && packages.isEmpty()) return owners.contains(modId);
            // Selectors are optional-dependency-safe and include subclasses of named controls.
            for (Class<?> base = type; base != null; base = base.getSuperclass()) {
                String name = base.getName();
                if (classes.contains(name) || packages.stream().anyMatch(name::startsWith)) return true;
            }
            return false;
        }
    }

    private static final class Rules {
        private final List<Rule> entries;
        private final Set<String> modIds;
        private final ClassValue<Set<String>> owners = new ClassValue<>() {
            @Override protected Set<String> computeValue(Class<?> type) {
                Set<String> result = new HashSet<>();
                for (String id : modIds) {
                    if (InterfacePlatform.get().isClassFromMod(id, type)) result.add(id);
                }
                return Set.copyOf(result);
            }
        };
        private final ClassValue<List<Rule>> matches = new ClassValue<>() {
            @Override protected List<Rule> computeValue(Class<?> type) {
                Set<String> ownedBy = owners.get(type);
                return entries.stream().filter(rule -> rule.matches(type, ownedBy)).toList();
            }
        };

        private Rules(List<Rule> entries) {
            this.entries = entries;
            Set<String> ids = new HashSet<>();
            entries.forEach(rule -> ids.add(rule.modId));
            modIds = Set.copyOf(ids);
        }
    }
}
