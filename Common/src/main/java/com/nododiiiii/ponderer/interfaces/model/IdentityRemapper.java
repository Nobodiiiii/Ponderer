package com.nododiiiii.ponderer.interfaces.model;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Strips the author's identity from captured NBT and re-fills the recorded identity
 * slots at replay time, so ownership checks ("is this my machine?") resolve against
 * whoever is watching rather than whoever recorded the scene.
 *
 * <p>Paths are dot-separated compound keys with optional list indices, e.g.
 * {@code Security.SecUUID} or {@code Items[0].components.owner}.
 */
public final class IdentityRemapper {

    public static final String INT_ARRAY = "int_array";
    public static final String STRING = "string";
    public static final String MOST_LEAST = "most_least";
    public static final String NAME = "name";

    /** Stable stand-in owner for {@code viewer_guest} replays. */
    public static final UUID DEMO_OWNER = UUID.nameUUIDFromBytes("ponderer:demo_owner".getBytes(StandardCharsets.UTF_8));
    public static final String DEMO_OWNER_NAME = "Demo";

    private static final UUID ZERO = new UUID(0L, 0L);

    private IdentityRemapper() {
    }

    // ---- Capture ------------------------------------------------------------------------

    /**
     * Replaces every occurrence of the author's UUID (all three common encodings) and exact
     * name with neutral values, returning the slots that were cleared.
     */
    public static List<InterfaceDescriptor.IdentitySlot> strip(CompoundTag root, UUID author, @Nullable String authorName) {
        List<InterfaceDescriptor.IdentitySlot> slots = new ArrayList<>();
        stripCompound(root, "", author, authorName, slots);
        return slots;
    }

    private static void stripCompound(CompoundTag tag, String path, UUID author, @Nullable String authorName,
                                      List<InterfaceDescriptor.IdentitySlot> out) {
        for (String key : new ArrayList<>(tag.getAllKeys())) {
            Tag value = tag.get(key);
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (key.endsWith("Most") && value instanceof LongTag most) {
                String base = key.substring(0, key.length() - 4);
                if (tag.get(base + "Least") instanceof LongTag least
                    && most.getAsLong() == author.getMostSignificantBits()
                    && least.getAsLong() == author.getLeastSignificantBits()) {
                    tag.putLong(base + "Most", 0L);
                    tag.putLong(base + "Least", 0L);
                    out.add(new InterfaceDescriptor.IdentitySlot(path.isEmpty() ? base : path + "." + base, MOST_LEAST, "owner"));
                }
                continue;
            }
            Tag replacement = stripValue(value, childPath, author, authorName, out);
            if (replacement != null) {
                tag.put(key, replacement);
            }
        }
    }

    @Nullable
    private static Tag stripValue(Tag value, String path, UUID author, @Nullable String authorName,
                                  List<InterfaceDescriptor.IdentitySlot> out) {
        if (value instanceof CompoundTag compound) {
            stripCompound(compound, path, author, authorName, out);
        } else if (value instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag replacement = stripValue(list.get(i), path + "[" + i + "]", author, authorName, out);
                if (replacement != null) {
                    list.set(i, replacement);
                }
            }
        } else if (value instanceof IntArrayTag ints
            && Arrays.equals(ints.getAsIntArray(), UUIDUtil.uuidToIntArray(author))) {
            out.add(new InterfaceDescriptor.IdentitySlot(path, INT_ARRAY, "owner"));
            return new IntArrayTag(UUIDUtil.uuidToIntArray(ZERO));
        } else if (value instanceof StringTag string) {
            String text = string.getAsString();
            if (text.equalsIgnoreCase(author.toString())) {
                out.add(new InterfaceDescriptor.IdentitySlot(path, STRING, "owner"));
                return StringTag.valueOf(ZERO.toString());
            }
            if (authorName != null && !authorName.isEmpty() && text.equals(authorName)) {
                out.add(new InterfaceDescriptor.IdentitySlot(path, NAME, "owner"));
                return StringTag.valueOf("");
            }
        }
        return null;
    }

    // ---- Replay -------------------------------------------------------------------------

    /** Writes the identity chosen by {@code identity} into every recorded slot that still resolves. */
    public static int apply(CompoundTag root, List<InterfaceDescriptor.IdentitySlot> slots,
                            InterfaceDescriptor.Identity identity, UUID viewer, String viewerName) {
        UUID uuid = identity == InterfaceDescriptor.Identity.VIEWER_GUEST ? DEMO_OWNER : viewer;
        String name = identity == InterfaceDescriptor.Identity.VIEWER_GUEST ? DEMO_OWNER_NAME : viewerName;
        int written = 0;
        for (InterfaceDescriptor.IdentitySlot slot : slots) {
            if (write(root, slot, identity == InterfaceDescriptor.Identity.UNOWNED, uuid, name)) {
                written++;
            }
        }
        return written;
    }

    private static boolean write(CompoundTag root, InterfaceDescriptor.IdentitySlot slot, boolean clear,
                                 UUID uuid, String name) {
        List<String> segments = splitPath(slot.path());
        if (segments.isEmpty()) {
            return false;
        }
        Tag parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            parent = child(parent, segments.get(i));
            if (parent == null) {
                return false;
            }
        }
        String leaf = segments.get(segments.size() - 1);
        if (MOST_LEAST.equals(slot.encoding())) {
            if (!(parent instanceof CompoundTag compound)) {
                return false;
            }
            if (clear) {
                compound.remove(leaf + "Most");
                compound.remove(leaf + "Least");
            } else {
                compound.putLong(leaf + "Most", uuid.getMostSignificantBits());
                compound.putLong(leaf + "Least", uuid.getLeastSignificantBits());
            }
            return true;
        }
        Tag value = switch (slot.encoding()) {
            case INT_ARRAY -> new IntArrayTag(UUIDUtil.uuidToIntArray(uuid));
            case STRING -> StringTag.valueOf(uuid.toString());
            case NAME -> StringTag.valueOf(name);
            default -> null;
        };
        if (value == null) {
            return false;
        }
        return put(parent, leaf, clear ? null : value);
    }

    @Nullable
    private static Tag child(Tag parent, String segment) {
        if (segment.startsWith("[") && segment.endsWith("]")) {
            if (!(parent instanceof ListTag list)) {
                return null;
            }
            int index = parseIndex(segment);
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        return parent instanceof CompoundTag compound ? compound.get(segment) : null;
    }

    private static boolean put(Tag parent, String segment, @Nullable Tag value) {
        if (segment.startsWith("[") && segment.endsWith("]")) {
            if (!(parent instanceof ListTag list)) {
                return false;
            }
            int index = parseIndex(segment);
            if (index < 0 || index >= list.size() || value == null) {
                return false;
            }
            // ListTag rejects elements of a different type; identity slots keep the original type.
            return list.setTag(index, value);
        }
        if (!(parent instanceof CompoundTag compound)) {
            return false;
        }
        if (value == null) {
            compound.remove(segment);
        } else {
            compound.put(segment, value);
        }
        return true;
    }

    private static int parseIndex(String segment) {
        try {
            return Integer.parseInt(segment.substring(1, segment.length() - 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    static List<String> splitPath(String path) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.') {
                if (!current.isEmpty()) {
                    segments.add(current.toString());
                    current.setLength(0);
                }
            } else if (c == '[') {
                if (!current.isEmpty()) {
                    segments.add(current.toString());
                    current.setLength(0);
                }
                int end = path.indexOf(']', i);
                if (end < 0) {
                    return List.of();
                }
                segments.add(path.substring(i, end + 1));
                i = end;
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            segments.add(current.toString());
        }
        return segments;
    }
}
