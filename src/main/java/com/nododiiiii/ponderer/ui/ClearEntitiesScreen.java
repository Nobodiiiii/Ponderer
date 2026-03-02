package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared editor screen for clear_item_entities and clear_entities step types.
 */
public class ClearEntitiesScreen extends AbstractStepEditorScreen {

    private final String stepType;
    private final IdFieldMode jeiMode;

    private HintableTextFieldWidget idField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private boolean fullScene = false;
    private BoxWidget fullSceneToggle;
    private PonderButton pickBtn1, pickBtn2;
    @Nullable
    private PonderButton jeiBtn;

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
    protected int getFormRowCount() { return 4; }

    @Override
    protected String getHeaderTitle() { return UIText.of("ponderer.ui." + stepType); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 38;
        int lx = guiLeft + 10;

        // Row 1: ID field (optional) + JEI button
        idField = createTextField(x, y, 124, 18, UIText.of("ponderer.ui." + stepType + ".id.hint"));
        jeiBtn = createJeiButton(x + 129, y, idField, jeiMode);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".id"), UIText.of("ponderer.ui." + stepType + ".id.tooltip"));
        y += 22;

        // Row 2: From position
        posXField = createSmallNumberField(x, y, sw, "X");
        posYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        posZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        pickBtn1 = createPickButton(x + 3 * (sw + 5), y, PickState.TargetField.POS1);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".pos_from"), UIText.of("ponderer.ui." + stepType + ".pos_from.tooltip"));
        y += 22;

        // Row 3: To position
        pos2XField = createSmallNumberField(x, y, sw, "X");
        pos2YField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        pos2ZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        pickBtn2 = createPickButton(x + 3 * (sw + 5), y, PickState.TargetField.POS2);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".pos_to"), UIText.of("ponderer.ui." + stepType + ".pos_to.tooltip"));
        y += 22;

        // Row 4: Full scene toggle
        fullSceneToggle = createToggle(x, y);
        fullSceneToggle.withCallback(() -> fullScene = !fullScene);
        addRenderableWidget(fullSceneToggle);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".full_scene"), UIText.of("ponderer.ui." + stepType + ".full_scene.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.item != null) idField.setValue(step.item);
        if (step.entity != null) idField.setValue(step.entity);
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
        if (step.fullScene != null) fullScene = step.fullScene;
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;

        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".id"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".pos_from"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".pos_to"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".full_scene"), lx, y + 3, lc);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderToggleState(graphics, fullSceneToggle, fullScene);
        renderPickButtonLabel(graphics, pickBtn1);
        renderPickButtonLabel(graphics, pickBtn2);
        renderJeiButtonLabel(graphics, jeiBtn);
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("id", idField.getValue());
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        m.put("fullScene", String.valueOf(fullScene));
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("id")) idField.setValue(snapshot.get("id"));
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (snapshot.containsKey("fullScene")) fullScene = Boolean.parseBoolean(snapshot.get("fullScene"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

        Integer px = null, py = null, pz = null;
        Integer px2 = null, py2 = null, pz2 = null;

        if (!fullScene) {
            px = parseInt(posXField.getValue(), "X");
            py = parseInt(posYField.getValue(), "Y");
            pz = parseInt(posZField.getValue(), "Z");
            if (px == null || py == null || pz == null) return null;

            String pos2X = pos2XField.getValue().trim();
            String pos2Y = pos2YField.getValue().trim();
            String pos2Z = pos2ZField.getValue().trim();
            boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
            if (hasPos2) {
                if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                    errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_to");
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
        if (fullScene) {
            s.fullScene = true;
        } else {
            s.blockPos = List.of(px, py, pz);
            if (px2 != null) s.blockPos2 = List.of(px2, py2, pz2);
        }
        return s;
    }
}
