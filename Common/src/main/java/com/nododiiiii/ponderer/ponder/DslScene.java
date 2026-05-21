package com.nododiiiii.ponderer.ponder;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class DslScene {
    public String id;
    public List<String> items = List.of();
    public LocalizedText title;
    /**
     * Legacy single-structure field (kept for backward compatibility).
     */
    public String structure;
    /**
     * Structure pool used by this ponder. Can be referenced by show_structure.structure
     * using either resource id/path or numeric index (1-based preferred, 0-based accepted).
     */
    public List<String> structures = List.of();
    public List<String> tags = List.of();
    /**
     * Whether normal users can edit this scene. Null means the client config's
     * default editable value should be used.
     */
    @Nullable
    public Boolean editable;
    public List<SceneSegment> scenes = List.of();
    /**
     * Optional SNBT filter string. When set, scenes for this DslScene
     * are only shown if the hovered item's NBT contains these tags.
     * Example: "{CustomModelData:1}" or "{display:{Name:'\"Special\"'}}"
     */
    public String nbtFilter;

    /**
     * Pack name this scene belongs to. Null or absent for local scenes.
     * Set during import from a Ponderer resource pack.
     * Serialized to JSON (NOT transient).
     */
    @Nullable
    public String pack;

    /**
     * Trigger mode: null or "none" = normal (item hover), "structure" = near a structure,
     * "coordinate" = within coordinate range.
     */
    @Nullable
    public String triggerMode;

    /**
     * Hint display style: null or "subtitle" = action bar text (persistent),
     * "title" = title text (fades after 1s).
     * Legacy field — superseded by hintAuto/hintTitle/hintSubtitle.
     */
    @Nullable
    public String hintStyle;

    /** Structure id for "structure" trigger mode. e.g. "minecraft:village_plains" */
    @Nullable
    public String triggerStructure;

    /** Maximum range (in blocks) from structure boundary for "structure" trigger. */
    @Nullable
    public Integer triggerStructureRange;

    /** First corner coordinate for "coordinate" trigger mode. */
    @Nullable
    public List<Integer> triggerCoord1;

    /** Second corner coordinate for "coordinate" trigger mode. */
    @Nullable
    public List<Integer> triggerCoord2;

    /** Legacy: if true, the trigger hint is only shown on the first entry (per session). */
    @Nullable
    public Boolean onlyFirstTime;

    /** Legacy control for hint display frequency. Superseded by per-style fields. */
    @Nullable
    public String hintFrequency;

    /**
     * Per-hint-style frequency. null = disabled, "always" / "first_time" / "until_read".
     * These 3 fields can be independently enabled, allowing multiple hint styles at once.
     */
    @Nullable public String hintAuto;
    @Nullable public String hintTitle;
    @Nullable public String hintSubtitle;

    /** Custom display text for title hint. If null/empty, uses default key-name hint. */
    @Nullable public String hintTitleText;
    /** Custom display text for subtitle hint. If null/empty, uses default key-name hint. */
    @Nullable public String hintSubtitleText;

    /**
     * Transient field set by SceneStore.reloadFromDisk().
     * Stores the source filename (e.g. "example.json" or "[my_pack] example.json").
     * Not serialized by GSON.
     */
    public transient String sourceFile;

    /**
     * Extract pack prefix from a source filename.
     * "[my_pack] example.json" → "[my_pack]"
     * "example.json" → null
     */
    @Nullable
    public static String extractPackPrefix(@Nullable String sourceFile) {
        if (sourceFile == null) return null;
        if (sourceFile.startsWith("[")) {
            int close = sourceFile.indexOf(']');
            if (close > 0) {
                return sourceFile.substring(0, close + 1);
            }
        }
        return null;
    }

    /**
     * Get the pack prefix string from the pack field.
     * Returns "[PackName]" or null for local scenes.
     */
    @Nullable
    public String getPackPrefix() {
        if (pack != null && !pack.isEmpty()) {
            return "[" + pack + "]";
        }
        return null;
    }

    /**
     * Generate a unique scene key.
     * Local scene: "ponderer:example"
     * Pack scene: "[my_pack] ponderer:example"
     */
    public String sceneKey() {
        if (pack != null && !pack.isEmpty()) {
            return "[" + pack + "] " + id;
        }
        return id;
    }

    public boolean isEditable() {
        return !Boolean.FALSE.equals(editable);
    }

    public boolean isEditable(boolean defaultEditable) {
        return editable == null ? defaultEditable : editable;
    }

    public static class SceneSegment {
        public String id;
        public LocalizedText title;
        public List<DslStep> steps = List.of();
    }

    public static class DslStep {
        public String type;
        public String structure;
        public Integer duration;
        public Integer height;
        public LocalizedText text;
        public String key;
        public List<Double> point;
        public String entity;
        public List<Double> pos;
        public List<Double> motion;
        public List<Double> lookAt;
        public Float yaw;
        public Float pitch;
        public Float degrees;
        public Float degreesX;
        public Float scale;
        public Float rotation;
        public Integer count;
        public List<Integer> bounds;
        public String direction;
        public String linkId;
        public String action;
        public String item;
        public String sound;
        public Float soundVolume;
        public String source;
        public String color;
        public String block;
        public Map<String, String> blockProperties;
        public List<Integer> blockPos;
        public List<Integer> blockPos2;
        public List<Double> offset;
        public Float rotX;
        public Float rotY;
        public Float rotZ;
        public String nbt;
        public Boolean reDrawBlocks;
        public Boolean destroyParticles;
        public Boolean spawnParticles;
        public Boolean immediateDisplay;
        public Boolean smartDisplay;
        public String entranceAnimation;
        public Integer entranceDuration;
        public Integer entranceInterval;
        public Boolean placeNearTarget;
        public Boolean attachKeyFrame;
        public Boolean whileSneaking;
        public Boolean whileCTRL;
        public Boolean enableNbt;
        public Boolean fullScene;
        /**
         * show_extra_structure only: when true, air-family blocks in the structure also
         * overwrite the corresponding scene cells (cleared with air). Default false — air
         * positions leave the existing scene untouched.
         */
        public Boolean replaceAir;
        public List<InterfaceSlotBinding> interfaceSlots;

        public int durationOrDefault(int fallback) {
            return duration == null ? fallback : Math.max(duration, 0);
        }
    }

    public static class InterfaceSlotBinding {
        public Integer slotIndex;
        @Nullable
        public Integer slotX;
        @Nullable
        public Integer slotY;
        public String ingredientId;
        @Nullable
        public String ingredientKind;

        public InterfaceSlotBinding() {
        }

        public InterfaceSlotBinding(int slotIndex, String ingredientId, @Nullable String ingredientKind) {
            this(slotIndex, null, null, ingredientId, ingredientKind);
        }

        public InterfaceSlotBinding(int slotIndex, @Nullable Integer slotX, @Nullable Integer slotY,
                                    String ingredientId, @Nullable String ingredientKind) {
            this.slotIndex = slotIndex;
            this.slotX = slotX;
            this.slotY = slotY;
            this.ingredientId = ingredientId;
            this.ingredientKind = ingredientKind;
        }
    }
}
