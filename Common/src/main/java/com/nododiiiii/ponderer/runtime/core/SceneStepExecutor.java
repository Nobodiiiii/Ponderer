package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.interfaces.instruction.ShowInterfaceInstruction;
import com.nododiiiii.ponderer.runtime.core.AdjustViewInstruction;
import com.nododiiiii.ponderer.runtime.core.ParticleEffects;
import com.nododiiiii.ponderer.runtime.core.PonderSceneViewOffsetAccess;
import com.nododiiiii.ponderer.runtime.core.SceneSectionOwnership;
import com.nododiiiii.ponderer.runtime.core.TextMarkerInstruction;
import com.nododiiiii.ponderer.scene.DslScene;
import com.mojang.logging.LogUtils;
import com.nododiiiii.ponderer.runtime.mixin.PonderSceneAccessor;
import com.nododiiiii.ponderer.runtime.interfaces.host.InterfaceHost;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.AwaitInterfaceInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.HideInterfaceInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.InterfaceActionInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.SetInterfaceContextInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.SetInterfaceDataInstruction;
import com.nododiiiii.ponderer.runtime.interfaces.instruction.SetInterfaceSlotsInstruction;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.runtime.viewer.UiAnchorPoint;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.RotateSceneInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.nododiiiii.ponderer.runtime.spi.*;
import com.nododiiiii.ponderer.support.structure.ExtraStructurePlanner;
import com.nododiiiii.ponderer.runtime.api.PlaybackExecutionContext;

/** Runtime step semantics shared by every playback host. */
final class SceneStepExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final StructureSource structures;
    private final IngredientResolver ingredients;
    SceneStepExecutor(StructureSource structures, IngredientResolver ingredients) {
        this.structures = structures;
        this.ingredients = ingredients;
    }

    static class StepContext {
        final Map<String, ElementLink<WorldSectionElement>> sectionLinks = new HashMap<>();
        final Map<String, List<ElementLink<EntityElement>>> entityLinks = new HashMap<>();
        final SceneSectionOwnership sections = new SceneSectionOwnership();
        boolean uiAnchorMode;
    }

    private static final class EntityMoveTarget {
        final Entity entity;
        final Vec3 startPos;

        private EntityMoveTarget(Entity entity, Vec3 startPos) {
            this.entity = entity;
            this.startPos = startPos;
        }
    }

    private static final class EntityMoveInstruction extends TickingInstruction {
        @Nullable
        private final List<ElementLink<EntityElement>> linkedTargets;
        @Nullable
        private final Selection selection;
        @Nullable
        private final ResourceLocation entityFilter;
        private final Vec3 totalOffset;
        private final boolean walkAnimation;
        private final float walkAnimationSpeed;
        private final List<EntityMoveTarget> targets = new ArrayList<>();

        private EntityMoveInstruction(@Nullable List<ElementLink<EntityElement>> linkedTargets,
                                      @Nullable Selection selection,
                                      @Nullable ResourceLocation entityFilter,
                                      Vec3 totalOffset,
                                      int duration,
                                      boolean walkAnimation) {
            super(false, duration);
            this.linkedTargets = linkedTargets == null ? null : List.copyOf(linkedTargets);
            this.selection = selection;
            this.entityFilter = entityFilter;
            this.totalOffset = totalOffset;
            this.walkAnimation = walkAnimation;
            this.walkAnimationSpeed = computeWalkAnimationSpeed(totalOffset, duration);
        }

        @Override
        protected void firstTick(PonderScene scene) {
            super.firstTick(scene);
            if (linkedTargets != null) {
                for (ElementLink<EntityElement> link : linkedTargets) {
                    EntityElement element = scene.resolve(link);
                    if (element == null) {
                        continue;
                    }
                    element.ifPresent(entity -> collectTarget(entity));
                }
                return;
            }

            scene.forEachWorldEntity(Entity.class, this::collectTarget);
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            if (targets.isEmpty()) {
                return;
            }

            int elapsedTicks = totalTicks - remainingTicks;
            double progress = Math.min(1.0, elapsedTicks / (double) totalTicks);

            for (EntityMoveTarget target : targets) {
                Entity entity = target.entity;
                if (entity == null || !entity.isAlive()) {
                    continue;
                }

                Vec3 targetPos = target.startPos.add(totalOffset.scale(progress));
                entity.setOldPosAndRot();
                entity.setPos(targetPos.x, targetPos.y, targetPos.z);
                entity.setDeltaMovement(Vec3.ZERO);
                if (walkAnimation && progress < 1.0) {
                    applyWalkAnimation(entity, walkAnimationSpeed);
                } else {
                    stopWalkAnimation(entity);
                }
            }
        }

        private void collectTarget(Entity entity) {
            if (entity == null || !entity.isAlive() || entity instanceof ItemEntity) {
                return;
            }
            if (selection != null && !selection.test(entity.blockPosition())) {
                return;
            }
            if (!matchesEntityType(entity, entityFilter)) {
                return;
            }
            targets.add(new EntityMoveTarget(entity, entity.position()));
        }
    }

    private static final class EntityEntranceInstruction extends TickingInstruction {
        private final ElementLink<EntityElement> entityLink;
        private final Vec3 targetPos;
        private final Vec3 entranceOffset;

        private EntityEntranceInstruction(ElementLink<EntityElement> entityLink,
                                          Vec3 targetPos,
                                          Vec3 entranceOffset,
                                          int duration) {
            super(false, duration);
            this.entityLink = entityLink;
            this.targetPos = targetPos;
            this.entranceOffset = entranceOffset;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            super.firstTick(scene);
            positionEntity(scene, targetPos.add(entranceOffset), true);
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            double fade = totalTicks <= 0 ? 0.0d : remainingTicks / (double) totalTicks;
            positionEntity(scene, targetPos.add(entranceOffset.scale(fade * fade)), false);
        }

        private void positionEntity(PonderScene scene, Vec3 pos, boolean snapOld) {
            EntityElement element = scene.resolve(entityLink);
            if (element == null) {
                return;
            }
            element.ifPresent(entity -> {
                if (entity == null || !entity.isAlive()) {
                    return;
                }
                if (!snapOld) {
                    entity.setOldPosAndRot();
                }
                entity.setPos(pos.x, pos.y, pos.z);
                entity.setDeltaMovement(Vec3.ZERO);
                if (snapOld) {
                    entity.setOldPosAndRot();
                }
                stopWalkAnimation(entity);
            });
        }
    }

    private static final class EntityExitInstruction extends TickingInstruction {
        @Nullable
        private final List<ElementLink<EntityElement>> linkedTargets;
        @Nullable
        private final Selection selection;
        @Nullable
        private final ResourceLocation entityFilter;
        private final Vec3 exitOffset;
        private final List<EntityMoveTarget> targets = new ArrayList<>();

        private EntityExitInstruction(@Nullable List<ElementLink<EntityElement>> linkedTargets,
                                      @Nullable Selection selection,
                                      @Nullable ResourceLocation entityFilter,
                                      Vec3 exitOffset,
                                      int duration) {
            super(false, duration);
            this.linkedTargets = linkedTargets == null ? null : List.copyOf(linkedTargets);
            this.selection = selection;
            this.entityFilter = entityFilter;
            this.exitOffset = exitOffset;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            super.firstTick(scene);
            if (linkedTargets != null) {
                for (ElementLink<EntityElement> link : linkedTargets) {
                    EntityElement element = scene.resolve(link);
                    if (element == null) {
                        continue;
                    }
                    element.ifPresent(this::collectTarget);
                }
                return;
            }

            scene.forEachWorldEntity(Entity.class, this::collectTarget);
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            if (targets.isEmpty()) {
                return;
            }

            double progress = Math.min(1.0d, (totalTicks - remainingTicks) / (double) totalTicks);
            boolean finished = remainingTicks == 0;
            for (EntityMoveTarget target : targets) {
                Entity entity = target.entity;
                if (entity == null || !entity.isAlive()) {
                    continue;
                }

                Vec3 targetPos = target.startPos.add(exitOffset.scale(progress * progress));
                entity.setOldPosAndRot();
                entity.setPos(targetPos.x, targetPos.y, targetPos.z);
                entity.setDeltaMovement(Vec3.ZERO);
                stopWalkAnimation(entity);
                if (finished) {
                    entity.discard();
                }
            }
        }

        private void collectTarget(Entity entity) {
            if (entity == null || !entity.isAlive() || entity instanceof ItemEntity) {
                return;
            }
            if (selection != null && !selection.test(entity.blockPosition())) {
                return;
            }
            if (!matchesEntityType(entity, entityFilter)) {
                return;
            }
            targets.add(new EntityMoveTarget(entity, entity.position()));
        }
    }


    void applyStep(SceneBuilder scene, SceneBuildingUtil util, DslScene dsl, DslScene.DslStep step, StepContext context) {
        if (Boolean.TRUE.equals(step.attachKeyFrame)) {
            scene.addKeyframe();
        }
        switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" -> applyShowStructure(scene, step, context);
            case "show_extra_structure" -> applyShowExtraStructure(scene, dsl, step, context);
            case "idle" -> scene.idle(step.durationOrDefault(20));
            case "text" -> applyText(scene, step, context);
            case "shared_text" -> applySharedText(scene, step, context);
            case "create_entity" -> applyCreateEntity(scene, step, context);
            case "create_item_entity" -> applyCreateItemEntity(scene, step, context);
            case "rotate_camera_y" -> applyRotateCameraY(scene, step);
            case "zoom_scene" -> applyZoomScene(scene, step);
            case "highlight_section" -> applyHighlightSection(scene, step);
            case "show_controls" -> applyShowControls(scene, step, context);
            case "show_interface" -> applyShowInterface(scene, step, context);
            case "change_interface_slot" -> applyChangeInterfaceSlot(scene, step);
            case "click_interface" -> applyClickInterface(scene, step);
            case "set_interface_slots" -> scene.addInstruction(
                new SetInterfaceSlotsInstruction(InterfaceStepSpecs.slots(step)));
            case "set_interface_data" -> scene.addInstruction(
                new SetInterfaceDataInstruction(InterfaceStepSpecs.data(step), step.durationOrDefault(1)));
            case "set_interface_context" -> scene.addInstruction(
                new SetInterfaceContextInstruction(InterfaceStepSpecs.context(step)));
            case "interface_action" -> applyInterfaceAction(scene, step);
            case "hide_interface" -> {
                scene.addInstruction(new HideInterfaceInstruction(step.durationOrDefault(1)));
                context.uiAnchorMode = false;
            }
            case "await_interface" -> applyAwaitInterface(scene, step);
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
            case "spawn_particles" -> {
                try {
                    ParticleEffects.emit(scene, step);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid spawn_particles step: {}", e.getMessage());
                }
            }
            case "clear_entities" -> applyClearEntities(scene, step, context);
            case "clear_item_entities" -> applyClearItemEntities(scene, step, context);
            case "modify_entities_nbt" -> applyModifyEntitiesNbt(scene, step, context);
            case "modify_item_entities_nbt" -> applyModifyItemEntitiesNbt(scene, step, context);
            case "next_scene" -> {
            }
            default -> LOGGER.warn("Unknown step type '{}' in scene {}", step.type, dsl.id);
        }
    }

    private void applyText(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        String text = step.text == null ? "" : step.text.resolve();
        int duration = step.durationOrDefault(60);

        scene.addInstruction(new TextMarkerInstruction(text, false));

        TextElementBuilder builder = scene.overlay()
            .showText(duration)
            .text(text);

        if (hasExplicitPoint(step)) {
            builder.pointAt(resolveOverlayPoint(step, context));
        }

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
            : ResourceLocation.fromNamespaceAndPath(scene.getScene().getNamespace(), key);
        if (loc == null) {
            LOGGER.warn("shared_text invalid key: {}", key);
            return;
        }

        int duration = step.durationOrDefault(60);
        scene.addInstruction(new TextMarkerInstruction(key, true));
        TextElementBuilder builder = scene.overlay().showText(duration).sharedText(loc);

        if (hasExplicitPoint(step)) {
            builder.pointAt(resolveOverlayPoint(step, context));
        }

        PonderPalette palette = parsePalette(step.color);
        if (palette != null) {
            builder.colored(palette);
        }
        if (Boolean.TRUE.equals(step.placeNearTarget)) {
            builder.placeNearTarget();
        }
    }

    private static boolean hasExplicitPoint(DslScene.DslStep step) {
        return step.point != null && !step.point.isEmpty();
    }

    private void applyCreateEntity(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        ResourceLocation entityId = step.entity == null ? null : ResourceLocation.tryParse(step.entity);
        if (entityId == null) {
            LOGGER.warn("create_entity missing/invalid entity id");
            return;
        }

        Vec3 targetPos = toPoint(step.pos != null ? step.pos : step.point);
        Vec3 entranceOffset = entityEntranceOffset(step);
        int entranceDuration = step.entranceDuration == null ? 20 : Math.max(0, step.entranceDuration);
        boolean animatedEntrance = entranceOffset != null && entranceDuration > 0;
        Vec3 spawnPos = animatedEntrance ? targetPos.add(entranceOffset) : targetPos;
        ElementLink<EntityElement> entityLink = scene.world().createEntity((Level level) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
            if (type == null) {
                LOGGER.warn("Unknown entity type: {}", entityId);
                return null;
            }
            Entity entity = type.create(level);
            if (entity != null) {
                entity.setPosRaw(spawnPos.x, spawnPos.y, spawnPos.z);
                entity.setOldPosAndRot();
                Vec3 lookAt = step.lookAt != null && step.lookAt.size() >= 3
                    ? new Vec3(step.lookAt.get(0), step.lookAt.get(1), step.lookAt.get(2))
                    : targetPos.add(0, 0, -1);
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
                stopWalkAnimation(entity);

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
        registerEntityLink(context, step.linkId, entityLink);
        if (animatedEntrance) {
            scene.addInstruction(new EntityEntranceInstruction(entityLink, targetPos, entranceOffset, entranceDuration));
        }
    }

    private void applyCreateItemEntity(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
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
        ElementLink<EntityElement> entityLink = scene.world().createEntity((Level level) -> {
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
        registerEntityLink(context, step.linkId, entityLink);
    }

    private void applyRotateCameraY(SceneBuilder scene, DslScene.DslStep step) {
        float degreesY = step.degrees == null ? 90f : step.degrees;
        Float degreesX = step.degreesX;
        int duration = step.durationOrDefault(20);
        if (duration > 0) {
            // Match Ponder's exponential rotation; duration only controls the scene wait.
            scene.addInstruction(new RotateSceneInstruction(degreesX == null ? 0f : degreesX, degreesY, true));
            scene.idle(duration);
            return;
        }
        scene.addInstruction(ponderScene -> {
            var yRotation = ponderScene.getTransform().yRotation;
            float targetY = yRotation.getChaseTarget() + degreesY;
            yRotation.startWithValue(targetY);
            if (degreesX != null) {
                var xRotation = ponderScene.getTransform().xRotation;
                float targetX = xRotation.getChaseTarget() + degreesX;
                xRotation.startWithValue(targetX);
            }
        });
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
        Vec3 point = resolveOverlayPoint(step, context);
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
        if (step.interfaceSpec == null) {
            String source = step.interfaceSource == null ? "block" : step.interfaceSource.toLowerCase(java.util.Locale.ROOT);
            switch (source) {
                case "held_item" -> {
                    if (step.item == null || step.item.isBlank()) {
                        LOGGER.warn("show_interface missing item id for held_item source");
                        return;
                    }
                }
                case "ui_id" -> {
                    if (step.uiId == null || step.uiId.isBlank()) {
                        LOGGER.warn("show_interface missing uiId for ui_id source");
                        return;
                    }
                }
                default -> {
                    if (!"block".equals(source)) {
                        LOGGER.warn("show_interface unknown interfaceSource '{}', treating as block", step.interfaceSource);
                    }
                    if (step.block == null || step.block.isBlank()) {
                        LOGGER.warn("show_interface missing block id");
                        return;
                    }
                }
            }
        }

        scene.addInstruction(this::ponderer$resetSceneViewState);
        scene.addInstruction(new OverlayMarkerInstruction(step));
        scene.addInstruction(new ShowInterfaceInstruction(step));
        context.uiAnchorMode = true;
    }

    private void applyClickInterface(SceneBuilder scene, DslScene.DslStep step) {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.fromLegacyClick(step);
        if (action == null) {
            LOGGER.warn("click_interface needs pos [u, v] and action left|right");
            return;
        }
        scene.addInstruction(new InterfaceActionInstruction(action));
    }

    private void applyChangeInterfaceSlot(SceneBuilder scene, DslScene.DslStep step) {
        if (step.interfaceSlots == null || step.interfaceSlots.isEmpty()) {
            LOGGER.warn("change_interface_slot missing interfaceSlots");
            return;
        }
        scene.addInstruction(new SetInterfaceSlotsInstruction(InterfaceStepSpecs.fromLegacyChangeSlot(step.interfaceSlots)));
    }

    private void applyInterfaceAction(SceneBuilder scene, DslScene.DslStep step) {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.action(step);
        if (action == null) {
            LOGGER.warn("interface_action unknown action '{}'", step.action);
            return;
        }
        scene.addInstruction(new InterfaceActionInstruction(action));
    }

    private void applyAwaitInterface(SceneBuilder scene, DslScene.DslStep step) {
        InterfaceStepSpecs.Goal goal = InterfaceStepSpecs.goal(step);
        if (goal == null) {
            LOGGER.warn("await_interface needs goal.slot");
            return;
        }
        String hint = step.hint == null ? null : step.hint.resolve();
        scene.addInstruction(new AwaitInterfaceInstruction(goal, hint == null || hint.isBlank() ? null : hint,
            step.timeout == null ? 600 : step.timeout));
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
            if (ingredients.isAvailable()) {
                // Use JEI ingredient helper to create a fluid element in a platform-agnostic way
                net.createmod.catnip.gui.element.ScreenElement element =
                        ingredients.fluid(fluid, 1000);
                if (element != null) {
                    builder.showing(element);
                    return;
                }
            }
            LOGGER.warn("show_controls: fluid '{}' found but JEI is not available for rendering", id);
            return;
        }

        // 3. Fallback: search all JEI ingredient types (chemicals, etc.)
        if (ingredients.isAvailable()) {
            net.createmod.catnip.gui.element.ScreenElement element =
                    ingredients.resolve(id);
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
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.of(tag));
                }
            } catch (Exception e) {
                LOGGER.warn("show_controls invalid item nbt: {}", finalNbtPart);
            }
        }
        return stack;
    }

    private void applyShowStructure(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        if (context.uiAnchorMode) {
            scene.addInstruction(InterfaceHost::hide);
        }
        context.uiAnchorMode = false;
        Selection selection;
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
            BlockPos pos2 = pos1;
            if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
                pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
            }
            selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        } else {
            // Default behavior: show full structure when no region is provided.
            selection = scene.getScene().getSceneBuildingUtil().select().everywhere();
        }
        SceneSectionOwnership.Reveal reveal = context.sections.prepare(scene, selection, false);
        context.sections.show(scene, selection, 15, Direction.UP, scene.getScene()::getBaseWorldSection, reveal);
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

    private void applyShowExtraStructure(SceneBuilder scene, DslScene dsl, DslScene.DslStep step, StepContext context) {
        if (step.structure == null || step.structure.isBlank()) {
            LOGGER.warn("show_extra_structure missing structure");
            return;
        }
        if (step.blockPos == null || step.blockPos.size() < 3) {
            LOGGER.warn("show_extra_structure missing blockPos");
            return;
        }

        BlockPos base = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        int rotationDegrees = step.rotation == null ? 0 : Math.round(step.rotation);
        boolean skipAir = !Boolean.TRUE.equals(step.replaceAir);

        List<ExtraStructurePlanner.PlacedBlock> placed;
        try (java.io.InputStream input = openExtraStructure(dsl, step.structure)) {
            if (input == null) {
                LOGGER.warn("show_extra_structure structure not found: {}", step.structure);
                return;
            }
            placed = ExtraStructurePlanner.plan(input, base, rotationDegrees, skipAir);
        } catch (Exception e) {
            LOGGER.error("show_extra_structure failed to read structure '{}': {}", step.structure, e.getMessage());
            return;
        }
        if (placed.isEmpty()) {
            return;
        }

        // Three exclusive entrance paths:
        //   - "none" / null   → no animated reveal; obey step.immediateDisplay.
        //   - "simultaneous"  → fade-in once, in parallel across strips so air holes are skipped.
        //   - directional     → strip-by-strip reveal along the chosen axis.
        String entranceAnimation = normalizeEntranceAnimation(step.entranceAnimation);
        boolean simultaneous = "simultaneous".equals(entranceAnimation);
        boolean directional = entranceAnimation != null && !"none".equals(entranceAnimation) && !simultaneous;
        boolean animatedReveal = simultaneous || directional;

        boolean placeVisible = !animatedReveal && !Boolean.FALSE.equals(step.immediateDisplay);
        boolean particles = placeVisible && !Boolean.FALSE.equals(step.spawnParticles);

        Selection placement = selectionForGroup(scene, placed.stream().map(b -> b.pos).toList());
        SceneSectionOwnership.Reveal reveal = null;
        if (animatedReveal) {
            reveal = context.sections.prepare(scene, placement, !Boolean.FALSE.equals(step.smartDisplay));
        } else {
            context.sections.place(scene, placement, placeVisible);
        }

        for (ExtraStructurePlanner.PlacedBlock b : placed) {
            scene.world().setBlock(b.pos, b.state, particles);
            if (b.nbt != null && !b.nbt.isEmpty()) {
                CompoundTag patch = b.nbt;
                Selection sel = scene.getScene().getSceneBuildingUtil().select().position(b.pos);
                scene.world().modifyBlockEntityNBT(sel, BlockEntity.class, nbt -> nbt.merge(patch.copy()), true);
            }
        }

        if (animatedReveal) {
            String linkId = step.linkId == null ? "" : step.linkId.trim();
            if (linkId.isEmpty()) {
                linkId = autoLinkId(context);
            }
            List<List<BlockPos>> groups = ExtraStructurePlanner.segmentForAnimation(placed,
                    simultaneous ? "up" : entranceAnimation);
            int rowDuration = step.entranceDuration == null ? 20 : Math.max(0, step.entranceDuration);
            int rowInterval = simultaneous ? 0 : step.entranceInterval == null ? 1 : Math.max(0, step.entranceInterval);
            showAnimatedGroups(scene, context, linkId, context.sectionLinks.get(linkId), groups,
                    parseDirection(step.direction), rowDuration, rowInterval, reveal);
        }
    }

    @Nullable
    private java.io.InputStream openExtraStructure(DslScene dsl, String reference) throws java.io.IOException {
        String trimmed = reference.trim();
        String relativePath;
        if (trimmed.contains(":")) {
            ResourceLocation rl = ResourceLocation.tryParse(trimmed);
            if (rl == null) {
                return null;
            }
            relativePath = "ponderer".equals(rl.getNamespace())
                    ? rl.getPath()
                    : rl.getNamespace() + "/" + rl.getPath();
        } else {
            relativePath = trimmed;
        }
        return structures.open(relativePath, dsl.pack);
    }

    private Vec3 resolveOverlayPoint(DslScene.DslStep step, StepContext context) {
        if (!context.uiAnchorMode) {
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

        return new UiAnchorPoint(u, v);
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
            if (PlaybackExecutionContext.isActive()) {
                return;
            }
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

        // Setting AIR is logically a destroy: there is nothing to animate, and the position must
        // be tracked as not-visible so subsequent smart-display checks animate replacements rather
        // than skipping them as "already visible".
        boolean isAir = state.isAir();
        String entranceAnimation = normalizeEntranceAnimation(step.entranceAnimation);
        if (!isAir && entranceAnimation != null && !"none".equals(entranceAnimation)) {
            applyAnimatedSetBlock(scene, step, context, state, pos, pos2, entranceAnimation);
            return;
        }

        ensureSceneCanShowRange(scene, context, pos, pos2, immediateDisplay && !isAir);

        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            var selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos, pos2);
            scene.world().setBlocks(selection, state, particles && !isAir);
            applySetBlockNbtPatch(scene, step, selection);
            return;
        }
        scene.world().setBlock(pos, state, particles && !isAir);
        applySetBlockNbtPatch(scene, step, scene.getScene().getSceneBuildingUtil().select().position(pos));
    }

    private void applyAnimatedSetBlock(SceneBuilder scene, DslScene.DslStep step, StepContext context, BlockState state,
                                       BlockPos pos1, BlockPos pos2, String entranceAnimation) {
        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        // Snapshot visibility before writing the new state, so air never counts as an existing block.
        SceneSectionOwnership.Reveal reveal = context.sections.prepare(scene, selection,
                !Boolean.FALSE.equals(step.smartDisplay));
        scene.world().setBlocks(selection, state, false);
        applySetBlockNbtPatch(scene, step, selection);

        String linkId = step.linkId == null ? "" : step.linkId.trim();
        if (linkId.isEmpty()) {
            linkId = autoLinkId(context);
        }
        int rowDuration = step.entranceDuration == null ? 20 : Math.max(0, step.entranceDuration);
        int rowInterval = step.entranceInterval == null ? 1 : Math.max(0, step.entranceInterval);
        showAnimatedGroups(scene, context, linkId, context.sectionLinks.get(linkId),
                orderedLayerGroups(pos1, pos2, entranceAnimation), parseDirection(step.direction),
                rowDuration, rowInterval, reveal);
    }

    private void ensureSceneCanShowRange(SceneBuilder scene, StepContext context,
                                         BlockPos pos1, BlockPos pos2, boolean forceVisibleNow) {
        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        context.sections.place(scene, selection, forceVisibleNow);
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
        BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        BlockPos pos2 = pos1;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        ensureSceneCanShowRange(scene, context, pos1, pos2, false);
        boolean particles = !Boolean.FALSE.equals(step.destroyParticles);
        if (pos1.equals(pos2)) {
            if (particles) {
                scene.world().destroyBlock(pos1);
            } else {
                scene.world().setBlock(pos1, Blocks.AIR.defaultBlockState(), false);
            }
        } else {
            for (BlockPos pos : BlockPos.betweenClosed(pos1, pos2)) {
                BlockPos target = pos.immutable();
                if (particles) {
                    scene.world().destroyBlock(target);
                } else {
                    scene.world().setBlock(target, Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
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
        var selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        BlockState state = block.defaultBlockState();
        state = applyBlockProperties(state, step);
        ensureSceneCanShowRange(scene, context, pos1, pos2, !state.isAir());
        scene.world().replaceBlocks(selection, state, particles);
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
        if (selection != null) {
            context.sections.hide(scene, selection, step.durationOrDefault(20), parseDirection(step.direction));
        }
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
            return;
        }

        SceneSectionOwnership.Reveal reveal = context.sections.prepare(scene, selection, false);
        ElementLink<WorldSectionElement> created = context.sections.show(scene, selection, duration, direction,
                existing == null ? null : () -> scene.getScene().resolve(existing), reveal);
        if (existing == null) {
            context.sectionLinks.put(linkId, created);
        }
    }

    private void applyAnimatedShowSectionAndMerge(SceneBuilder scene, StepContext context,
                                                  String linkId,
                                                  @Nullable ElementLink<WorldSectionElement> existing,
                                                  BlockPos pos1, BlockPos pos2,
                                                  String entranceAnimation, Direction entryDirection,
                                                  int rowDuration, int rowInterval, boolean smartDisplay) {
        Selection selection = scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
        SceneSectionOwnership.Reveal reveal = context.sections.prepare(scene, selection, smartDisplay);
        showAnimatedGroups(scene, context, linkId, existing, orderedLayerGroups(pos1, pos2, entranceAnimation),
                entryDirection, rowDuration, rowInterval, reveal);
    }

    private void showAnimatedGroups(SceneBuilder scene, StepContext context, String linkId,
                                    @Nullable ElementLink<WorldSectionElement> existing,
                                    List<List<BlockPos>> groups, Direction direction,
                                    int rowDuration, int rowInterval, SceneSectionOwnership.Reveal reveal) {
        ElementLink<WorldSectionElement> working = existing;
        for (List<BlockPos> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            ElementLink<WorldSectionElement> target = working;
            ElementLink<WorldSectionElement> created = context.sections.show(scene, selectionForGroup(scene, group),
                    rowDuration, direction, target == null ? null : () -> scene.getScene().resolve(target), reveal);
            if (working == null) {
                working = created;
                context.sectionLinks.put(linkId, working);
            }
            if (rowInterval > 0) {
                scene.idle(rowInterval);
            }
        }
    }

    private Selection selectionForGroup(SceneBuilder scene, List<BlockPos> group) {
        Selection selection = scene.getScene().getSceneBuildingUtil().select().position(group.get(0));
        for (int i = 1; i < group.size(); i++) {
            selection = selection.add(scene.getScene().getSceneBuildingUtil().select().position(group.get(i)));
        }
        return selection;
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

    private void applyClearEntities(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);
        ResourceLocation filterLoc = tryParseFilter(step.entity);
        Selection selection = optionalSelectionFromStep(scene, step);
        String linkId = normalizeEntityLinkId(step.linkId);

        if (linkId != null) {
            scheduleEntityClear(scene, linkedEntityTargets(context, linkId), selection, filterLoc, step);
            return;
        }

        if (isFullScene) {
            scheduleEntityClear(scene, null, null, filterLoc, step);
            return;
        }

        if (selection == null) {
            LOGGER.warn("clear_entities missing blockPos/linkId");
            return;
        }
        scheduleEntityClear(scene, null, selection, filterLoc, step);
    }

    private void applyClearItemEntities(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);
        ResourceLocation filterLoc = tryParseFilter(step.item);
        Selection selection = optionalSelectionFromStep(scene, step);
        String linkId = normalizeEntityLinkId(step.linkId);

        if (linkId != null) {
            applyToLinkedEntities(scene, linkedEntityTargets(context, linkId), selection,
                entity -> matchesItemEntity(entity, filterLoc), Entity::discard);
            return;
        }

        if (isFullScene) {
            scene.world().modifyEntities(ItemEntity.class, entity -> {
                if (matchesItemEntity(entity, filterLoc)) {
                    entity.discard();
                }
            });
            return;
        }

        if (selection == null) {
            LOGGER.warn("clear_item_entities missing blockPos/linkId");
            return;
        }
        scene.world().modifyEntitiesInside(ItemEntity.class, selection, entity -> {
            if (matchesItemEntity(entity, filterLoc)) {
                entity.discard();
            }
        });
    }

    private void applyModifyEntitiesNbt(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        CompoundTag patch = null;
        if (step.nbt != null && !step.nbt.isBlank()) {
            patch = parseEntityNbtPatch(step, "modify_entities_nbt");
            if (patch == null) {
                return;
            }
        }
        boolean hasMove = step.offset != null
            && step.offset.size() >= 3
            && new Vec3(step.offset.get(0), step.offset.get(1), step.offset.get(2)).lengthSqr() > 0;
        if (patch == null && !hasMove) {
            LOGGER.warn("modify_entities_nbt missing nbt/offset");
            return;
        }

        ResourceLocation filterLoc = tryParseFilter(step.entity);
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);
        Selection selection = optionalSelectionFromStep(scene, step);
        String linkId = normalizeEntityLinkId(step.linkId);

        if (linkId != null) {
            List<ElementLink<EntityElement>> links = linkedEntityTargets(context, linkId);
            if (patch != null) {
                CompoundTag finalPatch = patch;
                applyToLinkedEntities(scene, links, selection,
                    entity -> matchesNonItemEntity(entity, filterLoc),
                    entity -> mergeEntityNbt(entity, finalPatch));
            }
            scheduleEntityMove(scene, links, selection, filterLoc, step);
            return;
        }

        if (isFullScene) {
            if (patch != null) {
                CompoundTag finalPatch = patch;
                scene.world().modifyEntities(Entity.class, entity -> {
                    if (matchesNonItemEntity(entity, filterLoc)) {
                        mergeEntityNbt(entity, finalPatch);
                    }
                });
            }
            scheduleEntityMove(scene, null, null, filterLoc, step);
            return;
        }

        if (selection == null) {
            LOGGER.warn("modify_entities_nbt missing blockPos/linkId");
            return;
        }
        if (patch != null) {
            CompoundTag finalPatch = patch;
            scene.world().modifyEntitiesInside(Entity.class, selection, entity -> {
                if (matchesNonItemEntity(entity, filterLoc)) {
                    mergeEntityNbt(entity, finalPatch);
                }
            });
        }
        scheduleEntityMove(scene, null, selection, filterLoc, step);
    }

    private void applyModifyItemEntitiesNbt(SceneBuilder scene, DslScene.DslStep step, StepContext context) {
        CompoundTag patch = parseEntityNbtPatch(step, "modify_item_entities_nbt");
        if (patch == null) return;

        ResourceLocation filterLoc = tryParseFilter(step.item);
        boolean isFullScene = Boolean.TRUE.equals(step.fullScene);
        Selection selection = optionalSelectionFromStep(scene, step);
        String linkId = normalizeEntityLinkId(step.linkId);

        Consumer<Entity> itemPatchAction = entity -> {
            ItemEntity itemEntity = (ItemEntity) entity;
            itemEntity.setItem(applyPatchToItemStack(itemEntity.getItem(), patch));
            if (isLikelyEntityPatch(patch)) {
                mergeEntityNbt(itemEntity, patch);
            }
        };

        if (linkId != null) {
            applyToLinkedEntities(scene, linkedEntityTargets(context, linkId), selection,
                entity -> matchesItemEntity(entity, filterLoc), itemPatchAction);
            return;
        }

        if (isFullScene) {
            scene.world().modifyEntities(ItemEntity.class, entity -> {
                if (matchesItemEntity(entity, filterLoc)) {
                    entity.setItem(applyPatchToItemStack(entity.getItem(), patch));
                    if (isLikelyEntityPatch(patch)) {
                        mergeEntityNbt(entity, patch);
                    }
                }
            });
            return;
        }

        if (selection == null) {
            LOGGER.warn("modify_item_entities_nbt missing blockPos/linkId");
            return;
        }
        scene.world().modifyEntitiesInside(ItemEntity.class, selection, entity -> {
            if (matchesItemEntity(entity, filterLoc)) {
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
            net.minecraft.world.item.component.CustomData existing = copy.getOrDefault(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY);
            CompoundTag stackTag = existing.copyTag();
            stackTag.merge(itemPatch.copy());
            copy.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(stackTag));
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

    @Nullable
    private ResourceLocation tryParseFilter(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    private void registerEntityLink(StepContext context, @Nullable String rawLinkId, ElementLink<EntityElement> link) {
        String linkId = normalizeEntityLinkId(rawLinkId);
        if (linkId == null) {
            return;
        }
        context.entityLinks.computeIfAbsent(linkId, key -> new ArrayList<>()).add(link);
    }

    @Nullable
    private String normalizeEntityLinkId(@Nullable String rawLinkId) {
        if (rawLinkId == null) {
            return null;
        }
        String normalized = rawLinkId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<ElementLink<EntityElement>> linkedEntityTargets(StepContext context, String linkId) {
        List<ElementLink<EntityElement>> targets = context.entityLinks.get(linkId);
        return targets == null ? List.of() : List.copyOf(targets);
    }

    private void applyToLinkedEntities(SceneBuilder scene, List<ElementLink<EntityElement>> links,
                                       @Nullable Selection selection,
                                       Predicate<Entity> filter,
                                       Consumer<Entity> action) {
        if (links.isEmpty()) {
            return;
        }
        List<ElementLink<EntityElement>> captured = List.copyOf(links);
        scene.addInstruction(ponderScene -> {
            for (ElementLink<EntityElement> link : captured) {
                EntityElement element = ponderScene.resolve(link);
                if (element == null) {
                    continue;
                }
                element.ifPresent(entity -> {
                    if (entity == null || !entity.isAlive()) {
                        return;
                    }
                    if (selection != null && !selection.test(entity.blockPosition())) {
                        return;
                    }
                    if (!filter.test(entity)) {
                        return;
                    }
                    action.accept(entity);
                });
            }
        });
    }

    private static boolean matchesNonItemEntity(Entity entity, @Nullable ResourceLocation filterLoc) {
        return !(entity instanceof ItemEntity) && matchesEntityType(entity, filterLoc);
    }

    private static boolean matchesEntityType(Entity entity, @Nullable ResourceLocation filterLoc) {
        return filterLoc == null || EntityType.getKey(entity.getType()).equals(filterLoc);
    }

    private static boolean matchesItemEntity(Entity entity, @Nullable ResourceLocation filterLoc) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return false;
        }
        return filterLoc == null || BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem()).equals(filterLoc);
    }

    private void scheduleEntityMove(SceneBuilder scene,
                                    @Nullable List<ElementLink<EntityElement>> linkedTargets,
                                    @Nullable Selection selection,
                                    @Nullable ResourceLocation filterLoc,
                                    DslScene.DslStep step) {
        if (step.offset == null || step.offset.size() < 3) {
            return;
        }

        Vec3 offset = new Vec3(step.offset.get(0), step.offset.get(1), step.offset.get(2));
        if (offset.lengthSqr() == 0) {
            return;
        }

        int duration = step.durationOrDefault(20);
        if (duration <= 0) {
            applyInstantEntityMove(scene, linkedTargets, selection, filterLoc, offset);
            return;
        }

        scene.addInstruction(new EntityMoveInstruction(linkedTargets, selection, filterLoc, offset, duration,
            Boolean.TRUE.equals(step.walkAnimation)));
    }

    private void applyInstantEntityMove(SceneBuilder scene,
                                        @Nullable List<ElementLink<EntityElement>> linkedTargets,
                                        @Nullable Selection selection,
                                        @Nullable ResourceLocation filterLoc,
                                        Vec3 offset) {
        if (linkedTargets != null) {
            applyToLinkedEntities(scene, linkedTargets, selection,
                entity -> matchesNonItemEntity(entity, filterLoc),
                entity -> moveEntityInstant(entity, offset));
            return;
        }

        scene.addInstruction(ponderScene -> ponderScene.forEachWorldEntity(Entity.class, entity -> {
            if (!matchesNonItemEntity(entity, filterLoc)) {
                return;
            }
            if (selection != null && !selection.test(entity.blockPosition())) {
                return;
            }
            moveEntityInstant(entity, offset);
        }));
    }

    private void moveEntityInstant(Entity entity, Vec3 offset) {
        if (entity == null || !entity.isAlive()) {
            return;
        }
        Vec3 targetPos = entity.position().add(offset);
        entity.setPos(targetPos.x, targetPos.y, targetPos.z);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOldPosAndRot();
        stopWalkAnimation(entity);
    }

    private void scheduleEntityClear(SceneBuilder scene,
                                     @Nullable List<ElementLink<EntityElement>> linkedTargets,
                                     @Nullable Selection selection,
                                     @Nullable ResourceLocation filterLoc,
                                     DslScene.DslStep step) {
        Vec3 exitOffset = entityExitOffset(step);
        int duration = step.entranceDuration == null ? 20 : Math.max(0, step.entranceDuration);
        if (exitOffset == null || duration <= 0) {
            applyInstantEntityClear(scene, linkedTargets, selection, filterLoc);
            return;
        }
        scene.addInstruction(new EntityExitInstruction(linkedTargets, selection, filterLoc, exitOffset, duration));
    }

    private void applyInstantEntityClear(SceneBuilder scene,
                                         @Nullable List<ElementLink<EntityElement>> linkedTargets,
                                         @Nullable Selection selection,
                                         @Nullable ResourceLocation filterLoc) {
        if (linkedTargets != null) {
            applyToLinkedEntities(scene, linkedTargets, selection,
                entity -> matchesNonItemEntity(entity, filterLoc),
                Entity::discard);
            return;
        }

        if (selection == null) {
            scene.world().modifyEntities(Entity.class, entity -> {
                if (matchesNonItemEntity(entity, filterLoc)) {
                    entity.discard();
                }
            });
            return;
        }

        scene.world().modifyEntitiesInside(Entity.class, selection, entity -> {
            if (matchesNonItemEntity(entity, filterLoc)) {
                entity.discard();
            }
        });
    }

    @Nullable
    private Vec3 entityEntranceOffset(DslScene.DslStep step) {
        String animation = normalizeEntranceAnimation(step.entranceAnimation);
        if (animation == null || "none".equals(animation)) {
            return null;
        }

        Direction direction = "simultaneous".equals(animation)
            ? parseDirection(step.direction)
            : parseDirection(animation);
        return Vec3.atLowerCornerOf(direction.getNormal()).scale(-0.5d);
    }

    @Nullable
    private Vec3 entityExitOffset(DslScene.DslStep step) {
        String animation = normalizeEntranceAnimation(step.entranceAnimation);
        if (animation == null || "none".equals(animation)) {
            return null;
        }

        Direction direction = "simultaneous".equals(animation)
            ? parseDirection(step.direction)
            : parseDirection(animation);
        return Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5d);
    }

    private static float computeWalkAnimationSpeed(Vec3 totalOffset, int durationTicks) {
        if (durationTicks <= 0) {
            return 0.0f;
        }
        double seconds = durationTicks / 20.0d;
        double blocksPerSecond = totalOffset.length() / seconds;
        return (float) Math.min(1.0d, blocksPerSecond / 5.0d);
    }

    private static void applyWalkAnimation(Entity entity, float speed) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        float clampedSpeed = Math.max(0.0f, Math.min(1.0f, speed));
        livingEntity.walkAnimation.update(clampedSpeed, 0.4f);
    }

    private static void stopWalkAnimation(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        livingEntity.walkAnimation.update(0.0f, 1.0f);
        if (livingEntity instanceof Mob mob) {
            mob.setXxa(0.0f);
            mob.setYya(0.0f);
            mob.setZza(0.0f);
        }
    }

    @Nullable
    private Selection optionalSelectionFromStep(SceneBuilder scene, DslScene.DslStep step) {
        if (step.blockPos == null || step.blockPos.size() < 3) {
            return null;
        }
        BlockPos pos1 = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        BlockPos pos2 = pos1;
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2 = new BlockPos(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        return scene.getScene().getSceneBuildingUtil().select().fromTo(pos1, pos2);
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

        SceneSectionOwnership.Reveal reveal = context.sections.prepare(scene, selection, false);
        ElementLink<WorldSectionElement> created = context.sections.show(scene, selection, 0, Direction.DOWN, null, reveal);
        String key = linkId.isEmpty() ? autoLinkId(context) : linkId;
        context.sectionLinks.put(key, created);

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

}
