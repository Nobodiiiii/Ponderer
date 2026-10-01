package com.nododiiiii.ponderer.interfaces.model;

import com.google.gson.JsonElement;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Versioned description of an interface shown by {@code show_interface} (schema 2).
 *
 * <p>Deliberately free of Minecraft types so that parsing and legacy migration can be
 * unit-tested without a bootstrapped game. The sandbox resolves ids against registries
 * when it builds a session.
 */
public final class InterfaceDescriptor {

    public static final int SCHEMA = 2;

    public enum TargetKind {
        BLOCK, SCENE_BLOCK, MENU, ITEM, ENTITY;

        @Nullable
        public static TargetKind parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "block" -> BLOCK;
                case "scene_block" -> SCENE_BLOCK;
                case "menu" -> MENU;
                case "item" -> ITEM;
                case "entity" -> ENTITY;
                default -> null;
            };
        }
    }

    /** How an item target opens: a client-side screen from {@code use}, a menu from extra data, or either. */
    public enum ItemMode {
        SCREEN, MENU, AUTO;

        public static ItemMode parse(@Nullable String raw) {
            if (raw == null) {
                return AUTO;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "screen" -> SCREEN;
                case "menu" -> MENU;
                default -> AUTO;
            };
        }
    }

    public enum Identity {
        VIEWER_OWNER, VIEWER_GUEST, UNOWNED;

        public static Identity parse(@Nullable String raw) {
            if (raw == null) {
                return VIEWER_OWNER;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "viewer_guest" -> VIEWER_GUEST;
                case "unowned" -> UNOWNED;
                default -> VIEWER_OWNER;
            };
        }
    }

    public enum Interaction {
        VIEW, TRY;

        public static Interaction parse(@Nullable String raw) {
            if (raw != null && "view".equals(raw.trim().toLowerCase(Locale.ROOT))) {
                return VIEW;
            }
            return TRY;
        }
    }

    /** An item stack in id/count/components form; components stay raw JSON until the sandbox decodes them. */
    public record ItemSpec(String id, int count, @Nullable JsonElement components, @Nullable String legacyNbt) {
        public ItemSpec {
            count = Math.max(1, count);
        }

        public static ItemSpec of(String id) {
            return new ItemSpec(id, 1, null, null);
        }
    }

    public record Hit(String face, @Nullable double[] point, boolean inside) {
    }

    public record SlotState(int index, ItemSpec item) {
    }

    public record DataState(int id, int value) {
    }

    public record InventoryItem(String slot, ItemSpec item) {
    }

    public record ContextBlock(int dx, int dy, int dz, String block, Map<String, String> properties,
                               @Nullable String nbt) {
    }

    /** NBT path of an identity field that was stripped at capture time and is re-filled on replay. */
    public record IdentitySlot(String path, String encoding, String role) {
    }

    public static final class PlayerContext {
        public Identity identity = Identity.VIEWER_OWNER;
        public String gameMode = "survival";
        @Nullable
        public Boolean instabuild;
        public int xpLevel;
        public float xpProgress;
        public int selectedSlot;
        public final List<InventoryItem> inventory = new ArrayList<>();
        public double[] offset = {0.5, 0.0, -1.5};
        public float yaw;
        public float pitch = 30.0f;
        public boolean sneaking;
    }

    // ---- Target -------------------------------------------------------------------------

    public TargetKind targetKind = TargetKind.BLOCK;
    @Nullable
    public String block;
    public final Map<String, String> blockProperties = new LinkedHashMap<>();
    /** Block entity SNBT (block targets) or item custom data SNBT (legacy item targets). */
    @Nullable
    public String nbt;
    @Nullable
    public Hit hit;
    /** Scene coordinate of a {@code scene_block} target. */
    @Nullable
    public int[] scenePos;
    @Nullable
    public ItemSpec item;
    public ItemMode itemMode = ItemMode.AUTO;
    /** "main" or "off". */
    public String hand = "main";

    // ---- Menu ---------------------------------------------------------------------------

    @Nullable
    public String menuType;
    /** Base64 of the loader open-screen extra data, when captured. */
    @Nullable
    public String extraData;
    /** Original block coordinates referenced by opaque opening data. Resolved only inside the sandbox. */
    @Nullable
    public int[] lookupOrigin;
    @Nullable
    public String resolvedStrategy;

    // ---- State and context --------------------------------------------------------------

    public final List<SlotState> slots = new ArrayList<>();
    public final List<DataState> data = new ArrayList<>();
    public final List<IdentitySlot> identitySlots = new ArrayList<>();
    public final PlayerContext player = new PlayerContext();
    public final List<ContextBlock> contextBlocks = new ArrayList<>();
    @Nullable
    public String updateTag;
    public Interaction interaction = Interaction.TRY;

    /** Where this descriptor came from ("schema2", "legacy:block", ...), for logs and reports. */
    public String origin = "schema2";

    public boolean isItemTarget() {
        return targetKind == TargetKind.ITEM;
    }

    public boolean isBlockTarget() {
        return targetKind == TargetKind.BLOCK || targetKind == TargetKind.SCENE_BLOCK;
    }

    @Override
    public String toString() {
        String what = switch (targetKind) {
            case BLOCK -> "block " + block;
            case SCENE_BLOCK -> "scene_block " + (scenePos == null ? "?" : scenePos[0] + "," + scenePos[1] + "," + scenePos[2]);
            case MENU -> "menu " + menuType;
            case ITEM -> "item " + (item == null ? "?" : item.id()) + " (" + itemMode.name().toLowerCase(Locale.ROOT) + ")";
            case ENTITY -> "entity";
        };
        return what + " [" + origin + "]";
    }
}
