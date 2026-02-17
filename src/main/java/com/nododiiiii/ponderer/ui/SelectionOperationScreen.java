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
import java.util.Locale;
import java.util.Map;

public class SelectionOperationScreen extends AbstractStepEditorScreen {

    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};

    private final String stepType;
    private final boolean withDirection;
    private final boolean withLinkId;

    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private BoxWidget directionButton;
    private HintableTextFieldWidget linkIdField;
    private int directionIndex = 0;
    private PonderButton pickBtn1, pickBtn2;

    public SelectionOperationScreen(String stepType, boolean withDirection, boolean withLinkId,
                                    DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui." + stepType + ".add"), scene, sceneIndex, parent);
        this.stepType = stepType;
        this.withDirection = withDirection;
        this.withLinkId = withLinkId;
    }

    public SelectionOperationScreen(String stepType, boolean withDirection, boolean withLinkId,
                                    DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                    int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui." + stepType + ".edit"), scene, sceneIndex, parent, editIndex, step);
        this.stepType = stepType;
        this.withDirection = withDirection;
        this.withLinkId = withLinkId;
    }

    @Override
    protected int getFormRowCount() {
        int rows = 2;
        if (withDirection) rows++;
        if (withLinkId) rows++;
        return rows;
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui." + stepType);
    }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + 26, sw = 38;
        int lx = guiLeft + 10;

        posXField = createSmallNumberField(x, y, sw, "X");
        posYField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        posZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        pickBtn1 = createPickButton(x + 3 * (sw + 5), y, PickState.TargetField.POS1);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".pos_from"), UIText.of("ponderer.ui." + stepType + ".pos_from.tooltip"));

        y += 22;
        pos2XField = createSmallNumberField(x, y, sw, "X");
        pos2YField = createSmallNumberField(x + sw + 5, y, sw, "Y");
        pos2ZField = createSmallNumberField(x + 2 * (sw + 5), y, sw, "Z");
        pickBtn2 = createPickButton(x + 3 * (sw + 5), y, PickState.TargetField.POS2);
        addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".pos_to"), UIText.of("ponderer.ui." + stepType + ".pos_to.tooltip"));

        if (withDirection) {
            y += 22;
            directionButton = createFormButton(x, y, 140);
            directionButton.withCallback(() -> directionIndex = (directionIndex + 1) % DIRECTIONS.length);
            addRenderableWidget(directionButton);
            addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".direction"), UIText.of("ponderer.ui." + stepType + ".direction.tooltip"));
        }

        if (withLinkId) {
            y += 22;
            linkIdField = createTextField(x, y, 140, 18, "default");
            addLabelTooltip(lx, y + 3, UIText.of("ponderer.ui." + stepType + ".link"), UIText.of("ponderer.ui." + stepType + ".link.tooltip"));
        }
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
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
        if (withDirection && step.direction != null) {
            String normalized = normalizeDirection(step.direction);
            for (int i = 0; i < DIRECTIONS.length; i++) {
                if (DIRECTIONS[i].equals(normalized)) {
                    directionIndex = i;
                    break;
                }
            }
        }
        if (withLinkId && step.linkId != null) {
            linkIdField.setValue(step.linkId);
        }
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        int lx = guiLeft + 10, y = guiTop + 29, lc = 0xCCCCCC;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".pos_from"), lx, y, lc);
        y += 22;
        graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".pos_to"), lx, y, lc);
        if (withDirection) {
            y += 22;
            graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".direction"), lx, y, lc);
        }
        if (withLinkId) {
            y += 22;
            graphics.drawString(font, UIText.of("ponderer.ui." + stepType + ".link"), lx, y, lc);
        }
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (withDirection && directionButton != null) {
            var font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font,
                optionLabel("ponderer.ui.show_controls.direction", DIRECTIONS[directionIndex]),
                directionButton.getX() + 70,
                directionButton.getY() + 2,
                0xFFFFFF);
        }
        renderPickButtonLabel(graphics, pickBtn1);
        renderPickButtonLabel(graphics, pickBtn2);
    }

    private String optionLabel(String prefix, String value) {
        String key = prefix + "." + value;
        String translated = UIText.of(key);
        return key.equals(translated) ? value : translated;
    }

    private String normalizeDirection(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "上", "向上", "up" -> "up";
            case "北", "向北", "north" -> "north";
            case "南", "向南", "south" -> "south";
            case "西", "向西", "west" -> "west";
            case "东", "向东", "east" -> "east";
            default -> "down";
        };
    }

    @Override
    protected String getStepType() { return stepType; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        if (withDirection) m.put("direction", String.valueOf(directionIndex));
        if (withLinkId && linkIdField != null) m.put("linkId", linkIdField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (withDirection && snapshot.containsKey("direction")) {
            try { directionIndex = Integer.parseInt(snapshot.get("direction")); } catch (NumberFormatException ignored) {}
        }
        if (withLinkId && linkIdField != null && snapshot.containsKey("linkId")) linkIdField.setValue(snapshot.get("linkId"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        errorMessage = null;

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
                errorMessage = UIText.of("ponderer.ui." + stepType + ".error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "X2");
            py2 = parseInt(pos2Y, "Y2");
            pz2 = parseInt(pos2Z, "Z2");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }

        DslScene.DslStep s = new DslScene.DslStep();
        s.type = stepType;
        s.blockPos = List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = List.of(px2, py2, pz2);

        if (withDirection) {
            s.direction = DIRECTIONS[directionIndex];
        }

        if (withLinkId) {
            String linkId = linkIdField.getValue().trim();
            s.linkId = linkId.isEmpty() ? "default" : linkId;
        }

        return s;
    }
}
