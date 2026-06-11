package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.blueprint.RaycastHelper;
import com.nododiiiii.ponderer.projector.ProjectorKind;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectorAnchorPickState {

    private static boolean active;
    @Nullable
    private static BlockPos projectorPos;
    @Nullable
    private static ProjectorKind projectorKind;
    @Nullable
    private static Map<String, String> snapshot;
    private static boolean middleWasDown;

    private ProjectorAnchorPickState() {
    }

    public static void startPick(Map<String, String> snapshot, BlockPos projectorPos, ProjectorKind projectorKind) {
        ProjectorAnchorPickState.active = true;
        ProjectorAnchorPickState.projectorPos = projectorPos;
        ProjectorAnchorPickState.projectorKind = projectorKind;
        ProjectorAnchorPickState.snapshot = new LinkedHashMap<>(snapshot);
        ProjectorAnchorPickState.middleWasDown = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static void onClientTick() {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("ponderer.ui.projector.pick_anchor.prompt"), true);
        }
    }

    public static void handleMiddleClick() {
        if (!active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }

        boolean middleDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
            mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!middleDown || middleWasDown) {
            middleWasDown = middleDown;
            return;
        }
        middleWasDown = true;

        BlockHitResult hit = RaycastHelper.rayTraceRange(mc.level, mc.player, 75.0D);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || snapshot == null || projectorPos == null || projectorKind == null) {
            return;
        }

        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(
                "ponderer.ui.projector.pick_anchor.selected",
                hit.getBlockPos().getX(),
                hit.getBlockPos().getY(),
                hit.getBlockPos().getZ()), true);
        }
        reset();
    }

    public static void reset() {
        active = false;
        projectorPos = null;
        projectorKind = null;
        snapshot = null;
        middleWasDown = false;
    }
}
