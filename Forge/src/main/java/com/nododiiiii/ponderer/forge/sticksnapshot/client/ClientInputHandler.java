package com.nododiiiii.ponderer.forge.sticksnapshot.client;

import com.nododiiiii.ponderer.forge.sticksnapshot.StickSnapshotFeature;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.MirrorClosePacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ModNetworking;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.ReplaySnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.network.SaveSnapshotPacket;
import com.nododiiiii.ponderer.forge.sticksnapshot.snapshot.BlockSnapshot;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientInputHandler {
    private static BlockSnapshot localSnapshot;
    private static long lastReplayMillis = 0L;
    private static boolean awaitingMirrorOpen = false;
    private static boolean mirrorScreenActive = false;
    private static int lastObservedContainerId = -999;
    private static int mirrorAutoCloseTicks = -1;
    @Nullable
    private static Screen embeddedMirrorScreen;

    private ClientInputHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ClientInputHandler.class);
    }

    public static void prepareMirrorReplay(int autoCloseTicks) {
        Minecraft mc = Minecraft.getInstance();
        awaitingMirrorOpen = true;
        mirrorScreenActive = false;
        embeddedMirrorScreen = null;
        mirrorAutoCloseTicks = autoCloseTicks > 0 ? autoCloseTicks : -1;
        if (mc.player != null && mc.player.containerMenu != null) {
            lastObservedContainerId = mc.player.containerMenu.containerId;
        }
    }

    public static void attachMirrorToPonder(Screen mirrorScreen) {
        Minecraft mc = Minecraft.getInstance();
        embeddedMirrorScreen = mirrorScreen;
        awaitingMirrorOpen = false;
        mirrorScreenActive = true;
        mirrorScreen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
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

    public static void closeEmbeddedMirrorFromPonder(String reason) {
        if (embeddedMirrorScreen == null) {
            return;
        }
        closeMirrorSession(reason);
    }

    @SubscribeEvent
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

    @SubscribeEvent
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
        if (mirror == null || !(event.getScreen() instanceof PonderUI)) {
            return;
        }

        mirror.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
    }

    @SubscribeEvent
    public static void onPonderMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Screen mirror = embeddedMirrorScreen;
        if (mirror == null || !(event.getScreen() instanceof PonderUI)) {
            return;
        }

        mirror.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
    }

    @SubscribeEvent
    public static void onPonderMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        Screen mirror = embeddedMirrorScreen;
        if (mirror == null || !(event.getScreen() instanceof PonderUI)) {
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
        embeddedMirrorScreen = null;
        MirrorForgeOpenClient.restoreInjectedBlock();
        ModNetworking.CHANNEL.sendToServer(new MirrorClosePacket());
        StickSnapshotFeature.LOGGER.debug("[client] mirror screen closed, requested inventory restore, reason={}", reason);
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
}
