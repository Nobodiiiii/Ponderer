package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.BitSet;

/** One-shot alpha readback and four-connected component bounds in native GUI pixels. */
final class PanelPixelSelection {
    private PanelPixelSelection() {}

    @Nullable
    static int[] bounds(TextureTarget image, double scale, int[] source, int seedX, int seedY) {
        RenderSystem.assertOnRenderThread();
        int width = source[2], height = source[3];
        int x = seedX - source[0], y = seedY - source[1];
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x >= width || y >= height) return null;
        int x0 = Math.clamp((int) Math.floor(source[0] * scale), 0, image.width);
        int x1 = Math.clamp((int) Math.ceil((source[0] + width) * scale), 0, image.width);
        int y0 = Math.clamp((int) Math.floor(image.height - (source[1] + height) * scale), 0, image.height);
        int y1 = Math.clamp((int) Math.ceil(image.height - source[1] * scale), 0, image.height);
        int readWidth = x1 - x0, readHeight = y1 - y0;
        if (readWidth <= 0 || readHeight <= 0) return null;

        int pixelCount = Math.multiplyExact(width, height);
        BitSet visible = new BitSet(pixelCount);
        ByteBuffer pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(readWidth, readHeight), 4));
        int framebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, image.frameBufferId);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glReadPixels(x0, y0, readWidth, readHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            // Framebuffer rows are bottom-up. Any visible physical sample covers its GUI pixel.
            for (int row = 0; row < readHeight; row++) {
                int guiY = (int) Math.floor((image.height - y0 - row - 0.5) / scale) - source[1];
                if (guiY < 0 || guiY >= height) continue;
                for (int column = 0; column < readWidth; column++) {
                    if (pixels.get((row * readWidth + column) * 4 + 3) == 0) continue;
                    int guiX = (int) Math.floor((x0 + column + 0.5) / scale) - source[0];
                    if (guiX >= 0 && guiX < width) visible.set(guiY * width + guiX);
                }
            }
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            MemoryUtil.memFree(pixels);
        }

        int seed = y * width + x;
        if (!visible.get(seed)) return null;
        int[] queue = new int[pixelCount];
        queue[0] = seed;
        visible.clear(seed);
        int head = 0, tail = 1, minX = x, maxX = x, minY = y, maxY = y;
        while (head < tail) {
            int pixel = queue[head++];
            int px = pixel % width, py = pixel / width;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
            if (px > 0) tail = enqueue(visible, queue, tail, pixel - 1);
            if (px + 1 < width) tail = enqueue(visible, queue, tail, pixel + 1);
            if (py > 0) tail = enqueue(visible, queue, tail, pixel - width);
            if (py + 1 < height) tail = enqueue(visible, queue, tail, pixel + width);
        }
        return new int[]{source[0] + minX, source[1] + minY, maxX - minX + 1, maxY - minY + 1};
    }

    private static int enqueue(BitSet visible, int[] queue, int tail, int pixel) {
        if (visible.get(pixel)) {
            visible.clear(pixel);
            queue[tail++] = pixel;
        }
        return tail;
    }
}
