package com.nododiiiii.ponderer.scene;

import java.util.Set;

/** Format-level step names; no Minecraft initialization is needed to inspect content. */
public final class SceneStepTypes {
    private SceneStepTypes() {}
    public static final Set<String> BUILTIN = Set.of(
        "show_structure",
        "show_extra_structure",
        "idle",
        "text",
        "shared_text",
        "create_entity",
        "create_item_entity",
        "rotate_camera_y",
        "zoom_scene",
        "highlight_section",
        "show_controls",
        "show_interface",
        "interface_panel",
        "change_interface_slot",
        "click_interface",
        "set_interface_slots",
        "set_interface_data",
        "set_interface_context",
        "interface_action",
        "hide_interface",
        "await_interface",
        "encapsulate_bounds",
        "play_sound",
        "set_block",
        "destroy_block",
        "replace_blocks",
        "hide_section",
        "show_section_and_merge",
        "rotate_section",
        "move_section",
        "toggle_redstone_power",
        "modify_block_entity_nbt",
        "indicate_redstone",
        "indicate_success",
        "spawn_particles",
        "clear_entities",
        "clear_item_entities",
        "modify_entities_nbt",
        "modify_item_entities_nbt",
        "next_scene");
    public static final Set<String> INTERFACES = Set.of("interface_panel", "show_interface", "change_interface_slot", "click_interface", "set_interface_slots", "set_interface_data", "set_interface_context", "interface_action", "hide_interface", "await_interface");
}
