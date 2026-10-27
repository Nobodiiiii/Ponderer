package com.nododiiiii.ponderer.interfaces.model;

import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nododiiiii.ponderer.scene.LocalizedText;
import com.nododiiiii.ponderer.scene.DslScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceStepSpecsTest {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalizedText.class, new LocalizedText.GsonAdapter())
        .create();

    private static DslScene.DslStep step(String json) {
        return GSON.fromJson(json, DslScene.DslStep.class);
    }

    @Test
    void setInterfaceSlotsParsesItemsExpectAndGhosts() {
        InterfaceStepSpecs.Slots spec = InterfaceStepSpecs.slots(step("""
            { "type": "set_interface_slots",
              "slots": [ { "index": 2, "item": { "id": "minecraft:iron_ingot", "count": 3 }, "expect": { "x": 116, "y": 35 } },
                         { "index": 4, "item": null },
                         { "index": -1, "item": "minecraft:stone" } ],
              "ghosts": [ { "index": 5, "ingredient": { "kind": "fluid", "id": "minecraft:water" } } ] }
            """));
        assertEquals(2, spec.slots().size());
        InterfaceStepSpecs.SlotWrite first = spec.slots().get(0);
        assertEquals(2, first.index());
        assertEquals("minecraft:iron_ingot", first.item().id());
        assertEquals(3, first.item().count());
        assertEquals(116, first.expectX());
        assertEquals(35, first.expectY());
        assertNull(spec.slots().get(1).item(), "null item clears the slot");
        assertEquals(List.of(new InterfaceStepSpecs.GhostWrite(5, "fluid", "minecraft:water")), spec.ghosts());
        assertFalse(spec.replace());
    }

    @Test
    void legacyChangeSlotSplitsItemsFromGhostsAndReplaces() {
        DslScene.DslStep legacy = step("""
            { "type": "change_interface_slot",
              "interfaceSlots": [ { "slotIndex": 0, "slotX": 56, "slotY": 17, "ingredientId": "minecraft:raw_iron" },
                                  { "slotIndex": 1, "ingredientId": "mekanism:hydrogen", "ingredientKind": "chemical" },
                                  { "slotIndex": 2, "ingredientId": " " } ] }
            """);
        InterfaceStepSpecs.Slots spec = InterfaceStepSpecs.fromLegacyChangeSlot(legacy.interfaceSlots);
        assertTrue(spec.replace());
        assertEquals(1, spec.slots().size());
        assertEquals("minecraft:raw_iron", spec.slots().get(0).item().id());
        assertEquals(1, spec.slots().get(0).item().count());
        assertEquals(56, spec.slots().get(0).expectX());
        assertEquals(List.of(new InterfaceStepSpecs.GhostWrite(1, "chemical", "mekanism:hydrogen")), spec.ghosts());
    }

    @Test
    void dataTweenInterpolatesAndAcceptsPlainValue() {
        List<InterfaceStepSpecs.DataTween> tweens = InterfaceStepSpecs.data(step("""
            { "type": "set_interface_data", "data": [ { "id": 2, "from": 0, "to": 200 }, { "id": 3, "value": 7 } ], "duration": 100 }
            """));
        assertEquals(2, tweens.size());
        InterfaceStepSpecs.DataTween progress = tweens.get(0);
        assertEquals(0, InterfaceStepSpecs.tweenValue(progress, 0, 100));
        assertEquals(100, InterfaceStepSpecs.tweenValue(progress, 50, 100));
        assertEquals(200, InterfaceStepSpecs.tweenValue(progress, 100, 100));
        assertEquals(200, InterfaceStepSpecs.tweenValue(progress, 5, 0));
        assertEquals(new InterfaceStepSpecs.DataTween(3, 7, 7), tweens.get(1));
    }

    @Test
    void contextPatchOnlySetsGivenFields() {
        InterfaceStepSpecs.ContextPatch patch = InterfaceStepSpecs.context(step("""
            { "type": "set_interface_context", "player": { "xp": { "level": 5 } } }
            """));
        assertEquals(5, patch.xpLevel());
        assertNull(patch.xpProgress());
        assertNull(patch.gameMode());
        assertNull(patch.selectedSlot());
        assertNull(patch.inventory());

        InterfaceStepSpecs.ContextPatch full = InterfaceStepSpecs.context(step("""
            { "type": "set_interface_context",
              "player": { "gameMode": "creative", "selectedSlot": 3,
                          "inventory": { "items": [ { "slot": 0, "item": { "id": "minecraft:coal", "count": 4 } } ] } } }
            """));
        assertEquals("creative", full.gameMode());
        assertEquals(3, full.selectedSlot());
        assertNotNull(full.inventory());
        assertEquals(1, full.inventory().size());
        assertEquals("minecraft:coal", full.inventory().get(0).item().id());
    }

    @Test
    void interfaceActionDragUsesFromTo() {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.action(step("""
            { "type": "interface_action", "action": "drag", "from": { "slot": 30 }, "to": { "slot": 1 }, "button": "left" }
            """));
        assertNotNull(action);
        assertEquals(InterfaceStepSpecs.ActionKind.DRAG, action.kind());
        assertEquals(30, action.target().slot());
        assertEquals(1, action.to().slot());
        assertEquals(0, action.button());
        assertEquals(20, action.duration());
        assertTrue(action.cursor());
    }

    @Test
    void interfaceActionGuiClickAndUnknownAction() {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.action(step("""
            { "type": "interface_action", "action": "right_click", "target": { "gui": [0.72, -0.35] }, "cursor": false, "duration": 4 }
            """));
        assertNotNull(action);
        assertEquals(1, action.button());
        assertArrayEquals(new double[]{0.72, -0.35}, action.target().gui());
        assertFalse(action.cursor());
        assertEquals(4, action.duration());

        assertNull(InterfaceStepSpecs.action(step("{ \"type\": \"interface_action\", \"action\": \"dance\" }")));
    }

    @Test
    void legacyClickMigratesToInstantGuiClick() {
        InterfaceStepSpecs.Action action = InterfaceStepSpecs.fromLegacyClick(step("""
            { "type": "click_interface", "pos": [0.25, 0.5], "action": "right" }
            """));
        assertNotNull(action);
        assertEquals(InterfaceStepSpecs.ActionKind.RIGHT_CLICK, action.kind());
        assertEquals(1, action.button());
        assertArrayEquals(new double[]{0.25, 0.5}, action.target().gui());
        assertEquals(0, action.duration());
        assertFalse(action.cursor());

        assertNull(InterfaceStepSpecs.fromLegacyClick(step("{ \"type\": \"click_interface\", \"pos\": [0.1, 0.1], \"action\": \"middle\" }")));
        assertNull(InterfaceStepSpecs.fromLegacyClick(step("{ \"type\": \"click_interface\", \"action\": \"left\" }")));
    }

    @Test
    void awaitGoalMatching() {
        InterfaceStepSpecs.Goal goal = InterfaceStepSpecs.goal(step("""
            { "type": "await_interface", "goal": { "slot": 2, "item": "minecraft:iron_ingot", "minCount": 2 },
              "hint": { "en_us": "Smelt it" }, "timeout": 100 }
            """));
        assertNotNull(goal);
        assertFalse(InterfaceStepSpecs.goalMet(goal, null, 0));
        assertFalse(InterfaceStepSpecs.goalMet(goal, "minecraft:iron_ingot", 1));
        assertFalse(InterfaceStepSpecs.goalMet(goal, "minecraft:gold_ingot", 5));
        assertTrue(InterfaceStepSpecs.goalMet(goal, "minecraft:iron_ingot", 2));

        InterfaceStepSpecs.Goal empty = new InterfaceStepSpecs.Goal(0, null, 0);
        assertTrue(InterfaceStepSpecs.goalMet(empty, null, 0), "minCount 0 means the slot must be emptied");
        assertFalse(InterfaceStepSpecs.goalMet(empty, "minecraft:stone", 1));

        assertNull(InterfaceStepSpecs.goal(step("{ \"type\": \"await_interface\", \"goal\": { \"item\": \"x\" } }")));
    }
}
