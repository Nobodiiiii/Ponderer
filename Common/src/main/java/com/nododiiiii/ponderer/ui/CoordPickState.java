package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.blueprint.RaycastHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.ponder.enums.PonderSpecialTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Coordinate picking from the real world (middle-click) for the TriggerEditorScreen.
 * Picks two points sequentially: point 1 → point 2, with live Outliner preview.
 */
public final class CoordPickState {

    private static boolean active = false;
    /** 1 = waiting for first point, 2 = waiting for second point */
    private static int phase = 0;
    @Nullable private static BlockPos firstPos;
    private static Map<String, String> formSnapshot = new HashMap<>();
    private static DslScene scene;
    private static int sceneIndex;
    private static SceneEditorScreen parent;
    private static final Object OUTLINE_SLOT = new Object();

    private static final double PICK_RANGE = 75;

    private CoordPickState() {}

    /** Start picking both coordinates sequentially. */
    public static void startPick(Map<String, String> snapshot,
                                 DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        CoordPickState.active = true;
        CoordPickState.phase = 1;
        CoordPickState.firstPos = null;
        CoordPickState.formSnapshot = new HashMap<>(snapshot);
        CoordPickState.scene = scene;
        CoordPickState.sceneIndex = sceneIndex;
        CoordPickState.parent = parent;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Called every client tick from the mixin to show prompts and render preview.
     */
    public static void onClientTick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // Render Outliner preview
        BlockHitResult lookingAt = getLookingAtBlock(mc);
        BlockPos hoverPos = lookingAt != null ? lookingAt.getBlockPos() : null;
        AABB previewBox = getPreviewBox(hoverPos);
        if (previewBox != null) {
            Outliner.getInstance().chaseAABB(OUTLINE_SLOT, previewBox)
                    .colored(0x55FF55)
                    .lineWidth(1 / 16f)
                    .withFaceTexture(PonderSpecialTextures.BLANK);
        }

        // Show action bar prompt
        String promptKey = phase == 1
                ? "ponderer.ui.trigger_editor.pick_prompt.first"
                : "ponderer.ui.trigger_editor.pick_prompt.second";
        mc.player.displayClientMessage(Component.translatable(promptKey), true);
    }

    @Nullable
    private static AABB getPreviewBox(@Nullable BlockPos hoverPos) {
        if (phase == 1) {
            // Show single block at hover
            return hoverPos != null ? new AABB(hoverPos) : null;
        } else {
            // Show box from firstPos to hover
            if (firstPos == null) return null;
            if (hoverPos == null) return new AABB(firstPos);
            return new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(hoverPos))
                    .expandTowards(1, 1, 1);
        }
    }

    /**
     * Called from the mixin intercepting middle-click.
     * @return true if the pick was consumed
     */
    public static boolean handleMiddleClick() {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;

        BlockHitResult hit = getLookingAtBlock(mc);
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true; // consumed but no block hit
        }

        BlockPos pos = hit.getBlockPos();
        if (phase == 1) {
            firstPos = pos;
            formSnapshot.put("coord1_x", String.valueOf(pos.getX()));
            formSnapshot.put("coord1_y", String.valueOf(pos.getY()));
            formSnapshot.put("coord1_z", String.valueOf(pos.getZ()));
            phase = 2; // advance to second point
            return true;
        } else if (phase == 2) {
            formSnapshot.put("coord2_x", String.valueOf(pos.getX()));
            formSnapshot.put("coord2_y", String.valueOf(pos.getY()));
            formSnapshot.put("coord2_z", String.valueOf(pos.getZ()));
            reopenEditor();
            return true;
        }
        return true;
    }

    public static void reset() {
        active = false;
        phase = 0;
        firstPos = null;
        formSnapshot.clear();
    }

    @Nullable
    private static BlockHitResult getLookingAtBlock(Minecraft mc) {
        if (mc.player == null || mc.player.level() == null) return null;
        BlockHitResult hit = RaycastHelper.rayTraceRange(mc.player.level(), mc.player, PICK_RANGE);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }
        return null;
    }

    private static void reopenEditor() {
        active = false;
        phase = 0;
        firstPos = null;
        TriggerEditorScreen editor = new TriggerEditorScreen(scene, sceneIndex, parent);
        editor.setPendingFormRestore(formSnapshot);
        Minecraft.getInstance().setScreen(editor);
    }
}
