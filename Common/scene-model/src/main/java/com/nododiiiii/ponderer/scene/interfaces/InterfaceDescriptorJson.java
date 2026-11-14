package com.nododiiiii.ponderer.scene.interfaces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Parses the schema 2 {@code "interface"} object of a {@code show_interface} step.
 * Unknown or malformed fields are ignored; the result is always usable (worst case it
 * falls back to the schematic tier at open time).
 */
public final class InterfaceDescriptorJson {

    private InterfaceDescriptorJson() {
    }

    public static InterfaceDescriptor parse(JsonObject root) {
        InterfaceDescriptor d = new InterfaceDescriptor();
        d.origin = "schema2";

        JsonObject target = object(root, "target");
        if (target != null) {
            InterfaceDescriptor.TargetKind kind = InterfaceDescriptor.TargetKind.parse(string(target, "kind"));
            if (kind != null) {
                d.targetKind = kind;
            }
            d.block = string(target, "block");
            readStringMap(object(target, "properties"), d.blockProperties);
            d.nbt = string(target, "nbt");
            d.scenePos = intArray(target, "pos", 3);
            JsonObject hit = object(target, "hit");
            if (hit != null) {
                d.hit = new InterfaceDescriptor.Hit(
                    orDefault(string(hit, "face"), "up"),
                    doubleArray(hit, "point", 3),
                    bool(hit, "inside", false));
            }
            d.item = item(target.get("item"));
            d.itemMode = InterfaceDescriptor.ItemMode.parse(string(target, "mode"));
            d.hand = "off".equalsIgnoreCase(string(target, "hand")) ? "off" : "main";
        }

        d.menuType = string(root, "menuType");
        d.extraData = string(root, "extraData");
        // An extended factory can legitimately have an empty payload.
        if (root.has("extraData") && root.get("extraData").isJsonPrimitive()
            && root.get("extraData").getAsString().isEmpty()) {
            d.extraData = "";
        }
        d.lookupOrigin = intArray(root, "lookupOrigin", 3);
        d.resolvedStrategy = string(root, "resolvedStrategy");
        d.interaction = InterfaceDescriptor.Interaction.parse(string(root, "interaction"));

        JsonArray slots = array(root, "slots");
        if (slots != null) {
            for (JsonElement element : slots) {
                if (element instanceof JsonObject slot) {
                    Integer index = integer(slot, "index");
                    InterfaceDescriptor.ItemSpec spec = item(slot.get("item"));
                    if (index != null && index >= 0 && spec != null) {
                        d.slots.add(new InterfaceDescriptor.SlotState(index, spec));
                    }
                }
            }
        }

        JsonArray data = array(root, "data");
        if (data != null) {
            for (JsonElement element : data) {
                if (element instanceof JsonObject entry) {
                    Integer id = integer(entry, "id");
                    Integer value = integer(entry, "value");
                    if (id != null && id >= 0 && value != null) {
                        d.data.add(new InterfaceDescriptor.DataState(id, value));
                    }
                }
            }
        }

        JsonArray identitySlots = array(root, "identitySlots");
        if (identitySlots != null) {
            for (JsonElement element : identitySlots) {
                if (element instanceof JsonObject slot) {
                    String path = string(slot, "path");
                    String encoding = string(slot, "encoding");
                    if (path != null && encoding != null) {
                        d.identitySlots.add(new InterfaceDescriptor.IdentitySlot(path, encoding,
                            orDefault(string(slot, "role"), "owner")));
                    }
                }
            }
        }

        readPlayer(object(root, "player"), d.player);

        JsonObject context = object(root, "context");
        if (context != null) {
            d.updateTag = string(context, "updateTag");
            JsonArray blocks = array(context, "blocks");
            if (blocks != null) {
                for (JsonElement element : blocks) {
                    if (element instanceof JsonObject block) {
                        int[] pos = intArray(block, "pos", 3);
                        String id = string(block, "block");
                        if (pos == null || id == null) {
                            continue;
                        }
                        InterfaceDescriptor.ContextBlock contextBlock = new InterfaceDescriptor.ContextBlock(
                            pos[0], pos[1], pos[2], id, new java.util.LinkedHashMap<>(), string(block, "nbt"));
                        readStringMap(object(block, "properties"), contextBlock.properties());
                        d.contextBlocks.add(contextBlock);
                    }
                }
            }
        }
        return d;
    }

    private static void readPlayer(@Nullable JsonObject player, InterfaceDescriptor.PlayerContext out) {
        if (player == null) {
            return;
        }
        out.identity = InterfaceDescriptor.Identity.parse(string(player, "identity"));
        out.gameMode = orDefault(string(player, "gameMode"), "survival");
        JsonObject abilities = object(player, "abilities");
        if (abilities != null && abilities.has("instabuild")) {
            out.instabuild = bool(abilities, "instabuild", false);
        }
        JsonObject xp = object(player, "xp");
        if (xp != null) {
            Integer level = integer(xp, "level");
            out.xpLevel = level == null ? 0 : Math.max(0, level);
            Double progress = number(xp, "progress");
            out.xpProgress = progress == null ? 0.0f : (float) Math.max(0.0, Math.min(1.0, progress));
        }
        Integer selected = integer(player, "selectedSlot");
        out.selectedSlot = selected == null ? 0 : Math.max(0, Math.min(8, selected));

        JsonObject inventory = object(player, "inventory");
        if (inventory != null && !"empty".equalsIgnoreCase(string(inventory, "mode"))) {
            readInventoryList(array(inventory, "items"), null, out.inventory);
            readInventoryList(array(inventory, "armor"), "armor", out.inventory);
            InterfaceDescriptor.ItemSpec offhand = item(inventory.get("offhand"));
            if (offhand != null) {
                out.inventory.add(new InterfaceDescriptor.InventoryItem("offhand", offhand));
            }
        }

        JsonObject pose = object(player, "pose");
        if (pose != null) {
            double[] offset = doubleArray(pose, "offset", 3);
            if (offset != null) {
                out.offset = offset;
            }
            Double yaw = number(pose, "yaw");
            Double pitch = number(pose, "pitch");
            if (yaw != null) {
                out.yaw = yaw.floatValue();
            }
            if (pitch != null) {
                out.pitch = pitch.floatValue();
            }
            out.sneaking = bool(pose, "sneaking", false);
        }
    }

    private static void readInventoryList(@Nullable JsonArray items, @Nullable String prefix,
                                          List<InterfaceDescriptor.InventoryItem> out) {
        if (items == null) {
            return;
        }
        for (JsonElement element : items) {
            if (!(element instanceof JsonObject entry)) {
                continue;
            }
            JsonElement slot = entry.get("slot");
            InterfaceDescriptor.ItemSpec spec = item(entry.get("item"));
            if (slot == null || !slot.isJsonPrimitive() || spec == null) {
                continue;
            }
            String slotName = slot.getAsString();
            out.add(new InterfaceDescriptor.InventoryItem(prefix == null ? slotName : prefix + ":" + slotName, spec));
        }
    }

    /** Accepts {@code "minecraft:stone"} or {@code {"id": ..., "count": ..., "components": {...}}}. */
    @Nullable
    static InterfaceDescriptor.ItemSpec item(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String id = primitive.getAsString();
            return id.isBlank() ? null : InterfaceDescriptor.ItemSpec.of(id);
        }
        if (element instanceof JsonObject object) {
            String id = string(object, "id");
            if (id == null || id.isBlank()) {
                return null;
            }
            Integer count = integer(object, "count");
            JsonElement components = object.get("components");
            return new InterfaceDescriptor.ItemSpec(id, count == null ? 1 : count,
                components == null || components.isJsonNull() ? null : components, string(object, "nbt"));
        }
        return null;
    }

    private static void readStringMap(@Nullable JsonObject object, Map<String, String> out) {
        if (object == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                out.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    @Nullable
    private static JsonObject object(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element instanceof JsonObject object ? object : null;
    }

    @Nullable
    private static JsonArray array(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        return element instanceof JsonArray array ? array : null;
    }

    @Nullable
    private static String string(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value.isBlank() ? null : value;
    }

    @Nullable
    private static Integer integer(JsonObject parent, String key) {
        Double value = number(parent, key);
        return value == null ? null : (int) Math.round(value);
    }

    @Nullable
    private static Double number(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return null;
    }

    private static boolean bool(JsonObject parent, String key, boolean fallback) {
        JsonElement element = parent.get(key);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return fallback;
    }

    @Nullable
    private static int[] intArray(JsonObject parent, String key, int size) {
        double[] values = doubleArray(parent, key, size);
        if (values == null) {
            return null;
        }
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = (int) Math.floor(values[i]);
        }
        return result;
    }

    @Nullable
    private static double[] doubleArray(JsonObject parent, String key, int size) {
        JsonArray array = array(parent, key);
        if (array == null || array.size() < size) {
            return null;
        }
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            JsonElement element = array.get(i);
            if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                return null;
            }
            result[i] = primitive.getAsDouble();
        }
        return result;
    }

    private static String orDefault(@Nullable String value, String fallback) {
        return value == null ? fallback : value;
    }
}
