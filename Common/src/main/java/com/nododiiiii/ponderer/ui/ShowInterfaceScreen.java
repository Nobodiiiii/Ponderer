package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShowInterfaceScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget blockField;
    private HintableTextFieldWidget durationField;

    @Nullable
    private List<Integer> contextPos;
    @Nullable
    private String contextFace;
    @Nullable
    private List<Double> contextHit;

    public ShowInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_interface"), scene, sceneIndex, parent);
    }

    public ShowInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_interface"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected int getFormRowCount() {
        return 2;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.show_interface");
    }

    @Override
    protected void buildForm() {
        beginForm();
        var blockPick = addFormTextFieldWithJeiAndBlockPick(
            "ponderer.ui.show_interface.block",
            "ponderer.ui.show_interface.block.tooltip",
            UIText.of("ponderer.ui.show_interface.block.hint"),
            IdFieldMode.BLOCK,
            "show_interface_nbt");
        blockField = blockPick.field();
        blockField.setEditable(false);
        blockField.setCanLoseFocus(true);
        if (blockPick.jeiBtn() != null) {
            blockPick.jeiBtn().visible = false;
            blockPick.jeiBtn().active = false;
        }

        durationField = addFormNumberField(
            "ponderer.ui.duration",
            "ponderer.ui.duration.tooltip.show_interface",
            "60",
            60,
            "ponderer.ui.ticks");
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) {
            blockField.setValue(step.block);
        }
        if (step.duration != null) {
            durationField.setValue(String.valueOf(step.duration));
        }
        contextPos = step.blockPos;
        contextFace = step.direction;
        contextHit = step.point;
    }

    @Override
    protected String getStepType() {
        return "show_interface";
    }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("block", blockField.getValue());
        snapshot.put("duration", durationField.getValue());
        if (contextPos != null && contextPos.size() >= 3) {
            snapshot.put("ctx_pos", contextPos.get(0) + "," + contextPos.get(1) + "," + contextPos.get(2));
        }
        if (contextFace != null) {
            snapshot.put("ctx_face", contextFace);
        }
        if (contextHit != null && contextHit.size() >= 3) {
            snapshot.put("ctx_hit", contextHit.get(0) + "," + contextHit.get(1) + "," + contextHit.get(2));
        }
        return snapshot;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("block")) {
            blockField.setValue(snapshot.get("block"));
        }
        if (snapshot.containsKey("duration")) {
            durationField.setValue(snapshot.get("duration"));
        }

        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_ID_KEY)) {
            blockField.setValue(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_ID_KEY));
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_POS_KEY)) {
            contextPos = parseInt3(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_POS_KEY));
        } else if (snapshot.containsKey("ctx_pos")) {
            contextPos = parseInt3(snapshot.get("ctx_pos"));
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_FACE_KEY)) {
            contextFace = snapshot.get(NbtPickState.SNAPSHOT_BLOCK_FACE_KEY);
        } else if (snapshot.containsKey("ctx_face")) {
            contextFace = snapshot.get("ctx_face");
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_HIT_KEY)) {
            contextHit = parseDouble3(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_HIT_KEY));
        } else if (snapshot.containsKey("ctx_hit")) {
            contextHit = parseDouble3(snapshot.get("ctx_hit"));
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String blockId = blockField.getValue().trim();
        if (blockId.isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.error.required_field", UIText.of("ponderer.ui.show_interface.block"));
            return null;
        }

        int duration = Math.max(1, parseIntOr(durationField.getValue(), 60));
        if (contextPos == null || contextPos.size() < 3) {
            errorMessage = UIText.of("ponderer.ui.show_interface.error.no_context");
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "show_interface";
        step.block = blockId;
        step.duration = duration;
        step.blockPos = List.of(contextPos.get(0), contextPos.get(1), contextPos.get(2));
        if (contextFace != null && !contextFace.isBlank()) {
            step.direction = contextFace;
        }
        if (contextHit != null && contextHit.size() >= 3) {
            step.point = List.of(contextHit.get(0), contextHit.get(1), contextHit.get(2));
        }
        return step;
    }

    @Nullable
    private static List<Integer> parseInt3(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length < 3) return null;
            return List.of(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static List<Double> parseDouble3(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length < 3) return null;
            return List.of(
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()));
        } catch (Exception ignored) {
            return null;
        }
    }
}