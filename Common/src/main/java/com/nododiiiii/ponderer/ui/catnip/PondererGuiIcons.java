package com.nododiiiii.ponderer.ui.catnip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.createmod.catnip.gui.element.AbstractRenderElement;
import net.createmod.catnip.gui.element.RenderElement;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Reuses Ponder icons when available, with a white-only atlas for the remaining actions. */
public enum PondererGuiIcons {
    PICK_POSITION(PonderGuiTextures.ICON_PONDER_IDENTIFY),
    PICK_BLOCK(0, 0),
    PICK_NBT(1, 0),
    PICK_ITEM(2, 0),
    CAPTURE(0, 1),
    ADD(1, 1),
    INSERT(2, 1),
    COPY(0, 2),
    PASTE(1, 2),
    EDIT_NBT(PonderGuiTextures.ICON_CONFIG_OPEN),
    STRUCTURE(PonderGuiTextures.ICON_CONFIG_OPEN),
    LIST(2, 2),
    BROWSE_FOLDER(PonderGuiTextures.ICON_CONFIG_OPEN),
    REMOVE(PonderGuiTextures.ICON_CONFIG_DISCARD),
    EXPAND(PonderGuiTextures.ICON_PONDER_RIGHT),
    COLLAPSE(PonderGuiTextures.ICON_PONDER_RIGHT, 90);

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("ponderer", "textures/gui/action_icons.png");
    private static final int ICON_SIZE = 16;
    private static final int SHEET_SIZE = 48;

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

    private RenderElement asElement(BoxWidget button) {
        return new AbstractRenderElement() {
            @Override
            public void render(GuiGraphics graphics) {
                PondererGuiIcons.this.render(graphics,
                    Math.round(x + (width - ICON_SIZE) / 2f),
                    Math.round(y + (height - ICON_SIZE) / 2f),
                    alpha * (button.active ? 1 : 0.45f));
            }
        };
    }

    public void renderCentered(GuiGraphics graphics, BoxWidget button) {
        render(graphics, button.getX() + (button.getWidth() - ICON_SIZE) / 2,
            button.getY() + (button.getHeight() - ICON_SIZE) / 2, button.active ? 1 : 0.45f);
    }

    private void render(GuiGraphics graphics, int x, int y, float alpha) {
        float[] previousColor = RenderSystem.getShaderColor().clone();
        graphics.setColor(1, 1, 1, alpha);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + ICON_SIZE / 2f, y + ICON_SIZE / 2f, 0);
            if (rotation != 0) {
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
            }
            if (nativeIcon != null) {
                nativeIcon.render(graphics, -ICON_SIZE / 2, -ICON_SIZE / 2);
            } else {
                graphics.blit(TEXTURE, -ICON_SIZE / 2, -ICON_SIZE / 2,
                    u, v, ICON_SIZE, ICON_SIZE, SHEET_SIZE, SHEET_SIZE);
            }
        } finally {
            graphics.pose().popPose();
            graphics.setColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
        }
    }
}
