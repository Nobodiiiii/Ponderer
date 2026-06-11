package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.SceneRuntime;

import java.util.Locale;

public final class ProjectorSceneTimeline {

    private ProjectorSceneTimeline() {
    }

    public static int estimateTotalTicks(String sceneKey) {
        DslScene scene = SceneRuntime.findByKey(sceneKey);
        if (scene == null || scene.scenes == null || scene.scenes.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (DslScene.SceneSegment segment : scene.scenes) {
            total += estimateSegmentTicks(segment);
        }
        return total;
    }

    public static int estimateSegmentTicks(DslScene.SceneSegment segment) {
        if (segment == null || segment.steps == null) {
            return 0;
        }

        int total = 0;
        for (DslScene.DslStep step : segment.steps) {
            total += estimateStepTicks(step);
        }
        return total;
    }

    public static int estimateStepTicks(DslScene.DslStep step) {
        if (step == null || step.type == null) {
            return 0;
        }

        return switch (step.type.toLowerCase(Locale.ROOT)) {
            case "idle" -> step.durationOrDefault(20);
            case "text", "shared_text", "show_controls", "show_interface",
                 "highlight_section" -> step.durationOrDefault(60);
            case "rotate_camera_y", "zoom_scene", "create_entity", "create_item_entity",
                 "hide_section", "show_section", "show_structure", "show_extra_structure",
                 "rotate_section", "move_section", "move_entity", "remove_entities",
                 "move_point_of_interest", "modify_block_entity_nbt", "modify_entities_nbt",
                 "modify_item_entities_nbt", "destroy_block", "indicate_redstone",
                 "indicate_success", "toggle_redstone_power", "replace_blocks", "set_block",
                 "clear_entities", "clear_item_entities", "spawn_particles", "play_sound",
                 "click_interface", "change_interface_slot", "show_section_and_merge" ->
                Math.max(0, step.duration == null ? 0 : step.duration);
            default -> Math.max(0, step.duration == null ? 0 : step.duration);
        };
    }
}
