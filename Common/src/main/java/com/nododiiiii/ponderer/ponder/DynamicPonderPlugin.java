package com.nododiiiii.ponderer.ponder;

import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.blueprint.BlueprintFeature;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import com.nododiiiii.ponderer.registry.ModItems;
import com.nododiiiii.ponderer.ui.UiAnchorCoords;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
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
import net.createmod.ponder.foundation.instruction.DisplayWorldSectionInstruction;
import net.createmod.ponder.foundation.instruction.FadeOutOfSceneInstruction;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DynamicPonderPlugin implements PonderPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static class StepContext {
        final Map<String, ElementLink<WorldSectionElement>> sectionLinks = new HashMap<>();
        final Set<Long> visibleBlockKeys = new java.util.HashSet<>();
        final Set<Long> hiddenBlockKeys = new java.util.HashSet<>();
        boolean allBlocksVisible;
        boolean uiAnchorMode;
    }

    @Override
    public String getModId() {
        return "ponderer";
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        NbtSceneFilter.clear();
        SceneRuntime.clearPonderIdMapping();
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
                new ResourceLocation("ponderer", "basic"),
                (scene, util) -> {
                    scene.title("blueprint_usage", I18n.get("ponderer.guide.blueprint.title"));
                    scene.showBasePlate();

                    scene.addKeyframe();
                    scene.idle(20);

                    Vec3 firstPoint = new Vec3(1.5, 1.0, 1.5);
                    Vec3 secondPoint = new Vec3(3.5, 4.0, 3.5);
                    Vec3 resizePoint = new Vec3(2.5, 2.5, 1.5);
                    Selection firstSelection = util.select().fromTo(1, 1, 1, 3, 3, 3);
                    Selection resizedSelection = util.select().fromTo(1, 1, 2, 3, 3, 3);
                    ItemStack carrierStack = BlueprintFeature.getCarrierStack();

                    scene.overlay().showControls(firstPoint, Pointing.DOWN, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step1"))
                        .pointAt(firstPoint)
                        .placeNearTarget();
                    scene.idle(50);

                    scene.overlay().showControls(secondPoint, Pointing.UP, 40)
                        .rightClick()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step2"))
                        .pointAt(secondPoint)
                        .placeNearTarget();
                    scene.overlay().showOutline(PonderPalette.BLUE, new Object(), firstSelection, 50);
                    scene.idle(50);

                    scene.overlay().showControls(resizePoint, Pointing.RIGHT, 40)
                        .scroll()
                        .whileCTRL()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step3_resize"))
                        .pointAt(resizePoint)
                        .placeNearTarget();
                    scene.overlay().showOutline(PonderPalette.BLUE, new Object(), resizedSelection, 50);
                    scene.idle(50);

                    scene.overlay().showControls(resizePoint, Pointing.RIGHT, 40)
                        .rightClick()
                        .whileSneaking()
                        .withItem(carrierStack);
                    scene.addKeyframe();
                    scene.overlay().showText(40)
                        .text(I18n.get("ponderer.guide.blueprint.step5_discard"))
                        .pointAt(resizePoint)
                        .placeNearTarget();
                    scene.overlay().showOutline(PonderPalette.RED, new Object(), resizedSelection, 40);
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

            // Register mapping from PonderScene ID to DslScene sceneKey for pack disambiguation
            SceneRuntime.registerPonderIdMapping(fullSceneId, scene.sceneKey(), i);
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
        return new ResourceLocation("ponder", "debug/scene_1");
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
            return new ResourceLocation("ponder", "debug/scene_1");
        }
        if (structure.contains(":")) {
            ResourceLocation loc = ResourceLocation.tryParse(structure);
            return loc == null ? new ResourceLocation("ponder", "debug/scene_1") : loc;
        }
        return new ResourceLocation("ponder", structure);
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
            try {
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

                StepContext context = new StepContext();
                if (firstStepIsShowInterface(sc)) {
                    builder.removeShadow();
                }

                if (!firstStepIsShowStructure(sc)) {
                    applyShowStructure(builder, new DslScene.DslStep(), context);
                    builder.idle(20);
                }

                for (DslScene.DslStep step : sc.steps) {
                    if (step == null || step.type == null) {
                        continue;
                    }
                    if ("next_scene".equalsIgnoreCase(step.type)) {
                        continue;
                    }
                    applyStep(builder, util, scene, step, context);
                }
            } catch (Exception e) {
                LOGGER.error("Error building ponder storyboard for scene {} segment {}: {}", scene.id, index, e.getMessage(), e);
            }
        };
    }

    private void applyStep(SceneBuilder scene, SceneBuildingUtil util, DslScene dsl, DslScene.DslStep step, StepContext context) {
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            scene.addKeyframe();
        }
        switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" -> applyShowStructure(scene, step, context);
            case "idle" -> scene.idle(step.durationOrDefault(20));
            case "text" -> applyText(scene, step, context);
            case "shared_text" -> applySharedText(scene, step, context);
            case "create_entity" -> applyCreateEntity(scene, step);
            case "create_item_entity" -> applyCreateItemEntity(scene, step);
            case "rotate_camera_y" -> applyRotateCameraY(scene, step);
            case "zoom_scene" -> applyZoomScene(scene, step);
            case "highlight_section" -> applyHighlightSection(scene, step);
            case "show_controls" -> applyShowControls(scene, step, context);
            case "show_interface" -> applyShowInterface(scene, step, context);
            case "change_interface_slot" -> applyChangeInterfaceSlot(scene, step);
            case "click_interface" -> applyClickInterface(scene, step);
            case "encapsulate_bounds" -> applyEncapsulateBounds(scene, step);
            case "play_sound" -> applyPlaySound(scene, step);
            case "set_block" -> applySetBlock(scene, step, context);
            case "destroy_block" -> applyDestroyBlock(scene, step, context);
            case "replace_blocks" -> applyReplaceBlocks(scene, step, context);
            case "hide_section" -> applyHideSection(scene, step, context);
            case "show_section_and_merge" -> applyShowSectionAndMerge(scene, step, context);
            case "rotate_section" -> applyRotateSection(scene, step, context);
            case "move_section" -> applyMoveSection(scene, step, context);
            case "toggle_redstone_power" -> applyToggleRedstonePower(scene, step);
            case "modify_block_entity_nbt" -> applyModifyBlockEntityNbt(scene, step);
            case "indicate_redstone" -> applyIndicateRedstone(scene, step);
            case "indicate_success" -> applyIndicateSuccess(scene, step);
            case "clear_entities" -> applyClearEntities(scene, step);
            case "clear_item_entities" -> applyClearItemEntities(scene, step);
            case "modify_entities_nbt" -> applyModifyEntitiesNbt(scene, step);
            case "modify_item_entities_nbt" -> applyModifyItemEntitiesNbt(scene, step);
            case "next_scene" -> {
            }
            default -> LOGGER.warn("Unknown step type '{}' in scene {}", step.type, dsl.id);
        }
    }

    private void applyText(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        String text = step.text == null ? "" : step.text.resolve();
        Vec3 point = resolveOverlayPoint(scene, step, context);
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

    private void applySharedText(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        String key = step.key;
        if (key == null || key.isBlank()) {
            LOGGER.warn("shared_text missing key");
            return;
        }
        ResourceLocation loc = key.contains(":")
            ? ResourceLocation.tryParse(key)
            : new ResourceLocation(scene.getScene().getNamespace(), key);
        if (loc == null) {
            LOGGER.warn("shared_text invalid key: {}", key);
            return;
        }

        Vec3 point = resolveOverlayPoint(scene, step, context);
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

                if (step.nbt != null && !step.nbt.isBlank()) {
                    try {
                        CompoundTag patch = TagParser.parseTag(step.nbt);
                        CompoundTag data = new CompoundTag();
                        entity.saveWithoutId(data);
                        data.merge(patch);
                        entity.load(data);
                    } catch (Exception e) {
                        LOGGER.warn("create_entity invalid nbt: {}", step.nbt);
                    }
                }
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

        CompoundTag patch = null;
        if (step.nbt != null && !step.nbt.isBlank()) {
            try {
                patch = TagParser.parseTag(step.nbt);
            } catch (Exception e) {
                LOGGER.warn("create_item_entity invalid nbt: {}", step.nbt);
            }
        }

        final CompoundTag finalPatch = patch;
        scene.world().createEntity((Level level) -> {
            ItemStack stack = new ItemStack(item, count);
            if (finalPatch != null) {
                stack = applyPatchToItemStack(stack, finalPatch);
            }

            ItemEntity entity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
            entity.setDeltaMovement(motion);
            if (finalPatch != null && isLikelyEntityPatch(finalPatch)) {
                CompoundTag data = new CompoundTag();
                entity.saveWithoutId(data);
                data.merge(finalPatch.copy());
                entity.load(data);
            }
            return entity;
        });
    }

    private void applyRotateCameraY(SceneBuilder scene, DslScene.DslStep step) {
        float degrees = step.degrees == null ? 90f : step.degrees;
        int duration = step.durationOrDefault(20);
        scene.addInstruction(ponderScene -> {
            var yRotation = ponderScene.getTransform().yRotation;
            float target = yRotation.getChaseTarget() + degrees;
            if (duration == 0) {
                yRotation.startWithValue(target);
            } else {
                yRotation.chaseTimed(target, duration);
            }
        });
        if (duration > 0) {
            scene.idle(duration);
        }
    }

    private void applyZoomScene(SceneBuilder scene, DslScene.DslStep step) {
        float multiplier = step.scale == null ? Float.NaN : step.scale;
        if (multiplier <= 0) {
            LOGGER.warn("zoom_scene scale must be > 0, got {}", multiplier);
            return;
        }

        boolean useDefaultCenter = step.point == null || step.point.size() < 3;
        Vec3 center = useDefaultCenter ? Vec3.ZERO : toPoint(step.point);
        int duration = step.durationOrDefault(20);
        scene.addInstruction(new AdjustViewInstruction(center, useDefaultCenter, multiplier, duration));
    }

    private void applyHighlightSection(SceneBuilder scene, DslScene.DslStep step) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("highlight_section missing blockPos");
            return;
        }
        BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        BlockPos pos2 = pos1;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        int duration = step.durationOrDefault(40);
        PonderPalette palette = parsePalette(step.color);
        if (palette == null) palette = PonderPalette.BLUE;
        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        scene.overlay().showOutline(palette, new Object(), selection, duration);
    }

    private void applyShowControls(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        Vec3 point = resolveOverlayPoint(scene, step, context);
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
            resolveIngredient(builder, step.item, step.nbt);
        }

        if (Boolean.TRUE.equals(step.whileSneaking)) {
            builder.whileSneaking();
        }
        if (Boolean.TRUE.equals(step.whileCTRL)) {
            builder.whileCTRL();
        }
    }

    private void applyShowInterface(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        if (step.block == null || step.block.isBlank()) {
            LOGGER.warn("show_interface missing block id");
            return;
        }

        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("show_interface missing block context position");
            return;
        }

        PondererServices.PLATFORM.closeInterfaceStep("replace-with-show_interface");
        InterfaceSlotOverlayRenderer.clearRuntimeBindings();
        // Build-time pointAt() conversion for UI-anchored overlays happens after this step.
        // Reset immediately so subsequent resolveOverlayPoint() uses a clean baseline.
        ponderer$resetSceneViewState(scene.getScene());
        // Keep a runtime reset as a safety net for replay/scene lifecycle paths.
        scene.addInstruction(this::ponderer$resetSceneViewState);
        scene.addInstruction(new ShowInterfaceInstruction(step));
        context.uiAnchorMode = true;
    }

    private void applyClickInterface(SceneBuilder scene, DslScene.DslStep step) {
        if (step.pos == null || step.pos.size() < 2) {
            LOGGER.warn("click_interface missing point");
            return;
        }
        if (step.action == null || step.action.isBlank()) {
            LOGGER.warn("click_interface missing action");
            return;
        }
        scene.addInstruction(new ClickInterfaceInstruction(step));
    }

    private void applyChangeInterfaceSlot(SceneBuilder scene, DslScene.DslStep step) {
        if (step.interfaceSlots == null || step.interfaceSlots.isEmpty()) {
            LOGGER.warn("change_interface_slot missing interfaceSlots");
            return;
        }
        scene.addInstruction(new ChangeInterfaceSlotInstruction(step));
    }

    private void ponderer$resetSceneViewState(net.createmod.ponder.foundation.PonderScene ps) {
        if (!(ps instanceof PonderSceneViewOffsetAccess viewOffset)) {
            return;
        }

        viewOffset.ponderer$resetViewOffset();
        float defaultScale = viewOffset.ponderer$getDefaultScale();
        if (ps instanceof PonderSceneAccessor accessor && !Float.isNaN(defaultScale)) {
            accessor.ponderer$setScaleFactor(defaultScale);
        }
        viewOffset.ponderer$setDefaultScale(Float.NaN);
    }

    /**
     * Resolve an ingredient ID and apply it to the builder.
     * For items: use withItem() so the item renders alongside the action icon (LMB/RMB/Scroll).
     * For non-items (fluids, chemicals, etc.): use showing() via JEI renderer, which replaces the icon slot.
     * This distinction is critical because showing() overwrites the icon field (leftClick/rightClick/scroll),
     * while withItem() uses a separate item field that renders alongside the icon.
     */
    private void resolveIngredient(InputElementBuilder builder, String id, @Nullable String nbt) {
        // 1. Try parsing as item stack spec first (supports optional NBT)
        ItemStack stack = parseItemStackSpec(id, nbt);
        if (stack != null) {
            builder.withItem(stack);
            return;
        }

        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return;

        // 2. Try fluid registry (requires JEI for rendering)
        net.minecraft.world.level.material.Fluid fluid =
                BuiltInRegistries.FLUID.getOptional(loc).orElse(null);
        if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
            if (JeiCompat.isAvailable()) {
                // Use JEI ingredient helper to create a fluid element in a platform-agnostic way
                net.createmod.catnip.gui.element.ScreenElement element =
                        JeiCompat.createFluidIngredientElement(fluid, 1000);
                if (element != null) {
                    builder.showing(element);
                    return;
                }
            }
            LOGGER.warn("show_controls: fluid '{}' found but JEI is not available for rendering", id);
            return;
        }

        // 3. Fallback: search all JEI ingredient types (chemicals, etc.)
        if (JeiCompat.isAvailable()) {
            net.createmod.catnip.gui.element.ScreenElement element =
                    JeiCompat.resolveIngredientById(id);
            if (element != null) {
                builder.showing(element);
                return;
            }
        }

        LOGGER.warn("show_controls: unable to resolve ingredient '{}'", id);
    }

    @Nullable
    private ItemStack parseItemStackSpec(String raw, @Nullable String nbtOverride) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        String itemIdPart = trimmed;
        String nbtPart = null;

        int brace = trimmed.indexOf('{');
        if (brace >= 0) {
            itemIdPart = trimmed.substring(0, brace).trim();
            nbtPart = trimmed.substring(brace).trim();
        }

        ResourceLocation itemLoc = ResourceLocation.tryParse(itemIdPart);
        if (itemLoc == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemLoc).orElse(null);
        if (item == null) {
            return null;
        }

        ItemStack stack = new ItemStack(item);
        String finalNbtPart = (nbtOverride != null && !nbtOverride.isBlank()) ? nbtOverride.trim() : nbtPart;
        if (finalNbtPart != null && !finalNbtPart.isBlank() && !"{}".equals(finalNbtPart)) {
            try {
                CompoundTag tag = TagParser.parseTag(finalNbtPart);
                if (!tag.isEmpty()) {
                    stack.setTag(tag);
                }
            } catch (Exception e) {
                LOGGER.warn("show_controls invalid item nbt: {}", finalNbtPart);
            }
        }
        return stack;
    }

    private void applyShowStructure(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        PondererServices.PLATFORM.closeInterfaceStep("replace-with-show_structure");
        context.uiAnchorMode = false;
        Selection selection;
        boolean isEverywhere;
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
            BlockPos pos2 = pos1;
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
            }
            selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
            isEverywhere = false;
        } else {
            // Default behavior: show full structure when no region is provided.
            selection = scene.getScene().getSceneBuildingUtil().select().everywhere();
            isEverywhere = true;
        }
        scene.world().showSection(selection, Direction.UP);
        if (isEverywhere) {
            context.allBlocksVisible = true;
            context.visibleBlockKeys.clear();
            context.hiddenBlockKeys.clear();
        } else {
            updateVisibleRange(context, step, true);
        }
        if (step.scale != null) {
            scene.scaleSceneView(step.scale);
        }
        final float rotationOffset = step.rotation == null ? 0f : step.rotation;
        scene.addInstruction(ps -> {
            if (ps instanceof PonderSceneAccessor accessor && ps instanceof PonderSceneViewOffsetAccess viewOffset) {
                viewOffset.ponderer$setDefaultScale(accessor.ponderer$getScaleFactor());
            }
            if (rotationOffset != 0f) {
                var yRotation = ps.getTransform().yRotation;
                float target = yRotation.getChaseTarget() + rotationOffset;
                yRotation.startWithValue(target);
            }
        });
    }

    private Vec3 resolveOverlayPoint(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        if (!context.uiAnchorMode) {
            return toPoint(step.point);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || scene.getScene() == null) {
            return toPoint(step.point);
        }

        double u = 0.0;
        double v = 0.0;
        if (step.point != null && !step.point.isEmpty()) {
            u = step.point.get(0);
            if (step.point.size() >= 2) {
                v = step.point.get(1);
            }
        }

        // Shared conversion with picker: centered UI anchor is canonical,
        // where (0,0) is the UI center.
        int guiW = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());
        UiAnchorViewport.Rect viewport = UiAnchorViewport.resolve(mc);
        double xTopLeft = viewport.left() + UiAnchorCoords.decodeToPixelX(u, (int) Math.max(1, viewport.width()));
        double yTopLeft = viewport.top() + UiAnchorCoords.decodeToPixelYTopLeft(v, (int) Math.max(1, viewport.height()));
        double screenX = UiAnchorCoords.topLeftToTransformX(xTopLeft, guiW);
        double screenY = UiAnchorCoords.topLeftToTransformY(yTopLeft, guiH);
        return scene.getScene().getTransform().screenToScene(screenX, screenY, 0, 0);
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

    private void applySetBlock(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
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
        state = applyBlockProperties(state, step);
        BlockPos pos;
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            pos = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        } else {
            LOGGER.warn("set_block missing blockPos");
            return;
        }
        boolean immediateDisplay = !Boolean.FALSE.equals(step.immediateDisplay);
        boolean particles = immediateDisplay && !Boolean.FALSE.equals(step.spawnParticles);
        BlockPos pos2 = pos;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }

        String entranceAnimation = normalizeEntranceAnimation(step.entranceAnimation);
        if (entranceAnimation != null && !"none".equals(entranceAnimation)) {
            applyAnimatedSetBlock(scene, step, context, state, pos, pos2, entranceAnimation);
            return;
        }

        ensureSceneCanShowRange(scene, pos, pos2, immediateDisplay);
        updateVisibleRange(context, step, immediateDisplay);
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            var selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos, pos2);
            scene.world().setBlocks(selection, state, particles);
            applySetBlockNbtPatch(scene, step, selection);
            return;
        }
        scene.world().setBlock(pos, state, particles);
        applySetBlockNbtPatch(scene, step, scene.getScene().getSceneBuildingUtil().select().position(pos));
    }

    private void applyAnimatedSetBlock(SceneBuilder scene, DslScene.DslStep step, StepContext context, BlockState state,
                                       BlockPos pos1, BlockPos pos2, String entranceAnimation) {
        // Equivalent flow: hidden set_block, then animated show_section_and_merge reveal in one step.
        ensureSceneCanShowRange(scene, pos1, pos2, false);

        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        scene.world().setBlocks(selection, state, false);
        applySetBlockNbtPatch(scene, step, selection);

        String linkId = step.linkId == null ? "" : step.linkId.trim();
        if (linkId.isEmpty()) {
            linkId = autoLinkId(context);
        }
        Direction direction = parseDirection(step.direction);
        ElementLink<WorldSectionElement> existing = context.sectionLinks.get(linkId);
        int rowDuration = step.entranceDuration == null ? 20 : Math.max(0, step.entranceDuration);
        int rowInterval = step.entranceInterval == null ? 1 : Math.max(0, step.entranceInterval);
        boolean smartDisplay = !Boolean.FALSE.equals(step.smartDisplay);
        applyAnimatedShowSectionAndMerge(scene, context, linkId, existing, pos1, pos2,
                entranceAnimation, direction, rowDuration, rowInterval, smartDisplay);
        updateVisibleRange(context, step, true);
    }

    /**
     * Ensure scene bounds and the base visible world section cover the target range.
     * This prevents newly placed blocks from being clipped when initial structure height is too small.
     * Only Y limit is expanded; XZ bounds are intentionally left unchanged.
     */
    private void ensureSceneCanShowRange(SceneBuilder scene, BlockPos pos1, BlockPos pos2, boolean forceVisibleNow) {
        int maxY = Math.max(pos1.getY(), pos2.getY());

        // Bounds in ponder are effectively size-based for this use, so +1 keeps the max block included.
        BlockPos requiredBounds = new BlockPos(0, maxY + 1, 0);
        Selection targetSelection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);

        scene.addInstruction(ps -> {
            ps.getWorld().getBounds().encapsulate(requiredBounds);

            if (!forceVisibleNow) {
                if (!ps.getBaseWorldSection().isEmpty()) {
                    ps.getBaseWorldSection().erase(targetSelection);
                    ps.getBaseWorldSection().queueRedraw();
                }
                return;
            }

            if (ps.getBaseWorldSection().isEmpty()) {
                Selection all = ps.getSceneBuildingUtil().select().everywhere();
                ps.getBaseWorldSection().set(all);
                ps.getBaseWorldSection().setVisible(true);
                ps.getBaseWorldSection().setFade(1);
            } else {
                ps.getBaseWorldSection().add(targetSelection);
            }

            ps.getBaseWorldSection().queueRedraw();
        });
    }

    private void applySetBlockNbtPatch(SceneBuilder scene, DslScene.DslStep step, Selection selection) {
        if (step.nbt == null || step.nbt.isBlank()) {
            return;
        }
        CompoundTag patch;
        try {
            patch = TagParser.parseTag(step.nbt);
        } catch (Exception e) {
            LOGGER.warn("set_block invalid nbt: {}", step.nbt);
            return;
        }
        scene.world().modifyBlockEntityNBT(selection, BlockEntity.class, nbt -> nbt.merge(patch.copy()), true);
    }

    private void applyDestroyBlock(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("destroy_block missing blockPos");
            return;
        }
        BlockPos pos = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        boolean particles = !Boolean.FALSE.equals(step.destroyParticles);
        if (particles) {
            scene.world().destroyBlock(pos);
            updateVisibleRange(context, step, false);
            return;
        }
        scene.world().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
        updateVisibleRange(context, step, false);
    }

    private void applyReplaceBlocks(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
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
        ensureSceneCanShowRange(scene, pos1, pos2, true);
        var selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        BlockState state = block.defaultBlockState();
        state = applyBlockProperties(state, step);
        scene.world().replaceBlocks(selection, state, particles);
        updateVisibleRange(context, step, true);
    }

    private BlockState applyBlockProperties(BlockState state, DslScene.DslStep step) {
        if (step.blockProperties == null || step.blockProperties.isEmpty()) return state;
        var definition = state.getBlock().getStateDefinition();
        for (var entry : step.blockProperties.entrySet()) {
            Property<?> prop = definition.getProperty(entry.getKey());
            if (prop == null) {
                LOGGER.warn("Unknown block property '{}' for {}", entry.getKey(), step.block);
                continue;
            }
            state = setPropertyValue(state, prop, entry.getValue());
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value)
                .map(v -> state.setValue(prop, v))
                .orElse(state);
    }

    private void applyHideSection(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        Selection selection = selectionFromStep(scene, step, "hide_section");
        if (selection == null) {
            return;
        }
        updateVisibleRange(context, step, false);
        int duration = step.durationOrDefault(20);
        // Safety: ensure the base world section has been initialized before hiding.
        // If show_structure was somehow skipped, the base section's internal Selection is null,
        // and hideSection's erase() would NPE. This pre-instruction prevents that.
        scene.addInstruction(ps -> {
            if (ps.getBaseWorldSection().isEmpty()) {
                LOGGER.warn("hide_section executed before show_structure; auto-showing structure");
                Selection all = ps.getSceneBuildingUtil().select().everywhere();
                ps.getBaseWorldSection().set(all);
                ps.getBaseWorldSection().setVisible(true);
                ps.getBaseWorldSection().setFade(1);
                ps.getBaseWorldSection().queueRedraw();
            }
        });
        Direction direction = parseDirection(step.direction);

        ElementLink<WorldSectionElement> link = scene.world().makeSectionIndependent(selection);
        if (duration <= 0) {
            scene.addInstruction(ps -> {
                WorldSectionElement element = ps.resolve(link);
                if (element != null) {
                    element.setVisible(false);
                    element.setFade(0);
                }
            });
            return;
        }
        if (duration == 15) {
            scene.world().hideIndependentSection(link, direction);
            return;
        }
        scene.addInstruction(new FadeOutOfSceneInstruction<>(duration, direction, link));
    }

    private void applyShowSectionAndMerge(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        Selection selection = selectionFromStep(scene, step, "show_section_and_merge");
        if (selection == null) {
            return;
        }
        String linkId = step.linkId == null ? "" : step.linkId.trim();
        if (linkId.isEmpty()) {
            linkId = autoLinkId(context);
        }
        int duration = step.durationOrDefault(20);
        Direction direction = parseDirection(step.direction);
        ElementLink<WorldSectionElement> existing = context.sectionLinks.get(linkId);

        String entranceAnimation = normalizeEntranceAnimation(step.entranceAnimation);
        boolean smartDisplay = !Boolean.FALSE.equals(step.smartDisplay);
        if ("none".equals(entranceAnimation)) {
            duration = 0;
        }
        if (entranceAnimation != null && !"none".equals(entranceAnimation)) {
            BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
            BlockPos pos2 = pos1;
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
            }
            int rowDuration = step.entranceDuration == null ? 20 : Math.max(0, step.entranceDuration);
            int rowInterval = step.entranceInterval == null ? 1 : Math.max(0, step.entranceInterval);
            applyAnimatedShowSectionAndMerge(scene, context, linkId, existing, pos1, pos2,
                    entranceAnimation, direction, rowDuration, rowInterval, smartDisplay);
                updateVisibleRange(context, step, true);
            return;
        }

        if (existing == null) {
            ElementLink<WorldSectionElement> created;
            if (duration <= 0) {
                created = scene.world().showIndependentSectionImmediately(selection);
            } else if (duration == 15) {
                created = scene.world().showIndependentSection(selection, direction);
            } else {
                DisplayWorldSectionInstruction instruction = new DisplayWorldSectionInstruction(duration, direction, selection, null);
                scene.addInstruction(instruction);
                created = instruction.createLink(scene.getScene());
            }
            context.sectionLinks.put(linkId, created);
            updateVisibleRange(context, step, true);
            return;
        }
        if (duration <= 0) {
            ElementLink<WorldSectionElement> target = existing;
            scene.addInstruction(ps -> {
                WorldSectionElement element = ps.resolve(target);
                if (element != null) {
                    element.add(selection);
                    element.queueRedraw();
                }
            });
            updateVisibleRange(context, step, true);
            return;
        }
        if (duration == 15) {
            scene.world().showSectionAndMerge(selection, direction, existing);
            updateVisibleRange(context, step, true);
            return;
        }
        scene.addInstruction(new DisplayWorldSectionInstruction(duration, direction, selection,
                () -> scene.getScene().resolve(existing)));
        updateVisibleRange(context, step, true);
    }

    private void applyAnimatedShowSectionAndMerge(SceneBuilder scene, StepContext context,
                                                  String linkId,
                                                  @Nullable ElementLink<WorldSectionElement> existing,
                                                  BlockPos pos1, BlockPos pos2,
                                                  String entranceAnimation,
                                                  Direction entryDirection,
                                                  int rowDuration,
                                                  int rowInterval,
                                                  boolean smartDisplay) {
        List<List<BlockPos>> groups = orderedLayerGroups(pos1, pos2, entranceAnimation);
        if (smartDisplay) {
            groups = filterVisibleGroups(groups, context);
        }
        if (groups.isEmpty()) {
            return;
        }

        ElementLink<WorldSectionElement> working = existing;
        for (List<BlockPos> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            Selection groupSelection = selectionForGroup(scene, group);
            if (working == null) {
                DisplayWorldSectionInstruction instruction = new DisplayWorldSectionInstruction(rowDuration, entryDirection, groupSelection, null);
                scene.addInstruction(instruction);
                working = instruction.createLink(scene.getScene());
                context.sectionLinks.put(linkId, working);
            } else {
                ElementLink<WorldSectionElement> target = working;
                scene.addInstruction(new DisplayWorldSectionInstruction(rowDuration, entryDirection, groupSelection,
                        () -> scene.getScene().resolve(target)));
            }
            scene.idle(rowInterval);
        }
    }

    private Selection selectionForGroup(SceneBuilder scene, List<BlockPos> group) {
        BlockPos first = group.get(0);
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = first.getX();
        int maxY = first.getY();
        int maxZ = first.getZ();

        for (int i = 1; i < group.size(); i++) {
            BlockPos pos = group.get(i);
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        return scene.getScene().getSceneBuildingUtil().select().fromTo(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ));
    }

    private void applyRotateSection(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        ElementLink<WorldSectionElement> link = resolveSectionLink(scene, step, context, "rotate_section");
        if (link == null) {
            return;
        }
        double x = step.rotX == null ? 0.0 : step.rotX;
        double y = step.rotY == null ? (step.degrees == null ? 0.0 : step.degrees) : step.rotY;
        double z = step.rotZ == null ? 0.0 : step.rotZ;
        int duration = step.durationOrDefault(20);
        scene.world().rotateSection(link, x, y, z, duration);
    }

    private void applyMoveSection(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        ElementLink<WorldSectionElement> link = resolveSectionLink(scene, step, context, "move_section");
        if (link == null) {
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
        boolean hasProps = step.blockProperties != null && !step.blockProperties.isEmpty();
        boolean hasNbt = step.nbt != null && !step.nbt.isBlank();

        if (!hasProps && !hasNbt) {
            LOGGER.warn("modify_block_entity_nbt missing both properties and nbt");
            return;
        }

        boolean redraw = Boolean.TRUE.equals(step.reDrawBlocks);

        // Apply block state properties
        if (hasProps) {
            BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
            BlockPos pos2 = pos1;
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
            }
            for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2)) {
                BlockPos targetPos = pos.immutable();
                scene.world().modifyBlock(targetPos, state -> {
                    var definition = state.getBlock().getStateDefinition();
                    BlockState result = state;
                    for (var entry : step.blockProperties.entrySet()) {
                        Property<?> prop = definition.getProperty(entry.getKey());
                        if (prop != null) {
                            result = setPropertyValue(result, prop, entry.getValue());
                        }
                    }
                    return result;
                }, false);
            }
        }

        // Apply NBT patch
        if (hasNbt) {
            CompoundTag patch;
            try {
                patch = TagParser.parseTag(step.nbt);
            } catch (Exception e) {
                LOGGER.warn("modify_block_entity_nbt invalid nbt: {}", step.nbt);
                return;
            }
            scene.world().modifyBlockEntityNBT(selection, BlockEntity.class, nbt -> nbt.merge(patch.copy()), redraw);
        }
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

    private void applyClearEntities(SceneBuilder scene, DslScene.DslStep step) {
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);
        String filterId = step.entity;
        ResourceLocation filterLoc = (filterId != null && !filterId.isBlank()) ? ResourceLocation.tryParse(filterId) : null;

        if (isFullScene) {
            scene.world().modifyEntities(Entity.class, entity -> {
                if (entity instanceof ItemEntity) return;
                if (filterLoc == null || EntityType.getKey(entity.getType()).equals(filterLoc)) {
                    entity.discard();
                }
            });
        } else {
            Selection selection = selectionFromStep(scene, step, "clear_entities");
            if (selection == null) return;
            scene.world().modifyEntitiesInside(Entity.class, selection, entity -> {
                if (entity instanceof ItemEntity) return;
                if (filterLoc == null || EntityType.getKey(entity.getType()).equals(filterLoc)) {
                    entity.discard();
                }
            });
        }
    }

    private void applyClearItemEntities(SceneBuilder scene, DslScene.DslStep step) {
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);
        String filterId = step.item;
        ResourceLocation filterLoc = (filterId != null && !filterId.isBlank()) ? ResourceLocation.tryParse(filterId) : null;

        if (isFullScene) {
            scene.world().modifyEntities(ItemEntity.class, entity -> {
                if (filterLoc == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filterLoc)) {
                    entity.discard();
                }
            });
        } else {
            Selection selection = selectionFromStep(scene, step, "clear_item_entities");
            if (selection == null) return;
            scene.world().modifyEntitiesInside(ItemEntity.class, selection, entity -> {
                if (filterLoc == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filterLoc)) {
                    entity.discard();
                }
            });
        }
    }

    private void applyModifyEntitiesNbt(SceneBuilder scene, DslScene.DslStep step) {
        CompoundTag patch = parseEntityNbtPatch(step, "modify_entities_nbt");
        if (patch == null) return;

        String filterId = step.entity;
        ResourceLocation filterLoc = (filterId != null && !filterId.isBlank()) ? ResourceLocation.tryParse(filterId) : null;
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);

        if (isFullScene) {
            scene.world().modifyEntities(Entity.class, entity -> {
                if (entity instanceof ItemEntity) return;
                if (filterLoc == null || EntityType.getKey(entity.getType()).equals(filterLoc)) {
                    mergeEntityNbt(entity, patch);
                }
            });
            return;
        }

        Selection selection = selectionFromStep(scene, step, "modify_entities_nbt");
        if (selection == null) return;
        scene.world().modifyEntitiesInside(Entity.class, selection, entity -> {
            if (entity instanceof ItemEntity) return;
            if (filterLoc == null || EntityType.getKey(entity.getType()).equals(filterLoc)) {
                mergeEntityNbt(entity, patch);
            }
        });
    }

    private void applyModifyItemEntitiesNbt(SceneBuilder scene, DslScene.DslStep step) {
        CompoundTag patch = parseEntityNbtPatch(step, "modify_item_entities_nbt");
        if (patch == null) return;

        String filterId = step.item;
        ResourceLocation filterLoc = (filterId != null && !filterId.isBlank()) ? ResourceLocation.tryParse(filterId) : null;
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);

        if (isFullScene) {
            scene.world().modifyEntities(ItemEntity.class, entity -> {
                if (filterLoc == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filterLoc)) {
                    entity.setItem(applyPatchToItemStack(entity.getItem(), patch));
                    if (isLikelyEntityPatch(patch)) {
                        mergeEntityNbt(entity, patch);
                    }
                }
            });
            return;
        }

        Selection selection = selectionFromStep(scene, step, "modify_item_entities_nbt");
        if (selection == null) return;
        scene.world().modifyEntitiesInside(ItemEntity.class, selection, entity -> {
            if (filterLoc == null || BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(filterLoc)) {
                entity.setItem(applyPatchToItemStack(entity.getItem(), patch));
                if (isLikelyEntityPatch(patch)) {
                    mergeEntityNbt(entity, patch);
                }
            }
        });
    }

    private ItemStack applyPatchToItemStack(ItemStack base, CompoundTag patch) {
        ItemStack copy = base.copy();

        // Support both syntaxes:
        // 1) Item-level patch: {BlockEntityTag:{...},display:{...}}
        // 2) Entity-style patch: {Item:{id:"...",Count:1b,tag:{...}}}
        CompoundTag itemPatch = patch;
        if (patch.contains("Item", Tag.TAG_COMPOUND)) {
            CompoundTag entityItem = patch.getCompound("Item");
            if (entityItem.contains("tag", Tag.TAG_COMPOUND)) {
                itemPatch = entityItem.getCompound("tag");
            } else {
                itemPatch = new CompoundTag();
            }
        }

        if (!itemPatch.isEmpty()) {
            CompoundTag stackTag = copy.getOrCreateTag();
            stackTag.merge(itemPatch.copy());
        }
        return copy;
    }

    private boolean isLikelyEntityPatch(CompoundTag patch) {
        return patch.contains("Item", Tag.TAG_COMPOUND)
                || patch.contains("Age")
                || patch.contains("PickupDelay")
                || patch.contains("Health")
                || patch.contains("Motion")
                || patch.contains("Pos")
                || patch.contains("Rotation")
                || patch.contains("NoGravity")
                || patch.contains("Glowing")
                || patch.contains("Invulnerable")
                || patch.contains("UUID")
                || patch.contains("Tags");
    }

    @Nullable
    private CompoundTag parseEntityNbtPatch(DslScene.DslStep step, String stepName) {
        if (step.nbt == null || step.nbt.isBlank()) {
            LOGGER.warn("{} missing nbt", stepName);
            return null;
        }
        try {
            return TagParser.parseTag(step.nbt);
        } catch (Exception e) {
            LOGGER.warn("{} invalid nbt: {}", stepName, step.nbt);
            return null;
        }
    }

    private void mergeEntityNbt(Entity entity, CompoundTag patch) {
        CompoundTag data = new CompoundTag();
        entity.saveWithoutId(data);
        data.merge(patch.copy());
        entity.load(data);
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

    @Nullable
    private ElementLink<WorldSectionElement> resolveSectionLink(SceneBuilder scene, DslScene.DslStep step,
                                                                StepContext context, String stepName) {
        String linkId = step.linkId == null ? "" : step.linkId.trim();
        if (!linkId.isEmpty()) {
            ElementLink<WorldSectionElement> existing = context.sectionLinks.get(linkId);
            if (existing != null) {
                return existing;
            }
        }

        Selection selection = selectionFromStep(scene, step, stepName);
        if (selection == null) {
            if (linkId.isEmpty()) {
                LOGGER.warn("{} requires linkId or blockPos/blockPos2 selection", stepName);
            } else {
                LOGGER.warn("{} missing linkId: {} and no selection to create one", stepName, linkId);
            }
            return null;
        }

        ElementLink<WorldSectionElement> created = scene.world().showIndependentSectionImmediately(selection);
        String key = linkId.isEmpty() ? autoLinkId(context) : linkId;
        context.sectionLinks.put(key, created);
        updateVisibleRange(context, step, true);
        return created;
    }

    private String autoLinkId(StepContext context) {
        return "section_" + (context.sectionLinks.size() + 1);
    }

    @Nullable
    private String normalizeEntranceAnimation(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "none", "(无)", "无" -> "none";
            case "simultaneous", "同时" -> "simultaneous";
            case "down", "从上到下", "上到下", "top_to_bottom", "top-down" -> "down";
            case "up", "从下到上", "下到上", "bottom_to_top", "bottom-up" -> "up";
            case "south", "从北到南", "北到南", "north_to_south", "north-south" -> "south";
            case "north", "从南到北", "南到北", "south_to_north", "south-north" -> "north";
            case "east", "从西到东", "西到东", "west_to_east", "west-east" -> "east";
            case "west", "从东到西", "东到西", "east_to_west", "east-west" -> "west";
            default -> null;
        };
    }

    private List<List<BlockPos>> orderedLayerGroups(BlockPos pos1, BlockPos pos2, String entranceAnimation) {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        List<BlockPos> positions = new ArrayList<>((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }

        Comparator<BlockPos> tieBreaker = Comparator
                .comparingInt((BlockPos p) -> p.getY())
                .thenComparingInt((BlockPos p) -> p.getZ())
                .thenComparingInt((BlockPos p) -> p.getX());

        Comparator<BlockPos> comparator = switch (entranceAnimation) {
            case "down" -> Comparator.comparingInt((BlockPos p) -> p.getY()).reversed().thenComparing(tieBreaker);
            case "up" -> Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparing(tieBreaker);
            case "south" -> Comparator.comparingInt((BlockPos p) -> p.getZ()).thenComparing(tieBreaker);
            case "north" -> Comparator.comparingInt((BlockPos p) -> p.getZ()).reversed().thenComparing(tieBreaker);
            case "east" -> Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparing(tieBreaker);
            case "west" -> Comparator.comparingInt((BlockPos p) -> p.getX()).reversed().thenComparing(tieBreaker);
            default -> tieBreaker;
        };

        positions.sort(comparator);

        java.util.function.ToIntFunction<BlockPos> layerKey = switch (entranceAnimation) {
            case "simultaneous" -> (BlockPos p) -> 0;
            case "south", "north" -> (BlockPos p) -> p.getZ();
            case "east", "west" -> (BlockPos p) -> p.getX();
            case "down", "up" -> (BlockPos p) -> p.getY();
            default -> (BlockPos p) -> p.getY();
        };

        List<List<BlockPos>> groups = new ArrayList<>();
        List<BlockPos> currentGroup = null;
        int currentKey = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            int key = layerKey.applyAsInt(pos);
            if (currentGroup == null || key != currentKey) {
                currentGroup = new ArrayList<>();
                groups.add(currentGroup);
                currentKey = key;
            }
            currentGroup.add(pos);
        }
        return groups;
    }

    private List<List<BlockPos>> filterVisibleGroups(List<List<BlockPos>> groups, StepContext context) {
        List<List<BlockPos>> filtered = new ArrayList<>();
        for (List<BlockPos> group : groups) {
            List<BlockPos> pending = new ArrayList<>();
            for (BlockPos pos : group) {
                if (!isBlockVisible(context, pos.asLong())) {
                    pending.add(pos);
                }
            }
            if (!pending.isEmpty()) {
                filtered.add(pending);
            }
        }
        return filtered;
    }

    private void updateVisibleRange(StepContext context, DslScene.DslStep step, boolean visible) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            return;
        }
        BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        BlockPos pos2 = pos1;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    long key = BlockPos.asLong(x, y, z);
                    if (context.allBlocksVisible) {
                        if (visible) {
                            context.hiddenBlockKeys.remove(key);
                        } else {
                            context.hiddenBlockKeys.add(key);
                        }
                    } else {
                        if (visible) {
                            context.visibleBlockKeys.add(key);
                        } else {
                            context.visibleBlockKeys.remove(key);
                        }
                    }
                }
            }
        }
    }

    private boolean isBlockVisible(StepContext context, long key) {
        if (context.allBlocksVisible) {
            return !context.hiddenBlockKeys.contains(key);
        }
        return context.visibleBlockKeys.contains(key);
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
            // Ensure each segment starts with show_structure
            for (DslScene.SceneSegment seg : scene.scenes) {
                SceneStore.sanitizeScene(scene);
            }
            return scene.scenes;
        }
        List<DslScene.SceneSegment> sceneList = new ArrayList<>();
        DslScene.SceneSegment fallback = new DslScene.SceneSegment();
        fallback.steps = List.of();
        sceneList.add(fallback);
        return sceneList;
    }

    private String sceneSuffix(DslScene.SceneSegment sc, int index) {
        if (sc.id != null && !sc.id.isBlank()) {
            return sc.id;
        }
        return String.valueOf(index + 1);
    }

    private boolean firstStepIsShowStructure(DslScene.SceneSegment sc) {
        if (sc.steps == null) {
            return false;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            // Return whether the first meaningful step is a valid scene-start step
            return "show_structure".equalsIgnoreCase(step.type)
                    || "show_interface".equalsIgnoreCase(step.type);
        }
        return false;
    }

    private boolean firstStepIsShowInterface(DslScene.SceneSegment sc) {
        if (sc.steps == null) {
            return false;
        }
        for (DslScene.DslStep step : sc.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            return "show_interface".equalsIgnoreCase(step.type);
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
