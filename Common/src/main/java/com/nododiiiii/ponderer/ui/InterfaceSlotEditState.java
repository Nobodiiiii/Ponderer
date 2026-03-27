package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiOverlayController;
import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the "return to Ponder, drag JEI ingredients into interface slots, then
 * reopen the editor" workflow for change_interface_slot steps.
 */
public final class InterfaceSlotEditState {
    private static boolean active = false;
    private static Map<String, String> formSnapshot = new LinkedHashMap<>();
    private static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> slotBindings = new LinkedHashMap<>();
    private static String stepType;
    private static int editIndex = -1;
    private static int insertAfterIndex = -1;
    private static DslScene scene;
    private static int sceneIndex;
    private static SceneEditorScreen parent;

    private InterfaceSlotEditState() {
    }

    public static void startEdit(Map<String, String> snapshot,
                                 Map<Integer, DslScene.InterfaceSlotBinding> initialBindings,
                                 String stepType,
                                 int editIndex,
                                 int insertAfterIndex,
                                 DslScene scene,
                                 int sceneIndex,
                                 SceneEditorScreen parent) {
        InterfaceSlotEditState.active = true;
        InterfaceSlotEditState.formSnapshot = new LinkedHashMap<>(snapshot);
        InterfaceSlotEditState.slotBindings = copyBindings(initialBindings);
        InterfaceSlotEditState.stepType = stepType;
        InterfaceSlotEditState.editIndex = editIndex;
        InterfaceSlotEditState.insertAfterIndex = insertAfterIndex;
        InterfaceSlotEditState.scene = scene;
        InterfaceSlotEditState.sceneIndex = sceneIndex;
        InterfaceSlotEditState.parent = parent;
    }

    public static void openPonderUIForEdit() {
        if (!active || scene == null) {
            return;
        }

        ResourceLocation itemId = getItemId();
        if (itemId == null) {
            finishAndReopenEditor();
            return;
        }

        PonderUI ponderUI = PonderUI.of(itemId);
        PonderUIAccessor accessor = (PonderUIAccessor) ponderUI;
        List<PonderScene> ponderScenes = accessor.ponderer$getScenes();
        for (int i = 0; i < ponderScenes.size(); i++) {
            SceneRuntime.SceneMatch match = SceneRuntime.findBySceneId(ponderScenes.get(i).getId());
            if (match != null && match.sceneIndex() == sceneIndex && match.scene().id.equals(scene.id)) {
                accessor.ponderer$setIndex(i);
                accessor.ponderer$getLazyIndex().startWithValue(i);
                ponderScenes.get(i).begin();
                break;
            }
        }

        JeiOverlayController.pushEnabled();
        Minecraft.getInstance().setScreen(ponderUI);
    }

    public static boolean isActive() {
        return active;
    }

    public static int bindingCount() {
        return slotBindings.size();
    }

    public static void putBinding(int slotIndex, @Nullable Integer slotX, @Nullable Integer slotY,
                                  String ingredientId, @Nullable String ingredientKind) {
        if (!active || ingredientId == null || ingredientId.isBlank()) {
            return;
        }
        slotBindings.put(slotIndex, new DslScene.InterfaceSlotBinding(slotIndex, slotX, slotY, ingredientId, ingredientKind));
    }

    public static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> getBindingsForRender() {
        return copyBindings(slotBindings);
    }

    @Nullable
    public static DslScene.InterfaceSlotBinding getBinding(int slotIndex) {
        DslScene.InterfaceSlotBinding binding = slotBindings.get(slotIndex);
        if (binding == null) {
            return null;
        }
        return new DslScene.InterfaceSlotBinding(
            binding.slotIndex,
            binding.slotX,
            binding.slotY,
            binding.ingredientId,
            binding.ingredientKind);
    }

    public static boolean removeBinding(int slotIndex) {
        return slotBindings.remove(slotIndex) != null;
    }

    public static void finishAndReopenEditor() {
        if (!active) {
            return;
        }

        writeBindingsToSnapshot(formSnapshot, slotBindings);
        int reopenInsertAfterIndex = insertAfterIndex;

        AbstractStepEditorScreen editor;
        if (editIndex >= 0) {
            List<DslScene.DslStep> steps = getStepsForScene();
            DslScene.DslStep existingStep = (steps != null && editIndex < steps.size()) ? steps.get(editIndex) : null;
            editor = StepEditorFactory.createEditScreen(existingStep, editIndex, scene, sceneIndex, parent);
        } else {
            editor = StepEditorFactory.createAddScreen(stepType, scene, sceneIndex, parent);
        }

        cleanupState();

        if (editor != null) {
            editor.setInsertAfterIndex(reopenInsertAfterIndex);
            editor.setPendingPickRestore(formSnapshot);
            Minecraft.getInstance().setScreen(editor);
        } else {
            formSnapshot.clear();
        }
    }

    public static void reset() {
        if (!active) {
            return;
        }
        cleanupState();
        formSnapshot.clear();
    }

    public static void writeBindingsToSnapshot(Map<String, String> snapshot,
                                               Map<Integer, DslScene.InterfaceSlotBinding> bindings) {
        List<String> oldKeys = new ArrayList<>();
        for (String key : snapshot.keySet()) {
            if (key.startsWith("slot_")) {
                oldKeys.add(key);
            }
        }
        for (String key : oldKeys) {
            snapshot.remove(key);
        }

        snapshot.put("slot_count", String.valueOf(bindings.size()));
        int i = 0;
        for (DslScene.InterfaceSlotBinding binding : bindings.values()) {
            if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                continue;
            }
            snapshot.put("slot_" + i + "_index", String.valueOf(binding.slotIndex));
            if (binding.slotX != null) {
                snapshot.put("slot_" + i + "_x", String.valueOf(binding.slotX));
            }
            if (binding.slotY != null) {
                snapshot.put("slot_" + i + "_y", String.valueOf(binding.slotY));
            }
            snapshot.put("slot_" + i + "_id", binding.ingredientId);
            if (binding.ingredientKind != null && !binding.ingredientKind.isBlank()) {
                snapshot.put("slot_" + i + "_kind", binding.ingredientKind);
            }
            i++;
        }
        snapshot.put("slot_count", String.valueOf(i));
    }

    public static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> readBindingsFromSnapshot(Map<String, String> snapshot) {
        LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> result = new LinkedHashMap<>();
        if (snapshot == null || !snapshot.containsKey("slot_count")) {
            return result;
        }

        int count;
        try {
            count = Integer.parseInt(snapshot.getOrDefault("slot_count", "0"));
        } catch (NumberFormatException e) {
            return result;
        }

        for (int i = 0; i < count; i++) {
            String indexRaw = snapshot.get("slot_" + i + "_index");
            String id = snapshot.get("slot_" + i + "_id");
            if (indexRaw == null || id == null || id.isBlank()) {
                continue;
            }
            try {
                int slotIndex = Integer.parseInt(indexRaw);
                Integer slotX = parseOptionalInt(snapshot.get("slot_" + i + "_x"));
                Integer slotY = parseOptionalInt(snapshot.get("slot_" + i + "_y"));
                String kind = snapshot.get("slot_" + i + "_kind");
                result.put(slotIndex, new DslScene.InterfaceSlotBinding(slotIndex, slotX, slotY, id, kind));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static void cleanupState() {
        active = false;
        slotBindings.clear();
        stepType = null;
        editIndex = -1;
        insertAfterIndex = -1;
        scene = null;
        sceneIndex = -1;
        parent = null;
        JeiOverlayController.popEnabled();
    }

    @Nullable
    private static ResourceLocation getItemId() {
        if (scene == null || scene.items == null || scene.items.isEmpty()) return null;
        return ResourceLocation.tryParse(scene.items.get(0));
    }

    @Nullable
    private static List<DslScene.DslStep> getStepsForScene() {
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return null;
        }
        if (sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return null;
        }
        return scene.scenes.get(sceneIndex).steps;
    }

    private static LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> copyBindings(
        Map<Integer, DslScene.InterfaceSlotBinding> bindings
    ) {
        LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> copy = new LinkedHashMap<>();
        for (DslScene.InterfaceSlotBinding binding : bindings.values()) {
            if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                continue;
            }
            copy.put(binding.slotIndex,
                new DslScene.InterfaceSlotBinding(
                    binding.slotIndex,
                    binding.slotX,
                    binding.slotY,
                    binding.ingredientId,
                    binding.ingredientKind));
        }
        return copy;
    }

    @Nullable
    private static Integer parseOptionalInt(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
