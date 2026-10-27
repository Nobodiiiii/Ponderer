package com.nododiiiii.ponderer.scene.interfaces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.scene.DslScene;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsed forms of the interface timeline steps (design doc 17.2) and the migration of the
 * v1 steps {@code change_interface_slot} / {@code click_interface}. Pure: no game state is
 * read, so everything here is unit-testable.
 */
public final class InterfaceStepSpecs {

    private InterfaceStepSpecs() {
    }

    // ---- set_interface_slots ------------------------------------------------------------

    /** A real item written into a menu slot; {@code item == null} clears the slot. */
    public record SlotWrite(int index, @Nullable InterfaceDescriptor.ItemSpec item,
                            @Nullable Integer expectX, @Nullable Integer expectY) {
    }

    /** A display-only ingredient (fluid, chemical, ...) drawn over a slot. */
    public record GhostWrite(int index, String kind, String id) {
    }

    /**
     * @param replace v1 semantics: the step lists the complete slot set, so earlier scripted
     *                writes and ghosts are cleared first
     */
    public record Slots(List<SlotWrite> slots, List<GhostWrite> ghosts, boolean replace) {
    }

    public static Slots slots(DslScene.DslStep step) {
        List<SlotWrite> slots = new ArrayList<>();
        List<GhostWrite> ghosts = new ArrayList<>();
        if (step.slots != null) {
            for (JsonElement element : step.slots) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                Integer index = integer(o, "index");
                if (index == null || index < 0) {
                    continue;
                }
                JsonElement item = o.get("item");
                InterfaceDescriptor.ItemSpec spec = item == null || item.isJsonNull() ? null : InterfaceDescriptorJson.item(item);
                JsonObject expect = o.has("expect") && o.get("expect").isJsonObject() ? o.getAsJsonObject("expect") : null;
                slots.add(new SlotWrite(index, spec,
                    expect == null ? null : integer(expect, "x"),
                    expect == null ? null : integer(expect, "y")));
            }
        }
        if (step.ghosts != null) {
            for (JsonElement element : step.ghosts) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                Integer index = integer(o, "index");
                JsonObject ingredient = o.has("ingredient") && o.get("ingredient").isJsonObject() ? o.getAsJsonObject("ingredient") : null;
                String id = ingredient == null ? null : string(ingredient, "id");
                if (index == null || index < 0 || id == null) {
                    continue;
                }
                String kind = string(ingredient, "kind");
                ghosts.add(new GhostWrite(index, kind == null ? "item" : kind, id));
            }
        }
        return new Slots(slots, ghosts, Boolean.TRUE.equals(step.replace));
    }

    /** v1 {@code change_interface_slot}: item bindings become real items, other kinds stay ghosts. */
    public static Slots fromLegacyChangeSlot(@Nullable List<DslScene.InterfaceSlotBinding> bindings) {
        List<SlotWrite> slots = new ArrayList<>();
        List<GhostWrite> ghosts = new ArrayList<>();
        if (bindings != null) {
            for (DslScene.InterfaceSlotBinding b : bindings) {
                if (b == null || b.slotIndex == null || b.slotIndex < 0 || b.ingredientId == null || b.ingredientId.isBlank()) {
                    continue;
                }
                String kind = b.ingredientKind == null || b.ingredientKind.isBlank()
                    ? "item" : b.ingredientKind.trim().toLowerCase(Locale.ROOT);
                if ("item".equals(kind)) {
                    slots.add(new SlotWrite(b.slotIndex, new InterfaceDescriptor.ItemSpec(b.ingredientId.trim(),
                        b.itemCount == null ? 1 : b.itemCount, b.itemComponents, null), b.slotX, b.slotY));
                } else {
                    ghosts.add(new GhostWrite(b.slotIndex, kind, b.ingredientId.trim()));
                }
            }
        }
        return new Slots(slots, ghosts, true);
    }

    // ---- set_interface_data -------------------------------------------------------------

    public record DataTween(int id, int from, int to) {
    }

    public static List<DataTween> data(DslScene.DslStep step) {
        List<DataTween> out = new ArrayList<>();
        if (step.data == null) {
            return out;
        }
        for (JsonElement element : step.data) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            Integer id = integer(o, "id");
            Integer to = integer(o, "to");
            if (to == null) {
                to = integer(o, "value");
            }
            if (id == null || to == null) {
                continue;
            }
            Integer from = integer(o, "from");
            out.add(new DataTween(id, from == null ? to : from, to));
        }
        return out;
    }

    /** Linear value of a tween at {@code tick} of {@code duration} (inclusive end). */
    public static int tweenValue(DataTween tween, int tick, int duration) {
        if (duration <= 0 || tick >= duration) {
            return tween.to();
        }
        double t = Math.max(0, tick) / (double) duration;
        return (int) Math.round(tween.from() + (tween.to() - tween.from()) * t);
    }

    // ---- set_interface_context ----------------------------------------------------------

    /** Fields left null are unchanged; a non-null inventory replaces the whole inventory. */
    public record ContextPatch(@Nullable String gameMode, @Nullable Integer xpLevel, @Nullable Float xpProgress,
                               @Nullable Integer selectedSlot,
                               @Nullable List<InterfaceDescriptor.InventoryItem> inventory) {
    }

    public static ContextPatch context(DslScene.DslStep step) {
        JsonObject player = step.player;
        if (player == null) {
            return new ContextPatch(null, null, null, null, null);
        }
        // Reuse the descriptor reader so the patch accepts exactly the show_interface syntax.
        JsonObject wrapper = new JsonObject();
        wrapper.add("player", player);
        InterfaceDescriptor parsed = InterfaceDescriptorJson.parse(wrapper);
        JsonObject xp = player.has("xp") && player.get("xp").isJsonObject() ? player.getAsJsonObject("xp") : null;
        boolean hasInventory = player.has("inventory");
        return new ContextPatch(
            string(player, "gameMode"),
            xp == null ? null : integer(xp, "level"),
            xp == null || !xp.has("progress") ? null : parsed.player.xpProgress,
            integer(player, "selectedSlot"),
            hasInventory ? List.copyOf(parsed.player.inventory) : null);
    }

    // ---- interface_action ---------------------------------------------------------------

    public enum ActionKind {
        CLICK, RIGHT_CLICK, QUICK_MOVE, DRAG, SWAP_HOTBAR, THROW, HOVER, TYPE_TEXT, PRESS_BUTTON, SCROLL, KEY;

        @Nullable
        public static ActionKind parse(@Nullable String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Where an action points: a menu slot index, a normalized UI-anchor coordinate
     * ({@code gui: [u, v]}, same encoding as {@code click_interface}), or a widget whose
     * message contains {@code widget}.
     */
    public record Target(@Nullable Integer slot, @Nullable double[] gui, @Nullable String widget) {
        public boolean isEmpty() {
            return slot == null && gui == null && widget == null;
        }
    }

    public record Action(ActionKind kind, @Nullable Target target, @Nullable Target to, int button,
                         int duration, boolean cursor, @Nullable String key, @Nullable String text,
                         double scroll, int hotbar) {
    }

    @Nullable
    public static Action action(DslScene.DslStep step) {
        ActionKind kind = ActionKind.parse(step.action);
        if (kind == null) {
            return null;
        }
        Target target = target(step.target);
        if (target == null) {
            target = target(step.from);
        }
        Target to = target(step.to);
        int button = switch (step.button == null ? "" : step.button.trim().toLowerCase(Locale.ROOT)) {
            case "right" -> 1;
            case "middle" -> 2;
            default -> kind == ActionKind.RIGHT_CLICK ? 1 : 0;
        };
        int hotbar = step.count == null ? 0 : Math.max(0, Math.min(8, step.count - 1));
        if (kind == ActionKind.SWAP_HOTBAR && step.key != null) {
            try {
                hotbar = Math.max(0, Math.min(8, Integer.parseInt(step.key.trim()) - 1));
            } catch (NumberFormatException ignored) {
            }
        }
        double scroll = step.scale == null ? -1 : step.scale;
        String text = step.chars;
        if (kind == ActionKind.PRESS_BUTTON && (target == null || target.widget() == null) && text != null) {
            target = new Target(null, null, text);
        }
        int duration = step.duration == null ? (kind == ActionKind.DRAG ? 20 : 10) : Math.max(0, step.duration);
        return new Action(kind, target, to, button, duration, !Boolean.FALSE.equals(step.cursor), step.key, text, scroll, hotbar);
    }

    /** v1 {@code click_interface}: {@code pos [u, v]} + {@code action left|right}. */
    @Nullable
    public static Action fromLegacyClick(DslScene.DslStep step) {
        if (step.pos == null || step.pos.size() < 2 || step.pos.get(0) == null || step.pos.get(1) == null || step.action == null) {
            return null;
        }
        int button = switch (step.action.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> 0;
            case "right" -> 1;
            default -> -1;
        };
        if (button < 0) {
            return null;
        }
        Target target = new Target(null, new double[]{step.pos.get(0), step.pos.get(1)}, null);
        // v1 clicked instantly without a cursor animation.
        return new Action(button == 1 ? ActionKind.RIGHT_CLICK : ActionKind.CLICK, target, null, button,
            0, false, null, null, 0, 0);
    }

    @Nullable
    private static Target target(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        Integer slot = integer(o, "slot");
        double[] gui = null;
        if (o.has("gui") && o.get("gui").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("gui");
            if (arr.size() >= 2 && arr.get(0).isJsonPrimitive() && arr.get(1).isJsonPrimitive()) {
                gui = new double[]{arr.get(0).getAsDouble(), arr.get(1).getAsDouble()};
            }
        }
        Target target = new Target(slot, gui, string(o, "widget"));
        return target.isEmpty() ? null : target;
    }

    // ---- await_interface ----------------------------------------------------------------

    public record Goal(int slot, @Nullable String item, int minCount) {
    }

    @Nullable
    public static Goal goal(DslScene.DslStep step) {
        JsonObject o = step.goal;
        if (o == null) {
            return null;
        }
        Integer slot = integer(o, "slot");
        if (slot == null || slot < 0) {
            return null;
        }
        Integer min = integer(o, "minCount");
        return new Goal(slot, string(o, "item"), min == null ? 1 : Math.max(0, min));
    }

    /** Whether a slot holding {@code itemId} x {@code count} satisfies {@code goal}. */
    public static boolean goalMet(Goal goal, @Nullable String itemId, int count) {
        if (goal.item() != null && !goal.item().equals(itemId)) {
            return false;
        }
        if (goal.minCount() == 0) {
            return itemId == null || count == 0;
        }
        return itemId != null && count >= goal.minCount();
    }

    // ---- JSON helpers -------------------------------------------------------------------

    @Nullable
    private static Integer integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        return e.getAsInt();
    }

    @Nullable
    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        String s = e.getAsString();
        return s.isBlank() ? null : s.trim();
    }
}
