package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetBlockScreen extends AbstractStepEditorScreen {

    private static final String[] ENTRANCE_ANIMATIONS = {"none", "simultaneous", "down", "up", "south", "north", "east", "west"};

    private HintableTextFieldWidget blockField;
    private HintableTextFieldWidget nbtField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private boolean spawnParticles = true;
    private boolean immediateDisplay = true;
    private BoxWidget particlesToggle;
    private BoxWidget immediateDisplayToggle;
    private BoxWidget entranceAnimationButton;
    private int entranceAnimationIndex = 0;
    private PonderButton pickBtn1, pickBtn2;
    @Nullable
    private PonderButton jeiBtn;
    @Nullable
    private PonderButton blockPickBtn;

    public SetBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.set_block.add"), scene, sceneIndex, parent);
    }

    public SetBlockScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                          int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.set_block.edit"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override
    protected boolean usesBlockProps() { return true; }

    @Override
    protected int getFormRowCount() { return 7 + blockPropRowCount(); }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.set_block"); }

    @Override
    protected void buildForm() {
        beginForm();
        var blk = addFormTextFieldWithJeiAndBlockPick("ponderer.ui.set_block", "ponderer.ui.set_block.tooltip",
                UIText.of("ponderer.ui.set_block.hint"), IdFieldMode.BLOCK, "nbt");
        blockField = blk.field();
        jeiBtn = blk.jeiBtn();
        blockPickBtn = blk.blockPickBtn();
        addFormBlockProps("ponderer.ui.block_properties", "ponderer.ui.block_properties.tooltip");
        nbtField = addFormNbtField("ponderer.ui.set_block.nbt", "ponderer.ui.set_block.nbt.tooltip",
            "{CustomName:'\"Demo\"'}", 124, "nbt");
        var from = addFormXyzRow("ponderer.ui.set_block.pos_from", "ponderer.ui.set_block.pos_from.tooltip", PickState.TargetField.POS1);
        posXField = from.x(); posYField = from.y(); posZField = from.z(); pickBtn1 = from.pickBtn();
        var to = addFormXyzRow("ponderer.ui.set_block.pos_to", "ponderer.ui.set_block.pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = to.x(); pos2YField = to.y(); pos2ZField = to.z(); pickBtn2 = to.pickBtn();
        entranceAnimationButton = addFormCycleButton("ponderer.ui.set_block.entrance_animation", "ponderer.ui.set_block.entrance_animation.tooltip",
            140, () -> entranceAnimationIndex = (entranceAnimationIndex + 1) % ENTRANCE_ANIMATIONS.length,
            () -> entranceAnimationLabel(ENTRANCE_ANIMATIONS[entranceAnimationIndex]));
        particlesToggle = addFormToggle("ponderer.ui.set_block.particles", "ponderer.ui.set_block.particles.tooltip",
                () -> spawnParticles, () -> spawnParticles = !spawnParticles);
        immediateDisplayToggle = addFormToggle("ponderer.ui.set_block.immediate_display", "ponderer.ui.set_block.immediate_display.tooltip",
            () -> immediateDisplay, () -> immediateDisplay = !immediateDisplay);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) blockField.setValue(step.block);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posXField.setValue(String.valueOf(step.blockPos.get(0)));
            posYField.setValue(String.valueOf(step.blockPos.get(1)));
            posZField.setValue(String.valueOf(step.blockPos.get(2)));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2XField.setValue(String.valueOf(step.blockPos2.get(0)));
            pos2YField.setValue(String.valueOf(step.blockPos2.get(1)));
            pos2ZField.setValue(String.valueOf(step.blockPos2.get(2)));
        }
        if (step.spawnParticles != null) {
            spawnParticles = step.spawnParticles;
        }
        if (step.immediateDisplay != null) {
            immediateDisplay = step.immediateDisplay;
        }
        if (step.entranceAnimation != null && !step.entranceAnimation.isBlank()) {
            String normalized = normalizeEntranceAnimation(step.entranceAnimation);
            for (int i = 0; i < ENTRANCE_ANIMATIONS.length; i++) {
                if (ENTRANCE_ANIMATIONS[i].equals(normalized)) {
                    entranceAnimationIndex = i;
                    break;
                }
            }
        }
        if (step.nbt != null) nbtField.setValue(step.nbt);
    }

    private String entranceAnimationLabel(String value) {
        return UIText.of("ponderer.ui.entrance_animation.option." + value);
    }

    private String normalizeEntranceAnimation(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        return switch (value) {
            case "从上到下", "上到下", "top_to_bottom", "top-down", "down" -> "down";
            case "从下到上", "下到上", "bottom_to_top", "bottom-up", "up" -> "up";
            case "从北到南", "北到南", "north_to_south", "north-south", "south" -> "south";
            case "从南到北", "南到北", "south_to_north", "south-north", "north" -> "north";
            case "从西到东", "西到东", "west_to_east", "west-east", "east" -> "east";
            case "从东到西", "东到西", "east_to_west", "east-west", "west" -> "west";
            case "同时", "simultaneous" -> "simultaneous";
            default -> "none";
        };
    }



    @Override
    protected String getStepType() { return "set_block"; }

    @Override
    protected Map<String, String> snapshotForm() {
        syncBlockPropFieldsToEntries();
        Map<String, String> m = new HashMap<>();
        m.put("block", blockField.getValue());
        snapshotBlockProps(m);
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        m.put("nbt", nbtField.getValue());
        m.put("particles", String.valueOf(spawnParticles));
        m.put("immediateDisplay", String.valueOf(immediateDisplay));
        m.put("entranceAnimation", String.valueOf(entranceAnimationIndex));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_ID_KEY)) {
            blockField.setValue(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_ID_KEY));
        } else if (snapshot.containsKey("block")) {
            blockField.setValue(snapshot.get("block"));
        }
        restoreBlockProps(snapshot);
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (snapshot.containsKey("nbt")) nbtField.setValue(snapshot.get("nbt"));
        restoreNbtPickNotice(snapshot);
        if (snapshot.containsKey("particles")) spawnParticles = Boolean.parseBoolean(snapshot.get("particles"));
        if (snapshot.containsKey("immediateDisplay")) immediateDisplay = Boolean.parseBoolean(snapshot.get("immediateDisplay"));
        if (snapshot.containsKey("entranceAnimation")) {
            try {
                entranceAnimationIndex = Integer.parseInt(snapshot.get("entranceAnimation"));
            } catch (NumberFormatException ignored) {
                entranceAnimationIndex = 0;
            }
            if (entranceAnimationIndex < 0 || entranceAnimationIndex >= ENTRANCE_ANIMATIONS.length) {
                entranceAnimationIndex = 0;
            }
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        String blockId = blockField.getValue().trim();
        if (blockId.isEmpty()) { errorMessage = UIText.of("ponderer.ui.set_block.error.required"); return null; }
        ResourceLocation loc = ResourceLocation.tryParse(blockId);
        if (loc == null) { errorMessage = UIText.of("ponderer.ui.set_block.error.invalid_id"); return null; }
        if (BuiltInRegistries.BLOCK.getOptional(loc).isEmpty()) {
            errorMessage = UIText.of("ponderer.ui.set_block.error.unknown", blockId); return null;
        }

        Integer px = parseInt(posXField.getValue(), "X");
        Integer py = parseInt(posYField.getValue(), "Y");
        Integer pz = parseInt(posZField.getValue(), "Z");
        if (px == null || py == null || pz == null) return null;

        String pos2X = pos2XField.getValue().trim();
        String pos2Y = pos2YField.getValue().trim();
        String pos2Z = pos2ZField.getValue().trim();
        boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
        Integer px2 = null, py2 = null, pz2 = null;
        if (hasPos2) {
            if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                errorMessage = UIText.of("ponderer.ui.set_block.error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "X2");
            py2 = parseInt(pos2Y, "Y2");
            pz2 = parseInt(pos2Z, "Z2");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "set_block";
        s.block = blockId;
        s.blockProperties = collectBlockProperties();
        s.blockPos = List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = List.of(px2, py2, pz2);
        String nbt = nbtField.getValue().trim();
        if (!nbt.isEmpty()) {
            try {
                TagParser.parseTag(nbt);
            } catch (Exception e) {
                errorMessage = UIText.of("ponderer.ui.modify_block_entity_nbt.error.invalid");
                return null;
            }
            s.nbt = nbt;
        }
        if (!spawnParticles) s.spawnParticles = false;
        s.immediateDisplay = immediateDisplay;
        String entranceAnimation = ENTRANCE_ANIMATIONS[entranceAnimationIndex];
        if (!"none".equals(entranceAnimation)) {
            s.entranceAnimation = entranceAnimation;
        }
        return s;
    }
}
