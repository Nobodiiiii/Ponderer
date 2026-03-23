package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.createmod.catnip.config.ui.HintableTextFieldWidget;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Editor for "show_structure" step - optional height and optional structure reference. */
public class ShowStructureScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget scaleField;
    private HintableTextFieldWidget rotationField;
    private HintableTextFieldWidget posXField, posYField, posZField;
    private HintableTextFieldWidget pos2XField, pos2YField, pos2ZField;
    private HintableTextFieldWidget structureField;
    private PonderButton browseButton;
    private boolean waitingDownload;
    private String waitingSourceId;
    private DslScene.DslStep pendingStep;

    public ShowStructureScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_structure"), scene, sceneIndex, parent);
    }

    public ShowStructureScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                               int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_structure"), scene, sceneIndex, parent, editIndex, step);
    }

    @Override protected int getFormRowCount() { return 5; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.show_structure"); }

    @Override
    protected void buildForm() {
        beginForm();
        addFormLabel("ponderer.ui.show_structure.structure", "ponderer.ui.show_structure.structure.tooltip");
        int fx = fieldX();
        structureField = createTextField(fx, formY(), 105, 18, UIText.of("ponderer.ui.show_structure.structure.hint"));
        browseButton = createStructureBrowseButton(fx + 105 + FIELD_TO_BUTTON_GAP, formY());
        nextFormRow();

        scaleField = addFormNumberField("ponderer.ui.show_structure.scale", "ponderer.ui.show_structure.scale.tooltip", "1.0", 60);
        rotationField = addFormNumberField("ponderer.ui.show_structure.rotation", "ponderer.ui.show_structure.rotation.tooltip", "0", 60);
        var from = addFormXyzRow("ponderer.ui.show_structure.pos_from", "ponderer.ui.show_structure.pos_from.tooltip", PickState.TargetField.POS1);
        posXField = from.x();
        posYField = from.y();
        posZField = from.z();
        var to = addFormXyzRow("ponderer.ui.show_structure.pos_to", "ponderer.ui.show_structure.pos_to.tooltip", PickState.TargetField.POS2);
        pos2XField = to.x();
        pos2YField = to.y();
        pos2ZField = to.z();
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.scale != null) scaleField.setValue(String.valueOf(step.scale));
        if (step.rotation != null) rotationField.setValue(String.valueOf(step.rotation));
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
        if (step.structure != null && !step.structure.isBlank()) structureField.setValue(step.structure);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderFormForeground(graphics, mouseX, mouseY, partialTicks);
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, "S",
            browseButton.getX() + browseButton.getWidth() / 2, browseButton.getY() + 2, 0xFFFFFF);
    }

    private void openFilePicker() {
        Path structuresDir = SceneStore.getStructureDir();
        CompletableFuture.supplyAsync(() -> {
            try {
                String defaultPath = Files.exists(structuresDir)
                    ? structuresDir.toAbsolutePath().toString() + java.io.File.separator
                    : null;
                MemoryStack stack = MemoryStack.stackPush();
                try {
                    PointerBuffer filters = stack.mallocPointer(1);
                    filters.put(stack.UTF8("*.nbt"));
                    filters.flip();
                    return TinyFileDialogs.tinyfd_openFileDialog(
                        UIText.of("ponderer.ui.show_structure.browse"),
                        defaultPath,
                        filters,
                        "NBT files (*.nbt)",
                        false
                    );
                } finally {
                    stack.pop();
                }
            } catch (Exception e) {
                return null;
            }
        }).thenAcceptAsync(result -> {
            if (result == null) return;
            Path selected = Path.of(result);
            if (selected.startsWith(structuresDir)) {
                Path relative = structuresDir.relativize(selected);
                String refPath = relative.toString().replace('\\', '/');
                if (refPath.toLowerCase().endsWith(".nbt")) {
                    refPath = refPath.substring(0, refPath.length() - 4);
                }
                structureField.setValue("ponderer:" + refPath);
            } else {
                String fileName = selected.getFileName().toString();
                if (fileName.toLowerCase().endsWith(".nbt")) {
                    fileName = fileName.substring(0, fileName.length() - 4);
                }
                Path target = structuresDir.resolve(fileName + ".nbt");
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(selected, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    structureField.setValue("ponderer:" + fileName);
                } catch (Exception e) {
                    errorMessage = UIText.of("ponderer.ui.show_structure.structure.error.copy_failed");
                }
            }
        }, Minecraft.getInstance());
    }

    private PonderButton createStructureBrowseButton(int x, int y) {
        PonderButton btn = new PonderButton(x, y + 3, 14, 12);
        btn.withCallback(this::openFilePicker);
        addRenderableWidget(btn);
        addTooltip(x, y + 3, 14, 12, UIText.of("ponderer.ui.show_structure.browse.tooltip"));
        return btn;
    }

    @Override
    protected String getStepType() { return "show_structure"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("scale", scaleField.getValue());
        m.put("rotation", rotationField.getValue());
        m.put("posX", posXField.getValue());
        m.put("posY", posYField.getValue());
        m.put("posZ", posZField.getValue());
        m.put("pos2X", pos2XField.getValue());
        m.put("pos2Y", pos2YField.getValue());
        m.put("pos2Z", pos2ZField.getValue());
        m.put("structure", structureField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("scale")) scaleField.setValue(snapshot.get("scale"));
        if (snapshot.containsKey("rotation")) rotationField.setValue(snapshot.get("rotation"));
        if (snapshot.containsKey("posX")) posXField.setValue(snapshot.get("posX"));
        if (snapshot.containsKey("posY")) posYField.setValue(snapshot.get("posY"));
        if (snapshot.containsKey("posZ")) posZField.setValue(snapshot.get("posZ"));
        if (snapshot.containsKey("pos2X")) pos2XField.setValue(snapshot.get("pos2X"));
        if (snapshot.containsKey("pos2Y")) pos2YField.setValue(snapshot.get("pos2Y"));
        if (snapshot.containsKey("pos2Z")) pos2ZField.setValue(snapshot.get("pos2Z"));
        if (snapshot.containsKey("structure")) structureField.setValue(snapshot.get("structure"));
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        if (waitingDownload) {
            errorMessage = UIText.of("ponderer.ui.show_structure.structure.error.wait_download");
            return null;
        }
        errorMessage = null;
        DslScene.DslStep s = new DslScene.DslStep();
        s.type = "show_structure";
        String sv = scaleField.getValue().trim();
        if (!sv.isEmpty()) {
            Float sc = parseFloat(sv, "Scale");
            if (sc == null) return null;
            s.scale = sc;
        }
        String rv = rotationField.getValue().trim();
        if (!rv.isEmpty()) {
            Float rotation = parseFloat(rv, "Rotation");
            if (rotation == null) return null;
            s.rotation = rotation;
        }

        Integer px = parseOptionalInt(posXField.getValue(), "From X");
        Integer py = parseOptionalInt(posYField.getValue(), "From Y");
        Integer pz = parseOptionalInt(posZField.getValue(), "From Z");
        boolean hasPos1 = px != null || py != null || pz != null;
        if (hasPos1 && (px == null || py == null || pz == null)) {
            errorMessage = UIText.of("ponderer.ui.show_structure.error.partial_from");
            return null;
        }

        String pos2X = pos2XField.getValue().trim();
        String pos2Y = pos2YField.getValue().trim();
        String pos2Z = pos2ZField.getValue().trim();
        boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
        Integer px2 = null;
        Integer py2 = null;
        Integer pz2 = null;
        if (hasPos2) {
            if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                errorMessage = UIText.of("ponderer.ui.show_structure.error.partial_to");
                return null;
            }
            px2 = parseInt(pos2X, "To X");
            py2 = parseInt(pos2Y, "To Y");
            pz2 = parseInt(pos2Z, "To Z");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }
        if (hasPos2 && !hasPos1) {
            errorMessage = UIText.of("ponderer.ui.show_structure.error.partial_from");
            return null;
        }
        if (hasPos1) s.blockPos = java.util.List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = java.util.List.of(px2, py2, pz2);

        String structure = structureField.getValue().trim();
        if (!structure.isEmpty()) {
            if (isNumeric(structure)) {
                errorMessage = UIText.of("ponderer.ui.show_structure.structure.error.no_index");
                return null;
            }

            if (structure.toLowerCase().startsWith("minecraft:") || structure.toLowerCase().startsWith("ponderer:")) {
                ResourceLocation source = ResourceLocation.tryParse(structure);
                if (source == null) {
                    errorMessage = UIText.of("ponderer.ui.show_structure.structure.error.invalid_id");
                    return null;
                }

                ResourceLocation target = source.getNamespace().equals("ponderer")
                    ? source
                    : new ResourceLocation("ponderer", source.getPath());

                if (source.getNamespace().equals("ponderer") && localStructureExists(target)) {
                    s.structure = target.toString();
                    return s;
                }

                // Try to copy built-in structure from jar before triggering download
                if (source.getNamespace().equals("ponderer") && SceneStore.ensureBuiltinStructure(target.getPath())) {
                    s.structure = target.toString();
                    return s;
                }

                s.structure = target.toString();
                pendingStep = s;
                waitingDownload = true;
                waitingSourceId = source.toString();
                if (confirmButton != null) {
                    confirmButton.active = false;
                }
                if (structureField != null) {
                    structureField.setEditable(false);
                }
                PondererClientCommands.requestStructureDownload(source);
                errorMessage = UIText.of("ponderer.ui.show_structure.structure.error.wait_download");
                return null;
            }

            s.structure = structure;
        }
        return s;
    }

    @Nullable
    private Integer parseOptionalInt(String raw, String label) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return null;
        return parseInt(trimmed, label);
    }

    public static void onDownloadResult(String sourceId, String targetId, boolean success, String message) {
        if (!(Minecraft.getInstance().screen instanceof ShowStructureScreen screen)) {
            return;
        }
        screen.handleDownloadResult(sourceId, success, message);
    }

    private void handleDownloadResult(String sourceId, boolean success, String message) {
        if (!waitingDownload) {
            return;
        }
        if (waitingSourceId != null && sourceId != null && !waitingSourceId.equals(sourceId)) {
            return;
        }

        waitingDownload = false;
        waitingSourceId = null;
        if (confirmButton != null) {
            confirmButton.active = true;
        }
        if (structureField != null) {
            structureField.setEditable(true);
        }

        if (!success || pendingStep == null) {
            pendingStep = null;
            errorMessage = message == null || message.isBlank()
                ? UIText.of("ponderer.ui.show_structure.structure.error.not_found", sourceId)
                : message;
            return;
        }

        DslScene.DslStep stepToSave = pendingStep;
        pendingStep = null;
        if (attachKeyFrame) {
            stepToSave.attachKeyFrame = true;
        }

        if (isEditMode()) {
            parent.replaceStepAndSave(editIndex, stepToSave);
        } else {
            parent.addStepAndSave(stepToSave);
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private boolean localStructureExists(ResourceLocation id) {
        Path path = resolveLocalStructurePath(id);
        return Files.exists(path);
    }

    private Path resolveLocalStructurePath(ResourceLocation id) {
        Path root = SceneStore.getStructureDir();
        if ("ponderer".equals(id.getNamespace())) {
            return root.resolve(id.getPath() + ".nbt");
        }
        return root.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
    }
}
