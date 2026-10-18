package com.nododiiiii.ponderer.ui.catnip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.createmod.catnip.gui.element.DelegatedStencilElement;
import net.createmod.catnip.gui.element.RenderElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Reuses Ponder icons when available, with a local atlas for the remaining actions. */
public enum PondererGuiIcons implements ScreenElement {
    PICK_POSITION(0, 0),
    PICK_BLOCK(0, 0),
    PICK_NBT(0, 0),
    PICK_ITEM(1, 0),
    CAPTURE(0, 0),
    ADD(2, 0),
    INSERT(2, 0),
    COPY(1, 1),
    PASTE(2, 1),
    EDIT_NBT(0, 1),
    STRUCTURE(0, 2),
    LIST(0, 2),
    BROWSE_FOLDER(1, 2),
    PULL(3, 0),
    UPLOAD(3, 1),
    HISTORY(2, 2),
    IGNORE(3, 2),
    OVERWRITE(0, 3),
    EDIT_SCENE(1, 3),
    REMOVE(PonderGuiTextures.ICON_CONFIG_DISCARD),
    EXPAND(PonderGuiTextures.ICON_PONDER_RIGHT),
    COLLAPSE(PonderGuiTextures.ICON_PONDER_RIGHT, 90);

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("ponderer", "textures/gui/action_icons.png");
    private static final int TEXTURE_ICON_SIZE = 16;
    private static final int RENDERED_ICON_SIZE = 12;
    private static final int SHEET_SIZE = 64;

    @Nullable
    private final PonderGuiTextures nativeIcon;
    private final float rotation;
    private final int u;
    private final int v;

    PondererGuiIcons(int column, int row) {
        nativeIcon = null;
        rotation = 0;
        u = column * 16;
        v = row * 16;
    }

    PondererGuiIcons(PonderGuiTextures nativeIcon) {
        this(nativeIcon, 0);
    }

    PondererGuiIcons(PonderGuiTextures nativeIcon, float rotation) {
        this.nativeIcon = nativeIcon;
        this.rotation = rotation;
        u = 0;
        v = 0;
    }

    public BoxWidget attach(BoxWidget button) {
        return button.showingElement(asElement(button));
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + TEXTURE_ICON_SIZE / 2f, y + TEXTURE_ICON_SIZE / 2f, 0);
        if (rotation != 0) {
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        }
        if (nativeIcon != null) {
            nativeIcon.render(graphics, -TEXTURE_ICON_SIZE / 2, -TEXTURE_ICON_SIZE / 2);
        } else {
            graphics.blit(TEXTURE, -TEXTURE_ICON_SIZE / 2, -TEXTURE_ICON_SIZE / 2,
                u, v, TEXTURE_ICON_SIZE, TEXTURE_ICON_SIZE, SHEET_SIZE, SHEET_SIZE);
        }
        graphics.pose().popPose();
    }

    public RenderElement asElement(BoxWidget button) {
        return asStencil(RENDERED_ICON_SIZE)
            .withElementRenderer(this == REMOVE
                ? PonderIconStencils.failGradient(button)
                : BoxWidget.gradientFactory.apply(button));
    }

    public DelegatedStencilElement asStencil(int iconSize) {
        return new DelegatedStencilElement()
            .withStencilRenderer((graphics, width, height, alpha) ->
                renderStencil(graphics, width / 2f, height / 2f, iconSize))
            .withBounds(iconSize, iconSize);
    }

    private void renderStencil(GuiGraphics graphics, float centerX, float centerY, int iconSize) {
        float[] previousColor = RenderSystem.getShaderColor().clone();
        graphics.setColor(1, 1, 1, 1);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, centerY, 0);
            float scale = iconSize / (float) TEXTURE_ICON_SIZE;
            graphics.pose().scale(scale, scale, 1);
            render(graphics, -TEXTURE_ICON_SIZE / 2, -TEXTURE_ICON_SIZE / 2);
        } finally {
            graphics.pose().popPose();
            graphics.setColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
        }
    }
}
