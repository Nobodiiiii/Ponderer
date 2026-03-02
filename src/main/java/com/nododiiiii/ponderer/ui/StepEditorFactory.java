package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ponder.DslScene;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Factory for creating the appropriate step editor screen based on step type.
 */
public final class StepEditorFactory {

    private StepEditorFactory() {}

    /** Create an editor screen for adding a new step of the given type. */
    @Nullable
    public static AbstractStepEditorScreen createAddScreen(String type, DslScene scene, int sceneIndex,
                                                           SceneEditorScreen parent) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "show_structure" -> new ShowStructureScreen(scene, sceneIndex, parent);
            case "idle" -> new IdleScreen(scene, sceneIndex, parent);
            case "text" -> new TextStepScreen(scene, sceneIndex, parent);
            case "shared_text" -> new SharedTextScreen(scene, sceneIndex, parent);
            case "create_entity" -> new CreateEntityScreen(scene, sceneIndex, parent);
            case "create_item_entity" -> new CreateItemEntityScreen(scene, sceneIndex, parent);
            case "rotate_camera_y" -> new RotateCameraScreen(scene, sceneIndex, parent);
            case "show_controls" -> new ShowControlsScreen(scene, sceneIndex, parent);
            case "encapsulate_bounds" -> new EncapsulateBoundsScreen(scene, sceneIndex, parent);
            case "play_sound" -> new PlaySoundScreen(scene, sceneIndex, parent);
            case "set_block" -> new SetBlockScreen(scene, sceneIndex, parent);
            case "destroy_block" -> new DestroyBlockScreen(scene, sceneIndex, parent);
            case "replace_blocks" -> new ReplaceBlocksScreen(scene, sceneIndex, parent);
            case "hide_section" -> new SelectionOperationScreen("hide_section", true, false, scene, sceneIndex, parent);
            case "show_section_and_merge" -> new SelectionOperationScreen("show_section_and_merge", true, true, scene, sceneIndex, parent);
            case "toggle_redstone_power" -> new SelectionOperationScreen("toggle_redstone_power", false, false, scene, sceneIndex, parent);
            case "rotate_section" -> new SectionTransformScreen("rotate_section", true, scene, sceneIndex, parent);
            case "move_section" -> new SectionTransformScreen("move_section", false, scene, sceneIndex, parent);
            case "modify_block_entity_nbt" -> new ModifyBlockEntityNbtScreen(scene, sceneIndex, parent);
            case "indicate_redstone" -> new IndicateEffectScreen("indicate_redstone", scene, sceneIndex, parent);
            case "indicate_success" -> new IndicateEffectScreen("indicate_success", scene, sceneIndex, parent);
            case "clear_entities" -> new ClearEntitiesScreen("clear_entities", scene, sceneIndex, parent);
            case "clear_item_entities" -> new ClearEntitiesScreen("clear_item_entities", scene, sceneIndex, parent);
            default -> null;
        };
    }

    /** Create an editor screen for editing an existing step. */
    @Nullable
    public static AbstractStepEditorScreen createEditScreen(DslScene.DslStep step, int stepIndex,
                                                            DslScene scene, int sceneIndex,
                                                            SceneEditorScreen parent) {
        if (step == null || step.type == null) return null;
        return switch (step.type.toLowerCase(Locale.ROOT)) {
            case "show_structure" -> new ShowStructureScreen(scene, sceneIndex, parent, stepIndex, step);
            case "idle" -> new IdleScreen(scene, sceneIndex, parent, stepIndex, step);
            case "text" -> new TextStepScreen(scene, sceneIndex, parent, stepIndex, step);
            case "shared_text" -> new SharedTextScreen(scene, sceneIndex, parent, stepIndex, step);
            case "create_entity" -> new CreateEntityScreen(scene, sceneIndex, parent, stepIndex, step);
            case "create_item_entity" -> new CreateItemEntityScreen(scene, sceneIndex, parent, stepIndex, step);
            case "rotate_camera_y" -> new RotateCameraScreen(scene, sceneIndex, parent, stepIndex, step);
            case "show_controls" -> new ShowControlsScreen(scene, sceneIndex, parent, stepIndex, step);
            case "encapsulate_bounds" -> new EncapsulateBoundsScreen(scene, sceneIndex, parent, stepIndex, step);
            case "play_sound" -> new PlaySoundScreen(scene, sceneIndex, parent, stepIndex, step);
            case "set_block" -> new SetBlockScreen(scene, sceneIndex, parent, stepIndex, step);
            case "destroy_block" -> new DestroyBlockScreen(scene, sceneIndex, parent, stepIndex, step);
            case "replace_blocks" -> new ReplaceBlocksScreen(scene, sceneIndex, parent, stepIndex, step);
            case "hide_section" -> new SelectionOperationScreen("hide_section", true, false, scene, sceneIndex, parent, stepIndex, step);
            case "show_section_and_merge" -> new SelectionOperationScreen("show_section_and_merge", true, true, scene, sceneIndex, parent, stepIndex, step);
            case "toggle_redstone_power" -> new SelectionOperationScreen("toggle_redstone_power", false, false, scene, sceneIndex, parent, stepIndex, step);
            case "rotate_section" -> new SectionTransformScreen("rotate_section", true, scene, sceneIndex, parent, stepIndex, step);
            case "move_section" -> new SectionTransformScreen("move_section", false, scene, sceneIndex, parent, stepIndex, step);
            case "modify_block_entity_nbt" -> new ModifyBlockEntityNbtScreen(scene, sceneIndex, parent, stepIndex, step);
            case "indicate_redstone" -> new IndicateEffectScreen("indicate_redstone", scene, sceneIndex, parent, stepIndex, step);
            case "indicate_success" -> new IndicateEffectScreen("indicate_success", scene, sceneIndex, parent, stepIndex, step);
            case "clear_entities" -> new ClearEntitiesScreen("clear_entities", scene, sceneIndex, parent, stepIndex, step);
            case "clear_item_entities" -> new ClearEntitiesScreen("clear_item_entities", scene, sceneIndex, parent, stepIndex, step);
            default -> null;
        };
    }
}
