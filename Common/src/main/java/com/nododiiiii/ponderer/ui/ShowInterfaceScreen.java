package com.nododiiiii.ponderer.ui;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ShowInterfaceScreen extends AbstractStepEditorScreen {
    private static final String NBT_SNAPSHOT_KEY = "show_interface_nbt";
    private static final BlockPos SANITIZED_CONTEXT_POS = BlockPos.ZERO;

    private final StepTextFieldHandle blockField = new StepTextFieldHandle("block");
    private final KeyValueListState capturedBlockProperties = new KeyValueListState("prop", 0);

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
    private boolean enableNbt = true;
    private final FieldBinding<Boolean> enableNbtBinding =
        FieldBindings.bool("enable_nbt", () -> enableNbt, value -> enableNbt = Boolean.TRUE.equals(value));

    public ShowInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_interface"), scene, sceneIndex, parent);
    }

    public ShowInterfaceScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_interface"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(capturedBlockProperties);
        participants.add(enableNbtBinding);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.show_interface");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            blockField,
            "ponderer.ui.show_interface.block",
            "ponderer.ui.show_interface.block.tooltip",
            UIText.of("ponderer.ui.show_interface.block.hint"),
            124,
            entry -> {
                entry.field().setEditable(false);
                entry.field().setCanLoseFocus(true);
            },
            FieldDecorators.blockPick(NBT_SNAPSHOT_KEY)));
        entries.add(FieldSpecs.toggle(
            enableNbtBinding,
            "ponderer.ui.show_interface.enable_nbt",
            "ponderer.ui.show_interface.enable_nbt.tooltip"));
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
        contextInside = step.whileSneaking;
        capturedNbt = step.nbt;
        capturedBlockProperties.replaceFromMap(step.blockProperties);
        enableNbt = !Boolean.FALSE.equals(step.enableNbt);
    }

    @Override
    protected String getStepType() {
        return "show_interface";
    }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
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
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_ID_KEY)) {
            blockField.setValue(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_ID_KEY));
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_POS_KEY)) {
            FormParsers.Int3 pos = FormParsers.parseInt3(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_POS_KEY));
            contextPos = pos == null ? null : pos.toList();
        } else if (snapshot.containsKey("ctx_pos")) {
            FormParsers.Int3 pos = FormParsers.parseInt3(snapshot.get("ctx_pos"));
            contextPos = pos == null ? null : pos.toList();
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_FACE_KEY)) {
            contextFace = snapshot.get(NbtPickState.SNAPSHOT_BLOCK_FACE_KEY);
        } else if (snapshot.containsKey("ctx_face")) {
            contextFace = snapshot.get("ctx_face");
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_HIT_KEY)) {
            FormParsers.Double3 hit = FormParsers.parseDouble3(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_HIT_KEY));
            contextHit = hit == null ? null : hit.toList();
        } else if (snapshot.containsKey("ctx_hit")) {
            FormParsers.Double3 hit = FormParsers.parseDouble3(snapshot.get("ctx_hit"));
            contextHit = hit == null ? null : hit.toList();
        }
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_INSIDE_KEY)) {
            contextInside = FormParsers.parseBoolean(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_INSIDE_KEY));
        } else if (snapshot.containsKey("ctx_inside")) {
            contextInside = FormParsers.parseBoolean(snapshot.get("ctx_inside"));
        }
        if (snapshot.containsKey(NBT_SNAPSHOT_KEY)) {
            capturedNbt = snapshot.get(NBT_SNAPSHOT_KEY);
        }
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
            step.whileSneaking = contextInside;
        }
        step.enableNbt = enableNbt;
        if (enableNbt && capturedNbt != null && !capturedNbt.isBlank()) {
            step.nbt = sanitizeCapturedNbt(capturedNbt, contextPos);
        }
        Map<String, String> props = capturedBlockProperties.toFilteredMap();
        if (props != null && !props.isEmpty()) {
            step.blockProperties = props;
        }

        return step;
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
