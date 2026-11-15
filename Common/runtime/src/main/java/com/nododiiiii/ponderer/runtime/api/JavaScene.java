package com.nododiiiii.ponderer.runtime.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Builds an editable segment with ordinary Java arguments, without running scene instructions. */
public final class JavaScene {
    private static final Gson CODEC = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private final DslScene.SceneSegment segment = new DslScene.SceneSegment();
    private final List<DslScene.DslStep> steps = new ArrayList<>();

    public JavaScene(String id, LocalizedText title) {
        segment.id = id;
        segment.title = title;
    }

    public Step keyframe() { return new Step().keyframe(); }
    public Step fields(JsonObject fields) { return new Step().fields(fields); }

    public void javaStep(String type, JsonObject parameters) { new Step().javaStep(type, parameters); }
    public void raw(String type, JsonObject fields) { new Step().raw(type, fields); }

    public DslScene.SceneSegment build() {
        // Identities belong to the data model. Inserting an unrelated call never renumbers existing steps.
        var used = new HashSet<String>();
        for (var step : steps) if (step != null && step.id != null && !used.add(step.id))
            throw new IllegalStateException("Duplicate internal step identity");
        for (var step : steps) if (step != null && step.id == null) {
            var fields = CODEC.toJsonTree(step).getAsJsonObject();
            fields.remove("id");
            fields.remove("attachKeyFrame");
            String base = "java_" + UUID.nameUUIDFromBytes(canonical(fields).toString().getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
            String id = base;
            int occurrence = 2;
            while (!used.add(id)) id = base + "_" + occurrence++;
            step.id = id;
        }
        segment.steps = new ArrayList<>(steps);
        return segment;
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            var sorted = new TreeMap<String, JsonElement>();
            value.getAsJsonObject().entrySet().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            var result = new JsonObject();
            sorted.forEach((key, child) -> result.add(key, canonical(child)));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(canonical(child)));
            return result;
        }
        return value;
    }

    // Ordinary steps receive identities when the segment is built.
    public void showStructure(String structure, BlockPos blockPos, BlockPos blockPos2, Float scale, Float rotation) { new Step().showStructure(structure, blockPos, blockPos2, scale, rotation); }
    public void idle(Integer duration) { new Step().idle(duration); }
    public void showText(LocalizedText text, Vec3 point, Integer duration, String color, Boolean placeNearTarget) { new Step().showText(text, point, duration, color, placeNearTarget); }
    public void sharedText(String key, Vec3 point, Integer duration, String color, Boolean placeNearTarget) { new Step().sharedText(key, point, duration, color, placeNearTarget); }
    public void showControls(Vec3 point, String direction, Integer duration, String action, String item, String nbt, Boolean whileSneaking, Boolean whileCTRL) { new Step().showControls(point, direction, duration, action, item, nbt, whileSneaking, whileCTRL); }
    public void showExtraStructure(String structure, BlockPos blockPos, Float rotation, Boolean replaceAir, Boolean immediateDisplay, Boolean spawnParticles, String entranceAnimation, Integer entranceDuration, Integer entranceInterval, Boolean smartDisplay, String linkId, String direction) { new Step().showExtraStructure(structure, blockPos, rotation, replaceAir, immediateDisplay, spawnParticles, entranceAnimation, entranceDuration, entranceInterval, smartDisplay, linkId, direction); }
    public void createEntity(String entity, Vec3 pos, Vec3 lookAt, Float yaw, Float pitch, String nbt, String linkId, String entranceAnimation, Integer entranceDuration, String direction) { new Step().createEntity(entity, pos, lookAt, yaw, pitch, nbt, linkId, entranceAnimation, entranceDuration, direction); }
    public void createItemEntity(String item, Integer count, Vec3 pos, Vec3 motion, String nbt, String linkId) { new Step().createItemEntity(item, count, pos, motion, nbt, linkId); }
    public void rotateCameraY(Float degrees, Float degreesX, Integer duration) { new Step().rotateCameraY(degrees, degreesX, duration); }
    public void highlightSection(String color, BlockPos blockPos, BlockPos blockPos2, Integer duration) { new Step().highlightSection(color, blockPos, blockPos2, duration); }
    public void encapsulateBounds(BlockPos bounds) { new Step().encapsulateBounds(bounds); }
    public void playSound(String sound, Float soundVolume, Float pitch, String source) { new Step().playSound(sound, soundVolume, pitch, source); }
    public void setBlock(String block, Map<String, String> blockProperties, BlockPos blockPos, BlockPos blockPos2, String nbt, Boolean immediateDisplay, Boolean spawnParticles, String entranceAnimation, Integer entranceDuration, Integer entranceInterval, Boolean smartDisplay, String linkId, String direction) { new Step().setBlock(block, blockProperties, blockPos, blockPos2, nbt, immediateDisplay, spawnParticles, entranceAnimation, entranceDuration, entranceInterval, smartDisplay, linkId, direction); }
    public void destroyBlock(BlockPos blockPos, BlockPos blockPos2, Boolean destroyParticles) { new Step().destroyBlock(blockPos, blockPos2, destroyParticles); }
    public void replaceBlocks(String block, Map<String, String> blockProperties, BlockPos blockPos, BlockPos blockPos2, Boolean spawnParticles) { new Step().replaceBlocks(block, blockProperties, blockPos, blockPos2, spawnParticles); }
    public void hideSection(BlockPos blockPos, BlockPos blockPos2, Integer duration, String direction) { new Step().hideSection(blockPos, blockPos2, duration, direction); }
    public void showSectionAndMerge(BlockPos blockPos, BlockPos blockPos2, String linkId, Integer duration, String direction, String entranceAnimation, Integer entranceDuration, Integer entranceInterval, Boolean smartDisplay) { new Step().showSectionAndMerge(blockPos, blockPos2, linkId, duration, direction, entranceAnimation, entranceDuration, entranceInterval, smartDisplay); }
    public void rotateSection(String linkId, BlockPos blockPos, BlockPos blockPos2, Float rotX, Float rotY, Float rotZ, Integer duration) { new Step().rotateSection(linkId, blockPos, blockPos2, rotX, rotY, rotZ, duration); }
    public void moveSection(String linkId, BlockPos blockPos, BlockPos blockPos2, Vec3 offset, Integer duration) { new Step().moveSection(linkId, blockPos, blockPos2, offset, duration); }
    public void toggleRedstonePower(BlockPos blockPos, BlockPos blockPos2) { new Step().toggleRedstonePower(blockPos, blockPos2); }
    public void modifyBlockEntity(Map<String, String> blockProperties, String nbt, Boolean reDrawBlocks, BlockPos blockPos, BlockPos blockPos2) { new Step().modifyBlockEntity(blockProperties, nbt, reDrawBlocks, blockPos, blockPos2); }
    public void indicateRedstone(BlockPos blockPos) { new Step().indicateRedstone(blockPos); }
    public void indicateSuccess(BlockPos blockPos) { new Step().indicateSuccess(blockPos); }
    public void spawnParticles(String particle, Vec3 pos, String distribution, Vec3 spread, Vec3 motion, Integer count, Integer duration) { new Step().spawnParticles(particle, pos, distribution, spread, motion, count, duration); }
    public void clearEntities(Boolean fullScene, String entity, String linkId, BlockPos blockPos, BlockPos blockPos2, String entranceAnimation, Integer entranceDuration, String direction) { new Step().clearEntities(fullScene, entity, linkId, blockPos, blockPos2, entranceAnimation, entranceDuration, direction); }
    public void clearItemEntities(Boolean fullScene, String item, String linkId, BlockPos blockPos, BlockPos blockPos2) { new Step().clearItemEntities(fullScene, item, linkId, blockPos, blockPos2); }
    public void modifyEntitiesNbt(Boolean fullScene, String entity, String linkId, String nbt, BlockPos blockPos, BlockPos blockPos2, Vec3 offset, Integer duration, Boolean walkAnimation) { new Step().modifyEntitiesNbt(fullScene, entity, linkId, nbt, blockPos, blockPos2, offset, duration, walkAnimation); }
    public void modifyItemEntitiesNbt(Boolean fullScene, String item, String linkId, String nbt, BlockPos blockPos, BlockPos blockPos2) { new Step().modifyItemEntitiesNbt(fullScene, item, linkId, nbt, blockPos, blockPos2); }
    public void zoomScene(Float scale, Vec3 point, Integer duration) { new Step().zoomScene(scale, point, duration); }
    public void nextScene() { new Step().nextScene(); }

    public void emptyStep() { steps.add(null); }

    /** Field order lets the exporter emit the same typed arguments as this authoring API. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Action {
        String type();
        String[] fields();
    }

    private static List<Integer> coordinates(BlockPos pos) {
        return pos == null ? null : List.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static List<Double> coordinates(Vec3 pos) {
        return pos == null ? null : List.of(pos.x, pos.y, pos.z);
    }

    public final class Step {
        private DslScene.DslStep step = new DslScene.DslStep();
        private boolean added;

        private Step() {}

        public Step keyframe() { step.attachKeyFrame = true; return this; }

        /** Additional and unknown fields stay attached to their original step. */
        public Step fields(JsonObject fields) {
            if (added) throw new IllegalStateException("Step already added: " + step.id);
            String id = step.id;
            Boolean keyframe = step.attachKeyFrame;
            step = CODEC.fromJson(fields.deepCopy(), DslScene.DslStep.class);
            step.id = id;
            if (keyframe != null) step.attachKeyFrame = keyframe;
            return this;
        }

        private void add(String type) {
            if (added) throw new IllegalStateException("Step already added: " + step.id);
            step.type = type;
            steps.add(step);
            added = true;
        }

        /** Compiled custom code is registered separately with engine.steps(). */
        public void javaStep(String type, JsonObject parameters) {
            step.parameters = parameters == null ? null : parameters.deepCopy();
            add(type);
        }

        /** Complex and future steps retain their complete data without inventing argument conversions. */
        public void raw(String type, JsonObject fields) {
            fields(fields);
            add(type);
        }

        @Action(type = "show_structure", fields = {"structure", "blockPos", "blockPos2", "scale", "rotation"})
        public void showStructure(String structure, BlockPos blockPos, BlockPos blockPos2, Float scale, Float rotation) {
            step.structure = structure;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.scale = scale;
            step.rotation = rotation;
            add("show_structure");
        }

        @Action(type = "idle", fields = {"duration"})
        public void idle(Integer duration) {
            step.duration = duration;
            add("idle");
        }

        @Action(type = "text", fields = {"text", "point", "duration", "color", "placeNearTarget"})
        public void showText(LocalizedText text, Vec3 point, Integer duration, String color, Boolean placeNearTarget) {
            step.text = text;
            step.point = coordinates(point);
            step.duration = duration;
            step.color = color;
            step.placeNearTarget = placeNearTarget;
            add("text");
        }

        @Action(type = "shared_text", fields = {"key", "point", "duration", "color", "placeNearTarget"})
        public void sharedText(String key, Vec3 point, Integer duration, String color, Boolean placeNearTarget) {
            step.key = key;
            step.point = coordinates(point);
            step.duration = duration;
            step.color = color;
            step.placeNearTarget = placeNearTarget;
            add("shared_text");
        }

        @Action(type = "show_controls", fields = {"point", "direction", "duration", "action", "item", "nbt", "whileSneaking", "whileCTRL"})
        public void showControls(Vec3 point, String direction, Integer duration, String action, String item, String nbt, Boolean whileSneaking, Boolean whileCTRL) {
            step.point = coordinates(point);
            step.direction = direction;
            step.duration = duration;
            step.action = action;
            step.item = item;
            step.nbt = nbt;
            step.whileSneaking = whileSneaking;
            step.whileCTRL = whileCTRL;
            add("show_controls");
        }

        @Action(type = "show_extra_structure", fields = {"structure", "blockPos", "rotation", "replaceAir", "immediateDisplay", "spawnParticles", "entranceAnimation", "entranceDuration", "entranceInterval", "smartDisplay", "linkId", "direction"})
        public void showExtraStructure(String structure, BlockPos blockPos, Float rotation, Boolean replaceAir, Boolean immediateDisplay, Boolean spawnParticles, String entranceAnimation, Integer entranceDuration, Integer entranceInterval, Boolean smartDisplay, String linkId, String direction) {
            step.structure = structure;
            step.blockPos = coordinates(blockPos);
            step.rotation = rotation;
            step.replaceAir = replaceAir;
            step.immediateDisplay = immediateDisplay;
            step.spawnParticles = spawnParticles;
            step.entranceAnimation = entranceAnimation;
            step.entranceDuration = entranceDuration;
            step.entranceInterval = entranceInterval;
            step.smartDisplay = smartDisplay;
            step.linkId = linkId;
            step.direction = direction;
            add("show_extra_structure");
        }

        @Action(type = "create_entity", fields = {"entity", "pos", "lookAt", "yaw", "pitch", "nbt", "linkId", "entranceAnimation", "entranceDuration", "direction"})
        public void createEntity(String entity, Vec3 pos, Vec3 lookAt, Float yaw, Float pitch, String nbt, String linkId, String entranceAnimation, Integer entranceDuration, String direction) {
            step.entity = entity;
            step.pos = coordinates(pos);
            step.lookAt = coordinates(lookAt);
            step.yaw = yaw;
            step.pitch = pitch;
            step.nbt = nbt;
            step.linkId = linkId;
            step.entranceAnimation = entranceAnimation;
            step.entranceDuration = entranceDuration;
            step.direction = direction;
            add("create_entity");
        }

        @Action(type = "create_item_entity", fields = {"item", "count", "pos", "motion", "nbt", "linkId"})
        public void createItemEntity(String item, Integer count, Vec3 pos, Vec3 motion, String nbt, String linkId) {
            step.item = item;
            step.count = count;
            step.pos = coordinates(pos);
            step.motion = coordinates(motion);
            step.nbt = nbt;
            step.linkId = linkId;
            add("create_item_entity");
        }

        @Action(type = "rotate_camera_y", fields = {"degrees", "degreesX", "duration"})
        public void rotateCameraY(Float degrees, Float degreesX, Integer duration) {
            step.degrees = degrees;
            step.degreesX = degreesX;
            step.duration = duration;
            add("rotate_camera_y");
        }

        @Action(type = "highlight_section", fields = {"color", "blockPos", "blockPos2", "duration"})
        public void highlightSection(String color, BlockPos blockPos, BlockPos blockPos2, Integer duration) {
            step.color = color;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.duration = duration;
            add("highlight_section");
        }

        @Action(type = "encapsulate_bounds", fields = {"bounds"})
        public void encapsulateBounds(BlockPos bounds) {
            step.bounds = coordinates(bounds);
            add("encapsulate_bounds");
        }

        @Action(type = "play_sound", fields = {"sound", "soundVolume", "pitch", "source"})
        public void playSound(String sound, Float soundVolume, Float pitch, String source) {
            step.sound = sound;
            step.soundVolume = soundVolume;
            step.pitch = pitch;
            step.source = source;
            add("play_sound");
        }

        @Action(type = "set_block", fields = {"block", "blockProperties", "blockPos", "blockPos2", "nbt", "immediateDisplay", "spawnParticles", "entranceAnimation", "entranceDuration", "entranceInterval", "smartDisplay", "linkId", "direction"})
        public void setBlock(String block, Map<String, String> blockProperties, BlockPos blockPos, BlockPos blockPos2, String nbt, Boolean immediateDisplay, Boolean spawnParticles, String entranceAnimation, Integer entranceDuration, Integer entranceInterval, Boolean smartDisplay, String linkId, String direction) {
            step.block = block;
            step.blockProperties = blockProperties;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.nbt = nbt;
            step.immediateDisplay = immediateDisplay;
            step.spawnParticles = spawnParticles;
            step.entranceAnimation = entranceAnimation;
            step.entranceDuration = entranceDuration;
            step.entranceInterval = entranceInterval;
            step.smartDisplay = smartDisplay;
            step.linkId = linkId;
            step.direction = direction;
            add("set_block");
        }

        @Action(type = "destroy_block", fields = {"blockPos", "blockPos2", "destroyParticles"})
        public void destroyBlock(BlockPos blockPos, BlockPos blockPos2, Boolean destroyParticles) {
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.destroyParticles = destroyParticles;
            add("destroy_block");
        }

        @Action(type = "replace_blocks", fields = {"block", "blockProperties", "blockPos", "blockPos2", "spawnParticles"})
        public void replaceBlocks(String block, Map<String, String> blockProperties, BlockPos blockPos, BlockPos blockPos2, Boolean spawnParticles) {
            step.block = block;
            step.blockProperties = blockProperties;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.spawnParticles = spawnParticles;
            add("replace_blocks");
        }

        @Action(type = "hide_section", fields = {"blockPos", "blockPos2", "duration", "direction"})
        public void hideSection(BlockPos blockPos, BlockPos blockPos2, Integer duration, String direction) {
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.duration = duration;
            step.direction = direction;
            add("hide_section");
        }

        @Action(type = "show_section_and_merge", fields = {"blockPos", "blockPos2", "linkId", "duration", "direction", "entranceAnimation", "entranceDuration", "entranceInterval", "smartDisplay"})
        public void showSectionAndMerge(BlockPos blockPos, BlockPos blockPos2, String linkId, Integer duration, String direction, String entranceAnimation, Integer entranceDuration, Integer entranceInterval, Boolean smartDisplay) {
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.linkId = linkId;
            step.duration = duration;
            step.direction = direction;
            step.entranceAnimation = entranceAnimation;
            step.entranceDuration = entranceDuration;
            step.entranceInterval = entranceInterval;
            step.smartDisplay = smartDisplay;
            add("show_section_and_merge");
        }

        @Action(type = "rotate_section", fields = {"linkId", "blockPos", "blockPos2", "rotX", "rotY", "rotZ", "duration"})
        public void rotateSection(String linkId, BlockPos blockPos, BlockPos blockPos2, Float rotX, Float rotY, Float rotZ, Integer duration) {
            step.linkId = linkId;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.rotX = rotX;
            step.rotY = rotY;
            step.rotZ = rotZ;
            step.duration = duration;
            add("rotate_section");
        }

        @Action(type = "move_section", fields = {"linkId", "blockPos", "blockPos2", "offset", "duration"})
        public void moveSection(String linkId, BlockPos blockPos, BlockPos blockPos2, Vec3 offset, Integer duration) {
            step.linkId = linkId;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.offset = coordinates(offset);
            step.duration = duration;
            add("move_section");
        }

        @Action(type = "toggle_redstone_power", fields = {"blockPos", "blockPos2"})
        public void toggleRedstonePower(BlockPos blockPos, BlockPos blockPos2) {
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            add("toggle_redstone_power");
        }

        @Action(type = "modify_block_entity_nbt", fields = {"blockProperties", "nbt", "reDrawBlocks", "blockPos", "blockPos2"})
        public void modifyBlockEntity(Map<String, String> blockProperties, String nbt, Boolean reDrawBlocks, BlockPos blockPos, BlockPos blockPos2) {
            step.blockProperties = blockProperties;
            step.nbt = nbt;
            step.reDrawBlocks = reDrawBlocks;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            add("modify_block_entity_nbt");
        }

        @Action(type = "indicate_redstone", fields = {"blockPos"})
        public void indicateRedstone(BlockPos blockPos) {
            step.blockPos = coordinates(blockPos);
            add("indicate_redstone");
        }

        @Action(type = "indicate_success", fields = {"blockPos"})
        public void indicateSuccess(BlockPos blockPos) {
            step.blockPos = coordinates(blockPos);
            add("indicate_success");
        }

        @Action(type = "spawn_particles", fields = {"particle", "pos", "distribution", "spread", "motion", "count", "duration"})
        public void spawnParticles(String particle, Vec3 pos, String distribution, Vec3 spread, Vec3 motion, Integer count, Integer duration) {
            step.particle = particle;
            step.pos = coordinates(pos);
            step.distribution = distribution;
            step.spread = coordinates(spread);
            step.motion = coordinates(motion);
            step.count = count;
            step.duration = duration;
            add("spawn_particles");
        }

        @Action(type = "clear_entities", fields = {"fullScene", "entity", "linkId", "blockPos", "blockPos2", "entranceAnimation", "entranceDuration", "direction"})
        public void clearEntities(Boolean fullScene, String entity, String linkId, BlockPos blockPos, BlockPos blockPos2, String entranceAnimation, Integer entranceDuration, String direction) {
            step.fullScene = fullScene;
            step.entity = entity;
            step.linkId = linkId;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.entranceAnimation = entranceAnimation;
            step.entranceDuration = entranceDuration;
            step.direction = direction;
            add("clear_entities");
        }

        @Action(type = "clear_item_entities", fields = {"fullScene", "item", "linkId", "blockPos", "blockPos2"})
        public void clearItemEntities(Boolean fullScene, String item, String linkId, BlockPos blockPos, BlockPos blockPos2) {
            step.fullScene = fullScene;
            step.item = item;
            step.linkId = linkId;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            add("clear_item_entities");
        }

        @Action(type = "modify_entities_nbt", fields = {"fullScene", "entity", "linkId", "nbt", "blockPos", "blockPos2", "offset", "duration", "walkAnimation"})
        public void modifyEntitiesNbt(Boolean fullScene, String entity, String linkId, String nbt, BlockPos blockPos, BlockPos blockPos2, Vec3 offset, Integer duration, Boolean walkAnimation) {
            step.fullScene = fullScene;
            step.entity = entity;
            step.linkId = linkId;
            step.nbt = nbt;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            step.offset = coordinates(offset);
            step.duration = duration;
            step.walkAnimation = walkAnimation;
            add("modify_entities_nbt");
        }

        @Action(type = "modify_item_entities_nbt", fields = {"fullScene", "item", "linkId", "nbt", "blockPos", "blockPos2"})
        public void modifyItemEntitiesNbt(Boolean fullScene, String item, String linkId, String nbt, BlockPos blockPos, BlockPos blockPos2) {
            step.fullScene = fullScene;
            step.item = item;
            step.linkId = linkId;
            step.nbt = nbt;
            step.blockPos = coordinates(blockPos);
            step.blockPos2 = coordinates(blockPos2);
            add("modify_item_entities_nbt");
        }

        @Action(type = "zoom_scene", fields = {"scale", "point", "duration"})
        public void zoomScene(Float scale, Vec3 point, Integer duration) {
            step.scale = scale;
            step.point = coordinates(point);
            step.duration = duration;
            add("zoom_scene");
        }

        @Action(type = "next_scene", fields = {})
        public void nextScene() {
            add("next_scene");
        }

    }
}
