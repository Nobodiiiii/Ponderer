package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for "show_controls" step.
 * Fields: point XYZ, direction, duration, action, item, whileSneaking, whileCTRL
 */
public class ShowControlsScreen extends AbstractStepEditorScreen {

    private static final String[] DIRECTIONS = {"down", "up", "left", "right"};
    private static final String[] ACTIONS = {"", "left", "right", "scroll"};

    private HintableTextFieldWidget pointXField, pointYField, pointZField;
    private HintableTextFieldWidget durationField;
    private HintableTextFieldWidget itemField;
    private HintableTextFieldWidget nbtField;
    private int dirIndex = 0;
    private int actionIndex = 0;
    private BoxWidget dirButton, actionButton;
    private boolean whileSneaking = false, whileCTRL = false;
    private BoxWidget sneakToggle, ctrlToggle;
    private PonderButton pickBtnPoint;
    @Nullable
    private PonderButton jeiBtn;
    @Nullable
    private PonderButton heldItemBtn;

    public ShowControlsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_controls"), scene, sceneIndex, parent);
    }

    public ShowControlsScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                              int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_controls"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 9; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.show_controls"); }

    @Override
    protected void buildForm() {
        beginForm();
        var pos = addFormXyzRow("ponderer.ui.point", "ponderer.ui.show_controls.point.tooltip",
                PickState.TargetField.POINT, true);
        pointXField = pos.x(); pointYField = pos.y(); pointZField = pos.z(); pickBtnPoint = pos.pickBtn();
        dirButton = addFormCycleButton("ponderer.ui.show_controls.direction", "ponderer.ui.show_controls.direction.tooltip",
                100, () -> dirIndex = (dirIndex + 1) % DIRECTIONS.length,
                () -> optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[dirIndex]));
        durationField = addFormNumberField("ponderer.ui.duration", "ponderer.ui.duration.tooltip.controls",
                "60", 50, "ponderer.ui.ticks");
        actionButton = addFormCycleButton("ponderer.ui.show_controls.action", "ponderer.ui.show_controls.action.tooltip",
                100, () -> actionIndex = (actionIndex + 1) % ACTIONS.length,
                () -> actionIndex == 0 ? UIText.of("ponderer.ui.none") : optionLabel("ponderer.ui.show_controls.action", ACTIONS[actionIndex]));
        var icon = addFormTextFieldWithJeiAndHeldItem("ponderer.ui.show_controls.item", "ponderer.ui.show_controls.item.tooltip",
            UIText.of("ponderer.ui.show_controls.item.hint"), IdFieldMode.INGREDIENT,
                stack -> {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    itemField.setValue(itemId);
                    if (nbtField != null) {
                        if (stack.getTag() != null && !stack.getTag().isEmpty()) {
                            nbtField.setValue(stack.getTag().toString());
                        } else {
                            nbtField.setValue("");
                        }
                    }
                });
        itemField = icon.field(); jeiBtn = icon.jeiBtn(); heldItemBtn = icon.heldItemBtn();
        nbtField = addFormNbtField("ponderer.ui.show_controls.nbt", "ponderer.ui.show_controls.nbt.tooltip",
                "{}", 124, "nbt");
        sneakToggle = addFormToggle("ponderer.ui.show_controls.sneaking", "ponderer.ui.show_controls.sneaking.tooltip",
                () -> whileSneaking, () -> whileSneaking = !whileSneaking);
        ctrlToggle = addFormToggle("ponderer.ui.show_controls.ctrl", "ponderer.ui.show_controls.ctrl.tooltip",
                () -> whileCTRL, () -> whileCTRL = !whileCTRL);
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 3) {
            pointXField.setValue(String.valueOf(step.point.get(0)));
            pointYField.setValue(String.valueOf(step.point.get(1)));
            pointZField.setValue(String.valueOf(step.point.get(2)));
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
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("pointX", pointXField.getValue());
        m.put("pointY", pointYField.getValue());
        m.put("pointZ", pointZField.getValue());
        m.put("duration", durationField.getValue());
        m.put("item", itemField.getValue());
        m.put("nbt", nbtField.getValue());
        m.put("dirIndex", String.valueOf(dirIndex));
        m.put("actionIndex", String.valueOf(actionIndex));
        m.put("whileSneaking", String.valueOf(whileSneaking));
        m.put("whileCTRL", String.valueOf(whileCTRL));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("pointX")) pointXField.setValue(snapshot.get("pointX"));
        if (snapshot.containsKey("pointY")) pointYField.setValue(snapshot.get("pointY"));
        if (snapshot.containsKey("pointZ")) pointZField.setValue(snapshot.get("pointZ"));
        if (snapshot.containsKey("duration")) durationField.setValue(snapshot.get("duration"));
        if (snapshot.containsKey("item")) itemField.setValue(snapshot.get("item"));
        if (snapshot.containsKey("nbt")) nbtField.setValue(snapshot.get("nbt"));
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
        Double px = parseDouble(pointXField.getValue(), "X");
        Double py = parseDouble(pointYField.getValue(), "Y");
        Double pz = parseDouble(pointZField.getValue(), "Z");
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
