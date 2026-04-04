package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class SetBlockScreen extends AbstractStepEditorScreen {

    private static final String[] ENTRANCE_MODES = {"hidden", "immediate", "animated"};
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final String[] ENTRANCE_ANIMATIONS = {"none", "simultaneous", "down", "up", "south", "north", "east", "west"};

    private final StepTextFieldHandle blockField = new StepTextFieldHandle("block");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle linkIdField = new StepTextFieldHandle("linkId");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private final StepTextFieldHandle intervalField = new StepTextFieldHandle("entranceInterval");
    private boolean spawnParticles = true;
    private boolean smartDisplay = true;
    private int entranceModeIndex = 1;
    private int directionIndex = 0;
    private int entranceAnimationIndex = 0;

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
    protected String getHeaderTitle() { return UIText.of("ponderer.ui.set_block"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(StepEditorEntries.text(
            blockField,
            "ponderer.ui.set_block",
            "ponderer.ui.set_block.tooltip",
            UIText.of("ponderer.ui.set_block.hint"),
            124,
            StepTextButtonSpec.jei(IdFieldMode.BLOCK),
            StepTextButtonSpec.blockPick("nbt")));
        entries.add(StepEditorEntries.blockProps(
            "ponderer.ui.block_properties",
            "ponderer.ui.block_properties.tooltip",
            this::blockPropRowCount));
        entries.add(StepEditorEntries.text(
            nbtField,
            "ponderer.ui.set_block.nbt",
            "ponderer.ui.set_block.nbt.tooltip",
            "{CustomName:'\"Demo\"'}",
            124,
            StepTextButtonSpec.nbtPick("nbt")));
        entries.add(StepEditorEntries.xyz(
            posField,
            "ponderer.ui.set_block.pos_from",
            "ponderer.ui.set_block.pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(StepEditorEntries.xyz(
            pos2Field,
            "ponderer.ui.set_block.pos_to",
            "ponderer.ui.set_block.pos_to.tooltip",
            PickState.TargetField.POS2));
        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.set_block.entrance_mode",
            "ponderer.ui.set_block.entrance_mode.tooltip",
            140,
            () -> {
                entranceModeIndex = (entranceModeIndex + 1) % ENTRANCE_MODES.length;
                rebuildFormPreservingState();
            },
            () -> UIText.of("ponderer.ui.set_block.entrance_mode.option." + ENTRANCE_MODES[entranceModeIndex])));
        String mode = ENTRANCE_MODES[entranceModeIndex];
        if ("immediate".equals(mode)) {
            entries.add(StepEditorEntries.toggle(
                "ponderer.ui.set_block.particles",
                "ponderer.ui.set_block.particles.tooltip",
                () -> spawnParticles,
                () -> spawnParticles = !spawnParticles));
        } else if ("animated".equals(mode)) {
            entries.add(StepEditorEntries.cycleButton(
                "ponderer.ui.set_block.entrance_animation",
                "ponderer.ui.set_block.entrance_animation.tooltip",
                140,
                () -> entranceAnimationIndex = (entranceAnimationIndex + 1) % ENTRANCE_ANIMATIONS.length,
                () -> entranceAnimationLabel(ENTRANCE_ANIMATIONS[entranceAnimationIndex])));
            entries.add(StepEditorEntries.cycleButton(
                "ponderer.ui.show_section_and_merge.direction",
                "ponderer.ui.show_section_and_merge.direction.tooltip",
                140,
                () -> directionIndex = (directionIndex + 1) % DIRECTIONS.length,
                () -> optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[directionIndex])));
            entries.add(StepEditorEntries.text(
                linkIdField,
                "ponderer.ui.show_section_and_merge.link",
                "ponderer.ui.show_section_and_merge.link.tooltip",
                "",
                140));
            entries.add(StepEditorEntries.number(
                durationField,
                "ponderer.ui.duration",
                "ponderer.ui.duration.tooltip.section_animation",
                "20",
                60,
                null));
            entries.add(StepEditorEntries.number(
                intervalField,
                "ponderer.ui.entrance_interval",
                "ponderer.ui.entrance_interval.tooltip",
                "1",
                60,
                null));
            entries.add(StepEditorEntries.toggle(
                "ponderer.ui.smart_display",
                "ponderer.ui.smart_display.tooltip",
                () -> smartDisplay,
                () -> smartDisplay = !smartDisplay));
        }
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.block != null) blockField.setValue(step.block);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
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
            linkIdField.setValue(step.linkId);
        }
        if (step.entranceDuration != null) {
            durationField.setValue(String.valueOf(step.entranceDuration));
        } else if (step.duration != null) {
            durationField.setValue(String.valueOf(step.duration));
        }
        if (step.entranceInterval != null) {
            intervalField.setValue(String.valueOf(step.entranceInterval));
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
    protected void appendCustomSnapshot(Map<String, String> m) {
        syncBlockPropFieldsToEntries();
        snapshotBlockProps(m);
        m.put("entranceMode", String.valueOf(entranceModeIndex));
        m.put("particles", String.valueOf(spawnParticles));
        m.put("direction", String.valueOf(directionIndex));
        m.put("entranceAnimation", String.valueOf(entranceAnimationIndex));
        m.put("smartDisplay", String.valueOf(smartDisplay));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey(NbtPickState.SNAPSHOT_BLOCK_ID_KEY)) {
            blockField.setValue(snapshot.get(NbtPickState.SNAPSHOT_BLOCK_ID_KEY));
        }
        restoreBlockProps(snapshot);
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
            linkIdField.setValue(snapshot.get("linkId"));
        }
        if (snapshot.containsKey("duration")) {
            durationField.setValue(snapshot.get("duration"));
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
            intervalField.setValue(snapshot.get("entranceInterval"));
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

        Integer px = parseInt(posField.x(), "X");
        Integer py = parseInt(posField.y(), "Y");
        Integer pz = parseInt(posField.z(), "Z");
        if (px == null || py == null || pz == null) return null;

        String pos2X = pos2Field.x().trim();
        String pos2Y = pos2Field.y().trim();
        String pos2Z = pos2Field.z().trim();
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
            String linkId = linkIdField.getValue().trim();
            if (!linkId.isEmpty()) s.linkId = linkId;
            String entranceAnimation = ENTRANCE_ANIMATIONS[entranceAnimationIndex];
            if ("none".equals(entranceAnimation)) {
                entranceAnimation = "down";
            }
            s.entranceAnimation = entranceAnimation;
            s.entranceDuration = Math.max(0, parseIntOr(durationField.getValue(), 20));
            s.entranceInterval = Math.max(0, parseIntOr(intervalField.getValue(), 1));
            s.smartDisplay = smartDisplay;
        }
        return s;
    }
}
