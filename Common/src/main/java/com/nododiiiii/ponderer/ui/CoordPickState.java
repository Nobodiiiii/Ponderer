package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.blueprint.RaycastHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.ponder.enums.PonderSpecialTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
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
    /** 1 = waiting for first point, 2 = waiting for second point, 3 = adjust + confirm */
    private static int phase = 0;
    @Nullable private static BlockPos firstPos;
    @Nullable private static BlockPos secondPos;
    @Nullable private static BlockPos selectedPos;
    @Nullable private static Direction selectedFace;
    private static int range = 10;
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
        CoordPickState.secondPos = null;
        CoordPickState.selectedPos = null;
        CoordPickState.selectedFace = null;
        CoordPickState.range = 10;
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

        selectedPos = getCurrentHoverPos(mc);

        // Determine which face is looked at while adjusting finalized box.
        selectedFace = null;
        if (phase >= 3 && firstPos != null && secondPos != null) {
            AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos))
                    .expandTowards(1, 1, 1).inflate(.45f);
            Vec3 projectedView = mc.gameRenderer.getMainCamera().getPosition();
            boolean inside = bb.contains(projectedView);
            RaycastHelper.PredicateTraceResult result =
                    RaycastHelper.rayTraceUntil(mc.player, 70,
                            pos -> inside ^ bb.contains(net.createmod.catnip.math.VecHelper.getCenterOf(pos)));
            selectedFace = result == null || result.missed() ? null
                    : inside ? result.getFacing().getOpposite() : result.getFacing();
        }

        // Render Outliner preview
        AABB previewBox = getPreviewBox();
        if (previewBox != null) {
            Outliner.getInstance().chaseAABB(OUTLINE_SLOT, previewBox)
                    .colored(0x55FF55)
                    .lineWidth(1 / 16f)
                    .withFaceTexture(PonderSpecialTextures.BLANK)
                    .highlightFace(selectedFace);
        }

        // Show action bar prompt
        String promptKey;
        if (phase == 1) {
            promptKey = "ponderer.ui.trigger_editor.pick_prompt.first";
        } else if (phase == 2) {
            promptKey = "ponderer.ui.trigger_editor.pick_prompt.second";
        } else {
            promptKey = "ponderer.ui.trigger_editor.pick_prompt.third";
        }
        mc.player.displayClientMessage(Component.translatable(promptKey), true);
    }

    @Nullable
    private static AABB getPreviewBox() {
        if (phase == 1) {
            // Show single block at hover
            return selectedPos != null ? new AABB(selectedPos) : null;
        } else if (phase == 2) {
            // Show box from firstPos to hover
            if (firstPos == null) return null;
            if (selectedPos == null) return new AABB(firstPos);
            return new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(selectedPos))
                    .expandTowards(1, 1, 1);
        }
        if (firstPos != null && secondPos != null) {
            return new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos))
                    .expandTowards(1, 1, 1);
        }
        return null;
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

        BlockPos pos = selectedPos;
        if (pos == null) {
            BlockHitResult hit = getLookingAtBlock(mc);
            if (hit == null || hit.getType() == HitResult.Type.MISS) {
                return true; // consumed but no block hit
            }
            pos = hit.getBlockPos();
        }
        if (phase == 1) {
            firstPos = pos;
            formSnapshot.put("coord1_x", String.valueOf(pos.getX()));
            formSnapshot.put("coord1_y", String.valueOf(pos.getY()));
            formSnapshot.put("coord1_z", String.valueOf(pos.getZ()));
            phase = 2; // advance to second point
            return true;
        } else if (phase == 2) {
            secondPos = pos;
            phase = 3; // allow ctrl+scroll face adjustment before final confirm
            return true;
        } else if (phase == 3) {
            if (firstPos == null || secondPos == null) {
                return true;
            }
            formSnapshot.put("coord1_x", String.valueOf(firstPos.getX()));
            formSnapshot.put("coord1_y", String.valueOf(firstPos.getY()));
            formSnapshot.put("coord1_z", String.valueOf(firstPos.getZ()));
            formSnapshot.put("coord2_x", String.valueOf(secondPos.getX()));
            formSnapshot.put("coord2_y", String.valueOf(secondPos.getY()));
            formSnapshot.put("coord2_z", String.valueOf(secondPos.getZ()));
            reopenEditor();
            return true;
        }
        return true;
    }

    /** Handle Ctrl+mouse-wheel while coord pick mode is active. */
    public static boolean handleMouseScrolled(double delta) {
        if (!active) return false;
        if (!net.minecraft.client.gui.screens.Screen.hasControlDown()) return false;
        if (delta == 0) return true;

        // Before second corner is fixed: free aim stepping distance like blueprint.
        if (phase < 3) {
            range = (int) net.minecraft.util.Mth.clamp(range + delta, 1, 100);
            return true;
        }

        if (firstPos == null || secondPos == null || selectedFace == null) {
            return true;
        }

        AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos));
        Vec3 vec = Vec3.atLowerCornerOf(selectedFace.getNormal());
        Vec3 projectedView = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (bb.contains(projectedView)) {
            delta *= -1;
        }
        int intDelta = (int) (delta > 0 ? Math.ceil(delta) : Math.floor(delta));
        int x = (int) vec.x * intDelta;
        int y = (int) vec.y * intDelta;
        int z = (int) vec.z * intDelta;

        AxisDirection axisDirection = selectedFace.getAxisDirection();
        if (axisDirection == AxisDirection.NEGATIVE) {
            bb = bb.move(-x, -y, -z);
        }
        double maxX = Math.max(bb.maxX - x * axisDirection.getStep(), bb.minX);
        double maxY = Math.max(bb.maxY - y * axisDirection.getStep(), bb.minY);
        double maxZ = Math.max(bb.maxZ - z * axisDirection.getStep(), bb.minZ);
        bb = new AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ);

        firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ);
        secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ);

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("item.ponderer.blueprint.dimensions",
                    (int) bb.getXsize() + 1, (int) bb.getYsize() + 1, (int) bb.getZsize() + 1), true);
        }
        return true;
    }

    public static void reset() {
        active = false;
        phase = 0;
        firstPos = null;
        secondPos = null;
        selectedPos = null;
        selectedFace = null;
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

    @Nullable
    private static BlockPos getCurrentHoverPos(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        if (net.minecraft.client.gui.screens.Screen.hasControlDown() && phase < 3) {
            float pt = mc.getFrameTime();
            Vec3 targetVec = mc.player.getEyePosition(pt).add(mc.player.getLookAngle().scale(range));
            return BlockPos.containing(targetVec);
        }
        BlockHitResult hit = getLookingAtBlock(mc);
        return hit != null ? hit.getBlockPos() : null;
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
