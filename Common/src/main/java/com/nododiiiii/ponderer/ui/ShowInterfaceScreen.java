package com.nododiiiii.ponderer.ui;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShowInterfaceScreen extends AbstractStepEditorScreen {
    private static final String NBT_SNAPSHOT_KEY = "show_interface_nbt";
    private static final String ITEM_NBT_SNAPSHOT_KEY = "show_interface_item_nbt";
    private static final BlockPos SANITIZED_CONTEXT_POS = BlockPos.ZERO;

    public static final String SOURCE_BLOCK = "block";
    public static final String SOURCE_HELD_ITEM = "held_item";
    public static final String SOURCE_UI_ID = "ui_id";
    private static final String[] SOURCES = {SOURCE_BLOCK, SOURCE_HELD_ITEM, SOURCE_UI_ID};

    private int sourceIndex = 0;
    private final FieldBinding<Integer> sourceBinding =
        FieldBindings.integer("interface_source", () -> sourceIndex, value -> sourceIndex = value);

    private final StepTextFieldHandle blockField = new StepTextFieldHandle("block");
    private final KeyValueListState capturedBlockProperties = new KeyValueListState("prop", 0);

    private final StepTextFieldHandle itemField = new StepTextFieldHandle("item");
    private final StepTextFieldHandle itemNbtField = new StepTextFieldHandle("item_nbt");

    private final StepTextFieldHandle uiIdField = new StepTextFieldHandle("ui_id");

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
        participants.add(sourceBinding);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.show_interface");
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.cycle(
            sourceBinding,
            "ponderer.ui.show_interface.source",
            "ponderer.ui.show_interface.source.tooltip",
            140,
            SOURCES.length,
            this::rebuildFormPreservingState,
            () -> UIText.of("ponderer.ui.show_interface.source.option." + SOURCES[sourceIndex]),
            () -> 0xFFFFFF));

        String source = SOURCES[sourceIndex];
        switch (source) {
            case SOURCE_BLOCK -> entries.add(FieldSpecs.text(
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
            case SOURCE_HELD_ITEM -> {
                entries.add(FieldSpecs.text(
                    itemField,
                    "ponderer.ui.show_interface.held_item",
                    "ponderer.ui.show_interface.held_item.tooltip",
                    UIText.of("ponderer.ui.show_interface.held_item.hint"),
                    124,
                    FieldDecorators.jei(IdFieldMode.ITEM),
                    FieldDecorators.heldItem(stack -> {
                        itemField.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        if (stack.getTag() != null && !stack.getTag().isEmpty()) {
                            itemNbtField.setValue(stack.getTag().toString());
                        } else {
                            itemNbtField.setValue("");
                        }
                    })));
                entries.add(FieldSpecs.text(
                    itemNbtField,
                    "ponderer.ui.show_interface.item_nbt",
                    "ponderer.ui.show_interface.item_nbt.tooltip",
                    "{}",
                    124,
                    FieldDecorators.nbtPick(ITEM_NBT_SNAPSHOT_KEY),
                    FieldDecorators.nbtExpand(ITEM_NBT_SNAPSHOT_KEY)));
            }
            case SOURCE_UI_ID -> entries.add(FieldSpecs.text(
                uiIdField,
                "ponderer.ui.show_interface.ui_id",
                "ponderer.ui.show_interface.ui_id.tooltip",
                UIText.of("ponderer.ui.show_interface.ui_id.hint"),
                124));
            default -> {
            }
        }

        entries.add(FieldSpecs.toggle(
            enableNbtBinding,
            "ponderer.ui.show_interface.enable_nbt",
            "ponderer.ui.show_interface.enable_nbt.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        String source = step.interfaceSource == null ? SOURCE_BLOCK : step.interfaceSource.toLowerCase(Locale.ROOT);
        for (int i = 0; i < SOURCES.length; i++) {
            if (SOURCES[i].equals(source)) {
                sourceIndex = i;
                break;
            }
        }
        if (step.block != null) {
            blockField.setValue(step.block);
        }
        if (step.item != null) {
            itemField.setValue(step.item);
        }
        if (step.uiId != null) {
            uiIdField.setValue(step.uiId);
        }
        contextPos = step.blockPos;
        contextFace = step.direction;
        contextHit = step.point;
        contextInside = step.whileSneaking;
        // For block source, step.nbt holds block-entity NBT.
        // For held_item source, step.nbt holds item NBT; populate the item NBT field.
        if (SOURCE_HELD_ITEM.equals(source)) {
            if (step.nbt != null) {
                itemNbtField.setValue(step.nbt);
            }
        } else {
            capturedNbt = step.nbt;
        }
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
        clearStatusMessages();
        String source = SOURCES[sourceIndex];

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "show_interface";
        step.duration = null;
        if (!SOURCE_BLOCK.equals(source)) {
            step.interfaceSource = source;
        }
        step.enableNbt = enableNbt;

        switch (source) {
            case SOURCE_BLOCK -> {
                String blockId = blockField.getValue().trim();
                if (blockId.isEmpty()) {
                    setErrorMessage(UIText.of("ponderer.ui.error.required_field",
                        UIText.of("ponderer.ui.show_interface.block")));
                    return null;
                }
                if (contextPos == null || contextPos.size() < 3) {
                    setErrorMessage(UIText.of("ponderer.ui.show_interface.error.no_context"));
                    return null;
                }
                step.block = blockId;
                step.blockPos = List.of(SANITIZED_CONTEXT_POS.getX(), SANITIZED_CONTEXT_POS.getY(),
                    SANITIZED_CONTEXT_POS.getZ());
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
                if (enableNbt && capturedNbt != null && !capturedNbt.isBlank()) {
                    step.nbt = sanitizeCapturedNbt(capturedNbt, contextPos);
                }
                Map<String, String> props = capturedBlockProperties.toFilteredMap();
                if (props != null && !props.isEmpty()) {
                    step.blockProperties = props;
                }
            }
            case SOURCE_HELD_ITEM -> {
                String itemId = itemField.getValue().trim();
                if (itemId.isEmpty()) {
                    setErrorMessage(UIText.of("ponderer.ui.show_interface.error.no_item"));
                    return null;
                }
                step.item = itemId;
                String itemNbt = itemNbtField.getValue().trim();
                if (!itemNbt.isEmpty()) {
                    try {
                        TagParser.parseTag(itemNbt);
                    } catch (CommandSyntaxException ignored) {
                        setErrorMessage(UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid"));
                        return null;
                    }
                    step.nbt = itemNbt;
                }
            }
            case SOURCE_UI_ID -> {
                String uiId = uiIdField.getValue().trim();
                if (uiId.isEmpty()) {
                    setErrorMessage(UIText.of("ponderer.ui.show_interface.error.no_ui_id"));
                    return null;
                }
                step.uiId = uiId;
            }
            default -> {
                return null;
            }
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
