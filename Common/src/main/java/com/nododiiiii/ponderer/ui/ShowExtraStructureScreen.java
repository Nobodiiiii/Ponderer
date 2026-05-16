package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.PondererClientCommands;
import com.nododiiiii.ponderer.ponder.SceneStore;
import com.nododiiiii.ponderer.util.SafePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ShowExtraStructureScreen extends AbstractStepEditorScreen {

    private static final String[] ENTRANCE_MODES = {"hidden", "immediate", "animated"};
    private static final int[] ROTATION_OPTIONS = {0, 90, 180, 270};

    private final StepTextFieldHandle structureField = new StepTextFieldHandle("structure");
    private final StepXyzFieldHandle posField = new StepXyzFieldHandle("pos");
    private final StepTextFieldHandle linkIdField = new StepTextFieldHandle("linkId");
    private final StepTextFieldHandle durationField = new StepTextFieldHandle("duration");
    private final StepTextFieldHandle intervalField = new StepTextFieldHandle("entranceInterval");

    private boolean spawnParticles = false;
    private boolean smartDisplay = true;
    private boolean replaceAir = false;
    private int rotationIndex = 0;
    private int entranceModeIndex = 1;
    private int directionIndex = 0;
    private int entranceAnimationIndex = 0;

    private boolean waitingDownload;
    private String waitingSourceId;
    private DslScene.DslStep pendingStep;

    private final FieldBinding<Boolean> spawnParticlesBinding =
        FieldBindings.bool("particles", () -> spawnParticles, value -> spawnParticles = Boolean.TRUE.equals(value));
    private final FieldBinding<Boolean> smartDisplayBinding =
        FieldBindings.bool("smartDisplay", () -> smartDisplay, value -> smartDisplay = Boolean.TRUE.equals(value));
    private final FieldBinding<Boolean> replaceAirBinding =
        FieldBindings.bool("replaceAir", () -> replaceAir, value -> replaceAir = Boolean.TRUE.equals(value));
    private final FieldBinding<Integer> rotationBinding =
        FieldBindings.integer("rotation", () -> rotationIndex, value -> rotationIndex = value);
    private final FieldBinding<Integer> entranceModeBinding =
        FieldBindings.integer("entranceMode", () -> entranceModeIndex, value -> entranceModeIndex = value);
    private final FieldBinding<Integer> directionBinding =
        FieldBindings.integer("direction", () -> directionIndex, value -> directionIndex = value);
    private final FieldBinding<Integer> entranceAnimationBinding =
        FieldBindings.integer("entranceAnimation", () -> entranceAnimationIndex, value -> entranceAnimationIndex = value);

    public ShowExtraStructureScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent) {
        super(Component.translatable("ponderer.ui.show_extra_structure.add"), scene, sceneIndex, parent);
    }

    public ShowExtraStructureScreen(DslScene scene, int sceneIndex, SceneEditorScreen parent,
                                    int editIndex, DslScene.DslStep step) {
        super(Component.translatable("ponderer.ui.show_extra_structure.edit"), scene, sceneIndex, parent, editIndex, step);
        boolean animatedMode = step != null
            && step.entranceAnimation != null
            && !step.entranceAnimation.isBlank()
            && !"none".equals(SelectionAnimationOptions.normalizeEntranceAnimation(step.entranceAnimation));
        if (animatedMode) {
            entranceModeIndex = 2;
        } else if (step != null && Boolean.FALSE.equals(step.immediateDisplay)) {
            entranceModeIndex = 0;
        } else {
            entranceModeIndex = 1;
        }
    }

    @Override
    protected void configureFormState(List<SnapshotParticipant> participants) {
        participants.add(spawnParticlesBinding);
        participants.add(smartDisplayBinding);
        participants.add(replaceAirBinding);
        participants.add(rotationBinding);
        participants.add(entranceModeBinding);
        participants.add(directionBinding);
        participants.add(entranceAnimationBinding);
    }

    @Override
    protected String getHeaderTitle() {
        return UIText.of("ponderer.ui.show_extra_structure");
    }

    @Override
    protected String getStepType() {
        return "show_extra_structure";
    }

    @Override
    protected void collectStepEntries(List<com.nododiiiii.ponderer.ui.catnip.DeclarativeFormEntry> entries) {
        entries.add(FieldSpecs.text(
            structureField,
            "ponderer.ui.show_extra_structure.structure",
            "ponderer.ui.show_extra_structure.structure.tooltip",
            UIText.of("ponderer.ui.show_extra_structure.structure.hint"),
            105,
            FieldDecorators.textAction(
                20,
                this::openFilePicker,
                () -> "S",
                () -> 0xFFFFFF,
                UIText.of("ponderer.ui.show_extra_structure.browse.tooltip"))));

        entries.add(FieldSpecs.xyz(
            posField,
            "ponderer.ui.show_extra_structure.pos",
            "ponderer.ui.show_extra_structure.pos.tooltip",
            "X",
            "Y",
            "Z",
            FieldDecorators.pointPick(PickState.TargetField.POS1)));

        entries.add(FieldSpecs.cycle(
            rotationBinding,
            "ponderer.ui.show_extra_structure.rotation",
            "ponderer.ui.show_extra_structure.rotation.tooltip",
            140,
            ROTATION_OPTIONS.length,
            () -> {
            },
            () -> ROTATION_OPTIONS[rotationIndex] + "°",
            () -> 0xFFFFFF));

        entries.add(FieldSpecs.toggle(
            replaceAirBinding,
            "ponderer.ui.show_extra_structure.replace_air",
            "ponderer.ui.show_extra_structure.replace_air.tooltip"));

        entries.add(FieldSpecs.cycle(
            entranceModeBinding,
            "ponderer.ui.set_block.entrance_mode",
            "ponderer.ui.set_block.entrance_mode.tooltip",
            140,
            ENTRANCE_MODES.length,
            this::rebuildFormPreservingState,
            () -> UIText.of("ponderer.ui.set_block.entrance_mode.option." + ENTRANCE_MODES[entranceModeIndex]),
            () -> 0xFFFFFF));

        String mode = ENTRANCE_MODES[entranceModeIndex];
        if ("immediate".equals(mode)) {
            entries.add(FieldSpecs.toggle(
                spawnParticlesBinding,
                "ponderer.ui.set_block.particles",
                "ponderer.ui.set_block.particles.tooltip"));
        } else if ("animated".equals(mode)) {
            entries.add(FieldSpecs.cycle(
                entranceAnimationBinding,
                "ponderer.ui.set_block.entrance_animation",
                "ponderer.ui.set_block.entrance_animation.tooltip",
                140,
                SelectionAnimationOptions.ENTRANCE_ANIMATIONS.length,
                () -> {
                },
                () -> SelectionAnimationOptions.entranceAnimationLabel(
                    SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex]),
                () -> 0xFFFFFF));
            entries.add(FieldSpecs.cycle(
                directionBinding,
                "ponderer.ui.show_section_and_merge.direction",
                "ponderer.ui.show_section_and_merge.direction.tooltip",
                140,
                SelectionAnimationOptions.DIRECTIONS.length,
                () -> {
                },
                () -> SelectionAnimationOptions.optionLabel(
                    "ponderer.ui.show_controls.direction",
                    SelectionAnimationOptions.DIRECTIONS[directionIndex]),
                () -> 0xFFFFFF));
            entries.add(FieldSpecs.text(
                linkIdField,
                "ponderer.ui.show_section_and_merge.link",
                "ponderer.ui.show_section_and_merge.link.tooltip",
                "",
                140));
            entries.add(FieldSpecs.ticksNumber(
                durationField,
                "ponderer.ui.duration",
                "ponderer.ui.duration.tooltip.section_animation",
                "20",
                60));
            entries.add(FieldSpecs.ticksNumber(
                intervalField,
                "ponderer.ui.entrance_interval",
                "ponderer.ui.entrance_interval.tooltip",
                "1",
                60));
            entries.add(FieldSpecs.toggle(
                smartDisplayBinding,
                "ponderer.ui.smart_display",
                "ponderer.ui.smart_display.tooltip"));
        }
    }

    @Override
    protected void populateFromStep(DslScene.DslStep step) {
        super.populateFromStep(step);
        if (step.structure != null && !step.structure.isBlank()) {
            structureField.setValue(step.structure);
        }
        if (step.blockPos != null && step.blockPos.size() >= 3) {
            posField.setValue(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        }
        if (step.rotation != null) {
            int rounded = Math.round(step.rotation);
            int normalized = ((rounded % 360) + 360) % 360;
            for (int i = 0; i < ROTATION_OPTIONS.length; i++) {
                if (ROTATION_OPTIONS[i] == normalized) {
                    rotationIndex = i;
                    break;
                }
            }
        }
        if (step.spawnParticles != null) {
            spawnParticles = step.spawnParticles;
        }
        if (step.smartDisplay != null) {
            smartDisplay = step.smartDisplay;
        }
        if (step.replaceAir != null) {
            replaceAir = step.replaceAir;
        }
        if (step.entranceAnimation != null && !step.entranceAnimation.isBlank()) {
            String normalized = SelectionAnimationOptions.normalizeEntranceAnimation(step.entranceAnimation);
            for (int i = 0; i < SelectionAnimationOptions.ENTRANCE_ANIMATIONS.length; i++) {
                if (SelectionAnimationOptions.ENTRANCE_ANIMATIONS[i].equals(normalized)) {
                    entranceAnimationIndex = i;
                    break;
                }
            }
        }
        if (step.direction != null) {
            String normalized = SelectionAnimationOptions.normalizeDirection(step.direction);
            for (int i = 0; i < SelectionAnimationOptions.DIRECTIONS.length; i++) {
                if (SelectionAnimationOptions.DIRECTIONS[i].equals(normalized)) {
                    directionIndex = i;
                    break;
                }
            }
        }
        if (step.linkId != null) {
            linkIdField.setValue(step.linkId);
        }
        if (step.entranceDuration != null) {
            durationField.setValue(String.valueOf(step.entranceDuration));
        }
        if (step.entranceInterval != null) {
            intervalField.setValue(String.valueOf(step.entranceInterval));
        }
    }

    @Override
    protected void restoreCustomSnapshot(Map<String, String> snapshot) {
        // No NBT pick interaction for this step.
    }

    @Nullable
    @Override
    protected DslScene.DslStep buildStep() {
        if (waitingDownload) {
            setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.wait_download"));
            return null;
        }
        clearStatusMessages();

        String structure = structureField.getValue().trim();
        if (structure.isEmpty()) {
            setErrorMessage(UIText.of("ponderer.ui.show_extra_structure.error.required_structure"));
            return null;
        }

        Integer px = parseInt(posField.x(), "X");
        if (px == null) return null;
        Integer py = parseInt(posField.y(), "Y");
        if (py == null) return null;
        Integer pz = parseInt(posField.z(), "Z");
        if (pz == null) return null;

        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "show_extra_structure";
        step.blockPos = List.of(px, py, pz);
        step.rotation = (float) ROTATION_OPTIONS[rotationIndex];
        if (replaceAir) {
            step.replaceAir = true;
        }

        String entranceMode = ENTRANCE_MODES[entranceModeIndex];
        if ("hidden".equals(entranceMode)) {
            step.immediateDisplay = false;
            step.spawnParticles = false;
            step.entranceAnimation = "none";
        } else if ("immediate".equals(entranceMode)) {
            step.immediateDisplay = true;
            step.entranceAnimation = "none";
            if (!spawnParticles) {
                step.spawnParticles = false;
            }
        } else {
            step.immediateDisplay = false;
            step.spawnParticles = false;
            step.direction = SelectionAnimationOptions.DIRECTIONS[directionIndex];
            String linkId = linkIdField.getValue().trim();
            if (!linkId.isEmpty()) {
                step.linkId = linkId;
            }
            String entranceAnimation = SelectionAnimationOptions.ENTRANCE_ANIMATIONS[entranceAnimationIndex];
            step.entranceAnimation = entranceAnimation;
            step.entranceDuration = Math.max(0, parseIntOr(durationField.getValue(), 20));
            step.entranceInterval = Math.max(0, parseIntOr(intervalField.getValue(), 1));
            step.smartDisplay = smartDisplay;
        }

        if (structure.toLowerCase().startsWith("minecraft:") || structure.toLowerCase().startsWith("ponderer:")) {
            ResourceLocation source = ResourceLocation.tryParse(structure);
            if (source == null) {
                setErrorMessage(UIText.of("ponderer.ui.show_structure.structure.error.invalid_id"));
                return null;
            }
            ResourceLocation target = source.getNamespace().equals("ponderer")
                ? source
                : new ResourceLocation("ponderer", source.getPath());

            if (source.getNamespace().equals("ponderer") && localStructureExists(target)) {
                step.structure = target.toString();
                return step;
            }
            if (source.getNamespace().equals("ponderer") && SceneStore.ensureBuiltinStructure(target.getPath())) {
                step.structure = target.toString();
                return step;
            }

            step.structure = target.toString();
            pendingStep = step;
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

        step.structure = structure;
        return step;
    }

    private boolean localStructureExists(ResourceLocation id) {
        Path path = SafePaths.resolveNamespacedPath(SceneStore.getStructureDir(), id, "ponderer", ".nbt");
        return path != null && Files.exists(path);
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

    public static void onDownloadResult(String sourceId, String targetId, boolean success, String message) {
        if (!(Minecraft.getInstance().screen instanceof ShowExtraStructureScreen screen)) {
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
}
