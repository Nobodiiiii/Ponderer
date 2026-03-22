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

    private static final String[] ENTRANCE_MODES = {"hidden", "immediate", "animated"};
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final String[] ENTRANCE_ANIMATIONS = {"none", "simultaneous", "down", "up", "south", "north", "east", "west"};

    private HintableTextFieldWidget blockField;
    private HintableTextFieldWidget nbtField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private HintableTextFieldWidget linkIdField;
    private HintableTextFieldWidget durationField;
    private HintableTextFieldWidget intervalField;
    private boolean spawnParticles = true;
    private boolean smartDisplay = true;
    private int entranceModeIndex = 1;
    private int directionIndex = 0;
    private String linkIdValue = "";
    private String durationValue = "20";
    private String intervalValue = "1";
    private BoxWidget particlesToggle;
    private BoxWidget entranceModeButton;
    private BoxWidget directionButton;
    private BoxWidget entranceAnimationButton;
    private BoxWidget smartDisplayToggle;
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
        boolean animatedMode = step != null
                && step.entranceAnimation != null
                && !step.entranceAnimation.isBlank()
                && !"none".equals(normalizeEntranceAnimation(step.entranceAnimation));
        if (animatedMode) {
            entranceModeIndex = 2;
        } else if (step != null && Boolean.FALSE.equals(step.immediateDisplay)) {
            entranceModeIndex = 0;
        } else {
            entranceModeIndex = 1;
        }
    }

    @Override
    protected boolean usesBlockProps() { return true; }

    @Override
    protected int getFormRowCount() {
        int rows = 5 + blockPropRowCount();
        String mode = ENTRANCE_MODES[entranceModeIndex];
        if ("immediate".equals(mode)) {
            rows += 1;
        } else if ("animated".equals(mode)) {
            rows += 6;
        }
        return rows;
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.set_block"); }

    @Override
    protected void buildForm() {
        linkIdField = null;
        durationField = null;
        intervalField = null;
        particlesToggle = null;
        directionButton = null;
        entranceAnimationButton = null;
        smartDisplayToggle = null;

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
        entranceModeButton = addFormCycleButton("ponderer.ui.set_block.entrance_mode", "ponderer.ui.set_block.entrance_mode.tooltip",
            140, () -> {
                Map<String, String> snapshot = snapshotForm();
                entranceModeIndex = (entranceModeIndex + 1) % ENTRANCE_MODES.length;
                snapshot.put("entranceMode", String.valueOf(entranceModeIndex));
                init(net.minecraft.client.Minecraft.getInstance(), this.width, this.height);
                restoreFromSnapshot(snapshot);
            },
            () -> UIText.of("ponderer.ui.set_block.entrance_mode.option." + ENTRANCE_MODES[entranceModeIndex]));

        String mode = ENTRANCE_MODES[entranceModeIndex];
        if ("immediate".equals(mode)) {
            particlesToggle = addFormToggle("ponderer.ui.set_block.particles", "ponderer.ui.set_block.particles.tooltip",
                    () -> spawnParticles, () -> spawnParticles = !spawnParticles);
        } else if ("animated".equals(mode)) {
            entranceAnimationButton = addFormCycleButton("ponderer.ui.set_block.entrance_animation", "ponderer.ui.set_block.entrance_animation.tooltip",
                140, () -> entranceAnimationIndex = (entranceAnimationIndex + 1) % ENTRANCE_ANIMATIONS.length,
                () -> entranceAnimationLabel(ENTRANCE_ANIMATIONS[entranceAnimationIndex]));
            directionButton = addFormCycleButton("ponderer.ui.show_section_and_merge.direction", "ponderer.ui.show_section_and_merge.direction.tooltip",
                    140, () -> directionIndex = (directionIndex + 1) % DIRECTIONS.length,
                    () -> optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[directionIndex]));
                linkIdField = addFormTextField("ponderer.ui.show_section_and_merge.link", "ponderer.ui.show_section_and_merge.link.tooltip", linkIdValue, 140);
                durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.section_animation", durationValue, 60);
            intervalField = addFormNumberField("ponderer.ui.entrance_interval", "ponderer.ui.entrance_interval.tooltip", "1", 60);
                intervalField.setValue(intervalValue);
            smartDisplayToggle = addFormToggle("ponderer.ui.smart_display", "ponderer.ui.smart_display.tooltip",
                    () -> smartDisplay, () -> smartDisplay = !smartDisplay);
        }
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
        if (step.smartDisplay != null) {
            smartDisplay = step.smartDisplay;
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
        if (step.direction != null) {
            String normalized = normalizeDirection(step.direction);
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if (DIRECTIONS[i].equals(normalized)) {
                    directionIndex = i;
                    break;
                }
            }
        }
        if (step.linkId != null) {
            linkIdValue = step.linkId;
            if (linkIdField != null) {
                linkIdField.setValue(step.linkId);
            }
        }
        if (step.entranceDuration != null) {
            durationValue = String.valueOf(step.entranceDuration);
            if (durationField != null) {
                durationField.setValue(durationValue);
            }
        } else if (step.duration != null) {
            durationValue = String.valueOf(step.duration);
            if (durationField != null) {
                durationField.setValue(durationValue);
            }
        }
        if (step.entranceInterval != null) {
            intervalValue = String.valueOf(step.entranceInterval);
            if (intervalField != null) {
                intervalField.setValue(intervalValue);
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

    private String normalizeDirection(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        return switch (value) {
            case "up", "上", "向上" -> "up";
            case "north", "北", "向北" -> "north";
            case "south", "南", "向南" -> "south";
            case "west", "西", "向西" -> "west";
            case "east", "东", "向东" -> "east";
            default -> "down";
        };
    }

    private String optionLabel(String prefix, String value) {
        String key = prefix + "." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
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
        if (linkIdField != null) linkIdValue = linkIdField.getValue();
        if (durationField != null) durationValue = durationField.getValue();
        if (intervalField != null) intervalValue = intervalField.getValue();
        m.put("entranceMode", String.valueOf(entranceModeIndex));
        m.put("particles", String.valueOf(spawnParticles));
        m.put("direction", String.valueOf(directionIndex));
        m.put("linkId", linkIdValue);
        m.put("duration", durationValue);
        m.put("entranceAnimation", String.valueOf(entranceAnimationIndex));
        m.put("entranceInterval", intervalValue);
        m.put("smartDisplay", String.valueOf(smartDisplay));
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
        if (snapshot.containsKey("entranceMode")) {
            try {
                entranceModeIndex = Integer.parseInt(snapshot.get("entranceMode"));
            } catch (NumberFormatException ignored) {
                entranceModeIndex = 1;
            }
            if (entranceModeIndex < 0 || entranceModeIndex >= ENTRANCE_MODES.length) {
                entranceModeIndex = 1;
            }
        }
        if (snapshot.containsKey("particles")) spawnParticles = Boolean.parseBoolean(snapshot.get("particles"));
        if (snapshot.containsKey("direction")) {
            try {
                directionIndex = Integer.parseInt(snapshot.get("direction"));
            } catch (NumberFormatException ignored) {
                directionIndex = 0;
            }
            if (directionIndex < 0 || directionIndex >= DIRECTIONS.length) {
                directionIndex = 0;
            }
        }
        if (snapshot.containsKey("linkId")) {
            linkIdValue = snapshot.get("linkId");
            if (linkIdField != null) linkIdField.setValue(linkIdValue);
        }
        if (snapshot.containsKey("duration")) {
            durationValue = snapshot.get("duration");
            if (durationField != null) durationField.setValue(durationValue);
        }
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
        if (snapshot.containsKey("entranceInterval")) {
            intervalValue = snapshot.get("entranceInterval");
            if (intervalField != null) intervalField.setValue(intervalValue);
        }
        if (snapshot.containsKey("smartDisplay")) smartDisplay = Boolean.parseBoolean(snapshot.get("smartDisplay"));
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
        String entranceMode = ENTRANCE_MODES[entranceModeIndex];
        if ("hidden".equals(entranceMode)) {
            s.immediateDisplay = false;
            s.spawnParticles = false;
            s.entranceAnimation = "none";
        } else if ("immediate".equals(entranceMode)) {
            s.immediateDisplay = true;
            s.entranceAnimation = "none";
            if (!spawnParticles) s.spawnParticles = false;
        } else {
            s.immediateDisplay = false;
            s.spawnParticles = false;
            s.direction = DIRECTIONS[directionIndex];
            String linkId = linkIdField != null ? linkIdField.getValue().trim() : linkIdValue.trim();
            if (!linkId.isEmpty()) s.linkId = linkId;
            String entranceAnimation = ENTRANCE_ANIMATIONS[entranceAnimationIndex];
            if ("none".equals(entranceAnimation)) {
                entranceAnimation = "down";
            }
            s.entranceAnimation = entranceAnimation;
            s.entranceDuration = Math.max(0, parseIntOr(durationField != null ? durationField.getValue() : durationValue, 20));
            s.entranceInterval = Math.max(0, parseIntOr(intervalField != null ? intervalField.getValue() : intervalValue, 1));
            s.smartDisplay = smartDisplay;
        }
        return s;
    }
}
