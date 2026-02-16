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

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Editor for "show_structure" step - optional height and optional structure reference. */
public class ShowStructureScreen extends AbstractStepEditorScreen {

    private HintableTextFieldWidget heightField;
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

    @Override protected int getFormRowCount() { return 2; }
    @Override protected String getHeaderTitle() { return UIText.of("ponderer.ui.show_structure"); }

    @Override
    protected void buildForm() {
        int x = guiLeft + 70, y = guiTop + FORM_TOP;
        heightField = createSmallNumberField(x, y, 60, UIText.of("ponderer.ui.show_structure.height.hint"));
        addLabelTooltip(guiLeft + 10, y + 3, UIText.of("ponderer.ui.show_structure.height"), UIText.of("ponderer.ui.show_structure.height.tooltip"));

        int y2 = y + ROW_HEIGHT;
        structureField = createTextField(x, y2, 95, 18, UIText.of("ponderer.ui.show_structure.structure.hint"));
        addLabelTooltip(guiLeft + 10, y2 + 3, UIText.of("ponderer.ui.show_structure.structure"), UIText.of("ponderer.ui.show_structure.structure.tooltip"));

        browseButton = new PonderButton(x + 100, y2, 30, 18);
        browseButton.withCallback(this::openFilePicker);
        addRenderableWidget(browseButton);
        addLabelTooltip(x + 100, y2, UIText.of("ponderer.ui.show_structure.browse"), UIText.of("ponderer.ui.show_structure.browse.tooltip"));
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.height != null) heightField.setValue(String.valueOf(step.height));
        if (step.structure != null && !step.structure.isBlank()) structureField.setValue(step.structure);
    }

    @Override
    protected void renderForm(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, UIText.of("ponderer.ui.show_structure.height"), guiLeft + 10, guiTop + FORM_TOP + 3, 0xCCCCCC);
        graphics.drawString(font, UIText.of("ponderer.ui.show_structure.structure"), guiLeft + 10, guiTop + FORM_TOP + ROW_HEIGHT + 3, 0xCCCCCC);
    }

    @Override
    protected void renderFormForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, UIText.of("ponderer.ui.show_structure.browse"),
                browseButton.getX() + 15, browseButton.getY() + 5, 0xFFFFFF);
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

    @Override
    protected String getStepType() { return "show_structure"; }

    @Override
    protected Map<String, String> snapshotForm() {
        Map<String, String> m = new HashMap<>();
        m.put("height", heightField.getValue());
        m.put("structure", structureField.getValue());
        return m;
    }

    @Override
    protected void restoreFromSnapshot(Map<String, String> snapshot) {
        restoreKeyFrame(snapshot);
        if (snapshot.containsKey("height")) heightField.setValue(snapshot.get("height"));
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
        String hv = heightField.getValue().trim();
        if (!hv.isEmpty()) {
            Integer h = parseInt(hv, "Height");
            if (h == null) return null;
            s.height = h;
        }
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
