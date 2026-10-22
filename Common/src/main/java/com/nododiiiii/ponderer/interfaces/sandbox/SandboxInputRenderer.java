package com.nododiiiii.ponderer.interfaces.sandbox;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;

/** Refreshes render-derived hit tests synchronously, including during seeks and script replay. */
final class SandboxInputRenderer implements AutoCloseable {

    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(256));
    @Nullable
    private TextureTarget target;

    /** Must run inside the session's dispatch scope, before delivering the input event. */
    void render(Minecraft mc, Screen screen, double mouseX, double mouseY) {
        RenderSystem.assertOnRenderThread();
        // Flush host geometry before switching targets; no refresh pixels may reach the host.
        mc.renderBuffers().bufferSource().endBatch();
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f texture = new Matrix4f(RenderSystem.getTextureMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        float[] color = RenderSystem.getShaderColor().clone();
        ShaderInstance shader = RenderSystem.getShader();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        GuiGraphics graphics = new GuiGraphics(mc, buffers);
        try {
            Window window = mc.getWindow();
            int width = Math.max(1, window.getWidth());
            int height = Math.max(1, window.getHeight());
            if (target == null) {
                target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            } else if (target.width != width || target.height != height) {
                target.resize(width, height, Minecraft.ON_OSX);
            }
            RenderSystem.disableScissor();
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            // Match GameRenderer's GUI projection, independently of the caller's render pass.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,
                (float) (width / window.getGuiScale()), (float) (height / window.getGuiScale()), 0,
                1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            modelView.translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.resetTextureMatrix();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            screen.render(graphics, (int) mouseX, (int) mouseY, 0);
        } finally {
            try {
                // Flush even if the screen throws, while its geometry is still offscreen.
                graphics.flush();
                mc.renderBuffers().bufferSource().endBatch();
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                if (!scissorEnabled) RenderSystem.disableScissor();
                if (depthEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (blendEnabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                if (cullEnabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
                RenderSystem.depthMask(depthMask);
                RenderSystem.depthFunc(depthFunc);
                RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.setTextureMatrix(texture);
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                RenderSystem.setShader(() -> shader);
            }
        }
    }

    @Override
    public void close() {
        if (target == null) {
            return;
        }
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            target.destroyBuffers();
        } finally {
            target = null;
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        }
    }
}
