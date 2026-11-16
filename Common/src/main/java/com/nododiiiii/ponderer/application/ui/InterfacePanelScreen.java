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
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A panel is edited as a nested tutorial; child saves stay in this draft until the panel is saved. */
public final class InterfacePanelScreen extends AbstractStepEditorScreen {
    private static final Gson JSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter()).create();
    private static final String[] DIRECTIONS = {"down", "up", "left", "right"};
    private final StepXyzFieldHandle point = new StepXyzFieldHandle("point");
    private final StepXyzFieldHandle transform = new StepXyzFieldHandle("panelTransform");
    private final List<DslScene.DslStep> children = new ArrayList<>();
    private final Map<DslScene.DslStep, DslScene.DslStep> copiedChildren = new IdentityHashMap<>();
    private int direction;
    private boolean panelBackground = true;
    private boolean panelAnimation = true;
    private boolean panelBlocking = true;
    @Nullable private List<Double> panelCrop;

    public InterfacePanelScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        this(scene, sceneIndex, parent, -1, null);
    }

    public InterfacePanelScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                int index, @Nullable DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.step.type.interface_panel"), scene, sceneIndex, parent, index, step);
        if (hasInterfaceBeforeStep()) point.setValue(0, 0, 0);
        else point.setValue(2.5, 1.5, 2.5);
        transform.setValue(0, 0, 0.5);
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
    @Override protected boolean showsKeyFrame() { return false; }

    @Override protected void collectStepEntries(List<DeclarativeFormEntry> entries) {
        if (!StepClipboard.isOwnedBy(copiedChildren)) copiedChildren.clear();
        copiedChildren.keySet().retainAll(children.subList(Math.min(1, children.size()), children.size()));
        if (!children.isEmpty()) entries.add(FieldSpecs.labeledButton(
            "ponderer.ui.show_interface", null, () -> openChild(0, null),
            () -> UIText.of("ponderer.ui.interface_panel.edit"), null));
        boolean uiAnchor = hasInterfaceBeforeStep();
        entries.add(FieldSpecs.xyz(point, uiAnchor ? "ponderer.ui.point" : "ponderer.ui.interface_panel.point",
            uiAnchor ? "ponderer.ui.pick.ui.tooltip" : "ponderer.ui.field.position.tooltip", PickState.TargetField.POINT));
        entries.add(FieldSpecs.choice("ponderer.ui.show_controls.direction", "ponderer.ui.show_controls.direction.tooltip", 70,
            () -> direction = (direction + 1) % DIRECTIONS.length,
            () -> UIText.of("ponderer.ui.show_controls.direction." + DIRECTIONS[direction])));
        entries.add(FieldSpecs.xyz(transform, "ponderer.ui.interface_panel.transform", "ponderer.ui.interface_panel.transform.tooltip",
            FieldType.XYZ, FieldDecorators.xyzHints("ponderer.ui.interface_panel.offset_x", "ponderer.ui.interface_panel.offset_y", "ponderer.ui.interface_panel.scale")));
        entries.add(FieldSpecs.toggle("ponderer.ui.interface_panel.background", "ponderer.ui.interface_panel.background.tooltip",
            () -> panelBackground, () -> panelBackground = !panelBackground));
        entries.add(FieldSpecs.toggle("ponderer.ui.interface_panel.animation", "ponderer.ui.interface_panel.animation.tooltip",
            () -> panelAnimation, () -> panelAnimation = !panelAnimation));
        entries.add(FieldSpecs.toggle("ponderer.ui.interface_panel.blocking", "ponderer.ui.interface_panel.blocking.tooltip",
            () -> panelBlocking, () -> panelBlocking = !panelBlocking));
        entries.add(FieldSpecs.toggle("ponderer.ui.key_frame", "ponderer.ui.key_frame.tooltip",
            () -> attachKeyFrame, () -> attachKeyFrame = !attachKeyFrame));
        entries.add(screen -> screen.createSectionHeaderEntry(UIText.of("ponderer.ui.interface_panel.children")));
        if (children.size() == 1) entries.add(screen -> screen.appendBuiltEntry(childEntry(0)));
        for (int i = 1; i < children.size(); i++) {
            int index = i;
            entries.add(screen -> screen.appendBuiltEntry(childEntry(index)));
        }
    }

    private SceneStepListEntry childEntry(int index) {
        return new SceneStepListEntry(
            () -> index == 0 ? UIText.of("ponderer.ui.scene_editor.no_steps")
                : index + ". " + StepPreviewText.title(children.get(index)),
            () -> index == 0 ? "" : StepPreviewText.summary(children.get(index)),
            index == 0 ? null : () -> openChild(index, null), List.of(
                SceneStepListEntry.iconButton(
                    PonderIconStencils.rotated(PonderGuiTextures.ICON_CONFIG_PREV, 90, 12),
                    () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.move_up")),
                    () -> moveChild(index, -1), null, () -> index > 1),
                SceneStepListEntry.iconButton(
                    PonderIconStencils.rotated(PonderGuiTextures.ICON_CONFIG_NEXT, 90, 12),
                    () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.move_down")),
                    () -> moveChild(index, 1), null, () -> index > 0 && index + 1 < children.size()),
                SceneStepListEntry.iconButton(PondererGuiIcons.INSERT,
                    () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.insert")),
                    () -> ScreenOpener.open(new ChildTypeSelector(index)), null, () -> true),
                SceneStepListEntry.iconButton(PondererGuiIcons.COPY,
                    () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.copy"),
                        Component.translatable("ponderer.ui.scene_editor.btn.copy.multi_select").withStyle(ChatFormatting.DARK_GRAY)),
                    () -> copyChild(index), () -> toggleChildCopy(index), () -> index > 0,
                    index > 0 && copiedChildren.containsKey(children.get(index))),
                SceneStepListEntry.iconButton(PondererGuiIcons.PASTE,
                    () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.paste_after")),
                    () -> pasteChildrenAfter(index), null, this::canPasteChildren),
                SceneStepListEntry.iconButton(PondererGuiIcons.REMOVE,
                    () -> List.of(Component.translatable("ponderer.ui.scene_editor.btn.delete")),
                    () -> { children.remove(index); rebuildListPreservingScroll(); }, null, () -> index > 0)));
    }

    private void moveChild(int index, int delta) {
        if (index <= 0 || index + delta <= 0 || index + delta >= children.size()) return;
        Collections.swap(children, index, index + delta);
        rebuildListPreservingScroll();
    }

    private void copyChild(int index) {
        if (index <= 0 || index >= children.size()) return;
        copiedChildren.clear();
        var step = children.get(index);
        copiedChildren.put(step, StepClipboard.copy(step));
        updateClipboardFromSelection();
        rebuildListPreservingScroll();
    }

    private void toggleChildCopy(int index) {
        if (index <= 0 || index >= children.size()) return;
        var step = children.get(index);
        if (copiedChildren.containsKey(step)) copiedChildren.remove(step);
        else copiedChildren.put(step, StepClipboard.copy(step));
        updateClipboardFromSelection();
        rebuildListPreservingScroll();
    }

    private void updateClipboardFromSelection() {
        StepClipboard.setContents(copiedChildren,
            children.stream().filter(copiedChildren::containsKey).map(copiedChildren::get).toList());
    }

    private boolean canPasteChildren() {
        if (StepClipboard.contents().isEmpty() || children.isEmpty()) return false;
        var panel = new DslScene.DslStep();
        panel.steps = new ArrayList<>();
        panel.steps.add(children.getFirst());
        panel.steps.addAll(StepClipboard.contents());
        try { InterfacePanelSpec.validate(panel); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private void pasteChildrenAfter(int afterIndex) {
        if (StepClipboard.isOwnedBy(copiedChildren) && !copiedChildren.isEmpty()) updateClipboardFromSelection();
        if (!canPasteChildren()) return;
        children.addAll(afterIndex + 1, StepClipboard.contents().stream().map(StepClipboard::copy).toList());
        rebuildListPreservingScroll();
    }

    private void openCrop(AbstractStepEditorScreen editor, @Nullable List<Double> crop) {
        var opening = editor.buildStep();
        if (opening == null || scene == null) return;
        // Range selection only needs the opening, even while the outer layout is incomplete.
        var panel = new DslScene.DslStep();
        panel.type = InterfacePanelSpec.TYPE;
        panel.steps = List.of(opening);
        panel.panelCrop = crop;
        DslScene draft = SceneCodec.copy(scene);
        var segment = draft.scenes.get(sceneIndex);
        var steps = new ArrayList<>(segment.steps == null ? List.<DslScene.DslStep>of() : segment.steps);
        int index = isEditMode() ? editIndex : insertAfterIndex >= 0 ? Math.min(insertAfterIndex + 1, steps.size()) : steps.size();
        if (isEditMode()) steps.set(index, panel);
        else steps.add(index, panel);
        segment.steps = steps;
        var snapshot = editor.snapshotForm();
        editor.setPendingPickRestore(snapshot);
        Minecraft.getInstance().setScreen(new InterfacePanelCropScreen(editor, draft, sceneIndex, index,
            opening, crop, chosen -> {
                var restored = new java.util.HashMap<>(snapshot);
                restored.put("panelCrop", JSON.toJson(chosen));
                editor.setPendingPickRestore(restored);
            }));
    }

    private void openChild(int index, @Nullable String addType) {
        DslScene draft = SceneCodec.copy(scene);
        var segment = new DslScene.SceneSegment();
        segment.id = "interface_panel_children";
        segment.steps = children;
        draft.scenes = List.of(segment);
        StepEditorTarget target = new StepEditorTarget() {
            @Nullable private List<Double> crop = panelCrop == null ? null : new ArrayList<>(panelCrop);
            @Override public Screen returnScreen() { return InterfacePanelScreen.this; }
            private boolean isOpening(AbstractStepEditorScreen editor) {
                return editor.editIndex == 0 && "show_interface".equals(editor.getStepType());
            }
            @Override public void configureFormState(AbstractStepEditorScreen editor, List<SnapshotParticipant> participants) {
                if (isOpening(editor)) participants.add(FieldBindings.string("panelCrop", () -> JSON.toJson(crop),
                    value -> crop = JSON.fromJson(value, new TypeToken<List<Double>>() {}.getType())));
            }
            @Override public void collectFormEntries(AbstractStepEditorScreen editor, List<DeclarativeFormEntry> entries) {
                if (isOpening(editor)) entries.add(FieldSpecs.labeledButton(
                    "ponderer.ui.interface_panel.range", "ponderer.ui.interface_panel.range.tooltip",
                    () -> openCrop(editor, crop),
                    () -> UIText.of(crop == null ? "ponderer.ui.interface_panel.range.full" : "ponderer.ui.interface_panel.range.custom"),
                    () -> UIText.of("ponderer.ui.interface_panel.range.tooltip")));
            }
            @Override public void save(int editIndex, int afterIndex, DslScene.DslStep step) {
                if (editIndex >= 0) {
                    var previous = children.get(editIndex);
                    step.id = previous.id;
                    step.extensionData = previous.extensionData == null ? null : previous.extensionData.deepCopy();
                    children.set(editIndex, step);
                    if (editIndex == 0) panelCrop = crop == null ? null : new ArrayList<>(crop);
                } else children.add(afterIndex >= 0 ? afterIndex + 1 : children.size(), step);
            }
        };
        AbstractStepEditorScreen editor = addType == null
            ? StepEditorFactory.createEditScreen(children.get(index), index, draft, 0, parent)
            : StepEditorFactory.createAddScreen(addType, draft, 0, parent);
        if (editor != null) {
            if (addType != null) editor.setInsertAfterIndex(index);
            ScreenOpener.open(editor.setStepTarget(target));
        }
    }

    @Override protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.point != null && step.point.size() >= 2)
            point.setValue(step.point.get(0), step.point.get(1), step.point.size() >= 3 ? step.point.get(2) : 0);
        for (int i = 0; i < DIRECTIONS.length; i++) if (DIRECTIONS[i].equalsIgnoreCase(step.direction)) direction = i;
        var values = step.panelTransform == null ? InterfacePanelSpec.DEFAULT_TRANSFORM : step.panelTransform;
        transform.setValue(values.get(0), values.get(1), values.get(2));
        panelCrop = step.panelCrop == null ? null : new ArrayList<>(step.panelCrop);
        panelBackground = !Boolean.FALSE.equals(step.panelBackground);
        panelAnimation = !Boolean.FALSE.equals(step.panelAnimation);
        panelBlocking = !Boolean.FALSE.equals(step.panelBlocking);
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
        snapshot.put("panelAnimation", Boolean.toString(panelAnimation));
        snapshot.put("panelBlocking", Boolean.toString(panelBlocking));
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
        if (snapshot.containsKey("panelAnimation")) panelAnimation = Boolean.parseBoolean(snapshot.get("panelAnimation"));
        if (snapshot.containsKey("panelBlocking")) panelBlocking = Boolean.parseBoolean(snapshot.get("panelBlocking"));
    }

    @Override @Nullable protected DslScene.DslStep buildStep() {
        Double x = parseDouble(point.x(), "X"), y = parseDouble(point.y(), "Y"), z = parseDouble(point.z(), "Z");
        Double offsetX = parseDouble(transform.x(), UIText.of("ponderer.ui.interface_panel.offset_x"));
        Double offsetY = parseDouble(transform.y(), UIText.of("ponderer.ui.interface_panel.offset_y"));
        Double factor = parseDouble(transform.z(), UIText.of("ponderer.ui.interface_panel.scale"));
        if (x == null || y == null || z == null || offsetX == null || offsetY == null || factor == null) return null;
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(factor) || factor <= 0
            || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            setErrorMessage(UIText.of("ponderer.ui.interface_panel.invalid"));
            return null;
        }
        var step = new DslScene.DslStep();
        step.type = InterfacePanelSpec.TYPE;
        step.point = List.of(x, y, z);
        step.direction = DIRECTIONS[direction];
        step.panelTransform = List.of(offsetX, offsetY, factor);
        step.panelCrop = panelCrop == null ? null : new ArrayList<>(panelCrop);
        step.panelBackground = panelBackground;
        step.panelAnimation = panelAnimation;
        step.panelBlocking = panelBlocking;
        step.steps = JSON.fromJson(JSON.toJson(children), new TypeToken<List<DslScene.DslStep>>() {}.getType());
        try { InterfacePanelSpec.validate(step); }
        catch (IllegalArgumentException error) { setErrorMessage(error.getMessage()); return null; }
        return step;
    }

    private final class ChildTypeSelector extends AbstractReadonlyDeclarativeListScreen {
        private final int afterIndex;
        ChildTypeSelector(int afterIndex) {
            super(InterfacePanelScreen.this, "ponderer.ui.scope.editor", "ponderer.ui.interface_panel.add", STEP_EDITOR_LIST_WIDTH);
            this.afterIndex = afterIndex;
        }
        @Override protected void collectEntries(List<ConfigScreenList.Entry> entries) {
            for (String type : InterfacePanelSpec.EDITABLE_STEPS)
                entries.add(new FullButtonListEntry(UIText.of("ponderer.ui.step.type." + type), null, () -> openChild(afterIndex, type)));
        }
    }
}
