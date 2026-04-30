package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.network.CaptureBlockEntityNbtRequestPayload;
import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.Vec3;

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
    public static final String SNAPSHOT_BLOCK_POS_KEY = "_block_pos";
    public static final String SNAPSHOT_BLOCK_FACE_KEY = "_block_face";
    public static final String SNAPSHOT_BLOCK_HIT_KEY = "_block_hit";
    public static final String SNAPSHOT_BLOCK_INSIDE_KEY = "_block_inside";

    private static boolean active = false;
    private static String targetKey;
    private static boolean captureBlockId = false;
    private static Map<String, String> formSnapshot = new HashMap<>();
    @Nullable
    private static SnapshotReturnContext context;
    @Nullable
    private static BlockPos pendingServerBlockPos;
    private static boolean awaitingServerBlockEntityNbt = false;

    private NbtPickState() {}

    public static void startPick(Map<String, String> snapshot,
                                 String targetKey,
                                 boolean captureBlockId,
                                 SnapshotReturnContext context) {
        NbtPickState.active = true;
        NbtPickState.awaitingServerBlockEntityNbt = false;
        NbtPickState.pendingServerBlockPos = null;
        NbtPickState.targetKey = targetKey;
        NbtPickState.captureBlockId = captureBlockId;
        NbtPickState.formSnapshot = new HashMap<>(snapshot);
        NbtPickState.context = context;
    }

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
        NbtPickState.awaitingServerBlockEntityNbt = false;
        NbtPickState.pendingServerBlockPos = null;
        NbtPickState.targetKey = targetKey;
        NbtPickState.captureBlockId = captureBlockId;
        NbtPickState.formSnapshot = new HashMap<>(snapshot);
        NbtPickState.context = new StepEditorContext(stepType, editIndex, insertAfterIndex, scene, sceneIndex, parent);
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean handleUseClick() {
        if (!active) return false;
        if (awaitingServerBlockEntityNbt) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true;
        }

        if (hit instanceof BlockHitResult bhr) {
            CaptureResult localResult = captureClientBlockContext(mc.level, bhr);
            if (localResult == null) {
                return true;
            }

            applyCaptureMetadata(localResult, false);
            if (mc.level.getBlockEntity(bhr.getBlockPos()) == null) {
                applyCapturedNbt(localResult.nbt);
                reopenEditor();
            } else {
                awaitingServerBlockEntityNbt = true;
                pendingServerBlockPos = bhr.getBlockPos().immutable();
                PondererServices.NETWORK.sendToServer(new CaptureBlockEntityNbtRequestPayload(pendingServerBlockPos));
            }
            return true;
        }

        CaptureResult result = captureFromHit(mc.level, hit);
        if (result == null) {
            return true;
        }

        applyCaptureMetadata(result, true);
        applyCapturedNbt(result.nbt);
        reopenEditor();
        return true;
    }

    public static void cancelPick() {
        if (!active) return;
        reopenEditor();
    }

    public static void reset() {
        active = false;
        awaitingServerBlockEntityNbt = false;
        pendingServerBlockPos = null;
        context = null;
        formSnapshot.clear();
    }

    public static void handleServerBlockEntityCapture(BlockPos pos, @Nullable CompoundTag nbt) {
        if (!active || !awaitingServerBlockEntityNbt || pendingServerBlockPos == null || !pendingServerBlockPos.equals(pos)) {
            return;
        }

        awaitingServerBlockEntityNbt = false;
        pendingServerBlockPos = null;
        applyCapturedNbt(nbt == null ? new CompoundTag() : nbt);
        reopenEditor();
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
            return new CaptureResult(nbt, name, null, null, entityId, null, null, null, null);
        }

        if (hit instanceof BlockHitResult bhr) {
            return captureClientBlockContext(level, bhr);
        }

        return null;
    }

    @Nullable
    private static CaptureResult captureClientBlockContext(Level level, BlockHitResult bhr) {
        BlockPos pos = bhr.getBlockPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        Map<String, String> props = new HashMap<>();
        for (Property<?> prop : state.getProperties()) {
            props.put(prop.getName(), getPropertyValueString(state, prop));
        }

        CompoundTag nbt = be != null ? be.saveWithoutMetadata(level.registryAccess()) : new CompoundTag();
        String name = state.getBlock().getName().getString();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return new CaptureResult(nbt, name, props.isEmpty() ? null : props, blockId, null,
            pos.immutable(), bhr.getDirection(), bhr.getLocation(), bhr.isInside());
    }

    private static void applyCaptureMetadata(CaptureResult result, boolean includeEntityId) {
        formSnapshot.put(SNAPSHOT_NOTICE_KEY, result.name);

        if (captureBlockId && result.blockId != null) {
            formSnapshot.put(SNAPSHOT_BLOCK_ID_KEY, result.blockId);
        }
        if (result.blockPos != null) {
            formSnapshot.put(SNAPSHOT_BLOCK_POS_KEY,
                result.blockPos.getX() + "," + result.blockPos.getY() + "," + result.blockPos.getZ());
        }
        if (result.blockFace != null) {
            formSnapshot.put(SNAPSHOT_BLOCK_FACE_KEY, result.blockFace.getName());
        }
        if (result.hitLocation != null) {
            formSnapshot.put(SNAPSHOT_BLOCK_HIT_KEY,
                result.hitLocation.x + "," + result.hitLocation.y + "," + result.hitLocation.z);
        }
        if (result.hitInside != null) {
            formSnapshot.put(SNAPSHOT_BLOCK_INSIDE_KEY, String.valueOf(result.hitInside));
        }
        if (includeEntityId && result.entityId != null) {
            formSnapshot.put(SNAPSHOT_ENTITY_ID_KEY, result.entityId);
        }

        if (result.blockProperties != null) {
            List<Map.Entry<String, String>> entries = new ArrayList<>(result.blockProperties.entrySet());
            formSnapshot.put("prop_count", String.valueOf(entries.size()));
            for (int i = 0; i < entries.size(); i++) {
                formSnapshot.put("prop_key_" + i, entries.get(i).getKey());
                formSnapshot.put("prop_val_" + i, entries.get(i).getValue());
            }
        }
    }

    private static void applyCapturedNbt(@Nullable CompoundTag nbt) {
        if (nbt != null && !nbt.isEmpty()) {
            formSnapshot.put(targetKey, nbt.toString());
        } else {
            formSnapshot.put(targetKey, "");
        }
    }

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
        SnapshotReturnContext reopenContext = context;
        active = false;
        awaitingServerBlockEntityNbt = false;
        pendingServerBlockPos = null;
        context = null;

        if (reopenContext != null) {
            reopenContext.reopenEditor(formSnapshot);
        } else {
            formSnapshot.clear();
        }
    }

    private record CaptureResult(CompoundTag nbt, String name, @Nullable Map<String, String> blockProperties,
                                 @Nullable String blockId, @Nullable String entityId,
                                 @Nullable BlockPos blockPos, @Nullable Direction blockFace,
                                 @Nullable Vec3 hitLocation, @Nullable Boolean hitInside) {}
}
