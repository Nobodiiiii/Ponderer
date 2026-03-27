package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShowInterfaceScreen extends AbstractStepEditorScreen {
    private static final String NBT_SNAPSHOT_KEY = "show_interface_nbt";
    private static final BlockPos SANITIZED_CONTEXT_POS = BlockPos.ZERO;

    private HintableTextFieldWidget blockField;

    @Nullable
    private List<Integer> contextPos;
    @Nullable
    private String contextFace;
    @Nullable
    private List<Double> contextHit;
    @Nullable
    private Boolean contextInside;
    @Nullable
    private String capturedNbt;
    @Nullable
    private Map<String, String> capturedBlockProperties;
    private boolean enableNbt = true;

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
            NBT_SNAPSHOT_KEY);
        blockField = blockPick.field();
        blockField.setEditable(false);
        blockField.setCanLoseFocus(true);
        if (blockPick.jeiBtn() != null) {
            blockPick.jeiBtn().visible = false;
            blockPick.jeiBtn().active = false;
        }
        addFormToggle(
            "ponderer.ui.show_interface.enable_nbt",
            "ponderer.ui.show_interface.enable_nbt.tooltip",
            () -> enableNbt,
            () -> enableNbt = !enableNbt);

    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) {
            blockField.setValue(step.block);
        }
        contextPos = step.blockPos;
        contextFace = step.direction;
        contextHit = step.point;
        // show_interface does not use whileSneaking semantics; reuse it to carry hit-inside parity.
        contextInside = step.whileSneaking;
        capturedNbt = step.nbt;
        capturedBlockProperties = copyProps(step.blockProperties);
        enableNbt = !Boolean.FALSE.equals(step.enableNbt);
    }

    @Override
    protected String getStepType() {
        return "show_interface";
    }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("block", blockField.getValue());
        if (contextPos != null && contextPos.size() >= 3) {
            snapshot.put("ctx_pos", contextPos.get(0) + "," + contextPos.get(1) + "," + contextPos.get(2));
        }
        if (contextFace != null) {
            snapshot.put("ctx_face", contextFace);
        }
        if (contextHit != null && contextHit.size() >= 3) {
            snapshot.put("ctx_hit", contextHit.get(0) + "," + contextHit.get(1) + "," + contextHit.get(2));
        }
        if (contextInside != null) {
            snapshot.put("ctx_inside", String.valueOf(contextInside));
        }
        if (capturedNbt != null) {
            snapshot.put(NBT_SNAPSHOT_KEY, capturedNbt);
        }
        snapshot.put("enable_nbt", String.valueOf(enableNbt));
        snapshotProps(snapshot, capturedBlockProperties);
        return snapshot;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("block")) {
            blockField.setValue(snapshot.get("block"));
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
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_INSIDE_KEY)) {
            contextInside = parseBoolean(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_INSIDE_KEY));
        } else if (snapshot.containsKey("ctx_inside")) {
            contextInside = parseBoolean(snapshot.get("ctx_inside"));
        }
        if (snapshot.containsKey(NBT_SNAPSHOT_KEY)) {
            capturedNbt = snapshot.get(NBT_SNAPSHOT_KEY);
        }
        if (snapshot.containsKey("enable_nbt")) {
            enableNbt = parseBoolean(snapshot.get("enable_nbt")) != Boolean.FALSE;
        } else {
            enableNbt = true;
        }
        capturedBlockProperties = parseProps(snapshot);
        restoreNbtPickNotice(snapshot);
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

        if (contextPos == null || contextPos.size() < 3) {
            errorMessage = UIText.of("ponderer.ui.show_interface.error.no_context");
            return null;
        }

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "show_interface";
        step.block = blockId;
        step.duration = null;
        step.blockPos = List.of(SANITIZED_CONTEXT_POS.getX(), SANITIZED_CONTEXT_POS.getY(), SANITIZED_CONTEXT_POS.getZ());
        if (contextFace != null && !contextFace.isBlank()) {
            step.direction = contextFace;
        }
        if (contextHit != null && contextHit.size() >= 3) {
            step.point = List.of(
                contextHit.get(0) - contextPos.get(0),
                contextHit.get(1) - contextPos.get(1),
                contextHit.get(2) - contextPos.get(2));
        }
        if (contextInside != null) {
            // Transport BlockHitResult#isInside parity without adding a new DSL schema dependency.
            step.whileSneaking = contextInside;
        }
        step.enableNbt = enableNbt;
        if (enableNbt && capturedNbt != null && !capturedNbt.isBlank()) {
            step.nbt = sanitizeCapturedNbt(capturedNbt, contextPos);
        }
        if (capturedBlockProperties != null && !capturedBlockProperties.isEmpty()) {
            step.blockProperties = new LinkedHashMap<>(capturedBlockProperties);
        }

        return step;
    }

    private static void snapshotProps(Map<String, String> snapshot, @Nullable Map<String, String> props) {
        if (props == null || props.isEmpty()) {
            return;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(props.entrySet());
        snapshot.put("prop_count", String.valueOf(entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            snapshot.put("prop_key_" + i, entries.get(i).getKey());
            snapshot.put("prop_val_" + i, entries.get(i).getValue());
        }
    }

    @Nullable
    private static Map<String, String> parseProps(Map<String, String> snapshot) {
        if (!snapshot.containsKey("prop_count")) {
            return null;
        }
        int count;
        try {
            count = Integer.parseInt(snapshot.get("prop_count"));
        } catch (NumberFormatException ignored) {
            return null;
        }
        Map<String, String> props = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = snapshot.get("prop_key_" + i);
            String value = snapshot.get("prop_val_" + i);
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            props.put(key, value);
        }
        return props.isEmpty() ? null : props;
    }

    @Nullable
    private static Map<String, String> copyProps(@Nullable Map<String, String> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        return new LinkedHashMap<>(props);
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

    @Nullable
    private static Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(v);
    }

    private static String sanitizeCapturedNbt(String rawNbt, List<Integer> sourcePos) {
        if (sourcePos == null || sourcePos.size() < 3) {
            return rawNbt;
        }
        try {
            CompoundTag parsed = TagParser.parseTag(rawNbt);
            remapEmbeddedPositions(parsed, -sourcePos.get(0), -sourcePos.get(1), -sourcePos.get(2));
            parsed.putInt("x", SANITIZED_CONTEXT_POS.getX());
            parsed.putInt("y", SANITIZED_CONTEXT_POS.getY());
            parsed.putInt("z", SANITIZED_CONTEXT_POS.getZ());
            return parsed.toString();
        } catch (CommandSyntaxException ignored) {
            return rawNbt;
        }
    }

    private static int remapEmbeddedPositions(CompoundTag tag, int dx, int dy, int dz) {
        int remapped = 0;

        if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
            tag.putInt("x", tag.getInt("x") + dx);
            tag.putInt("y", tag.getInt("y") + dy);
            tag.putInt("z", tag.getInt("z") + dz);
            remapped++;
        }

        if (tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
            tag.putInt("X", tag.getInt("X") + dx);
            tag.putInt("Y", tag.getInt("Y") + dy);
            tag.putInt("Z", tag.getInt("Z") + dz);
            remapped++;
        }

        for (String key : tag.getAllKeys()) {
            if (tag.get(key) instanceof CompoundTag nested) {
                remapped += remapEmbeddedPositions(nested, dx, dy, dz);
            } else if (tag.get(key) instanceof ListTag listTag) {
                remapped += remapEmbeddedPositionsInList(listTag, dx, dy, dz);
            }
        }

        return remapped;
    }

    private static int remapEmbeddedPositionsInList(ListTag listTag, int dx, int dy, int dz) {
        int remapped = 0;
        for (int i = 0; i < listTag.size(); i++) {
            if (listTag.get(i) instanceof CompoundTag nested) {
                remapped += remapEmbeddedPositions(nested, dx, dy, dz);
            } else if (listTag.get(i) instanceof ListTag nestedList) {
                remapped += remapEmbeddedPositionsInList(nestedList, dx, dy, dz);
            }
        }
        return remapped;
    }
}
