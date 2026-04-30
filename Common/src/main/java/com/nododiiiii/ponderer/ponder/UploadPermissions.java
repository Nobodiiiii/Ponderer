package com.nododiiiii.ponderer.ponder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UploadPermissions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PERMISSIONS_FILE = "permissions.txt";
    private static final String LEGACY_UPLOAD_ALLOWLIST_FILE = "upload_allowlist.txt";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private UploadPermissions() {
    }

    public enum Role {
        PULL("pull", 1),
        UPLOAD("upload", 2),
        ADMIN("admin", 3);

        private final String id;
        private final int level;

        Role(String id, int level) {
            this.id = id;
            this.level = level;
        }

        public String id() {
            return id;
        }

        public boolean includes(Role required) {
            return level >= required.level;
        }

        public static Role fromId(String id) {
            if (id == null) {
                return null;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (Role role : values()) {
                if (role.id.equals(normalized)) {
                    return role;
                }
            }
            return null;
        }
    }

    public record Entry(String subject, Role role, boolean operatorManaged) {
    }

    public record Snapshot(List<Entry> entries, Role viewerRole, boolean serverOperator, boolean canManage) {
    }

    public record UpdateResult(boolean success, boolean changed, String messageKey, String subject) {
    }

    public static boolean canUpload(ServerPlayer player) {
        return hasRole(player, Role.UPLOAD);
    }

    public static boolean canPull(ServerPlayer player) {
        return hasRole(player, Role.PULL);
    }

    public static boolean canManage(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return true;
        }
        Role role = effectiveRole(player);
        return role != null && role.includes(Role.ADMIN);
    }

    public static Role effectiveRole(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return Role.ADMIN;
        }

        MinecraftServer server = player.server;
        Map<String, Entry> entries = loadEntryMap(server);
        return bestRoleFor(player, entries);
    }

    public static Snapshot snapshotFor(ServerPlayer player) {
        List<Entry> entries = loadEntries(player.server);
        return new Snapshot(entries, effectiveRole(player), player.hasPermissions(2), canManage(player));
    }

    public static void ensurePullAccess(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            loadEntries(player.server);
            return;
        }

        try {
            Map<String, Entry> entries = loadEntryMap(player.server);
            if (bestRoleFor(player, entries) != null) {
                return;
            }

            String subject = sanitizeSubject(player.getGameProfile().getName());
            if (subject == null) {
                subject = player.getUUID().toString().toLowerCase(Locale.ROOT);
            }
            entries.put(subjectKey(subject), new Entry(subject, Role.PULL, false));
            saveEntries(player.server, entries.values());
        } catch (Exception e) {
            LOGGER.warn("Failed to add default pull permission for {}", player.getGameProfile().getName(), e);
        }
    }

    public static UpdateResult upsert(ServerPlayer actor, String rawSubject, Role role) {
        if (!canManage(actor)) {
            return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.denied", rawSubject);
        }

        String subject = sanitizeSubject(rawSubject);
        if (subject == null || role == null) {
            return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.invalid_player", rawSubject);
        }

        try {
            Map<String, Entry> entries = loadEntryMap(actor.server);
            String key = subjectKey(subject);
            Entry previous = entries.get(key);
            if (isOperatorSubject(actor.server, subject)
                || previous != null && previous.operatorManaged()) {
                return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.operator_locked",
                    subject);
            }
            if (previous != null && previous.role() == role && previous.subject().equals(subject)) {
                return new UpdateResult(true, false, "ponderer.ui.function_page.permissions.unchanged", subject);
            }

            if (previous != null
                && previous.role() == Role.ADMIN
                && role != Role.ADMIN
                && !actor.hasPermissions(2)
                && explicitAdminCount(entries) <= 1) {
                return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.last_admin", subject);
            }

            entries.put(key, new Entry(subject, role, false));
            saveEntries(actor.server, entries.values());
            return new UpdateResult(true, true,
                "ponderer.ui.function_page.permissions.updated." + role.id(), subject);
        } catch (Exception e) {
            LOGGER.warn("Failed to update Ponderer permissions", e);
            return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.error", subject);
        }
    }

    public static UpdateResult remove(ServerPlayer actor, String rawSubject) {
        if (!canManage(actor)) {
            return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.denied", rawSubject);
        }

        String subject = sanitizeSubject(rawSubject);
        if (subject == null) {
            return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.invalid_player", rawSubject);
        }

        try {
            Map<String, Entry> entries = loadEntryMap(actor.server);
            String key = subjectKey(subject);
            Entry previous = entries.get(key);
            if (previous == null) {
                return new UpdateResult(true, false, "ponderer.ui.function_page.permissions.not_found", subject);
            }
            if (isOperatorSubject(actor.server, subject) || previous.operatorManaged()) {
                return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.operator_locked",
                    previous.subject());
            }

            if (previous.role() == Role.ADMIN && !actor.hasPermissions(2) && explicitAdminCount(entries) <= 1) {
                return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.last_admin",
                    previous.subject());
            }

            entries.remove(key);
            saveEntries(actor.server, entries.values());
            return new UpdateResult(true, true, "ponderer.ui.function_page.permissions.removed", previous.subject());
        } catch (Exception e) {
            LOGGER.warn("Failed to update Ponderer permissions", e);
            return new UpdateResult(false, false, "ponderer.ui.function_page.permissions.error", subject);
        }
    }

    public static List<Entry> loadEntries(MinecraftServer server) {
        return sortedEntries(loadEntryMap(server).values());
    }

    private static boolean hasRole(ServerPlayer player, Role required) {
        if (player.hasPermissions(2)) {
            return true;
        }
        Role role = effectiveRole(player);
        return role != null && role.includes(required);
    }

    private static Role bestRoleFor(ServerPlayer player, Map<String, Entry> entries) {
        String uuid = player.getUUID().toString().toLowerCase(Locale.ROOT);
        String name = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        Role best = null;
        Entry uuidEntry = entries.get(uuid);
        if (uuidEntry != null) {
            best = uuidEntry.role();
        }
        Entry nameEntry = entries.get(name);
        if (nameEntry != null && (best == null || nameEntry.role().level > best.level)) {
            best = nameEntry.role();
        }
        return best;
    }

    private static Map<String, Entry> loadEntryMap(MinecraftServer server) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        Path permissionsPath = permissionPath(server);
        if (Files.exists(permissionsPath)) {
            readPermissionFile(permissionsPath, entries);
            syncServerOperators(server, entries);
            return entries;
        }

        Path legacyPath = legacyUploadAllowlistPath(server);
        if (Files.exists(legacyPath)) {
            readLegacyUploadAllowlist(legacyPath, entries);
        }
        syncServerOperators(server, entries);
        return entries;
    }

    private static void readPermissionFile(Path path, Map<String, Entry> entries) {
        try {
            for (String line : Files.readAllLines(path)) {
                Entry entry = parsePermissionLine(line);
                if (entry != null) {
                    mergeEntry(entries, entry);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read Ponderer permissions: {}", path, e);
        }
    }

    private static void readLegacyUploadAllowlist(Path path, Map<String, Entry> entries) {
        try {
            for (String line : Files.readAllLines(path)) {
                String subject = sanitizeSubject(stripComment(line));
                if (subject != null) {
                    mergeEntry(entries, new Entry(subject, Role.UPLOAD, false));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read legacy upload allowlist: {}", path, e);
        }
    }

    private static Entry parsePermissionLine(String line) {
        String stripped = stripComment(line);
        if (stripped.isBlank()) {
            return null;
        }

        String rolePart;
        String subjectPart;
        int colonIndex = stripped.indexOf(':');
        if (colonIndex > 0) {
            rolePart = stripped.substring(0, colonIndex);
            subjectPart = stripped.substring(colonIndex + 1);
        } else {
            String[] parts = stripped.split("\\s+", 2);
            if (parts.length == 2 && "op".equalsIgnoreCase(parts[0])) {
                rolePart = "op";
                subjectPart = parts[1];
            } else if (parts.length == 2 && Role.fromId(parts[0]) != null) {
                rolePart = parts[0];
                subjectPart = parts[1];
            } else {
                rolePart = Role.UPLOAD.id();
                subjectPart = stripped;
            }
        }

        boolean operatorManaged = "op".equalsIgnoreCase(rolePart);
        Role role = operatorManaged ? Role.ADMIN : Role.fromId(rolePart);
        String subject = sanitizeSubject(subjectPart);
        if (role == null || subject == null) {
            return null;
        }
        return new Entry(subject, role, operatorManaged);
    }

    private static void mergeEntry(Map<String, Entry> entries, Entry entry) {
        String key = subjectKey(entry.subject());
        Entry previous = entries.get(key);
        if (previous == null
            || entry.operatorManaged() && !previous.operatorManaged()
            || entry.role().level > previous.role().level) {
            entries.put(key, entry);
        }
    }

    private static void saveEntries(MinecraftServer server, Iterable<Entry> entries) throws Exception {
        Path path = permissionPath(server);
        Files.createDirectories(path.getParent());

        List<String> lines = new ArrayList<>();
        lines.add("# Ponderer server permission allowlist");
        lines.add("# Format: role playerNameOrUuid");
        lines.add("# Roles: admin = manage/upload/pull, upload = upload/pull, pull = pull only");
        lines.add("# OP-derived admins are stored as: op playerName");
        for (Entry entry : sortedEntries(entries)) {
            String roleId = entry.operatorManaged() ? "op" : entry.role().id();
            lines.add(roleId + " " + entry.subject());
        }
        Files.write(path, lines);
    }

    private static List<Entry> sortedEntries(Iterable<Entry> entries) {
        List<Entry> sorted = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry != null && entry.subject() != null && entry.role() != null) {
                sorted.add(entry);
            }
        }
        sorted.sort(Comparator
            .comparingInt((Entry entry) -> -entry.role().level)
            .thenComparing(entry -> !entry.operatorManaged())
            .thenComparing(entry -> entry.subject().toLowerCase(Locale.ROOT)));
        return List.copyOf(sorted);
    }

    private static int explicitAdminCount(Map<String, Entry> entries) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.role() == Role.ADMIN) {
                count++;
            }
        }
        return count;
    }

    private static Path permissionPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("ponderer")
            .resolve(PERMISSIONS_FILE);
    }

    private static Path legacyUploadAllowlistPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("ponderer")
            .resolve(LEGACY_UPLOAD_ALLOWLIST_FILE);
    }

    private static void syncServerOperators(MinecraftServer server, Map<String, Entry> entries) {
        List<String> operatorSubjects = operatorDisplaySubjects(server);
        boolean changed = false;

        for (Entry entry : new ArrayList<>(entries.values())) {
            if (entry.operatorManaged() && !containsSubject(operatorSubjects, entry.subject())) {
                entries.put(subjectKey(entry.subject()), new Entry(entry.subject(), Role.UPLOAD, false));
                changed = true;
            }
        }

        for (String operatorSubject : operatorSubjects) {
            Entry previous = entries.get(subjectKey(operatorSubject));
            if (previous == null
                || !previous.operatorManaged()
                || previous.role() != Role.ADMIN
                || !previous.subject().equals(operatorSubject)) {
                entries.put(subjectKey(operatorSubject), new Entry(operatorSubject, Role.ADMIN, true));
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        try {
            saveEntries(server, entries.values());
        } catch (Exception e) {
            LOGGER.warn("Failed to synchronize Ponderer operator permissions", e);
        }
    }

    private static List<String> operatorDisplaySubjects(MinecraftServer server) {
        List<String> subjects = new ArrayList<>();
        for (String name : server.getPlayerList().getOpNames()) {
            addSubject(subjects, name);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                addSubject(subjects, player.getGameProfile().getName());
            }
        }
        return subjects;
    }

    private static boolean isOperatorSubject(MinecraftServer server, String subject) {
        return containsSubject(operatorAliases(server), subject);
    }

    private static List<String> operatorAliases(MinecraftServer server) {
        List<String> aliases = new ArrayList<>();
        for (String subject : operatorDisplaySubjects(server)) {
            addSubject(aliases, subject);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                addSubject(aliases, player.getStringUUID());
            }
        }
        addOperatorFileAliases(server, aliases);
        return aliases;
    }

    private static void addOperatorFileAliases(MinecraftServer server, List<String> aliases) {
        Path path = server.getPlayerList().getOps().getFile().toPath();
        if (!Files.exists(path)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (object.has("name")) {
                    addSubject(aliases, object.get("name").getAsString());
                }
                if (object.has("uuid")) {
                    addSubject(aliases, object.get("uuid").getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read server operator aliases", e);
        }
    }

    private static void addSubject(List<String> subjects, String rawSubject) {
        String subject = sanitizeSubject(rawSubject);
        if (subject != null && !containsSubject(subjects, subject)) {
            subjects.add(subject);
        }
    }

    private static boolean containsSubject(List<String> subjects, String subject) {
        String key = subjectKey(subject);
        for (String candidate : subjects) {
            if (subjectKey(candidate).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String stripComment(String line) {
        if (line == null) {
            return "";
        }
        int commentIndex = line.indexOf('#');
        String stripped = commentIndex >= 0 ? line.substring(0, commentIndex) : line;
        return stripped.trim();
    }

    private static String sanitizeSubject(String rawSubject) {
        if (rawSubject == null) {
            return null;
        }

        String subject = rawSubject.trim();
        if (subject.isEmpty() || subject.length() > 64 || subject.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }

        try {
            return UUID.fromString(subject).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            if (USERNAME_PATTERN.matcher(subject).matches()) {
                return subject;
            }
            return null;
        }
    }

    private static String subjectKey(String subject) {
        return subject.toLowerCase(Locale.ROOT);
    }
}
