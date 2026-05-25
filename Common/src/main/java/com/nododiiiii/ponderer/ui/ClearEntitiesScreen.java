package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Shared editor screen for clear_item_entities and clear_entities step types.
 */
public class ClearEntitiesScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final IdFieldMode jeiMode;

    private final StepTextFieldHandle idField = new StepTextFieldHandle("id");
    private final StepTextFieldHandle linkIdField = new StepTextFieldHandle("linkId");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private boolean fullScene = false;
    private boolean animatedExit = false;
    private int exitDirectionIndex = 0;

    public ClearEntitiesScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.jeiMode = "clear_item_entities".equals(stepType) ? IdFieldMode.ITEM : IdFieldMode.ENTITY;
    }

    public ClearEntitiesScreen(String stepType, DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.jeiMode = "clear_item_entities".equals(stepType) ? IdFieldMode.ITEM : IdFieldMode.ENTITY;
    }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        if (jeiMode == IdFieldMode.ITEM) {
            entries.add(FieldSpecs.text(
                idField,
                "ponderer.ui." + stepType + ".id",
                "ponderer.ui." + stepType + ".id.tooltip",
                UIText.of("ponderer.ui." + stepType + ".id.hint"),
                124,
                FieldDecorators.jei(jeiMode),
                FieldDecorators.heldItem(
                    stack -> idField.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))));
        } else {
            entries.add(FieldSpecs.text(
                idField,
                "ponderer.ui." + stepType + ".id",
                "ponderer.ui." + stepType + ".id.tooltip",
                UIText.of("ponderer.ui." + stepType + ".id.hint"),
                124,
                FieldDecorators.jei(jeiMode)));
        }
        entries.add(FieldSpecs.text(
            linkIdField,
            "ponderer.ui.entity_link",
            "ponderer.ui." + stepType + ".link.tooltip",
            "",
            124));
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui." + stepType + ".pos_from",
            "ponderer.ui." + stepType + ".pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui." + stepType + ".pos_to",
            "ponderer.ui." + stepType + ".pos_to.tooltip",
            PickState.TargetField.POS2));
        if ("clear_entities".equals(stepType)) {
            entries.add(FieldSpecs.toggle(
                "ponderer.ui.clear_entities.exit",
                "ponderer.ui.clear_entities.exit.tooltip",
                () -> animatedExit,
                () -> {
                    animatedExit = !animatedExit;
                    rebuildFormPreservingState();
                }));
            if (animatedExit) {
                entries.add(FieldSpecs.choice(
                    "ponderer.ui.show_section_and_merge.direction",
                    "ponderer.ui.show_section_and_merge.direction.tooltip",
                    100,
                    () -> exitDirectionIndex = (exitDirectionIndex + 1) % SelectionAnimationOptions.DIRECTIONS.length,
                    () -> SelectionAnimationOptions.optionLabel(
                        "ponderer.ui.show_controls.direction",
                        SelectionAnimationOptions.DIRECTIONS[exitDirectionIndex])));
            }
        }
        entries.add(FieldSpecs.toggle(
            "ponderer.ui." + stepType + ".full_scene",
            "ponderer.ui." + stepType + ".full_scene.tooltip",
            () -> fullScene,
            () -> fullScene = !fullScene));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.item != null) idField.setValue(step.item);
        if (step.entity != null) idField.setValue(step.entity);
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        if (step.linkId != null) linkIdField.setValue(step.linkId);
        if (step.fullScene != null) fullScene = step.fullScene;
        if ("clear_entities".equals(stepType)) {
            String entranceAnimation = SelectionAnimationOptions.normalizeEntranceAnimation(step.entranceAnimation);
            animatedExit = !"none".equals(entranceAnimation);
            exitDirectionIndex = directionIndex(resolveExitDirection(entranceAnimation, step.direction));
        }
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("fullScene", String.valueOf(fullScene));
        snapshot.put("animatedExit", String.valueOf(animatedExit));
        snapshot.put("exitDirectionIndex", String.valueOf(exitDirectionIndex));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("fullScene")) {
            fullScene = Boolean.parseBoolean(snapshot.get("fullScene"));
        }
        if (snapshot.containsKey("animatedExit")) {
            animatedExit = Boolean.parseBoolean(snapshot.get("animatedExit"));
        }
        if (snapshot.containsKey("exitDirectionIndex")) {
            exitDirectionIndex = Math.floorMod(parseIntOr(snapshot.get("exitDirectionIndex"), 0),
                SelectionAnimationOptions.DIRECTIONS.length);
        }
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        clearStatusMessages();
        String linkId = linkIdField.getValue().trim();

        Integer px = null, py = null, pz = null;
        Integer px2 = null, py2 = null, pz2 = null;

        if (!fullScene) {
            String posX = posField.x().trim();
            String posY = posField.y().trim();
            String posZ = posField.z().trim();
            boolean hasPos1 = !posX.isEmpty() || !posY.isEmpty() || !posZ.isEmpty();
            if (hasPos1) {
                px = parseInt(posX, "X");
                py = parseInt(posY, "Y");
                pz = parseInt(posZ, "Z");
                if (px == null || py == null || pz == null) return null;
            } else if (linkId.isEmpty()) {
                setErrorMessage(UIText.of("ponderer.ui." + stepType + ".error.target_required"));
                return null;
            }

            String pos2X = pos2Field.x().trim();
            String pos2Y = pos2Field.y().trim();
            String pos2Z = pos2Field.z().trim();
            boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
            if (hasPos2) {
                if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                    setErrorMessage(UIText.of("ponderer.ui." + stepType + ".error.partial_to"));
                    return null;
                }
                px2 = parseInt(pos2X, "X2");
                py2 = parseInt(pos2Y, "Y2");
                pz2 = parseInt(pos2Z, "Z2");
                if (px2 == null || py2 == null || pz2 == null) return null;
            }
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        String id = idField.getValue().trim();
        if (!id.isEmpty()) {
            if ("clear_item_entities".equals(stepType)) {
                s.item = id;
            } else {
                s.entity = id;
            }
        }
        if (!linkId.isEmpty()) {
            s.linkId = linkId;
        }
        if (fullScene) {
            s.fullScene = true;
        } else if (px != null) {
            s.blockPos = List.of(px, py, pz);
            if (px2 != null) s.blockPos2 = List.of(px2, py2, pz2);
        }
        if ("clear_entities".equals(stepType) && animatedExit) {
            s.entranceAnimation = "simultaneous";
            s.direction = SelectionAnimationOptions.DIRECTIONS[exitDirectionIndex];
        }
        return s;
    }

    private int directionIndex(String direction) {
        for (int i = 0; i < SelectionAnimationOptions.DIRECTIONS.length; i++) {
            if (SelectionAnimationOptions.DIRECTIONS[i].equals(direction)) {
                return i;
            }
        }
        return 0;
    }

    private String resolveExitDirection(String entranceAnimation, @Nullable String direction) {
        return switch (entranceAnimation) {
            case "up", "north", "south", "west", "east" -> entranceAnimation;
            case "simultaneous" -> SelectionAnimationOptions.normalizeDirection(direction);
            default -> "down";
        };
    }
}
