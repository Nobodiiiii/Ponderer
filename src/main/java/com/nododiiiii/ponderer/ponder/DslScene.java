package com.nododiiiii.ponderer.ponder;

import java.util.List;

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
    public List<DslStep> steps = List.of();
    public List<SceneSegment> scenes = List.of();
    /**
     * Optional SNBT filter string. When set, scenes for this DslScene
     * are only shown if the hovered item's NBT contains these tags.
     * Example: "{CustomModelData:1}" or "{display:{Name:'\"Special\"'}}"
     */
    public String nbtFilter;

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
        public Boolean placeNearTarget;
        public Boolean attachKeyFrame;
        public Boolean whileSneaking;
        public Boolean whileCTRL;

        public int durationOrDefault(int fallback) {
            return duration == null ? fallback : Math.max(duration, 0);
        }
    }
}