package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Editor for "show_structure" step - optional height and optional structure reference. */
public class ShowStructureScreen extends AbstractStepEditorScreen {

    private final StepTextFieldHandle scaleField = new StepTextFieldHandle("scale");
    private final StepTextFieldHandle rotationField = new StepTextFieldHandle("rotation");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepXyzFieldHandle pos2Field = new StepXyzFieldHandle("pos2");
    private final StepTextFieldHandle structureField = new StepTextFieldHandle("structure");
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

    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.show_structure"); }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            structureField,
            "ponderer.ui.show_structure.structure",
            "ponderer.ui.show_structure.structure.tooltip",
            UIText.of("ponderer.ui.show_structure.structure.hint"),
            105,
            FieldDecorators.textAction(
                20,
                this::openFilePicker,
                () -> "S",
                () -> 0xFFFFFF,
                UIText.of("ponderer.ui.show_structure.browse.tooltip"))));
        entries.add(FieldSpecs.number(
            scaleField,
            "ponderer.ui.show_structure.scale",
            "ponderer.ui.show_structure.scale.tooltip",
            "1.0",
            60,
            null));
        entries.add(FieldSpecs.number(
            rotationField,
            "ponderer.ui.show_structure.rotation",
            "ponderer.ui.show_structure.rotation.tooltip",
            "0",
            60,
            null));
        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.show_structure.pos_from",
            "ponderer.ui.show_structure.pos_from.tooltip",
            PickState.TargetField.POS1));
        entries.add(FieldSpecs.xyz(
            pos2Field,
            "ponderer.ui.show_structure.pos_to",
            "ponderer.ui.show_structure.pos_to.tooltip",
            PickState.TargetField.POS2));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.scale != null) scaleField.setValue(String.valueOf(step.scale));
        if (step.rotation != null) rotationField.setValue(String.valueOf(step.rotation));
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.blockPos2 != null && step.blockPos2.size() >= 3) {
            pos2Field.setValue(step.blockPos2.get(0), step.blockPos2.get(1), step.blockPos2.get(2));
        }
        if (step.structure != null && !step.structure.isBlank()) structureField.setValue(step.structure);
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
                fileName = SafePaths.sanitizeWindowsFileName(fileName, "structure");
                Path target = SafePaths.resolveFileName(structuresDir, fileName + ".nbt");
                if (target == null) {
                    setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.copy_failed"));
                    return;
                }
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(selected, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    structureField.setValue("ponderer:" + fileName);
                } catch (Exception e) {
                    setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.copy_failed"));
                }
            }
        }, Minecraft.getInstance());
    }

    @Override
    protected String getStepType() { return "show_structure"; }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        if (waitingDownload) {
            setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.wait_download"));
            return null;
        }
        clearStatusMessages();
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

        Integer px = parseOptionalInt(posField.x(), "From X");
        Integer py = parseOptionalInt(posField.y(), "From Y");
        Integer pz = parseOptionalInt(posField.z(), "From Z");
        boolean hasPos1 = px != null || py != null || pz != null;
        if (hasPos1 && (px == null || py == null || pz == null)) {
            setErrorMessage(UIText.of("ponderer.ui.show_structure.error.partial_from"));
            return null;
        }

        String pos2X = pos2Field.x().trim();
        String pos2Y = pos2Field.y().trim();
        String pos2Z = pos2Field.z().trim();
        boolean hasPos2 = !pos2X.isEmpty() || !pos2Y.isEmpty() || !pos2Z.isEmpty();
        Integer px2 = null;
        Integer py2 = null;
        Integer pz2 = null;
        if (hasPos2) {
            if (pos2X.isEmpty() || pos2Y.isEmpty() || pos2Z.isEmpty()) {
                setErrorMessage(UIText.of("ponderer.ui.show_structure.error.partial_to"));
                return null;
            }
            px2 = parseInt(pos2X, "To X");
            py2 = parseInt(pos2Y, "To Y");
            pz2 = parseInt(pos2Z, "To Z");
            if (px2 == null || py2 == null || pz2 == null) return null;
        }
        if (hasPos2 && !hasPos1) {
            setErrorMessage(UIText.of("ponderer.ui.show_structure.error.partial_from"));
            return null;
        }
        if (hasPos1) s.blockPos = java.util.List.of(px, py, pz);
        if (hasPos2) s.blockPos2 = java.util.List.of(px2, py2, pz2);

        String structure = structureField.getValue().trim();
        if (!structure.isEmpty()) {
            if (isNumeric(structure)) {
                setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.no_index"));
                return null;
            }

            if (structure.toLowerCase().startsWith("minecraft:") || structure.toLowerCase().startsWith("ponderer:")) {
                ResourceLocation source = ResourceLocation.tryParse(structure);
                if (source == null) {
                    setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.invalid_id"));
                    return null;
                }

                ResourceLocation target = source.getNamespace().equals("ponderer")
                    ? source
                    : ResourceLocation.fromNamespaceAndPath("ponderer", source.getPath());

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
                if (structureField.widget() != null) {
                    structureField.widget().setEditable(false);
                }
                PondererClientCommands.requestStructureDownload(source);
                setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.wait_download"));
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
        if (structureField.widget() != null) {
            structureField.widget().setEditable(true);
        }

        if (!success || pendingStep == null) {
            pendingStep = null;
            setErrorMessage(message == null || message.isBlank()
                ? UIText.of("ponderer.ui.show_structure.structure.error.not_found", sourceId)
                : message);
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
        return path != null && Files.exists(path);
    }

    private Path resolveLocalStructurePath(ResourceLocation id) {
        return SafePaths.resolveNamespacedPath(SceneStore.getStructureDir(), id, "ponderer", ".nbt");
    }
}
