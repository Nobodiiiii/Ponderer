package com.nododiiiii.ponderer.ponder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.nododiiiii.ponderer.ModKeyBindings;
import com.nododiiiii.ponderer.platform.PondererServices;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side manager that checks trigger conditions (structure/coordinate)
 * and shows hints to the player, opening PonderUI on key press.
 */
public final class TriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ponderer/TriggerManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CHECK_INTERVAL = 20; // ticks between checks (1 second)
    private static int tickCounter = 0;
    private static boolean loggedOnce = false;

    @Nullable
    private static DslScene activeScene = null;

    /** Tick when the title hint was last shown, for auto-disappear after 1s. */
    private static int titleShownTick = -1;
    /** Whether the player is currently inside the trigger zone (for re-entry detection). */
    private static boolean wasInZone = false;
    /** Whether the title hint has already been shown for the current zone entry. */
    private static boolean titleShownThisEntry = false;
    /** Scene IDs that have already been shown (persisted). */
    private static final Set<String> shownSet = new HashSet<>();
    /** Scene IDs whose ponders have been read (persisted). */
    private static final Set<String> readSet = new HashSet<>();
    /** Whether the persisted state has been loaded from disk. */
    private static boolean stateLoaded = false;

    private TriggerManager() {}

    /**
     * Called every client tick from platform event handlers.
     * Checks trigger conditions at intervals and handles key press.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            activeScene = null;
            wasInZone = false;
            titleShownThisEntry = false;
            return;
        }

        ensureStateLoaded();

        // While a screen is open (e.g. PonderUI), pause trigger checks
        // but do NOT reset wasInZone — the player hasn't left the zone.
        if (mc.screen != null) {
            return;
        }

        // Always consume stale key presses to prevent accumulation.
        // Then act only if the player is currently in a trigger zone.
        if (ModKeyBindings.TRIGGER_PONDER.consumeClick()) {
            if (tryOpenTriggeredScene(mc)) {
                return;
            }
        }

        // Check triggers periodically
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        DslScene newScene = findTriggeredScene(mc);

        boolean inZoneNow = newScene != null;
        boolean justEntered = inZoneNow && !wasInZone;
        boolean justLeft = !inZoneNow && wasInZone;

        if (justLeft) {
            activeScene = null;
            titleShownThisEntry = false;
        }

        if (justEntered) {
            titleShownThisEntry = false;
        }

        wasInZone = inZoneNow;
        activeScene = newScene;

        if (activeScene != null) {
            String sceneKey = activeScene.sceneKey();

            // Resolve per-style frequencies (new parallel model or legacy fallback)
            String autoFreq = resolveHintFreq(activeScene, "auto");
            String titleFreq = resolveHintFreq(activeScene, "title");
            String subtitleFreq = resolveHintFreq(activeScene, "subtitle");

            // --- Auto trigger: open ponder directly on zone entry ---
            if (autoFreq != null && shouldShow(autoFreq, sceneKey + ":auto")) {
                if (justEntered) {
                    markShown(sceneKey, "auto");
                    openPonderFor(activeScene, sceneKey);
                    // Don't reset wasInZone — player is still in zone,
                    // just won't re-trigger until they leave and re-enter.
                    return;
                }
            }

            // --- Title hint: show once per entry, auto-disappear ---
            if (titleFreq != null && shouldShow(titleFreq, sceneKey + ":title")) {
                if (justEntered && !titleShownThisEntry) {
                    Component hintMsg = getHintText(activeScene.hintTitleText);
                    mc.gui.setTitle(hintMsg);
                    mc.gui.setTimes(0, 20, 10);
                    titleShownThisEntry = true;
                    markShown(sceneKey, "title");
                }
            }

            // --- Subtitle hint: persistent green text while in zone ---
            if (subtitleFreq != null && shouldShow(subtitleFreq, sceneKey + ":subtitle")) {
                Component hintMsg = getHintText(activeScene.hintSubtitleText);
                Component greenHint = hintMsg.copy().withStyle(ChatFormatting.GREEN);
                mc.player.displayClientMessage(greenHint, true);
                if (justEntered) {
                    markShown(sceneKey, "subtitle");
                }
            }
        }
    }

    @Nullable
    private static DslScene findTriggeredScene(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        BlockPos playerPos = player.blockPosition();

        List<DslScene> scenes = SceneRuntime.getScenes();
        if (!loggedOnce) {
            int triggerCount = 0;
            for (DslScene s : scenes) {
                if (s.triggerMode != null && !"none".equals(s.triggerMode)) triggerCount++;
            }
            if (triggerCount > 0) {
                LOGGER.info("TriggerManager checking {} scenes ({} with triggers)", scenes.size(), triggerCount);
            }
            loggedOnce = true;
        }

        for (DslScene scene : scenes) {
            if (scene.triggerMode == null || "none".equals(scene.triggerMode)) continue;

            if ("coordinate".equals(scene.triggerMode)) {
                if (isInCoordinateRange(playerPos, scene)) return scene;
            } else if ("structure".equals(scene.triggerMode)) {
                if (isInStructureRange(mc, playerPos, scene)) return scene;
            }
        }
        return null;
    }

    private static boolean isInCoordinateRange(BlockPos playerPos, DslScene scene) {
        List<Integer> c1 = scene.triggerCoord1;
        List<Integer> c2 = scene.triggerCoord2;
        if (c1 == null || c1.size() < 3 || c2 == null || c2.size() < 3) return false;

        int minX = Math.min(c1.get(0), c2.get(0));
        int minY = Math.min(c1.get(1), c2.get(1));
        int minZ = Math.min(c1.get(2), c2.get(2));
        int maxX = Math.max(c1.get(0), c2.get(0));
        int maxY = Math.max(c1.get(1), c2.get(1));
        int maxZ = Math.max(c1.get(2), c2.get(2));

        return playerPos.getX() >= minX && playerPos.getX() <= maxX
                && playerPos.getY() >= minY && playerPos.getY() <= maxY
                && playerPos.getZ() >= minZ && playerPos.getZ() <= maxZ;
    }

    /**
     * Check if the player is within a structure's bounding box (extended by range).
     * "Range" is extra padding around the structure's actual bounding box.
     * Only works in singleplayer (integrated server available).
     */
    private static boolean isInStructureRange(Minecraft mc, BlockPos playerPos, DslScene scene) {
        if (scene.triggerStructure == null || scene.triggerStructure.isEmpty()) return false;
        ResourceLocation structureLoc = ResourceLocation.tryParse(scene.triggerStructure);
        if (structureLoc == null) return false;

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return false;

        try {
            ServerLevel serverLevel = server.getLevel(mc.player.level().dimension());
            if (serverLevel == null) return false;

            StructureManager structureManager = serverLevel.structureManager();
            var registry = server.registryAccess().registryOrThrow(
                    net.minecraft.core.registries.Registries.STRUCTURE);
            Structure structure = registry.get(structureLoc);
            if (structure == null) return false;

            // Native structure check: true when player is inside this structure's pieces.
            StructureStart directStart = structureManager.getStructureWithPieceAt(playerPos, structure);
            if (directStart != null && directStart.isValid()) {
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to check structure trigger for {}: {}", scene.triggerStructure, e.getMessage());
        }
        return false;
    }

    /**
     * Check if at least one hint style is enabled for this scene.
     */
    private static boolean hasAnyHintEnabled(DslScene scene) {
        // New parallel fields
        if (scene.hintAuto != null || scene.hintTitle != null || scene.hintSubtitle != null) return true;
        // Legacy fallback
        return scene.hintStyle != null;
    }

    /**
     * Resolve the frequency for a given hint style from the new parallel fields,
     * falling back to legacy single-style fields for backward compatibility.
     * Returns null if this style is disabled, or "always"/"first_time"/"until_read".
     */
    @Nullable
    private static String resolveHintFreq(DslScene scene, String style) {
        // New parallel fields take precedence
        switch (style) {
            case "auto":
                if (scene.hintAuto != null) return scene.hintAuto;
                break;
            case "title":
                if (scene.hintTitle != null) return scene.hintTitle;
                break;
            case "subtitle":
                if (scene.hintSubtitle != null) return scene.hintSubtitle;
                break;
        }
        // Legacy fallback: if no new fields are set at all AND legacy fields exist
        if (scene.hintAuto == null && scene.hintTitle == null && scene.hintSubtitle == null
                && scene.hintStyle != null) {
            if (style.equals(scene.hintStyle)) {
                String freq = scene.hintFrequency;
                if (freq == null && Boolean.TRUE.equals(scene.onlyFirstTime)) freq = "first_time";
                return freq != null ? freq : "always";
            }
        }
        return null; // disabled
    }

    private static boolean shouldShow(String freq, String stateKey) {
        ensureStateLoaded();
        if ("first_time".equals(freq) && shownSet.contains(stateKey)) return false;
        if ("until_read".equals(freq) && readSet.contains(stateKey)) return false;
        return true;
    }

    /** Public keybind entry for platforms that want to dispatch trigger key presses directly. */
    public static boolean onTriggerKeyPressed() {
        Minecraft mc = Minecraft.getInstance();
        boolean opened = tryOpenTriggeredScene(mc);
        if (mc.player != null) {
            LOGGER.info("Trigger key pressed: opened={}, activeScene={}, pos={}",
                    opened,
                    activeScene != null ? activeScene.sceneKey() : "null",
                    mc.player.blockPosition());
        } else {
            LOGGER.info("Trigger key pressed: opened={}, no player", opened);
        }
        return opened;
    }

    private static boolean tryOpenTriggeredScene(Minecraft mc) {
        DslScene sceneToOpen = activeScene != null ? activeScene : findTriggeredScene(mc);
        if (sceneToOpen == null) return false;

        String sceneKey = sceneToOpen.sceneKey();
        openPonderFor(sceneToOpen, sceneKey);
        activeScene = null;
        return true;
    }

    /** Build the hint Component, using custom text if provided, otherwise the default key-name template. */
    private static Component getHintText(@Nullable String customText) {
        if (customText != null && !customText.isEmpty()) {
            return Component.literal(customText);
        }
        Component keyName = ModKeyBindings.TRIGGER_PONDER.getTranslatedKeyMessage();
        return Component.translatable("ponderer.ui.trigger.hint", keyName);
    }

    /** Mark a style-specific state as shown (persisted to disk). */
    private static void markShown(String sceneKey, String style) {
        if (shownSet.add(sceneKey + ":" + style)) {
            saveState();
        }
    }

    private static void openPonderFor(DslScene scene, String sceneKey) {
        if (scene.items == null || scene.items.isEmpty()) return;
        String itemIdStr = scene.items.get(0);
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) return;

        if (!PonderIndex.getSceneAccess().doScenesExistForId(itemId)) {
            LOGGER.warn("No ponder scenes registered for item: {}", itemId);
            return;
        }

        // Mark all styles as read for until_read frequency
        boolean changed = false;
        changed |= readSet.add(sceneKey + ":auto");
        changed |= readSet.add(sceneKey + ":title");
        changed |= readSet.add(sceneKey + ":subtitle");
        if (changed) saveState();

        PonderUI ui;
        if (scene.nbtFilter != null && !scene.nbtFilter.isBlank()) {
            ui = PonderUI.of(buildFilteredStack(itemId, scene.nbtFilter));
        } else {
            ui = PonderUI.of(itemId);
        }
        Minecraft.getInstance().setScreen(ui);
    }

    private static ItemStack buildFilteredStack(ResourceLocation itemId, String nbtFilter) {
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
            ItemStack stack = new ItemStack(item);
            var tag = net.minecraft.nbt.TagParser.parseTag(nbtFilter);
            stack.setTag(tag);
            return stack;
        } catch (Exception e) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
            return new ItemStack(item);
        }
    }

    // ---- Persistence for shown/read state ----

    private static Path getStateFile() {
        return PondererServices.PLATFORM.getConfigDir().resolve("ponderer").resolve("trigger_state.json");
    }

    private static void ensureStateLoaded() {
        if (stateLoaded) return;
        stateLoaded = true;
        Path file = getStateFile();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            if (obj.has("shown")) {
                for (JsonElement el : obj.getAsJsonArray("shown")) {
                    shownSet.add(el.getAsString());
                }
            }
            if (obj.has("read")) {
                for (JsonElement el : obj.getAsJsonArray("read")) {
                    readSet.add(el.getAsString());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load trigger state: {}", e.getMessage());
        }
    }

    private static void saveState() {
        Path file = getStateFile();
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            JsonArray shownArr = new JsonArray();
            for (String id : shownSet) shownArr.add(id);
            obj.add("shown", shownArr);
            JsonArray readArr = new JsonArray();
            for (String id : readSet) readArr.add(id);
            obj.add("read", readArr);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save trigger state: {}", e.getMessage());
        }
    }
}
