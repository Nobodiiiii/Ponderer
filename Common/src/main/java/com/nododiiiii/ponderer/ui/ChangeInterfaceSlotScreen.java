package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChangeInterfaceSlotScreen extends AbstractStepEditorScreen {
    private final LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> slotBindings = new LinkedHashMap<>();
    private boolean initialBindingsLoaded = false;

    public ChangeInterfaceSlotScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.change_interface_slot"), scene, sceneIndex, parent);
    }

    public ChangeInterfaceSlotScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                     int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.change_interface_slot"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() {
        return 1;
    }

    @Override
    protected void buildForm() {
        beginForm();
        addFormCycleButton(
            "ponderer.ui.change_interface_slot.slots",
            "ponderer.ui.change_interface_slot.slots.tooltip",
            70,
            this::openSlotEditor,
            () -> UIText.of("ponderer.ui.change_interface_slot.edit"));
        refreshInfoMessage();
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        slotBindings.clear();
        if (step != null && step.interfaceSlots != null) {
            for (DslScene.InterfaceSlotBinding binding : step.interfaceSlots) {
                if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                    continue;
                }
                slotBindings.put(binding.slotIndex,
                    new DslScene.InterfaceSlotBinding(
                        binding.slotIndex,
                        binding.slotX,
                        binding.slotY,
                        binding.ingredientId,
                        binding.ingredientKind));
            }
        }
        initialBindingsLoaded = true;
        refreshInfoMessage();
    }

    @Override
    protected String getStepType() {
        return "change_interface_slot";
    }

    @Override
    protected Map<String, String> snapshotForm() {
        ensureInitialBindingsLoaded();
        Map<String, String> snapshot = new LinkedHashMap<>();
        InterfaceSlotEditState.writeBindingsToSnapshot(snapshot, slotBindings);
        return snapshot;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        slotBindings.clear();
        slotBindings.putAll(InterfaceSlotEditState.readBindingsFromSnapshot(snapshot));
        initialBindingsLoaded = true;
        refreshInfoMessage();
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        ensureInitialBindingsLoaded();
        errorMessage = null;
        if (slotBindings.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.change_interface_slot.error.empty");
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "change_interface_slot";
        step.duration = null;
        step.interfaceSlots = new ArrayList<>(slotBindings.values());
        step.interfaceSlots.sort(Comparator.comparingInt(binding -> binding.slotIndex == null ? Integer.MAX_VALUE : binding.slotIndex));
        return step;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.change_interface_slot");
    }

    private void openSlotEditor() {
        ensureInitialBindingsLoaded();
        errorMessage = null;
        infoMessage = null;

        Map<String, String> snapshot = snapshotForm();
        snapshot.put("_keyFrame", String.valueOf(attachKeyFrame));
        InterfaceSlotEditState.startEdit(
            snapshot,
            slotBindings,
            getStepType(),
            editIndex,
            insertAfterIndex,
            scene,
            sceneIndex,
            parent
        );
        InterfaceSlotEditState.openPonderUIForEdit();
    }

    private void ensureInitialBindingsLoaded() {
        if (initialBindingsLoaded) {
            return;
        }

        slotBindings.clear();
        if (isEditMode() && existingStep != null && existingStep.interfaceSlots != null) {
            for (DslScene.InterfaceSlotBinding binding : existingStep.interfaceSlots) {
                if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                    continue;
                }
                slotBindings.put(binding.slotIndex,
                    new DslScene.InterfaceSlotBinding(
                        binding.slotIndex,
                        binding.slotX,
                        binding.slotY,
                        binding.ingredientId,
                        binding.ingredientKind));
            }
        } else {
            slotBindings.putAll(deriveInitialBindings());
        }
        initialBindingsLoaded = true;
        refreshInfoMessage();
    }

    private LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> deriveInitialBindings() {
        LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> result = new LinkedHashMap<>();
        if (scene == null || scene.scenes == null || sceneIndex < 0 || sceneIndex >= scene.scenes.size()) {
            return result;
        }

        List<DslScene.DslStep> steps = scene.scenes.get(sceneIndex).steps;
        if (steps == null || steps.isEmpty()) {
            return result;
        }

        int limit = insertAfterIndex >= 0 ? Math.min(insertAfterIndex, steps.size() - 1) : steps.size() - 1;
        for (int i = 0; i <= limit; i++) {
            DslScene.DslStep step = steps.get(i);
            if (step == null || step.type == null) {
                continue;
            }
            if ("show_interface".equalsIgnoreCase(step.type)) {
                result.clear();
                continue;
            }
            if (!"change_interface_slot".equalsIgnoreCase(step.type) || step.interfaceSlots == null) {
                continue;
            }

            result.clear();
            for (DslScene.InterfaceSlotBinding binding : step.interfaceSlots) {
                if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                    continue;
                }
                result.put(binding.slotIndex,
                    new DslScene.InterfaceSlotBinding(
                        binding.slotIndex,
                        binding.slotX,
                        binding.slotY,
                        binding.ingredientId,
                        binding.ingredientKind));
            }
        }
        return result;
    }

    private void refreshInfoMessage() {
        ensureInitialBindingsCountSafe();
        infoMessage = UIText.of("ponderer.ui.change_interface_slot.info", slotBindings.size());
    }

    private void ensureInitialBindingsCountSafe() {
        if (!initialBindingsLoaded) {
            return;
        }
    }
}
