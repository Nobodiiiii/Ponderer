package com.nododiiiii.ponderer.ui.catnip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.element.FadableScreenElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class PonderIconStencils {

    private static final int DEFAULT_ICON_SIZE = 16;
    private static final int TEXTURE_ICON_SIZE = 16;

    private PonderIconStencils() {
    }

    public static DelegatedStencilElement centered(PonderGuiTextures texture) {
        return centered(texture, DEFAULT_ICON_SIZE);
    }

    public static DelegatedStencilElement centered(PonderGuiTextures texture, int iconSize) {
        return transformed(texture, 0, false, iconSize);
    }

    public static DelegatedStencilElement mirrored(PonderGuiTextures texture) {
        return mirrored(texture, DEFAULT_ICON_SIZE);
    }

    public static DelegatedStencilElement mirrored(PonderGuiTextures texture, int iconSize) {
        return transformed(texture, 0, true, iconSize);
    }

    public static DelegatedStencilElement rotated(PonderGuiTextures texture, float degrees) {
        return rotated(texture, degrees, DEFAULT_ICON_SIZE);
    }

    public static DelegatedStencilElement rotated(PonderGuiTextures texture, float degrees, int iconSize) {
        return transformed(texture, degrees, false, iconSize);
    }

    public static BoxWidget attach(BoxWidget button, DelegatedStencilElement icon) {
        return attach(button, icon, BoxWidget.gradientFactory.apply(button));
    }

    public static BoxWidget attachFail(BoxWidget button, DelegatedStencilElement icon) {
        return attach(button, icon, failGradient(button));
    }

    public static BoxWidget attach(BoxWidget button, DelegatedStencilElement icon, FadableScreenElement renderer) {
        return button.showingElement(icon.withElementRenderer(renderer));
    }

    private static FadableScreenElement failGradient(BoxWidget button) {
        return (graphics, width, height, alpha) -> {
            Couple<Color> colors = button.active ? AbstractSimiWidget.COLOR_FAIL : button.getColorDisabled();
            UIRenderHelper.angledGradient(graphics, 0, 0, height / 2, height, width, colors);
        };
    }

    private static DelegatedStencilElement transformed(PonderGuiTextures texture, float degrees, boolean mirrorX,
                                                       int iconSize) {
        return new DelegatedStencilElement()
            .withStencilRenderer((graphics, width, height, alpha) -> {
                graphics.pose().pushPose();
                graphics.pose().translate(width / 2f, height / 2f, 0);
                if (degrees != 0) {
                    graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
                }
                float scale = iconSize / (float) TEXTURE_ICON_SIZE;
                graphics.pose().scale(scale, scale, 1);
                if (mirrorX) {
                    renderMirrored(texture, graphics.pose().last().pose(),
                        -TEXTURE_ICON_SIZE / 2, -TEXTURE_ICON_SIZE / 2);
                } else {
                    texture.render(graphics, -TEXTURE_ICON_SIZE / 2, -TEXTURE_ICON_SIZE / 2);
                }
                graphics.pose().popPose();
            })
            .withBounds(iconSize, iconSize);
    }

    private static void renderMirrored(PonderGuiTextures texture, Matrix4f pose, int x, int y) {
        texture.bind();
        float u1 = (texture.getStartX() + texture.getWidth()) / 256f;
        float u2 = texture.getStartX() / 256f;
        float v1 = texture.getStartY() / 256f;
        float v2 = (texture.getStartY() + texture.getHeight()) / 256f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        buffer.vertex(pose, x, y + texture.getHeight(), 0).color(255, 255, 255, 255).uv(u1, v2).endVertex();
        buffer.vertex(pose, x + texture.getWidth(), y + texture.getHeight(), 0).color(255, 255, 255, 255).uv(u2, v2).endVertex();
        buffer.vertex(pose, x + texture.getWidth(), y, 0).color(255, 255, 255, 255).uv(u2, v1).endVertex();
        buffer.vertex(pose, x, y, 0).color(255, 255, 255, 255).uv(u1, v1).endVertex();
        tesselator.end();
        RenderSystem.disableBlend();
    }
}
