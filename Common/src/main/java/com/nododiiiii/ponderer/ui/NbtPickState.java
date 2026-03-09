package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures NBT from real world by middle-clicking a block or entity and restores the editor screen.
 */
public final class NbtPickState {

    public static final String SNAPSHOT_NOTICE_KEY = "_nbt_pick_notice";
    public static final String SNAPSHOT_BLOCK_ID_KEY = "_block_id";
    public static final String SNAPSHOT_ENTITY_ID_KEY = "_entity_id";

    private static boolean active = false;
    private static String targetKey;
    private static boolean captureBlockId = false;
    private static Map<String, String> formSnapshot = new HashMap<>();
    private static String stepType;
    private static int editIndex = -1;
    private static int insertAfterIndex = -1;
    private static DslScene scene;
    private static int sceneIndex;
    private static SceneEditorScreen parent;

    private NbtPickState() {}

    public static void startPick(Map<String, String> snapshot,
                                 String targetKey,
                                 String stepType,
                                 int editIndex,
                                 int insertAfterIndex,
                                 DslScene scene,
                                 int sceneIndex,
                                 SceneEditorScreen parent) {
        startPick(snapshot, targetKey, false, stepType, editIndex, insertAfterIndex, scene, sceneIndex, parent);
    }

    public static void startPick(Map<String, String> snapshot,
                                 String targetKey,
                                 boolean captureBlockId,
                                 String stepType,
                                 int editIndex,
                                 int insertAfterIndex,
                                 DslScene scene,
                                 int sceneIndex,
                                 SceneEditorScreen parent) {
        NbtPickState.active = true;
        NbtPickState.targetKey = targetKey;
        NbtPickState.captureBlockId = captureBlockId;
        NbtPickState.formSnapshot = new HashMap<>(snapshot);
        NbtPickState.stepType = stepType;
        NbtPickState.editIndex = editIndex;
        NbtPickState.insertAfterIndex = insertAfterIndex;
        NbtPickState.scene = scene;
        NbtPickState.sceneIndex = sceneIndex;
        NbtPickState.parent = parent;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean handleUseClick() {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true;
        }

        CaptureResult result = captureFromHit(mc.level, hit);
        if (result == null) {
            return true;
        }

        if (!result.nbt.isEmpty()) {
            formSnapshot.put(targetKey, result.nbt.toString());
        } else {
            // Explicitly clear the target field when capture result has empty NBT ({}).
            formSnapshot.put(targetKey, "");
        }
        formSnapshot.put(SNAPSHOT_NOTICE_KEY, result.name);

        // If block ID was captured, fill it into the snapshot
        if (captureBlockId && result.blockId != null) {
            formSnapshot.put(SNAPSHOT_BLOCK_ID_KEY, result.blockId);
        }
        if (result.entityId != null) {
            formSnapshot.put(SNAPSHOT_ENTITY_ID_KEY, result.entityId);
        }

        // If properties were captured, fill them into the snapshot
        if (result.blockProperties != null) {
            List<Map.Entry<String, String>> entries = new ArrayList<>(result.blockProperties.entrySet());
            formSnapshot.put("prop_count", String.valueOf(entries.size()));
            for (int i = 0; i < entries.size(); i++) {
                formSnapshot.put("prop_key_" + i, entries.get(i).getKey());
                formSnapshot.put("prop_val_" + i, entries.get(i).getValue());
            }
        }

        reopenEditor();
        return true;
    }

    public static void cancelPick() {
        if (!active) return;
        reopenEditor();
    }

    public static void reset() {
        active = false;
        formSnapshot.clear();
    }

    @Nullable
    private static CaptureResult captureFromHit(Level level, HitResult hit) {
        if (hit instanceof EntityHitResult ehr) {
            Entity entity = ehr.getEntity();
            CompoundTag nbt = new CompoundTag();
            entity.saveWithoutId(nbt);
            sanitizeCapturedEntityNbt(nbt);
            String name = entity.getDisplayName().getString();
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            return new CaptureResult(nbt, name, null, null, entityId);
        }

        if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);

            // Always capture block state properties
            Map<String, String> props = new HashMap<>();
            for (Property<?> prop : state.getProperties()) {
                props.put(prop.getName(), getPropertyValueString(state, prop));
            }

            // Capture block entity NBT if present
            CompoundTag nbt = be != null ? be.saveWithoutMetadata(level.registryAccess()) : new CompoundTag();
            String name = state.getBlock().getName().getString();
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return new CaptureResult(nbt, name, props.isEmpty() ? null : props, blockId, null);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueString(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    private static void sanitizeCapturedEntityNbt(CompoundTag nbt) {
        // Runtime pose/identity fields from real world can place entities out of scene
        // or cause unpredictable behavior after replay in ponder scenes.
        nbt.remove("Pos");
        nbt.remove("Motion");
        nbt.remove("Rotation");
        nbt.remove("UUID");
        nbt.remove("UUIDMost");
        nbt.remove("UUIDLeast");
        nbt.remove("PortalCooldown");
        nbt.remove("OnGround");
        nbt.remove("FallDistance");
        nbt.remove("Air");
        nbt.remove("Fire");
        nbt.remove("HurtTime");
        nbt.remove("DeathTime");
    }

    private static void reopenEditor() {
        AbstractStepEditorScreen editor;
        if (editIndex >= 0) {
            List<DslScene.DslStep> steps = getStepsForScene();
            DslScene.DslStep existingStep = (steps != null && editIndex < steps.size())
                    ? steps.get(editIndex) : null;
            editor = StepEditorFactory.createEditScreen(existingStep, editIndex, scene, sceneIndex, parent);
        } else {
            editor = StepEditorFactory.createAddScreen(stepType, scene, sceneIndex, parent);
        }

        active = false;

        if (editor != null) {
            editor.setInsertAfterIndex(insertAfterIndex);
            editor.setPendingPickRestore(formSnapshot);
            Minecraft.getInstance().setScreen(editor);
        } else {
            formSnapshot.clear();
        }
    }

    @Nullable
    private static List<DslScene.DslStep> getStepsForScene() {
        if (scene == null) return null;
        if (scene.scenes != null && !scene.scenes.isEmpty()) {
            if (sceneIndex >= 0 && sceneIndex < scene.scenes.size()) {
                return scene.scenes.get(sceneIndex).steps;
            }
        }
        return null;
    }

    private record CaptureResult(CompoundTag nbt, String name, @Nullable Map<String, String> blockProperties,
                                 @Nullable String blockId, @Nullable String entityId) {}
}
