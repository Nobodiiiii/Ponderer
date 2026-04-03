package com.nododiiiii.ponderer.forge.sticksnapshot.client;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.mixin.PonderProgressBarAccessorMixin;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.MirrorClosePacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ModNetworking;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ReplaySnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.SaveSnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.BlockSnapshot;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.ponder.foundation.ui.PonderProgressBar;
import net.createmod.ponder.foundation.ui.PonderUI;
import com.nododiiiii.ponderer.ui.InterfaceSlotEditState;
import com.nododiiiii.ponderer.ui.UiAnchorCoords;
import com.nododiiiii.ponderer.ui.UiAnchorViewport;
import com.nododiiiii.ponderer.ui.InterfaceSlotOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientInputHandler {
    private static final int SHOW_INTERFACE_AUTO_REPLAY_DELAY_TICKS = 1;

    private static BlockSnapshot localSnapshot;
    private static long lastReplayMillis = 0L;
    private static boolean awaitingMirrorOpen = false;
    private static boolean mirrorScreenActive = false;
    private static int lastObservedContainerId = -999;
    private static int mirrorAutoCloseTicks = -1;
    private static int autoReplayTicks = -1;
    private static boolean autoReplayArmed = false;
    private static boolean suppressNextAutoReplay = false;
    private static boolean pendingAutoClick = false;
    private static double pendingAutoClickNormX = 0.0;
    private static double pendingAutoClickNormY = 0.0;
    private static int pendingAutoClickButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    @Nullable
    private static DslScene.InterfaceSlotBinding draggedSlotBinding;
    @Nullable
    private static Integer draggedSlotOriginIndex;
    @Nullable
    private static AbstractContainerMenu previousPlayerMenu;
    @Nullable
    private static Screen embeddedMirrorScreen;
    private static int embeddedMirrorRenderDepth = 0;

    private ClientInputHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ClientInputHandler.class);
    }

    public static void prepareMirrorReplay(int autoCloseTicks) {
        prepareMirrorReplay(autoCloseTicks, false);
    }

    public static void prepareMirrorReplay(int autoCloseTicks, boolean scheduleAutoReplay) {
        Minecraft mc = Minecraft.getInstance();
        awaitingMirrorOpen = true;
        mirrorScreenActive = false;
        embeddedMirrorScreen = null;
        mirrorAutoCloseTicks = autoCloseTicks > 0 ? autoCloseTicks : -1;
        boolean shouldAutoReplay = scheduleAutoReplay && !suppressNextAutoReplay;
        suppressNextAutoReplay = false;
        autoReplayArmed = shouldAutoReplay;
        autoReplayTicks = shouldAutoReplay ? SHOW_INTERFACE_AUTO_REPLAY_DELAY_TICKS : -1;
        pendingAutoClick = false;
        draggedSlotBinding = null;
        draggedSlotOriginIndex = null;
        InterfaceSlotEditState.clearJeiViewport();
        if (mc.player != null && mc.player.containerMenu != null) {
            lastObservedContainerId = mc.player.containerMenu.containerId;
        }
    }

    public static void clickEmbeddedMirrorAt(double normalizedX, double normalizedY, int button) {
        pendingAutoClick = true;
        pendingAutoClickNormX = normalizedX;
        pendingAutoClickNormY = normalizedY;
        pendingAutoClickButton = button;

        Minecraft mc = Minecraft.getInstance();
        if (embeddedMirrorScreen != null && mc.screen instanceof PonderUI && (!autoReplayArmed || autoReplayTicks <= 0)) {
            runPendingAutoClick(mc);
        }
    }

    public static void attachMirrorToPonder(Screen mirrorScreen) {
        Minecraft mc = Minecraft.getInstance();
        embeddedMirrorScreen = mirrorScreen;
        awaitingMirrorOpen = false;
        mirrorScreenActive = true;
        mirrorScreen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        // JEI should target the host PonderUI during embedded flows, not the child
        // mirror screen instance that only exists as a rendered subtree.
        stripJeiWidgets(mirrorScreen);
        if (InterfaceSlotEditState.isActive()) {
            InterfaceSlotEditState.captureJeiViewport(mirrorScreen);
        }
        StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] mirror attached to ponder screen: {}",
                mirrorScreen.getClass().getName());
    }

    @Nullable
    public static Screen getEmbeddedMirrorScreen() {
        return embeddedMirrorScreen;
    }

    public static boolean hasEmbeddedMirrorScreen() {
        return embeddedMirrorScreen != null;
    }

    public static void beginEmbeddedMirrorRender() {
        embeddedMirrorRenderDepth++;
    }

    public static void endEmbeddedMirrorRender() {
        if (embeddedMirrorRenderDepth > 0) {
            embeddedMirrorRenderDepth--;
        }
    }

    public static boolean isRenderingEmbeddedMirror() {
        return embeddedMirrorRenderDepth > 0;
    }

    public static boolean shouldRenderEmbeddedMirror() {
        if (embeddedMirrorScreen == null) {
            return false;
        }
        return !(autoReplayArmed && autoReplayTicks > 0);
    }

    public static boolean isAutoReplayWaitActive() {
        return autoReplayArmed && autoReplayTicks > 0;
    }

    public static void renderDraggedSlotBinding(GuiGraphics graphics, int mouseX, int mouseY) {
        if (draggedSlotBinding == null) {
            return;
        }
        var element = JeiCompat.resolveIngredientById(draggedSlotBinding.ingredientId, draggedSlotBinding.ingredientKind);
        if (element == null) {
            return;
        }
        element.render(graphics, mouseX - 8, mouseY - 8);
    }

    public static void bindMirrorMenu(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (previousPlayerMenu == null) {
            previousPlayerMenu = mc.player.containerMenu;
        }
        mc.player.containerMenu = menu;
        lastObservedContainerId = menu.containerId;
        StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] bound mirror menu containerId={} class={}",
                menu.containerId, menu.getClass().getName());
    }

    public static void closeEmbeddedMirrorFromPonder(String reason) {
        if (embeddedMirrorScreen == null) {
            return;
        }
        closeMirrorSession(reason);
    }

    // @SubscribeEvent
    public static void onMousePre(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.STICK)) {
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        BlockEntity be = mc.level.getBlockEntity(pos);

        localSnapshot = new BlockSnapshot(
                net.minecraft.world.level.block.Block.getId(state),
                blockId,
                be != null ? be.saveWithFullMetadata() : null,
                mc.level.dimension().location(),
                pos.immutable(),
                hit.getDirection(),
                hit.getLocation(),
                hit.isInside()
        );

        StickSnapshotFeature.LOGGER.debug("[client] captured snapshot block={} pos={} dim={}", blockId, pos, mc.level.dimension().location());
        ModNetworking.CHANNEL.sendToServer(new SaveSnapshotPacket(localSnapshot));
        // Cancel vanilla pick block behavior while using stick.
        event.setCanceled(true);
    }

    // @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (!mc.player.getMainHandItem().is(Items.STICK)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReplayMillis < 250L) {
            return;
        }
        lastReplayMillis = now;

        event.setCanceled(true);
        StickSnapshotFeature.LOGGER.debug("[client] request replay with stick, localSnapshotPresent={}", localSnapshot != null);
        prepareMirrorReplay(-1);
        ModNetworking.CHANNEL.sendToServer(new ReplaySnapshotPacket());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!awaitingMirrorOpen && !mirrorScreenActive) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) {
            return;
        }

        int currentId = mc.player.containerMenu.containerId;
        if (currentId != lastObservedContainerId) {
            StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] containerMenu changed old={} new={} class={}",
                    lastObservedContainerId, currentId, mc.player.containerMenu.getClass().getName());
            lastObservedContainerId = currentId;
        }

        if (mirrorScreenActive && mirrorAutoCloseTicks > 0) {
            mirrorAutoCloseTicks--;
            if (mirrorAutoCloseTicks <= 0) {
                if (embeddedMirrorScreen != null) {
                    closeMirrorSession("auto-close-embedded");
                } else if (mc.screen != null) {
                    mc.setScreen(null);
                }
            }
        }

        if (autoReplayArmed && autoReplayTicks >= 0 && embeddedMirrorScreen != null && mc.screen instanceof PonderUI ponder) {
            if (autoReplayTicks > 0) {
                autoReplayTicks--;
            }
            if (autoReplayTicks == 0) {
                runPendingAutoClick(mc);
                autoReplayArmed = false;
                autoReplayTicks = -1;
                suppressNextAutoReplay = true;
                StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] trigger delayed ponder replay after show_interface");
                resetProgressBarBeforeReplay(ponder);
                callPonderReplay(ponder);
            }
        }

        if (pendingAutoClick && embeddedMirrorScreen != null && mc.screen instanceof PonderUI
            && (!autoReplayArmed || autoReplayTicks <= 0)) {
            runPendingAutoClick(mc);
        }
    }

    @SubscribeEvent
    public static void onClientCustomPayload(NetworkEvent.ClientCustomPayloadEvent event) {
        if (!awaitingMirrorOpen) {
            return;
        }

        if (event.getPayload() == null) {
            return;
        }

        net.minecraft.network.FriendlyByteBuf copy = new net.minecraft.network.FriendlyByteBuf(event.getPayload().copy());
        int readable = copy.readableBytes();
        byte[] head = new byte[Math.min(32, readable)];
        copy.readBytes(head);
        StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] custom payload received loginIndex={} bytes={} head={}",
                event.getLoginIndex(), readable, toHex(head));
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!awaitingMirrorOpen || event.getNewScreen() == null || embeddedMirrorScreen != null) {
            return;
        }

        awaitingMirrorOpen = false;
        mirrorScreenActive = true;
        StickSnapshotFeature.LOGGER.debug("[client] mirror screen opened: {}", event.getNewScreen().getClass().getName());
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (!mirrorScreenActive) {
            return;
        }

        closeMirrorSession("screen-event-closing");
    }

    @SubscribeEvent
    public static void onPonderMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen mirror = embeddedMirrorScreen;
        if (mirror == null || !shouldRenderEmbeddedMirror() || !(event.getScreen() instanceof PonderUI)) {
            return;
        }

        if (handleMirrorMousePressed(mirror, event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onPonderMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Screen mirror = embeddedMirrorScreen;
        if (mirror == null || !shouldRenderEmbeddedMirror() || !(event.getScreen() instanceof PonderUI)) {
            return;
        }

        if (handleMirrorMouseReleased(mirror, event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onPonderMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        Screen mirror = embeddedMirrorScreen;
        if (mirror == null || !shouldRenderEmbeddedMirror() || !(event.getScreen() instanceof PonderUI)) {
            return;
        }

        mirror.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta());
    }

    private static void closeMirrorSession(String reason) {
        if (!mirrorScreenActive) {
            return;
        }

        mirrorScreenActive = false;
        awaitingMirrorOpen = false;
        mirrorAutoCloseTicks = -1;
        autoReplayArmed = false;
        autoReplayTicks = -1;
        pendingAutoClick = false;
        draggedSlotBinding = null;
        draggedSlotOriginIndex = null;
        InterfaceSlotEditState.clearJeiViewport();
        InterfaceSlotOverlayRenderer.clearRuntimeBindings();
        embeddedMirrorScreen = null;
        restorePlayerMenu();
        MirrorForgeOpenClient.restoreInjectedBlock();
        ModNetworking.CHANNEL.sendToServer(new MirrorClosePacket());
        StickSnapshotFeature.LOGGER.debug("[client] mirror screen closed, requested inventory restore, reason={}", reason);
    }

    private static void restorePlayerMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            previousPlayerMenu = null;
            return;
        }

        mc.player.containerMenu = previousPlayerMenu != null ? previousPlayerMenu : mc.player.inventoryMenu;
        lastObservedContainerId = mc.player.containerMenu.containerId;
        previousPlayerMenu = null;
    }

    private static void runPendingAutoClick(Minecraft mc) {
        if (!pendingAutoClick || embeddedMirrorScreen == null) {
            return;
        }

        UiAnchorViewport.Rect viewport = UiAnchorViewport.resolve(mc);
        double localX = UiAnchorCoords.decodeToPixelX(pendingAutoClickNormX, (int) Math.max(1, viewport.width()));
        double localY = UiAnchorCoords.decodeToPixelYTopLeft(pendingAutoClickNormY, (int) Math.max(1, viewport.height()));
        double mouseX = viewport.left() + localX;
        double mouseY = viewport.top() + localY;

        handleMirrorMousePressed(embeddedMirrorScreen, mouseX, mouseY, pendingAutoClickButton);
        handleMirrorMouseReleased(embeddedMirrorScreen, mouseX, mouseY, pendingAutoClickButton);
        StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] auto click at norm=({}, {}) mouse=({}, {}) button={}",
                pendingAutoClickNormX, pendingAutoClickNormY, mouseX, mouseY, pendingAutoClickButton);
        pendingAutoClick = false;
    }

    private static boolean handleMirrorMousePressed(Screen mirror, double mouseX, double mouseY, int button) {
        Slot slot = InterfaceSlotOverlayRenderer.findSlotAt(mirror, mouseX, mouseY);
        if (InterfaceSlotEditState.isActive() && slot != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && InterfaceSlotEditState.removeBinding(slot.index)) {
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                DslScene.InterfaceSlotBinding binding = InterfaceSlotEditState.getBinding(slot.index);
                if (binding != null) {
                    draggedSlotBinding = binding;
                    draggedSlotOriginIndex = binding.slotIndex;
                    InterfaceSlotEditState.removeBinding(slot.index);
                    return true;
                }
            }
        }

        if (slot != null) {
            // Never forward slot clicks to the mirrored container itself.
            // This disables native item pickup/drag (including NBT-backed stacks)
            // in all virtual interface states, while custom edit logic above still works.
            return true;
        }

        mirror.mouseClicked(mouseX, mouseY, button);
        return false;
    }

    private static boolean handleMirrorMouseReleased(Screen mirror, double mouseX, double mouseY, int button) {
        Slot slot = InterfaceSlotOverlayRenderer.findSlotAt(mirror, mouseX, mouseY);

        if (draggedSlotBinding != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Slot target = slot;
            if (target != null) {
                InterfaceSlotEditState.putBinding(target.index, target.x, target.y,
                    draggedSlotBinding.ingredientId, draggedSlotBinding.ingredientKind);
            } else if (draggedSlotOriginIndex != null) {
                InterfaceSlotEditState.putBinding(draggedSlotOriginIndex, draggedSlotBinding.slotX, draggedSlotBinding.slotY,
                    draggedSlotBinding.ingredientId, draggedSlotBinding.ingredientKind);
            }
            draggedSlotBinding = null;
            draggedSlotOriginIndex = null;
            return true;
        }

        if (slot != null) {
            return !InterfaceSlotEditState.isActive() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT;
        }

        mirror.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    private static String toHex(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    private static void stripJeiWidgets(Screen screen) {
        int removed = 0;
        removed += removeJeiEntries(screen.children());
        removed += removeJeiEntries(readListField(screen, "renderables"));
        removed += removeJeiEntries(readListField(screen, "narratables"));
        if (removed > 0) {
            StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] stripped {} JEI widgets from {}",
                    removed, screen.getClass().getName());
        }
    }

    private static int removeJeiEntries(@Nullable List<?> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int before = list.size();
        list.removeIf(ClientInputHandler::isJeiOwned);
        return before - list.size();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static List<?> readListField(Screen screen, String fieldName) {
        try {
            java.lang.reflect.Field field = Screen.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isJeiOwned(Object obj) {
        return obj != null && obj.getClass().getName().startsWith("mezz.jei.");
    }

    private static void callPonderReplay(PonderUI ponder) {
        try {
            java.lang.reflect.Method replay = PonderUI.class.getDeclaredMethod("replay");
            replay.setAccessible(true);
            replay.invoke(ponder);
        } catch (Throwable t) {
            StickSnapshotFeature.LOGGER.debug("[client][mirror-debug] delayed replay invoke failed: {}", t.toString());
        }
    }

    private static void resetProgressBarBeforeReplay(PonderUI ponder) {
        Screen screen = (Screen) (Object) ponder;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof PonderProgressBar bar) {
                LerpedFloatReset((PonderProgressBarAccessorMixin) (Object) bar);
            }
        }
    }

    private static void LerpedFloatReset(PonderProgressBarAccessorMixin accessor) {
        accessor.ponderer$getProgress().startWithValue(0);
    }
}
