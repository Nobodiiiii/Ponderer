package com.nododiiiii.ponderer.application.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nododiiiii.ponderer.application.ui.widgets.*;
import com.nododiiiii.ponderer.editor.capture.PickState;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.scene.SceneCodec;
import com.nododiiiii.ponderer.scene.interfaces.InterfacePanelSpec;
import com.nododiiiii.ponderer.support.ui.UIText;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A panel is edited as a nested tutorial; child saves stay in this draft until the panel is saved. */
public final class InterfacePanelScreen extends AbstractStepEditorScreen {
    private static final Gson JSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private static final String[] DIRECTIONS = {"down", "up", "left", "right"};
    private final StepXyzFieldHandle point = new StepXyzFieldHandle("point");
    private final StepTextFieldHandle scale = new StepTextFieldHandle("scale");
    private final StepTextFieldHandle hold = new StepTextFieldHandle("duration");
    private final List<DslScene.DslStep> children = new ArrayList<>();
    private int direction;
    private boolean panelBackground = true;
    @Nullable private List<Double> panelCrop;

    public InterfacePanelScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(scene, sceneIndex, parent, -1, null);
    }

    public InterfacePanelScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                int index, @Nullable DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.step.type.interface_panel"), scene, sceneIndex, parent, index, step);
        point.setValue(2.5, 1.5, 2.5);
        scale.setValue("0.5");
        hold.setValue("60");
        var opening = new DslScene.DslStep();
        opening.type = "show_interface";
        opening.block = "minecraft:crafting_table";
        children.add(opening);
        var idle = new DslScene.DslStep();
        idle.type = "idle";
        idle.duration = 20;
        children.add(idle);
    }

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.step.type.interface_panel"); }
    @Override protected String getStepType() { return InterfacePanelSpec.TYPE; }

    @Override protected void collectStepEntries(List<DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.xyz(point, "ponderer.ui.show_controls.point", "ponderer.ui.show_controls.point.tooltip", PickState.TargetField.POINT));
        entries.add(FieldSpecs.choice("ponderer.ui.show_controls.direction", "ponderer.ui.show_controls.direction.tooltip", 70,
            () -> direction = (direction + 1) % DIRECTIONS.length,
            () -> UIText.of("ponderer.ui.show_controls.direction." + DIRECTIONS[direction])));
        entries.add(FieldSpecs.number(scale, "ponderer.ui.interface_panel.scale", "ponderer.ui.interface_panel.scale.tooltip", "0.5", 70, null));
        entries.add(FieldSpecs.toggle("ponderer.ui.interface_panel.background", "ponderer.ui.interface_panel.background.tooltip",
            () -> panelBackground, () -> panelBackground = !panelBackground));
        entries.add(FieldSpecs.labeledButton("ponderer.ui.interface_panel.range", "ponderer.ui.interface_panel.range.tooltip",
            this::openCrop, () -> UIText.of(panelCrop == null ? "ponderer.ui.interface_panel.range.full" : "ponderer.ui.interface_panel.range.custom"),
            () -> UIText.of("ponderer.ui.interface_panel.range.tooltip")));
        entries.add(FieldSpecs.ticksNumber(hold, "ponderer.ui.interface_panel.hold", "ponderer.ui.interface_panel.hold.tooltip", "60", 70));
        entries.add(screen -> screen.createSectionHeaderEntry(UIText.of("ponderer.ui.interface_panel.children")));
        for (int i = 0; i < children.size(); i++) {
            int index = i;
            entries.add(screen -> screen.appendBuiltEntry(new SceneStepListEntry(
                () -> (index + 1) + ". " + StepPreviewText.title(children.get(index)),
                () -> StepPreviewText.summary(children.get(index)),
                () -> openChild(index, null), List.of(
                    SceneStepListEntry.button("↑", null, () -> moveChild(index, -1), null, () -> 0xFFFFFF, () -> index > 1),
                    SceneStepListEntry.button("↓", null, () -> moveChild(index, 1), null, () -> 0xFFFFFF,
                        () -> index > 0 && index + 1 < children.size()),
                    SceneStepListEntry.iconButton(PondererGuiIcons.REMOVE,
                        () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.delete")),
                        () -> { children.remove(index); rebuildListPreservingScroll(); }, null, () -> index > 0)))));
        }
        entries.add(screen -> screen.createFullButtonEntry(UIText.of("ponderer.ui.interface_panel.add"), null,
            () -> ScreenOpener.open(new ChildTypeSelector())));
    }

    private void moveChild(int index, int delta) {
        Collections.swap(children, index, index + delta);
        rebuildListPreservingScroll();
    }

    private void openCrop() {
        var panel = buildStep();
        if (panel == null || scene == null) return;
        DslScene draft = SceneCodec.copy(scene);
        var segment = draft.scenes.get(sceneIndex);
        var steps = new ArrayList<>(segment.steps == null ? List.<DslScene.DslStep>of() : segment.steps);
        int index = isEditMode() ? editIndex : insertAfterIndex >= 0 ? Math.min(insertAfterIndex + 1, steps.size()) : steps.size();
        if (isEditMode()) steps.set(index, panel);
        else steps.add(index, panel);
        segment.steps = steps;
        var snapshot = snapshotForm();
        setPendingPickRestore(snapshot);
        Minecraft.getInstance().setScreen(new InterfacePanelCropScreen(this, draft, sceneIndex, index,
            panel.steps.getFirst(), panelCrop, chosen -> {
                var restored = new java.util.HashMap<>(snapshot);
                restored.put("panelCrop", JSON.toJson(chosen));
                setPendingPickRestore(restored);
            }));
    }

    private void openChild(int index, @Nullable String addType) {
        DslScene draft = SceneCodec.copy(scene);
        var segment = new DslScene.SceneSegment();
        segment.id = "interface_panel_children";
        segment.steps = children;
        draft.scenes = List.of(segment);
        StepEditorTarget target = new StepEditorTarget() {
            @Override public Screen returnScreen() { return InterfacePanelScreen.this; }
            @Override public void save(int editIndex, int afterIndex, DslScene.DslStep step) {
                if (editIndex >= 0) {
                    var previous = children.get(editIndex);
                    step.id = previous.id;
                    step.extensionData = previous.extensionData == null ? null : previous.extensionData.deepCopy();
                    children.set(editIndex, step);
                } else children.add(afterIndex >= 0 ? afterIndex + 1 : children.size(), step);
            }
        };
        AbstractStepEditorScreen editor = addType == null
            ? StepEditorFactory.createEditScreen(children.get(index), index, draft, 0, parent)
            : StepEditorFactory.createAddScreen(addType, draft, 0, parent);
        if (editor != null) ScreenOpener.open(editor.setStepTarget(target));
    }

    @Override protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 3) point.setValue(step.point.get(0), step.point.get(1), step.point.get(2));
        for (int i = 0; i < DIRECTIONS.length; i++) if (DIRECTIONS[i].equalsIgnoreCase(step.direction)) direction = i;
        scale.setValue(Float.toString(step.scale == null ? InterfacePanelSpec.DEFAULT_SCALE : step.scale));
        panelCrop = step.panelCrop == null ? null : new ArrayList<>(step.panelCrop);
        panelBackground = !Boolean.FALSE.equals(step.panelBackground);
        hold.setValue(Integer.toString(step.durationOrDefault(60)));
        if (step.steps != null) {
            children.clear();
            children.addAll(JSON.fromJson(JSON.toJson(step.steps), new TypeToken<List<DslScene.DslStep>>() {}.getType()));
        }
    }

    @Override protected void appendCustomSnapshot(Map<String, String> snapshot) {
        snapshot.put("panelChildren", JSON.toJson(children));
        snapshot.put("panelDirection", Integer.toString(direction));
        snapshot.put("panelCrop", JSON.toJson(panelCrop));
        snapshot.put("panelBackground", Boolean.toString(panelBackground));
    }

    @Override protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        if (snapshot.containsKey("panelChildren")) {
            children.clear();
            children.addAll(JSON.fromJson(snapshot.get("panelChildren"), new TypeToken<List<DslScene.DslStep>>() {}.getType()));
        }
        direction = Math.floorMod(parseIntOr(snapshot.get("panelDirection"), direction), DIRECTIONS.length);
        if (snapshot.containsKey("panelCrop"))
            panelCrop = JSON.fromJson(snapshot.get("panelCrop"), new TypeToken<List<Double>>() {}.getType());
        if (snapshot.containsKey("panelBackground")) panelBackground = Boolean.parseBoolean(snapshot.get("panelBackground"));
    }

    @Override @Nullable protected DslScene.DslStep buildStep() {
        Double x = parseDouble(point.x(), "X"), y = parseDouble(point.y(), "Y"), z = parseDouble(point.z(), "Z");
        Double factor = parseDouble(scale.getValue(), UIText.of("ponderer.ui.interface_panel.scale"));
        Integer ticks = parseInt(hold.getValue(), UIText.of("ponderer.ui.interface_panel.hold"));
        if (x == null || y == null || z == null || factor == null || ticks == null) return null;
        if (!Float.isFinite(factor.floatValue()) || factor.floatValue() <= 0 || ticks < 0
            || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            setErrorMessage(UIText.of("ponderer.ui.interface_panel.invalid"));
            return null;
        }
        var step = new DslScene.DslStep();
        step.type = InterfacePanelSpec.TYPE;
        step.point = List.of(x, y, z);
        step.direction = DIRECTIONS[direction];
        step.scale = factor.floatValue();
        step.panelCrop = panelCrop == null ? null : new ArrayList<>(panelCrop);
        step.panelBackground = panelBackground;
        step.duration = ticks;
        step.steps = JSON.fromJson(JSON.toJson(children), new TypeToken<List<DslScene.DslStep>>() {}.getType());
        try { InterfacePanelSpec.validate(step); }
        catch (IllegalArgumentException error) { setErrorMessage(error.getMessage()); return null; }
        return step;
    }

    private final class ChildTypeSelector extends AbstractReadonlyDeclarativeListScreen {
        ChildTypeSelector() {
            super(InterfacePanelScreen.this, "ponderer.ui.scope.editor", "ponderer.ui.interface_panel.add", STEP_EDITOR_LIST_WIDTH);
        }
        @Override protected void collectEntries(List<ConfigScreenList.Entry> entries) {
            for (String type : InterfacePanelSpec.EDITABLE_STEPS)
                entries.add(new FullButtonListEntry(UIText.of("ponderer.ui.step.type." + type), null, () -> openChild(-1, type)));
        }
    }
}
