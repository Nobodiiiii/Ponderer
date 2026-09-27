package com.nododiiiii.ponderer.interfaces.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterfaceModelTest {

    // ---- Legacy migration ---------------------------------------------------------------

    @Test
    void legacyBlockStepMigratesTargetNbtAndHit() {
        DslScene.DslStep step = new DslScene.DslStep();
        step.type = "show_interface";
        step.block = "minecraft:furnace";
        step.blockProperties = Map.of("facing", "north");
        step.blockPos = List.of(0, 0, 0);
        step.nbt = "{Items:[]}";
        step.direction = "NORTH";
        step.point = List.of(0.5, 0.5, 0.0);
        step.whileSneaking = true;

        InterfaceDescriptor d = LegacyInterfaceMigrator.fromStep(step);

        assertEquals(InterfaceDescriptor.TargetKind.BLOCK, d.targetKind);
        assertEquals("minecraft:furnace", d.block);
        assertEquals("north", d.blockProperties.get("facing"));
        assertEquals("{Items:[]}", d.nbt);
        assertNotNull(d.hit);
        assertEquals("north", d.hit.face());
        assertArrayEquals(new double[]{0.5, 0.5, 0.0}, d.hit.point());
        // v1 stored the block hit's "inside" flag in whileSneaking.
        assertTrue(d.hit.inside());
        assertFalse(d.player.sneaking);
        assertEquals("legacy:block", d.origin);
    }

    @Test
    void legacySourceDefaultsToBlockAndHonoursDisabledNbt() {
        DslScene.DslStep step = new DslScene.DslStep();
        step.block = "minecraft:chest";
        step.nbt = "{Items:[]}";
        step.enableNbt = false;

        InterfaceDescriptor d = LegacyInterfaceMigrator.fromStep(step);

        assertEquals(InterfaceDescriptor.TargetKind.BLOCK, d.targetKind);
        assertNull(d.nbt);
        assertEquals("up", d.hit.face());
    }

    @Test
    void legacyHeldItemBecomesItemTargetWithSneakingPose() {
        DslScene.DslStep step = new DslScene.DslStep();
        step.interfaceSource = "held_item";
        step.item = "minecraft:writable_book";
        step.nbt = "{pages:[]}";
        step.whileSneaking = true;

        InterfaceDescriptor d = LegacyInterfaceMigrator.fromStep(step);

        assertEquals(InterfaceDescriptor.TargetKind.ITEM, d.targetKind);
        assertEquals(InterfaceDescriptor.ItemMode.AUTO, d.itemMode);
        assertEquals("minecraft:writable_book", d.item.id());
        assertEquals("{pages:[]}", d.item.legacyNbt());
        assertTrue(d.player.sneaking);
    }

    @Test
    void legacyUiIdBecomesMenuTarget() {
        DslScene.DslStep step = new DslScene.DslStep();
        step.interfaceSource = "ui_id";
        step.uiId = "minecraft:crafting";

        InterfaceDescriptor d = LegacyInterfaceMigrator.fromStep(step);

        assertEquals(InterfaceDescriptor.TargetKind.MENU, d.targetKind);
        assertEquals("minecraft:crafting", d.menuType);
    }

    // ---- Schema 2 -----------------------------------------------------------------------

    @Test
    void schema2ObjectTakesPrecedenceOverLegacyFields() {
        DslScene.DslStep step = new DslScene.DslStep();
        step.block = "minecraft:chest";
        step.interfaceSpec = JsonParser.parseString("""
            {
              "schema": 2,
              "target": {"kind": "block", "block": "tetra:basic_workbench", "properties": {"facing": "east"},
                         "hit": {"face": "up", "point": [0.5, 1.0, 0.5], "inside": false}},
              "menuType": "tetra:workbench",
              "slots": [{"index": 0, "item": {"id": "minecraft:iron_ingot", "count": 4}}, {"index": -1, "item": "minecraft:stone"}],
              "data": [{"id": 2, "value": 150}],
              "identitySlots": [{"path": "Security.SecUUID", "encoding": "int_array"}],
              "player": {
                "identity": "viewer_guest", "gameMode": "creative", "xp": {"level": 30, "progress": 0.5},
                "selectedSlot": 12,
                "inventory": {"mode": "preset", "items": [{"slot": 3, "item": {"id": "minecraft:iron_ingot", "count": 16}}],
                              "armor": [{"slot": "chest", "item": "minecraft:iron_chestplate"}], "offhand": "minecraft:shield"},
                "pose": {"offset": [0.5, 0.0, -2.0], "yaw": 90, "pitch": 10, "sneaking": true}
              },
              "context": {"blocks": [{"pos": [0, 1, 0], "block": "tetra:hammer_head"}], "updateTag": "{a:1b}"},
              "interaction": "view"
            }
            """).getAsJsonObject();

        InterfaceDescriptor d = LegacyInterfaceMigrator.fromStep(step);

        assertEquals("schema2", d.origin);
        assertEquals("tetra:basic_workbench", d.block);
        assertEquals("east", d.blockProperties.get("facing"));
        assertEquals("tetra:workbench", d.menuType);
        assertEquals(1, d.slots.size(), "negative slot indices are dropped");
        assertEquals(4, d.slots.get(0).item().count());
        assertEquals(new InterfaceDescriptor.DataState(2, 150), d.data.get(0));
        assertEquals("Security.SecUUID", d.identitySlots.get(0).path());
        assertEquals(InterfaceDescriptor.Identity.VIEWER_GUEST, d.player.identity);
        assertEquals("creative", d.player.gameMode);
        assertEquals(30, d.player.xpLevel);
        assertEquals(0.5f, d.player.xpProgress);
        assertEquals(8, d.player.selectedSlot, "selected slot is clamped to the hotbar");
        assertEquals(3, d.player.inventory.size());
        assertEquals("armor:chest", d.player.inventory.get(1).slot());
        assertEquals("offhand", d.player.inventory.get(2).slot());
        assertTrue(d.player.sneaking);
        assertEquals(1, d.contextBlocks.size());
        assertEquals(1, d.contextBlocks.get(0).dy());
        assertEquals("{a:1b}", d.updateTag);
        assertEquals(InterfaceDescriptor.Interaction.VIEW, d.interaction);
    }

    @Test
    void schema2ToleratesMissingAndMalformedFields() {
        JsonObject root = JsonParser.parseString("""
            {"target": {"kind": "bogus", "item": 5}, "slots": "nope", "player": {"xp": {"level": "high"}}}
            """).getAsJsonObject();

        InterfaceDescriptor d = InterfaceDescriptorJson.parse(root);

        assertEquals(InterfaceDescriptor.TargetKind.BLOCK, d.targetKind);
        assertNull(d.item);
        assertTrue(d.slots.isEmpty());
        assertEquals(0, d.player.xpLevel);
        assertEquals(InterfaceDescriptor.Interaction.TRY, d.interaction);
    }

    // ---- Sanitizer ----------------------------------------------------------------------

    @Test
    void sanitizerStripsLootTablesAndCoordinates() {
        DataSanitizer.Parsed parsed = DataSanitizer.parseBlockEntityNbt(
            "{LootTable:\"minecraft:chests/simple_dungeon\",LootTableSeed:5L,x:1,y:2,z:3,Items:[]}", 1024);

        assertTrue(parsed.ok());
        CompoundTag tag = parsed.tag();
        assertFalse(tag.contains("LootTable"));
        assertFalse(tag.contains("LootTableSeed"));
        assertFalse(tag.contains("x"));
        assertTrue(tag.contains("Items"));
    }

    @Test
    void sanitizerRejectsOversizedAndInvalidInput() {
        DataSanitizer.Parsed oversized = DataSanitizer.parse("{a:\"" + "x".repeat(100) + "\"}", 50);
        assertFalse(oversized.ok());
        assertNotNull(oversized.error());

        DataSanitizer.Parsed invalid = DataSanitizer.parse("{a:", 1024);
        assertFalse(invalid.ok());
        assertNotNull(invalid.error());

        assertNull(DataSanitizer.parse("  ", 1024).error());
    }

    // ---- Identity remapping -------------------------------------------------------------

    private static final UUID AUTHOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID VIEWER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void stripRemovesAllAuthorEncodingsAndApplyWritesViewer() {
        CompoundTag root = new CompoundTag();
        CompoundTag security = new CompoundTag();
        security.putIntArray("SecUUID", UUIDUtil.uuidToIntArray(AUTHOR));
        security.putString("SecName", "Author");
        root.put("Security", security);
        root.putString("OwnerString", AUTHOR.toString());
        root.putLong("OwnerMost", AUTHOR.getMostSignificantBits());
        root.putLong("OwnerLeast", AUTHOR.getLeastSignificantBits());
        ListTag list = new ListTag();
        list.add(new IntArrayTag(UUIDUtil.uuidToIntArray(AUTHOR)));
        root.put("Members", list);
        root.putString("Unrelated", "keep");

        List<InterfaceDescriptor.IdentitySlot> slots = IdentityRemapper.strip(root, AUTHOR, "Author");

        assertEquals(5, slots.size());
        String stripped = root.toString();
        assertFalse(stripped.contains("Author"), stripped);
        assertFalse(stripped.contains(AUTHOR.toString()), stripped);
        assertEquals(0L, root.getLong("OwnerMost"));
        assertEquals("keep", root.getString("Unrelated"));

        int written = IdentityRemapper.apply(root, slots, InterfaceDescriptor.Identity.VIEWER_OWNER, VIEWER, "Viewer");

        assertEquals(5, written);
        assertEquals(VIEWER, UUIDUtil.uuidFromIntArray(root.getCompound("Security").getIntArray("SecUUID")));
        assertEquals("Viewer", root.getCompound("Security").getString("SecName"));
        assertEquals(VIEWER.toString(), root.getString("OwnerString"));
        assertEquals(VIEWER.getMostSignificantBits(), root.getLong("OwnerMost"));
        assertEquals(VIEWER, UUIDUtil.uuidFromIntArray(((IntArrayTag) root.getList("Members", 11).get(0)).getAsIntArray()));
    }

    @Test
    void applyGuestAndUnownedIdentities() {
        CompoundTag root = new CompoundTag();
        root.putIntArray("Owner", UUIDUtil.uuidToIntArray(AUTHOR));
        List<InterfaceDescriptor.IdentitySlot> slots = IdentityRemapper.strip(root, AUTHOR, null);

        IdentityRemapper.apply(root, slots, InterfaceDescriptor.Identity.VIEWER_GUEST, VIEWER, "Viewer");
        assertEquals(IdentityRemapper.DEMO_OWNER, UUIDUtil.uuidFromIntArray(root.getIntArray("Owner")));

        IdentityRemapper.apply(root, slots, InterfaceDescriptor.Identity.UNOWNED, VIEWER, "Viewer");
        assertFalse(root.contains("Owner"));
    }

    @Test
    void pathSplittingHandlesListIndices() {
        assertEquals(List.of("Items", "[0]", "components", "owner"), IdentityRemapper.splitPath("Items[0].components.owner"));
        assertEquals(List.of("a", "[1]", "[2]"), IdentityRemapper.splitPath("a[1][2]"));
        assertTrue(IdentityRemapper.splitPath("broken[1").isEmpty());
    }
}
