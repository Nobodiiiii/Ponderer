package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static singleton that manages the "pick coordinate from scene" workflow.
 * <p>
 * Flow: editor screen saves form state -> closes -> PonderUI enters pick mode ->
 * user clicks a block -> editor screen is re-opened with restored form + picked coordinate.
 */
public final class PickState {

    /** Which XYZ field group the pick result should be written to. */
    public enum TargetField {
        /** First position (blockPos / pos) */
        POS1,
        /** Second position (blockPos2 / to) */
        POS2,
        /** Entity lookAt target */
        LOOK_AT,
        /** Display point (text/controls) */
        POINT,
        /** Display point anchored to UI space (normalized -2..2). */
        UI_POINT
    }

    // -- state fields --
    private static boolean active = false;
    private static TargetField targetField;
    private static Map<String, String> formSnapshot = new HashMap<>();
    @Nullable
    private static StepEditorContext context;
    /** For non-block fields (entity pos, text point, etc.), add 0.5 to get block center. */
    private static boolean useHalfOffset = false;

    @Nullable
    private static BlockPos pickedPos;
    @Nullable
    private static Direction pickedFace;
    @Nullable
    private static Double pickedUiX;
    @Nullable
    private static Double pickedUiY;

    private PickState() {}

    // -- API --

    /**
     * Enter pick mode: save the current form state and close the editor.
     *
     * @param target       which XYZ field group to fill
     * @param snapshot     current form field values (fieldName -> value)
     * @param stepType     the step type string (e.g. "set_block")
     * @param editIndex    index of existing step being edited, or -1 for add mode
     * @param insertAfterIndex index after which to insert (for add mode)
     * @param scene        current DslScene
     * @param sceneIndex   scene index
     * @param parent       the SceneEditorScreen to return to after editing
     * @param halfOffset   whether to add +0.5 offset (entity/text fields use block center)
     */
    public static void startPick(TargetField target, Map<String, String> snapshot,
                                  String stepType, int editIndex, int insertAfterIndex,
                                  DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                  boolean halfOffset) {
        PickState.active = true;
        PickState.targetField = target;
        PickState.formSnapshot = new HashMap<>(snapshot);
        PickState.context = new StepEditorContext(stepType, editIndex, insertAfterIndex, scene, sceneIndex, parent);
        PickState.useHalfOffset = halfOffset;
        PickState.pickedPos = null;
    }

    /**
     * Complete the pick: store the result and re-open the editor with restored form state.
     *
     * @param pos  the picked block position
     * @param face the block face that was clicked (used for face-aware offset)
     */
    public static void completePick(BlockPos pos, Direction face) {
        if (!active) return;
        pickedPos = pos;
        pickedFace = face;
        pickedUiX = null;
        pickedUiY = null;
        reopenEditor();
    }

    public static void completeUiPick(double normalizedX, double normalizedY) {
        if (!active) return;
        pickedPos = null;
        pickedFace = null;
        pickedUiX = normalizedX;
        pickedUiY = normalizedY;
        reopenEditor();
    }

    /**
     * Cancel the pick: re-open the editor with the previously saved form state, no coordinate change.
     */
    public static void cancelPick() {
        if (!active) return;
        pickedPos = null;
        pickedUiX = null;
        pickedUiY = null;
        reopenEditor();
    }

    /**
     * Re-open the step editor screen, restoring saved form state and applying any picked coordinate.
     */
    private static void reopenEditor() {
        // Determine the suffix keys for the target field
        String xKey, yKey, zKey;
        switch (targetField) {
            case POS2 -> { xKey = "pos2X"; yKey = "pos2Y"; zKey = "pos2Z"; }
            case LOOK_AT -> { xKey = "lookAtX"; yKey = "lookAtY"; zKey = "lookAtZ"; }
            case POINT, UI_POINT -> { xKey = "pointX"; yKey = "pointY"; zKey = "pointZ"; }
            default -> { xKey = "posX"; yKey = "posY"; zKey = "posZ"; }
        }

        // Write picked coordinate into the snapshot
        if (targetField == TargetField.UI_POINT && pickedUiX != null && pickedUiY != null) {
            formSnapshot.put(xKey, formatUiCoord(pickedUiX));
            formSnapshot.put(yKey, formatUiCoord(pickedUiY));
            formSnapshot.put(zKey, "0.000");
        } else if (pickedPos != null) {
            if (useHalfOffset && pickedFace != null) {
                // Offset only the two axes parallel to the face, not the perpendicular one.
                // E.g. clicking the top face (UP, axis=Y): offset X+0.5, Z+0.5, Y unchanged.
                Direction.Axis faceAxis = pickedFace.getAxis();
                double ox = faceAxis != Direction.Axis.X ? pickedPos.getX() + 0.5 : pickedPos.getX();
                double oy = faceAxis != Direction.Axis.Y ? pickedPos.getY() + 0.5 : pickedPos.getY();
                double oz = faceAxis != Direction.Axis.Z ? pickedPos.getZ() + 0.5 : pickedPos.getZ();
                formSnapshot.put(xKey, String.valueOf(ox));
                formSnapshot.put(yKey, String.valueOf(oy));
                formSnapshot.put(zKey, String.valueOf(oz));
            } else {
                formSnapshot.put(xKey, String.valueOf(pickedPos.getX()));
                formSnapshot.put(yKey, String.valueOf(pickedPos.getY()));
                formSnapshot.put(zKey, String.valueOf(pickedPos.getZ()));
            }
        }

        // Build the editor screen via StepEditorFactory
        StepEditorContext reopenContext = context;
        active = false;
        pickedPos = null;
        pickedFace = null;
        pickedUiX = null;
        pickedUiY = null;
        context = null;
        if (reopenContext != null) {
            reopenContext.reopenEditor(formSnapshot);
        } else {
            formSnapshot.clear();
        }
    }

    /**
     * Create and navigate to PonderUI for coordinate picking.
     * Attempts to navigate to the correct scene matching the one being edited.
     */
    public static void openPonderUIForPick() {
        if (!active || context == null) return;

        ResourceLocation itemId = getItemId();
        if (itemId == null) {
            // Fallback: cancel pick if we can't find the item
            cancelPick();
            return;
        }

        PonderUI ponderUI = PonderUI.of(itemId);
        PonderUIAccessor accessor = (PonderUIAccessor) ponderUI;

        // Try to navigate PonderUI to the scene matching our DslScene + sceneIndex
        List<PonderScene> ponderScenes = accessor.ponderer$getScenes();
        for (int i = 0; i < ponderScenes.size(); i++) {
            SceneRuntime.SceneMatch match = SceneRuntime.findBySceneId(ponderScenes.get(i).getId());
            if (match != null && match.sceneIndex() == context.sceneIndex()
                    && match.scene().id.equals(context.scene().id)) {
                accessor.ponderer$setIndex(i);
                accessor.ponderer$getLazyIndex().startWithValue(i);
                ponderScenes.get(i).begin();
                break;
            }
        }

        Minecraft.getInstance().setScreen(ponderUI);
    }

    @Nullable
    private static ResourceLocation getItemId() {
        if (context == null || context.scene().items == null || context.scene().items.isEmpty()) return null;
        return ResourceLocation.tryParse(context.scene().items.get(0));
    }

    // -- Queries --

    public static boolean isActive() {
        return active;
    }

    public static TargetField getTargetField() {
        return targetField;
    }

    public static boolean isUiPointPickActive() {
        return active && targetField == TargetField.UI_POINT;
    }

    public static boolean isHalfOffset() {
        return useHalfOffset;
    }

    /**
     * Force-reset pick state (safety valve, e.g. when PonderUI closes unexpectedly).
     */
    public static void reset() {
        active = false;
        pickedPos = null;
        pickedFace = null;
        pickedUiX = null;
        pickedUiY = null;
        context = null;
        formSnapshot.clear();
    }

    private static String formatUiCoord(double value) {
        double clamped = Math.max(-2.0, Math.min(2.0, value));
        double rounded = Math.round(clamped * 1000.0) / 1000.0;
        if (Math.abs(rounded) < 0.0005) {
            rounded = 0.0;
        }
        return String.format(Locale.ROOT, "%.3f", rounded);
    }
}
