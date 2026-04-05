package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

public abstract class AbstractDeclarativeGridScreen extends AbstractDeclarativeScreen {

    @Nullable
    protected BoxWidget goBack;

    protected AbstractDeclarativeGridScreen(@Nullable Screen parent, String scopeKey, String titleKey) {
        super(parent, scopeKey, titleKey);
    }

    @Override
    protected void init() {
        super.init();

        goBack = new BoxWidget(guiLeft + windowWidth + 12, guiTop + windowHeight / 2 - 10, 20, 20)
            .withPadding(2, 2)
            .withCallback(this::attemptBackToParent);
        goBack.showingElement(PonderGuiTextures.ICON_CONFIG_BACK.asStencil()
            .withElementRenderer(BoxWidget.gradientFactory.apply(goBack)));
        goBack.getToolTip().add(Component.translatable("catnip.ui.go_back_button"));
        addRenderableWidget(goBack);
    }

    protected final Component screenTitle() {
        return Component.translatable(titleKey);
    }

    @Override
    protected final void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBreadcrumb(graphics, width / 2, 15);
        renderGridWindow(graphics, mouseX, mouseY, partialTicks);
        renderStatusMessage(graphics, guiLeft, guiTop + windowHeight + 10, windowWidth);
    }

    protected abstract void renderGridWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            attemptBackToParent();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
