package com.nododiiiii.ponderer.ponder;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DynamicPonderPlugin implements PonderPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static class StepContext {
        final Map<String, ElementLink<WorldSectionElement>> sectionLinks = new HashMap<>();
    }

    @Override
    public String getModId() {
        return "ponderer";
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        NbtSceneFilter.clear();
        for (DslScene scene : SceneRuntime.getScenes()) {
            registerScene(helper, scene);
        }
        registerBlueprintGuideScene(helper);
    }

    private void registerBlueprintGuideScene(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation carrier = BuiltInRegistries.ITEM.getKey(BlueprintFeature.resolveCarrierItem());
        if (carrier == null) {
            return;
        }

        helper.forComponents(carrier)
            .addStoryBoard(
                ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1"),
                (scene, util) -> {
                    scene.title("blueprint_usage", I18n.get("ponderer.guide.blueprint.title"));
                    scene.showBasePlate();

                    Vec3 target = util.vector().centerOf(util.grid().at(2, 1, 2));
                    ItemStack carrierStack = BlueprintFeature.getCarrierStack();

                    // Step 1: first right-click to set the start position
                    scene.overlay().showText(80)
                        .text(I18n.get("ponderer.guide.blueprint.step1"))
                        .pointAt(target)
                        .placeNearTarget();
                    scene.overlay().showControls(target, Pointing.DOWN, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.idle(90);

                    // Step 2: second right-click to set the end position
                    scene.overlay().showText(80)
                        .text(I18n.get("ponderer.guide.blueprint.step2"))
                        .pointAt(target)
                        .placeNearTarget();
                    scene.overlay().showControls(target, Pointing.DOWN, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.idle(90);

                    // Step 3: ctrl+scroll to resize the selected face
                    scene.overlay().showText(80)
                        .text(I18n.get("ponderer.guide.blueprint.step3_resize"))
                        .pointAt(target)
                        .placeNearTarget();
                    scene.overlay().showControls(target, Pointing.DOWN, 40)
                        .scroll()
                        .whileCTRL()
                        .withItem(carrierStack);
                    scene.idle(90);

                    // Step 4: third right-click to open save prompt
                    scene.overlay().showText(80)
                        .text(I18n.get("ponderer.guide.blueprint.step4_save"))
                        .pointAt(target)
                        .placeNearTarget();
                    scene.overlay().showControls(target, Pointing.DOWN, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.idle(90);

                    // Step 5: shift+right-click to discard
                    scene.overlay().showText(80)
                        .text(I18n.get("ponderer.guide.blueprint.step5_discard"))
                        .pointAt(target)
                        .placeNearTarget();
                    scene.overlay().showControls(target, Pointing.DOWN, 40)
                        .rightClick()
                        .whileSneaking()
                        .withItem(carrierStack);
                    scene.idle(90);
                });
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        for (DslScene scene : SceneRuntime.getScenes()) {
            List<DslScene.SceneSegment> sceneList = normalizeScenes(scene);
            for (DslScene.SceneSegment sc : sceneList) {
                if (sc.steps == null) {
                    continue;
                }
                for (DslScene.DslStep step : sc.steps) {
                    if (step == null || step.type == null) {
                        continue;
                    }
                    if (!"shared_text".equalsIgnoreCase(step.type)) {
                        continue;
                    }
                    if (step.key == null || step.key.isBlank() || step.text == null || step.text.resolve().isBlank()) {
                        continue;
                    }
                    helper.registerSharedText(step.key, step.text.resolve());
                }
            }
        }
    }

    private void registerScene(PonderSceneRegistrationHelper<ResourceLocation> helper, DslScene scene) {
        if (scene.items == null || scene.items.isEmpty()) {
            LOGGER.warn("Scene {} has no items; skipping", scene.id);
            return;
        }

        List<ResourceLocation> components = new ArrayList<>();
        for (String itemId : scene.items) {
            ResourceLocation item = ResourceLocation.tryParse(itemId);
            if (item == null) {
                LOGGER.warn("Invalid item id {} in scene {}", itemId, scene.id);
                continue;
            }
            components.add(item);
        }

        if (components.isEmpty()) {
            LOGGER.warn("Scene {} has no valid items; skipping", scene.id);
            return;
        }

        ResourceLocation[] tags = resolveTags(scene.tags);

        List<DslScene.SceneSegment> sceneList = normalizeScenes(scene);
        List<ResourceLocation> schematics = resolveSceneSchematics(scene, sceneList);
        var multi = helper.forComponents(components);
        for (int i = 0; i < sceneList.size(); i++) {
            DslScene.SceneSegment sc = sceneList.get(i);
            if (sc.steps == null || sc.steps.isEmpty()) {
                continue;
            }
            ResourceLocation schematic = schematics.get(i);
            multi.addStoryBoard(schematic, createStoryBoard(scene, sc, i, sceneList.size()), tags);

            // Register scene in NbtSceneFilter
            ResourceLocation baseId = ResourceLocation.tryParse(scene.id);
            String basePath = baseId == null ? "scene" : baseId.getPath();
            String scenePath = sceneList.size() > 1 ? basePath + "_" + sceneSuffix(sc, i) : basePath;
            String fullSceneId = getModId() + ":" + scenePath;
            for (ResourceLocation comp : components) {
                NbtSceneFilter.registerScene(comp, fullSceneId);
            }
            if (scene.nbtFilter != null && !scene.nbtFilter.isBlank()) {
                CompoundTag nbt = NbtSceneFilter.parseNbt(scene.nbtFilter);
                if (nbt != null) {
                    NbtSceneFilter.registerFilter(fullSceneId, nbt);
                }
            }
        }
    }

    private List<ResourceLocation> resolveSceneSchematics(DslScene scene, List<DslScene.SceneSegment> sceneList) {
        List<ResourceLocation> resolved = new ArrayList<>();
        ResourceLocation current = resolveDefaultSchematic(scene);

        for (int i = 0; i < sceneList.size(); i++) {
            DslScene.SceneSegment sc = sceneList.get(i);
            String explicit = extractExplicitStructureRef(sc);
            if (explicit != null) {
                ResourceLocation next = resolveStructureReference(scene, explicit);
                if (next != null) {
                    current = next;
                } else {
                    LOGGER.warn("Invalid show_structure.structure '{}' in scene {} segment {}", explicit, scene.id, i + 1);
                }
            }
            resolved.add(current);
        }

        return resolved;
    }

    private ResourceLocation resolveDefaultSchematic(DslScene scene) {
        List<String> pool = getStructurePool(scene);
        if (!pool.isEmpty()) {
            ResourceLocation fromPool = resolveSchematic(pool.get(0));
            if (fromPool != null) {
                return fromPool;
            }
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1");
    }

    private List<String> getStructurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    private String extractExplicitStructureRef(DslScene.SceneSegment sc) {
        if (sc == null || sc.steps == null) {
            return null;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            if (!"show_structure".equalsIgnoreCase(step.type)) {
                continue;
            }
            if (step.structure != null && !step.structure.isBlank()) {
                return step.structure.trim();
            }
            return null;
        }
        return null;
    }

    private ResourceLocation resolveStructureReference(DslScene scene, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        List<String> pool = getStructurePool(scene);
        Integer parsed = tryParseInt(ref);
        if (parsed != null) {
            int index = -1;
            if (parsed >= 1 && parsed <= pool.size()) {
                index = parsed - 1;
            } else if (parsed >= 0 && parsed < pool.size()) {
                index = parsed;
            }
            if (index >= 0) {
                return resolveSchematic(pool.get(index));
            }
        }

        return resolveSchematic(ref);
    }

    private Integer tryParseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResourceLocation resolveSchematic(String structure) {
        if (structure == null || structure.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1");
        }
        if (structure.contains(":")) {
            ResourceLocation loc = ResourceLocation.tryParse(structure);
            return loc == null ? ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1") : loc;
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", structure);
    }

    private ResourceLocation[] resolveTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ResourceLocation[0];
        }
        List<ResourceLocation> result = new ArrayList<>();
        for (String tagId : tags) {
            ResourceLocation tag = ResourceLocation.tryParse(tagId);
            if (tag != null) {
                result.add(tag);
            }
        }
        return result.toArray(ResourceLocation[]::new);
    }

    private PonderStoryBoard createStoryBoard(DslScene scene, DslScene.SceneSegment sc, int index, int total) {
        return (builder, util) -> {
            ResourceLocation baseId = ResourceLocation.tryParse(scene.id);
            String basePath = baseId == null ? "scene" : baseId.getPath();
            String scenePath = total > 1 ? basePath + "_" + sceneSuffix(sc, index) : basePath;

            String title = sc.title != null ? sc.title.resolve() : null;
            if (title == null || title.isBlank()) {
                String sceneTitle = scene.title != null ? scene.title.resolve() : null;
                if (sceneTitle == null || sceneTitle.isBlank()) {
                    title = scenePath;
                } else {
                    title = total > 1 ? sceneTitle + " #" + (index + 1) : sceneTitle;
                }
            }
            builder.title(scenePath, title);

            if (sc.steps == null) {
                return;
            }

            if (!hasShowStructure(sc)) {
                applyShowStructure(builder, new DslScene.DslStep());
            }

            StepContext context = new StepContext();

            for (DslScene.DslStep step : sc.steps) {
                if (step == null || step.type == null) {
                    continue;
                }
                if ("next_scene".equalsIgnoreCase(step.type)) {
                    continue;
                }
                applyStep(builder, util, scene, step, context);
            }
        };
    }

    private void applyStep(SceneBuilder scene, SceneBuildingUtil util, DslScene dsl, DslScene.DslStep step, StepContext context) {
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            scene.addKeyframe();
        }
        switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" -> applyShowStructure(scene, step);
            case "idle" -> scene.idle(step.durationOrDefault(20));
            case "text" -> applyText(scene, step);
            case "shared_text" -> applySharedText(scene, step);
            case "create_entity" -> applyCreateEntity(scene, step);
            case "create_item_entity" -> applyCreateItemEntity(scene, step);
            case "rotate_camera_y" -> applyRotateCameraY(scene, step);
            case "show_controls" -> applyShowControls(scene, step);
            case "encapsulate_bounds" -> applyEncapsulateBounds(scene, step);
            case "play_sound" -> applyPlaySound(scene, step);
            case "set_block" -> applySetBlock(scene, step);
            case "destroy_block" -> applyDestroyBlock(scene, step);
            case "replace_blocks" -> applyReplaceBlocks(scene, step);
            case "hide_section" -> applyHideSection(scene, step);
            case "show_section_and_merge" -> applyShowSectionAndMerge(scene, step, context);
            case "rotate_section" -> applyRotateSection(scene, step, context);
            case "move_section" -> applyMoveSection(scene, step, context);
            case "toggle_redstone_power" -> applyToggleRedstonePower(scene, step);
            case "modify_block_entity_nbt" -> applyModifyBlockEntityNbt(scene, step);
            case "indicate_redstone" -> applyIndicateRedstone(scene, step);
            case "indicate_success" -> applyIndicateSuccess(scene, step);
            case "next_scene" -> {
            }
            default -> LOGGER.warn("Unknown step type '{}' in scene {}", step.type, dsl.id);
        }
    }

    private void applyText(SceneBuilder scene, DslScene.DslStep step) {
        String text = step.text == null ? "" : step.text.resolve();
        Vec3 point = toPoint(step.point);
        int duration = step.durationOrDefault(60);

        TextElementBuilder builder = scene.overlay()
            .showText(duration)
            .text(text)
            .pointAt(point);

        PonderPalette palette = parsePalette(step.color);
        if (palette != null) {
            builder.colored(palette);
        }

        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            builder.placeNearTarget();
        }
    }

    private void applySharedText(SceneBuilder scene, DslScene.DslStep step) {
        String key = step.key;
        if (key == null || key.isBlank()) {
            LOGGER.warn("shared_text missing key");
            return;
        }
        ResourceLocation loc = key.contains(":")
            ? ResourceLocation.tryParse(key)
            : ResourceLocation.fromNamespaceAndPath(scene.getScene().getNamespace(), key);
        if (loc == null) {
            LOGGER.warn("shared_text invalid key: {}", key);
            return;
        }

        Vec3 point = toPoint(step.point);
        int duration = step.durationOrDefault(60);
        TextElementBuilder builder = scene.overlay().showText(duration).sharedText(loc).pointAt(point);

        PonderPalette palette = parsePalette(step.color);
        if (palette != null) {
            builder.colored(palette);
        }
        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            builder.placeNearTarget();
        }
    }

    private void applyCreateEntity(SceneBuilder scene, DslScene.DslStep step) {
        ResourceLocation entityId = step.entity == null ? null : ResourceLocation.tryParse(step.entity);
        if (entityId == null) {
            LOGGER.warn("create_entity missing/invalid entity id");
            return;
        }

        Vec3 pos = toPoint(step.pos != null ? step.pos : step.point);
        scene.world().createEntity((Level level) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
            if (type == null) {
                LOGGER.warn("Unknown entity type: {}", entityId);
                return null;
            }
            Entity entity = type.create(level);
            if (entity != null) {
                entity.setPosRaw(pos.x, pos.y, pos.z);
                entity.setOldPosAndRot();
                Vec3 lookAt = step.lookAt != null && step.lookAt.size() >= 3
                    ? new Vec3(step.lookAt.get(0), step.lookAt.get(1), step.lookAt.get(2))
                    : pos.add(0, 0, -1);
                entity.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET, lookAt);

                if (step.yaw != null) {
                    entity.setYRot(step.yaw);
                    entity.setYHeadRot(step.yaw);
                    entity.setYBodyRot(step.yaw);
                }
                if (step.pitch != null) {
                    entity.setXRot(step.pitch);
                }

                // Always disable AI and gravity for ponder entities
                if (entity instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setNoAi(true);
                }
                entity.setNoGravity(true);

                entity.setDeltaMovement(Vec3.ZERO);
            }
            return entity;
        });
    }

    private void applyCreateItemEntity(SceneBuilder scene, DslScene.DslStep step) {
        if (step.item == null || step.item.isBlank()) {
            LOGGER.warn("create_item_entity missing item id");
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(step.item);
        Item item = itemId == null ? null : BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            LOGGER.warn("create_item_entity unknown item: {}", step.item);
            return;
        }

        Vec3 pos = toPoint(step.pos != null ? step.pos : step.point);
        Vec3 motion = toPoint(step.motion);
        int count = step.count == null ? 1 : Math.max(1, step.count);

        scene.world().createItemEntity(pos, motion, new ItemStack(item, count));
    }

    private void applyRotateCameraY(SceneBuilder scene, DslScene.DslStep step) {
        float degrees = step.degrees == null ? 90f : step.degrees;
        scene.rotateCameraY(degrees);
    }

    private void applyShowControls(SceneBuilder scene, DslScene.DslStep step) {
        Vec3 point = toPoint(step.point);
        Pointing pointing = parsePointing(step.direction);
        int duration = step.durationOrDefault(60);

        InputElementBuilder builder = scene.overlay().showControls(point, pointing, duration);

        switch (step.action == null ? "" : step.action.toLowerCase(Locale.ROOT)) {
            case "left" -> builder.leftClick();
            case "right" -> builder.rightClick();
            case "scroll" -> builder.scroll();
            default -> {
            }
        }

        if (step.item != null && !step.item.isBlank()) {
            ResourceLocation itemId = ResourceLocation.tryParse(step.item);
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item != null) {
                builder.withItem(new ItemStack(item));
            }
        }

        if (Boolean.TRUE.equals(step.whileSneaking)) {
            builder.whileSneaking();
        }
        if (Boolean.TRUE.equals(step.whileCTRL)) {
            builder.whileCTRL();
        }
    }

    private void applyShowStructure(SceneBuilder scene, DslScene.DslStep step) {
        if (step.height != null && step.height >= 0) {
            var selection = scene.getScene().getSceneBuildingUtil().select().layersFrom(step.height);
            scene.world().showSection(selection, Direction.UP);
        } else {
            var selection = scene.getScene().getSceneBuildingUtil().select().everywhere();
            scene.world().showSection(selection, Direction.UP);
        }
    }

    private void applyEncapsulateBounds(SceneBuilder scene, DslScene.DslStep step) {
        if (step.bounds == null || step.bounds.size() < 3) {
            LOGGER.warn("encapsulate_bounds missing bounds");
            return;
        }
        BlockPos size = new BlockPos(step.bounds.get(0), step.bounds.get(1), step.bounds.get(2));
        scene.addInstruction(ps -> ps.getWorld().getBounds().encapsulate(size));
    }

    private void applyPlaySound(SceneBuilder scene, DslScene.DslStep step) {
        if (step.sound == null || step.sound.isBlank()) {
            LOGGER.warn("play_sound missing sound id");
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(step.sound);
        if (id == null) {
            LOGGER.warn("play_sound invalid sound id: {}", step.sound);
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) {
            LOGGER.warn("play_sound unknown sound: {}", id);
            return;
        }
        float volume = step.soundVolume == null ? 1.0f : step.soundVolume;
        float pitch = step.pitch == null ? 1.0f : step.pitch;
        SoundSource source = parseSoundSource(step.source);

        scene.addInstruction(ps -> {
            if (Minecraft.getInstance().player == null) {
                return;
            }
            var soundInstance = new SimpleSoundInstance(sound, source, volume, pitch,
                SoundInstance.createUnseededRandom(), Minecraft.getInstance().player.blockPosition());
            Minecraft.getInstance().getSoundManager().play(soundInstance);
        });
    }

    private void applySetBlock(SceneBuilder scene, DslScene.DslStep step) {
        if (step.block == null || step.block.isBlank()) {
            LOGGER.warn("set_block missing block id");
            return;
        }
        ResourceLocation blockId = ResourceLocation.tryParse(step.block);
        if (blockId == null) {
            LOGGER.warn("set_block invalid block id: {}", step.block);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            LOGGER.warn("set_block unknown block: {}", blockId);
            return;
        }
        BlockState state = block.defaultBlockState();
        BlockPos pos;
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            pos = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        } else {
            LOGGER.warn("set_block missing blockPos");
            return;
        }
        boolean particles = !Boolean.FALSE.equals(step.spawnParticles);
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            BlockPos pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
            var selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos, pos2);
            scene.world().setBlocks(selection, state, particles);
            return;
        }
        scene.world().setBlock(pos, state, particles);
    }

    private void applyDestroyBlock(SceneBuilder scene, DslScene.DslStep step) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("destroy_block missing blockPos");
            return;
        }
        BlockPos pos = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        boolean particles = !Boolean.FALSE.equals(step.destroyParticles);
        if (particles) {
            scene.world().destroyBlock(pos);
            return;
        }
        scene.world().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
    }

    private void applyReplaceBlocks(SceneBuilder scene, DslScene.DslStep step) {
        if (step.block == null || step.block.isBlank()) {
            LOGGER.warn("replace_blocks missing block id");
            return;
        }
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("replace_blocks missing blockPos");
            return;
        }
        ResourceLocation blockId = ResourceLocation.tryParse(step.block);
        if (blockId == null) {
            LOGGER.warn("replace_blocks invalid block id: {}", step.block);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            LOGGER.warn("replace_blocks unknown block: {}", blockId);
            return;
        }

        BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        BlockPos pos2 = pos1;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        boolean particles = !Boolean.FALSE.equals(step.spawnParticles);
        var selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        scene.world().replaceBlocks(selection, block.defaultBlockState(), particles);
    }

    private void applyHideSection(SceneBuilder scene, DslScene.DslStep step) {
        Selection selection = selectionFromStep(scene, step, "hide_section");
        if (selection == null) {
            return;
        }
        scene.world().hideSection(selection, parseDirection(step.direction));
    }

    private void applyShowSectionAndMerge(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        Selection selection = selectionFromStep(scene, step, "show_section_and_merge");
        if (selection == null) {
            return;
        }
        String linkId = step.linkId == null || step.linkId.isBlank() ? "default" : step.linkId;
        Direction direction = parseDirection(step.direction);
        ElementLink<WorldSectionElement> existing = context.sectionLinks.get(linkId);
        if (existing == null) {
            ElementLink<WorldSectionElement> created = scene.world().showIndependentSection(selection, direction);
            context.sectionLinks.put(linkId, created);
            return;
        }
        scene.world().showSectionAndMerge(selection, direction, existing);
    }

    private void applyRotateSection(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        String linkId = step.linkId == null || step.linkId.isBlank() ? "default" : step.linkId;
        ElementLink<WorldSectionElement> link = context.sectionLinks.get(linkId);
        if (link == null) {
            LOGGER.warn("rotate_section missing linkId: {}", linkId);
            return;
        }
        double x = step.rotX == null ? 0.0 : step.rotX;
        double y = step.rotY == null ? (step.degrees == null ? 0.0 : step.degrees) : step.rotY;
        double z = step.rotZ == null ? 0.0 : step.rotZ;
        int duration = step.durationOrDefault(20);
        scene.world().rotateSection(link, x, y, z, duration);
    }

    private void applyMoveSection(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        String linkId = step.linkId == null || step.linkId.isBlank() ? "default" : step.linkId;
        ElementLink<WorldSectionElement> link = context.sectionLinks.get(linkId);
        if (link == null) {
            LOGGER.warn("move_section missing linkId: {}", linkId);
            return;
        }
        Vec3 offset = toPoint(step.offset);
        int duration = step.durationOrDefault(20);
        scene.world().moveSection(link, offset, duration);
    }

    private void applyToggleRedstonePower(SceneBuilder scene, DslScene.DslStep step) {
        Selection selection = selectionFromStep(scene, step, "toggle_redstone_power");
        if (selection == null) {
            return;
        }
        scene.world().toggleRedstonePower(selection);
    }

    private void applyModifyBlockEntityNbt(SceneBuilder scene, DslScene.DslStep step) {
        Selection selection = selectionFromStep(scene, step, "modify_block_entity_nbt");
        if (selection == null) {
            return;
        }
        if (step.nbt == null || step.nbt.isBlank()) {
            LOGGER.warn("modify_block_entity_nbt missing nbt");
            return;
        }
        CompoundTag patch;
        try {
            patch = TagParser.parseTag(step.nbt);
        } catch (Exception e) {
            LOGGER.warn("modify_block_entity_nbt invalid nbt: {}", step.nbt);
            return;
        }
        boolean redraw = Boolean.TRUE.equals(step.reDrawBlocks);
        scene.world().modifyBlockEntityNBT(selection, BlockEntity.class, nbt -> nbt.merge(patch.copy()), redraw);
    }

    private void applyIndicateRedstone(SceneBuilder scene, DslScene.DslStep step) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("indicate_redstone missing blockPos");
            return;
        }
        BlockPos pos = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        scene.effects().indicateRedstone(pos);
    }

    private void applyIndicateSuccess(SceneBuilder scene, DslScene.DslStep step) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("indicate_success missing blockPos");
            return;
        }
        BlockPos pos = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        scene.effects().indicateSuccess(pos);
    }

    private Selection selectionFromStep(SceneBuilder scene, DslScene.DslStep step, String stepName) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("{} missing blockPos", stepName);
            return null;
        }
        BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        BlockPos pos2 = pos1;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        return scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
    }

    private Direction parseDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return Direction.DOWN;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "up" -> Direction.UP;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "east" -> Direction.EAST;
            default -> Direction.DOWN;
        };
    }

    private Vec3 toPoint(List<Double> point) {
        if (point == null || point.size() < 3) {
            return new Vec3(2.5, 1.5, 2.5);
        }
        return new Vec3(point.get(0), point.get(1), point.get(2));
    }

    private Pointing parsePointing(String raw) {
        if (raw == null || raw.isBlank()) {
            return Pointing.DOWN;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "up" -> Pointing.UP;
            case "left" -> Pointing.LEFT;
            case "right" -> Pointing.RIGHT;
            default -> Pointing.DOWN;
        };
    }

    private SoundSource parseSoundSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return SoundSource.MASTER;
        }
        try {
            return SoundSource.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return SoundSource.MASTER;
        }
    }

    private String extractScenePath(String id) {
        if (id == null || id.isBlank()) {
            return "scene";
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? id : parsed.getPath();
    }

    private List<DslScene.SceneSegment> normalizeScenes(DslScene scene) {
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            return scene.scenes;
        }
        List<DslScene.SceneSegment> sceneList = new ArrayList<>();
        DslScene.SceneSegment current = new DslScene.SceneSegment();
        current.steps = new ArrayList<>();

        if (scene.steps != null) {
            for (DslScene.DslStep step : scene.steps) {
                if (step != null && step.type != null && "next_scene".equalsIgnoreCase(step.type)) {
                    if (!current.steps.isEmpty()) {
                        sceneList.add(current);
                    }
                    current = new DslScene.SceneSegment();
                    current.steps = new ArrayList<>();
                    continue;
                }
                current.steps.add(step);
            }
        }

        if (!current.steps.isEmpty()) {
            sceneList.add(current);
        }

        if (sceneList.isEmpty()) {
            DslScene.SceneSegment fallback = new DslScene.SceneSegment();
            fallback.steps = List.of();
            sceneList.add(fallback);
        }
        return sceneList;
    }

    private String sceneSuffix(DslScene.SceneSegment sc, int index) {
        if (sc.id != null && !sc.id.isBlank()) {
            return sc.id;
        }
        return String.valueOf(index + 1);
    }

    private boolean hasShowStructure(DslScene.SceneSegment sc) {
        if (sc.steps == null) {
            return false;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            if ("show_structure".equalsIgnoreCase(step.type)) {
                return true;
            }
        }
        return false;
    }

    private PonderPalette parsePalette(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "white" -> PonderPalette.WHITE;
            case "black" -> PonderPalette.BLACK;
            case "red" -> PonderPalette.RED;
            case "green" -> PonderPalette.GREEN;
            case "blue" -> PonderPalette.BLUE;
            case "input" -> PonderPalette.INPUT;
            case "output" -> PonderPalette.OUTPUT;
            case "slow" -> PonderPalette.SLOW;
            case "medium" -> PonderPalette.MEDIUM;
            case "fast" -> PonderPalette.FAST;
            default -> null;
        };
    }
}