package com.nododiiiii.ponderer.projector.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

final class ProjectorGuiGraphicsBridge {

    private static final Constructor<GuiGraphics> CONSTRUCTOR = resolveConstructor();

    private ProjectorGuiGraphicsBridge() {
    }

    static GuiGraphics create(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        return create(minecraft, poseStack, buffers);
    }

    static GuiGraphics createIsolated(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(256));
        return create(minecraft, poseStack, buffers);
    }

    static GuiGraphics create(PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        return create(minecraft, poseStack, buffers);
    }

    private static GuiGraphics create(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        try {
            return CONSTRUCTOR.newInstance(minecraft, poseStack, buffers);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to create GuiGraphics for projector rendering", e);
        }
    }

    private static Constructor<GuiGraphics> resolveConstructor() {
        try {
            Constructor<GuiGraphics> constructor = GuiGraphics.class.getDeclaredConstructor(
                Minecraft.class, PoseStack.class, MultiBufferSource.BufferSource.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
