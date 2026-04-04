package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Editor for "show_controls" step.
 * Fields: point XYZ, direction, duration, action, item, whileSneaking, whileCTRL
 */
public class ShowControlsScreen extends AbstractStepEditorScreen {

    private static final String[] DIRECTIONS = {"down", "up", "left", "right"};
    private static final String[] ACTIONS = {"", "left", "right", "scroll"};

    private final StepXyzFieldHandle pointField = new StepXyzFieldHandle("point");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private final StepTextFieldHandle itemField = new StepTextFieldHandle("item");
    private final StepTextFieldHandle nbtField = new StepTextFieldHandle("nbt");
    private int dirIndex = 0;
    private int actionIndex = 0;
    private boolean whileSneaking = false, whileCTRL = false;

    public ShowControlsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_controls"), scene, sceneIndex, parent);
    }

    public ShowControlsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_controls"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.show_controls"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(StepEditorEntries.xyz(
            pointField,
            "ponderer.ui.point",
            "ponderer.ui.show_controls.point.tooltip",
            PickState.TargetField.POINT,
            true));
        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.show_controls.direction",
            "ponderer.ui.show_controls.direction.tooltip",
            100,
            () -> dirIndex = (dirIndex + 1) % DIRECTIONS.length,
            () -> optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[dirIndex])));
        entries.add(StepEditorEntries.number(
            durationField,
            "ponderer.ui.duration",
            "ponderer.ui.duration.tooltip.controls",
            "60",
            50,
            "ponderer.ui.ticks"));
        entries.add(StepEditorEntries.cycleButton(
            "ponderer.ui.show_controls.action",
            "ponderer.ui.show_controls.action.tooltip",
            100,
            () -> actionIndex = (actionIndex + 1) % ACTIONS.length,
            () -> actionIndex == 0 ? UIText.of("ponderer.ui.none") : optionLabel("ponderer.ui.show_controls.action", ACTIONS[actionIndex])));
        entries.add(StepEditorEntries.text(
            itemField,
            "ponderer.ui.show_controls.item",
            "ponderer.ui.show_controls.item.tooltip",
            UIText.of("ponderer.ui.show_controls.item.hint"),
            124,
            StepTextButtonSpec.jei(IdFieldMode.INGREDIENT),
            StepTextButtonSpec.heldItem(
            stack -> {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                itemField.setValue(itemId);
                if (stack.getTag() != null && !stack.getTag().isEmpty()) {
                    nbtField.setValue(stack.getTag().toString());
                } else {
                    nbtField.setValue("");
                }
            })));
        entries.add(StepEditorEntries.text(
            nbtField,
            "ponderer.ui.show_controls.nbt",
            "ponderer.ui.show_controls.nbt.tooltip",
            "{}",
            124,
            StepTextButtonSpec.nbtPick("nbt")));
        entries.add(StepEditorEntries.toggle(
            "ponderer.ui.show_controls.sneaking",
            "ponderer.ui.show_controls.sneaking.tooltip",
            () -> whileSneaking,
            () -> whileSneaking = !whileSneaking));
        entries.add(StepEditorEntries.toggle(
            "ponderer.ui.show_controls.ctrl",
            "ponderer.ui.show_controls.ctrl.tooltip",
            () -> whileCTRL,
            () -> whileCTRL = !whileCTRL));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 3) {
            pointField.setValue(step.point.get(0), step.point.get(1), step.point.get(2));
        }
        if (step.direction != null) {
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if (DIRECTIONS[i].equalsIgnoreCase(step.direction)) { dirIndex = i; break; }
            }
        }
        if (step.duration != null) durationField.setValue(String.valueOf(step.duration));
        if (step.action != null) {
            for (int i = 0; i < ACTIONS.length; i++) {
                if (ACTIONS[i].equalsIgnoreCase(step.action)) { actionIndex = i; break; }
            }
        }
        if (step.item != null) {
            String itemValue = step.item;
            String nbtValue = step.nbt;
            if ((nbtValue == null || nbtValue.isBlank())) {
                int brace = itemValue.indexOf('{');
                if (brace >= 0) {
                    nbtValue = itemValue.substring(brace).trim();
                    itemValue = itemValue.substring(0, brace).trim();
                }
            }
            itemField.setValue(itemValue);
            if (nbtValue != null) nbtField.setValue(nbtValue);
        } else if (step.nbt != null) {
            nbtField.setValue(step.nbt);
        }
        whileSneaking = Boolean.TRUE.equals(step.whileSneaking);
        whileCTRL = Boolean.TRUE.equals(step.whileCTRL);
    }



    private String optionLabel(String prefix, String value) {
        String key = prefix + "." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    @Override
    protected String getStepType() { return "show_controls"; }

    @Override
    protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("dirIndex", String.valueOf(dirIndex));
        snapshot.put("actionIndex", String.valueOf(actionIndex));
        snapshot.put("whileSneaking", String.valueOf(whileSneaking));
        snapshot.put("whileCTRL", String.valueOf(whileCTRL));
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("dirIndex")) {
            try { dirIndex = Integer.parseInt(snapshot.get("dirIndex")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("actionIndex")) {
            try { actionIndex = Integer.parseInt(snapshot.get("actionIndex")); } catch (NumberFormatException ignored) {}
        }
        if (snapshot.containsKey("whileSneaking")) whileSneaking = Boolean.parseBoolean(snapshot.get("whileSneaking"));
        if (snapshot.containsKey("whileCTRL")) whileCTRL = Boolean.parseBoolean(snapshot.get("whileCTRL"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "show_controls";
        Double px = parseDouble(pointField.x(), "X");
        Double py = parseDouble(pointField.y(), "Y");
        Double pz = parseDouble(pointField.z(), "Z");
        if (px == null || py == null || pz == null) return null;
        s.point = List.of(px, py, pz);
        s.direction = DIRECTIONS[dirIndex];
        s.duration = parseIntOr(durationField.getValue(), 60);
        if (actionIndex > 0) s.action = ACTIONS[actionIndex];
        String item = itemField.getValue().trim();
        if (!item.isEmpty()) s.item = item;
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
        if (whileSneaking) s.whileSneaking = true;
        if (whileCTRL) s.whileCTRL = true;
        return s;
    }
}
