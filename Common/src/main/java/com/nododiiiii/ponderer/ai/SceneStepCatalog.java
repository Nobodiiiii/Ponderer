package com.nododiiiii.ponderer.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent-facing source of truth for the Ponderer scene step vocabulary.
 */
public final class SceneStepCatalog {

    public record StepSchema(String type, String purpose, List<String> requiredFields,
                             List<String> optionalFields, Map<String, List<String>> enums,
                             List<String> constraints, String example) {
    }

    private static final Map<String, StepSchema> SCHEMAS = buildSchemas();

    private SceneStepCatalog() {
    }

    public static List<String> types() {
        return List.copyOf(SCHEMAS.keySet());
    }

    public static StepSchema get(String type) {
        return type == null ? null : SCHEMAS.get(type.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static String compactIndex() {
        StringBuilder out = new StringBuilder();
        for (StepSchema schema : SCHEMAS.values()) {
            out.append("- ").append(schema.type()).append(": ").append(schema.purpose()).append("\n");
        }
        return out.toString();
    }

    private static Map<String, StepSchema> buildSchemas() {
        Map<String, StepSchema> schemas = new LinkedHashMap<>();
        add(schemas, "show_structure", "Show a structure and establish the current coordinate bounds",
            List.of("structure:string"), List.of("height:int", "scale:float", "rotation:float",
                "blockPos:int[3]", "blockPos2:int[3]", "attachKeyFrame:boolean"),
            List.of("Must be the first meaningful step of a normal segment"),
            "{\"type\":\"show_structure\",\"structure\":\"ponderer:basic\"}");
        add(schemas, "show_extra_structure", "Overlay another NBT structure",
            List.of("structure:string", "blockPos:int[3]"),
            List.of("rotation:float", "replaceAir:boolean", "attachKeyFrame:boolean"),
            List.of("rotation is 0/90/180/270"), "{\"type\":\"show_extra_structure\",\"structure\":\"ponderer:extra\",\"blockPos\":[0,1,0]}");
        add(schemas, "idle", "Pause the scene", List.of(), List.of("duration:int"),
            List.of("duration must be non-negative; 20 ticks = 1 second"), "{\"type\":\"idle\",\"duration\":60}");
        add(schemas, "text", "Show localized annotation text", List.of("text:LocalizedText"),
            List.of("point:number[3]", "duration:int", "color:string", "placeNearTarget:boolean", "attachKeyFrame:boolean"),
            List.of("Use both en_us and zh_cn; point should target the described component"),
            "{\"type\":\"text\",\"text\":{\"en_us\":\"Input\",\"zh_cn\":\"输入\"},\"point\":[1.5,1.5,1.5],\"duration\":50,\"placeNearTarget\":true}");
        add(schemas, "shared_text", "Show text from a shared localization key", List.of("key:string"),
            List.of("point:number[3]", "duration:int", "color:string", "placeNearTarget:boolean"),
            List.of(), "{\"type\":\"shared_text\",\"key\":\"ponderer.shared.example\",\"duration\":50}");
        add(schemas, "show_controls", "Show an interaction or ingredient indicator",
            List.of("point:number[3]", "direction:string", "item:string"),
            List.of("action:left|right|scroll", "duration:int", "whileSneaking:boolean", "whileCTRL:boolean"),
            List.of("Player interactions require action; machine I/O omits action"),
            "{\"type\":\"show_controls\",\"point\":[1.5,1.5,1.5],\"direction\":\"down\",\"action\":\"right\",\"item\":\"minecraft:lever\"}");
        add(schemas, "create_entity", "Spawn an entity", List.of("entity:string", "pos:number[3]"),
            List.of("lookAt:number[3]", "yaw:float", "pitch:float", "nbt:string", "linkId:string",
                "entranceAnimation:string", "entranceDuration:int", "walkAnimation:boolean"),
            List.of(), "{\"type\":\"create_entity\",\"entity\":\"minecraft:zombie\",\"pos\":[1.5,1,1.5]}");
        add(schemas, "create_item_entity", "Spawn a dropped item", List.of("item:string", "pos:number[3]", "motion:number[3]"),
            List.of("count:int", "nbt:string", "linkId:string"), List.of("Use [0,0,0] for a stationary item"),
            "{\"type\":\"create_item_entity\",\"item\":\"minecraft:paper\",\"pos\":[1.5,1.5,1.5],\"motion\":[0,0,0]}");
        add(schemas, "clear_entities", "Remove entities by id, link or region", List.of(),
            List.of("entity:string", "linkId:string", "blockPos:int[3]", "blockPos2:int[3]", "fullScene:boolean",
                "offset:number[3]", "entranceAnimation:string", "entranceDuration:int"),
            List.of("Provide at least one target mode"), "{\"type\":\"clear_entities\",\"fullScene\":true}");
        add(schemas, "clear_item_entities", "Remove dropped items", List.of(),
            List.of("item:string", "linkId:string", "blockPos:int[3]", "blockPos2:int[3]", "fullScene:boolean"),
            List.of("Provide fullScene or a target"), "{\"type\":\"clear_item_entities\",\"fullScene\":true}");
        add(schemas, "modify_entities_nbt", "Modify matching entity NBT", List.of("nbt:string"),
            List.of("entity:string", "linkId:string", "blockPos:int[3]", "blockPos2:int[3]", "fullScene:boolean",
                "offset:number[3]"), List.of("Provide at least one target mode"),
            "{\"type\":\"modify_entities_nbt\",\"entity\":\"minecraft:zombie\",\"nbt\":\"{Silent:1b}\",\"fullScene\":true}");
        add(schemas, "modify_item_entities_nbt", "Modify matching dropped-item NBT", List.of("nbt:string"),
            List.of("item:string", "linkId:string", "blockPos:int[3]", "blockPos2:int[3]", "fullScene:boolean"),
            List.of("Provide at least one target mode"),
            "{\"type\":\"modify_item_entities_nbt\",\"item\":\"minecraft:paper\",\"nbt\":\"{PickupDelay:40}\",\"fullScene\":true}");
        add(schemas, "set_block", "Place one block or fill a region", List.of("block:string", "blockPos:int[3]"),
            List.of("blockPos2:int[3]", "blockProperties:object", "nbt:string", "spawnParticles:boolean",
                "immediateDisplay:boolean", "entranceAnimation:string", "entranceDuration:int", "entranceInterval:int"),
            List.of("Coordinates must be inside current structure bounds"),
            "{\"type\":\"set_block\",\"block\":\"minecraft:lever\",\"blockPos\":[1,1,1]}");
        add(schemas, "replace_blocks", "Replace non-air blocks in a region", List.of("block:string", "blockPos:int[3]", "blockPos2:int[3]"),
            List.of("blockProperties:object", "spawnParticles:boolean"), List.of(),
            "{\"type\":\"replace_blocks\",\"block\":\"minecraft:stone\",\"blockPos\":[0,0,0],\"blockPos2\":[4,0,4]}");
        add(schemas, "destroy_block", "Destroy blocks in a region", List.of("blockPos:int[3]"),
            List.of("blockPos2:int[3]", "destroyParticles:boolean"), List.of(),
            "{\"type\":\"destroy_block\",\"blockPos\":[1,1,1],\"destroyParticles\":true}");
        add(schemas, "hide_section", "Hide a region with an animation", List.of("blockPos:int[3]", "blockPos2:int[3]"),
            List.of("direction:string", "duration:int"), List.of(),
            "{\"type\":\"hide_section\",\"blockPos\":[0,1,0],\"blockPos2\":[4,3,4],\"direction\":\"up\"}");
        add(schemas, "show_section_and_merge", "Reveal a region and optionally link it", List.of("blockPos:int[3]", "blockPos2:int[3]"),
            List.of("direction:string", "duration:int", "linkId:string"), List.of(),
            "{\"type\":\"show_section_and_merge\",\"blockPos\":[0,1,0],\"blockPos2\":[4,1,4],\"direction\":\"up\",\"linkId\":\"layer\"}");
        add(schemas, "rotate_section", "Rotate a linked or selected section", List.of(),
            List.of("linkId:string", "blockPos:int[3]", "blockPos2:int[3]", "rotX:float", "rotY:float", "rotZ:float", "duration:int"),
            List.of("Provide linkId or a selection"), "{\"type\":\"rotate_section\",\"linkId\":\"part\",\"rotY\":90,\"duration\":20}");
        add(schemas, "move_section", "Move a linked or selected section", List.of("offset:number[3]"),
            List.of("linkId:string", "blockPos:int[3]", "blockPos2:int[3]", "duration:int"),
            List.of("Provide linkId or a selection"), "{\"type\":\"move_section\",\"linkId\":\"part\",\"offset\":[0,1,0],\"duration\":20}");
        add(schemas, "toggle_redstone_power", "Toggle redstone power in a region", List.of("blockPos:int[3]"),
            List.of("blockPos2:int[3]"), List.of(), "{\"type\":\"toggle_redstone_power\",\"blockPos\":[1,1,1]}");
        add(schemas, "modify_block_entity_nbt", "Modify block state and block-entity NBT", List.of("blockPos:int[3]"),
            List.of("blockPos2:int[3]", "blockProperties:object", "nbt:string", "reDrawBlocks:boolean"), List.of(),
            "{\"type\":\"modify_block_entity_nbt\",\"blockPos\":[1,1,1],\"nbt\":\"{CustomName:'\\\"Demo\\\"'}\"}");
        add(schemas, "spawn_particles", "Emit configurable particles (including success and redstone dust)",
            List.of("particle:string", "pos:number[3]"),
            List.of("distribution:point|box|sphere", "spread:number[3]", "motion:number[3]", "count:int", "duration:int", "attachKeyFrame:boolean"),
            List.of("particle uses Minecraft particle command syntax: registry ID with optional SNBT options",
                "spread is box half extents or ellipsoid radii; defaults to [0,0,0]", "count is particles per tick (0..4096, default 10)",
                "duration is emission ticks (1..72000, default 1); does not pause the scene; add idle to wait"),
            "{\"type\":\"spawn_particles\",\"particle\":\"minecraft:large_smoke\",\"pos\":[1.5,1.5,1.5],\"distribution\":\"box\",\"spread\":[0.5,0.5,0.5],\"count\":1,\"duration\":60,\"motion\":[0,0.05,0]}");
        add(schemas, "indicate_redstone", "Legacy redstone indication; prefer spawn_particles", List.of("blockPos:int[3]"),
            List.of(), List.of(), "{\"type\":\"indicate_redstone\",\"blockPos\":[1,1,1]}");
        add(schemas, "indicate_success", "Legacy success effect; prefer spawn_particles", List.of("blockPos:int[3]"),
            List.of(), List.of("Use for major results, not every annotation"), "{\"type\":\"indicate_success\",\"blockPos\":[1,1,1]}");
        add(schemas, "rotate_camera_y", "Rotate the camera", List.of(),
            List.of("degrees:float", "duration:int"), List.of(), "{\"type\":\"rotate_camera_y\",\"degrees\":90}");
        add(schemas, "zoom_scene", "Adjust camera center and scale", List.of(),
            List.of("point:number[3]", "scale:float", "duration:int"), List.of(),
            "{\"type\":\"zoom_scene\",\"point\":[2.5,1.5,2.5],\"scale\":1.25,\"duration\":40}");
        add(schemas, "highlight_section", "Highlight a region", List.of("blockPos:int[3]"),
            List.of("blockPos2:int[3]", "duration:int", "color:string"), List.of(),
            "{\"type\":\"highlight_section\",\"blockPos\":[1,1,1],\"blockPos2\":[2,2,2],\"duration\":40}");
        add(schemas, "encapsulate_bounds", "Override camera bounds", List.of("bounds:int[3]"),
            List.of(), List.of("All dimensions must be positive"), "{\"type\":\"encapsulate_bounds\",\"bounds\":[7,5,7]}");
        add(schemas, "play_sound", "Play a sound event", List.of("sound:string"),
            List.of("soundVolume:float", "pitch:float", "source:string"), List.of(),
            "{\"type\":\"play_sound\",\"sound\":\"minecraft:block.lever.click\",\"source\":\"block\"}");
        add(schemas, "show_interface", "Open and mirror a container interface", List.of(),
            List.of("interfaceSource:block|held_item|ui_id", "blockPos:int[3]", "item:string", "nbt:string",
                "uiId:string", "duration:int", "enableNbt:boolean"),
            List.of("The segment may start with this instead of show_structure"),
            "{\"type\":\"show_interface\",\"interfaceSource\":\"block\",\"blockPos\":[1,1,1],\"duration\":100}");
        add(schemas, "change_interface_slot", "Temporarily change mirrored interface slots", List.of("interfaceSlots:array"),
            List.of("duration:int"), List.of(), "{\"type\":\"change_interface_slot\",\"interfaceSlots\":[{\"slotIndex\":0,\"ingredientId\":\"minecraft:paper\"}]}");
        add(schemas, "click_interface", "Simulate a click in the mirrored interface", List.of("point:number[3]", "action:left|right"),
            List.of("duration:int"), List.of(), "{\"type\":\"click_interface\",\"point\":[20,20,0],\"action\":\"left\"}");
        add(schemas, "set_interface_slots", "Set mirrored slots and ghosts", List.of(),
            List.of("slots:array", "ghosts:array", "replace:boolean"), List.of(), "{\"type\":\"set_interface_slots\",\"slots\":[]}");
        add(schemas, "set_interface_data", "Set or tween container data", List.of("data:array"),
            List.of("duration:int"), List.of(), "{\"type\":\"set_interface_data\",\"data\":[]}");
        add(schemas, "set_interface_context", "Set mirrored player context", List.of("player:object"),
            List.of(), List.of(), "{\"type\":\"set_interface_context\",\"player\":{}}");
        add(schemas, "interface_action", "Perform a semantic interface action", List.of("action:string"),
            List.of("target:object", "from:object", "to:object", "button:string", "duration:int", "cursor:boolean", "key:string", "chars:string", "scale:float", "count:int"),
            List.of("Use runtime-supported action names and explicit targets"), "{\"type\":\"interface_action\",\"action\":\"click\",\"target\":{\"slot\":0}}");
        add(schemas, "hide_interface", "Close the mirrored interface", List.of(), List.of("duration:int"), List.of(), "{\"type\":\"hide_interface\"}");
        add(schemas, "await_interface", "Wait for an interface goal", List.of("goal:object"), List.of("duration:int"), List.of(), "{\"type\":\"await_interface\",\"goal\":{}}");
        add(schemas, "next_scene", "Explicit no-op scene boundary marker", List.of(), List.of(), List.of(),
            "{\"type\":\"next_scene\"}");
        return Collections.unmodifiableMap(schemas);
    }

    private static void add(Map<String, StepSchema> schemas, String type, String purpose,
                            List<String> required, List<String> optional, List<String> constraints,
                            String example) {
        schemas.put(type, new StepSchema(
            type, purpose, required, optional, extractEnums(required, optional), constraints, example));
    }

    private static Map<String, List<String>> extractEnums(
        List<String> required, List<String> optional) {
        Map<String, List<String>> enums = new LinkedHashMap<>();
        for (String field : java.util.stream.Stream.concat(required.stream(), optional.stream()).toList()) {
            int separator = field.indexOf(':');
            if (separator < 1) {
                continue;
            }
            String values = field.substring(separator + 1);
            if (values.contains("|")) {
                enums.put(field.substring(0, separator), List.of(values.split("\\|")));
            }
        }
        return Collections.unmodifiableMap(enums);
    }
}
