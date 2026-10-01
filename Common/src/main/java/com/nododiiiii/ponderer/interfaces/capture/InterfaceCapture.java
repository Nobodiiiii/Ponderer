package com.nododiiiii.ponderer.interfaces.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.nododiiiii.ponderer.interfaces.model.DataSanitizer;
import com.nododiiiii.ponderer.interfaces.model.IdentityRemapper;
import com.nododiiiii.ponderer.interfaces.sandbox.SandboxScope;
import com.nododiiiii.ponderer.ui.SnapshotReturnContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Opt-in observation of a normal menu opening. No custom payload handlers are replayed. */
public final class InterfaceCapture {
    public static final String SNAPSHOT_KEY = "_interface_capture";
    public static final String NOTICE_KEY = "_interface_capture_notice";
    public static final int MAX_EXTRA_BYTES = 1_048_576;
    private static Capture active;

    private static final class Capture {
        final Map<String, String> snapshot;
        final SnapshotReturnContext context;
        final Object world;
        final long deadline = System.currentTimeMillis() + 60_000;
        BlockPos pos;
        BlockPos factoryPos;
        int factoryDepth;
        ItemStack item = ItemStack.EMPTY;
        String hand = "main";
        MenuType<?> type;
        byte[] extra;
        int window = -1;
        int stableTicks;
        Object screen;
        String error;
        Capture(Map<String, String> snapshot, SnapshotReturnContext context) {
            this.snapshot = new HashMap<>(snapshot);
            this.context = context;
            world = Minecraft.getInstance().level;
        }
    }

    private InterfaceCapture() {}

    public static void start(Map<String, String> snapshot, SnapshotReturnContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || SandboxScope.isActive()) return;
        active = new Capture(snapshot, context);
        mc.setScreen(null);
        mc.player.displayClientMessage(Component.translatable("ponderer.interface.capture.start"), false);
    }

    public static boolean isObserving() {
        return active != null && RenderSystem.isOnRenderThread() && !SandboxScope.isActive();
    }

    /** Start a new interaction; merely aiming at a block does not make it the menu source. */
    public static void beforeUse() {
        if (!isObserving()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || active.window >= 0) return;
        active.pos = null;
        active.factoryPos = null;
        active.item = ItemStack.EMPTY;
    }

    public static void useHand(InteractionHand hand) {
        if (!isObserving() || active.window >= 0) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        active.hand = hand == InteractionHand.OFF_HAND ? "off" : "main";
        active.item = player.getItemInHand(hand).copy();
    }

    /** Observe the block's result, before item fallback can consume the same click. */
    public static void observeBlockUse(Level level, BlockHitResult hit, boolean consumed) {
        if (isObserving() && level == active.world && consumed && active.window < 0) {
            active.pos = hit.getBlockPos().immutable();
        }
    }

    /** A menu factory's actual lookup also covers remote/proxy blocks and empty-hand openings. */
    public static void observeBlockEntity(Object level, BlockPos pos, @Nullable BlockEntity entity) {
        if (isObserving() && level == active.world && active.factoryDepth > 0
            && active.factoryPos == null && entity != null) {
            active.factoryPos = pos.immutable();
        }
    }

    public static void finishFactory(int window) {
        if (isObserving() && active.window == window && active.factoryDepth > 0) active.factoryDepth--;
    }

    public static void rejectFactory(int window, String reason) {
        if (!isObserving()) return;
        active.window = window;
        active.error = reason;
    }

    /** Loader hooks copy data without moving the real buffer's reader index. */
    public static void observeFactory(MenuType<?> type, int window, @Nullable byte[] extra) {
        if (!isObserving()) return;
        if (extra != null && extra.length > MAX_EXTRA_BYTES) {
            rejectFactory(window, "ponderer.interface.capture.too_large");
            return;
        }
        if (active.window != window) {
            active.factoryDepth = 0;
            active.factoryPos = null;
        }
        active.factoryDepth++;
        if (active.window == window && active.extra != null && extra == null) return;
        active.type = type;
        active.window = window;
        active.extra = extra == null ? null : extra.clone();
        active.stableTicks = 0;
    }

    public static void tick() {
        Capture capture = active;
        if (capture == null) return;
        // Factory calls are synchronous. Clear a scope left behind by a failed constructor.
        capture.factoryDepth = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != capture.world || mc.player == null) { active = null; return; }
        if (capture.error != null) { finish(null, capture.error); return; }
        if (System.currentTimeMillis() >= capture.deadline || mc.screen instanceof PauseScreen) {
            finish(null, "ponderer.interface.capture.cancelled");
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)
            || screen.getMenu().containerId != capture.window) {
            if (capture.screen != null) finish(null, "ponderer.interface.capture.cancelled");
            return;
        }
        capture.screen = screen;
        // Give vanilla slot sync and the mod's initial state packets time to arrive.
        if (++capture.stableTicks < 20) return;
        try {
            finish(snapshot(capture, screen), "ponderer.interface.capture.done");
        } catch (RuntimeException ex) {
            LogUtils.getLogger().warn("[Ponderer] Interface capture failed", ex);
            finish(null, "ponderer.interface.capture.failed");
        }
    }

    private static void finish(@Nullable JsonObject descriptor, String notice) {
        Capture capture = active;
        active = null;
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (descriptor != null) capture.snapshot.put(SNAPSHOT_KEY, descriptor.toString());
        capture.snapshot.put(NOTICE_KEY, notice);
        if (mc.player != null && mc.player.containerMenu.containerId == capture.window) mc.player.closeContainer();
        capture.context.reopenEditor(capture.snapshot);
    }

    private static JsonObject snapshot(Capture capture, AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("interaction", "view");
        root.addProperty("menuType", BuiltInRegistries.MENU.getKey(capture.type).toString());
        if (capture.extra != null) root.addProperty("extraData", Base64.getEncoder().encodeToString(capture.extra));
        JsonObject target = new JsonObject();
        root.add("target", target);
        if (capture.factoryPos != null) capture.pos = capture.factoryPos;
        if (capture.pos != null) {
            target.addProperty("kind", "block");
            BlockState state = mc.level.getBlockState(capture.pos);
            target.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            target.add("properties", properties(state));
            JsonArray origin = new JsonArray();
            origin.add(capture.pos.getX()); origin.add(capture.pos.getY()); origin.add(capture.pos.getZ());
            root.add("lookupOrigin", origin);
            BlockEntity be = mc.level.getBlockEntity(capture.pos);
            if (be != null) {
                CompoundTag tag = be.saveWithFullMetadata(mc.level.registryAccess());
                DataSanitizer.stripBlockEntityHazards(tag);
                JsonArray identities = new JsonArray();
                for (var slot : IdentityRemapper.strip(tag, mc.player.getUUID(), mc.player.getGameProfile().getName())) {
                    JsonObject identity = new JsonObject();
                    identity.addProperty("path", slot.path());
                    identity.addProperty("encoding", slot.encoding());
                    identity.addProperty("role", slot.role());
                    identities.add(identity);
                }
                target.addProperty("nbt", tag.toString());
                root.add("identitySlots", identities);
            }
            JsonObject context = new JsonObject();
            JsonArray blocks = new JsonArray();
            // A bounded local context also supports double chests and adjacent GUI lookups.
            for (BlockPos pos : BlockPos.betweenClosed(capture.pos.offset(-1, -1, -1), capture.pos.offset(1, 1, 1))) {
                if (pos.equals(capture.pos)) continue;
                BlockState neighbor = mc.level.getBlockState(pos);
                if (neighbor.isAir()) continue;
                JsonObject block = new JsonObject();
                JsonArray offset = new JsonArray();
                offset.add(pos.getX() - capture.pos.getX()); offset.add(pos.getY() - capture.pos.getY()); offset.add(pos.getZ() - capture.pos.getZ());
                block.add("pos", offset);
                block.addProperty("block", BuiltInRegistries.BLOCK.getKey(neighbor.getBlock()).toString());
                block.add("properties", properties(neighbor));
                blocks.add(block);
            }
            context.add("blocks", blocks);
            root.add("context", context);
        } else if (!capture.item.isEmpty()) {
            target.addProperty("kind", "item");
            target.addProperty("mode", "menu");
            target.addProperty("hand", capture.hand);
            target.add("item", encodeItem(capture.item));
        } else {
            target.addProperty("kind", "menu");
        }
        JsonArray slots = new JsonArray();
        for (int i = 0; i < screen.getMenu().slots.size(); i++) {
            var slot = screen.getMenu().slots.get(i);
            if (slot.container == mc.player.getInventory() || slot.getItem().isEmpty()) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.add("item", encodeItem(slot.getItem()));
            slots.add(entry);
        }
        root.add("slots", slots);
        JsonObject player = new JsonObject();
        player.addProperty("selectedSlot", mc.player.getInventory().selected);
        player.addProperty("gameMode", mc.gameMode.getPlayerMode().getName());
        JsonObject xp = new JsonObject();
        xp.addProperty("level", mc.player.experienceLevel);
        xp.addProperty("progress", mc.player.experienceProgress);
        player.add("xp", xp);
        root.add("player", player);
        return root;
    }

    private static JsonObject properties(BlockState state) {
        JsonObject result = new JsonObject();
        state.getValues().forEach((property, value) -> result.addProperty(property.getName(), propertyName(property, value)));
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyName(net.minecraft.world.level.block.state.properties.Property property, Comparable value) {
        return property.getName(value);
    }

    private static com.google.gson.JsonElement encodeItem(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess()), stack)
            .getOrThrow();
    }
}
