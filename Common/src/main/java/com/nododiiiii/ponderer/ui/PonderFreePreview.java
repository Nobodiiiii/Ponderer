package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Temporary camera controls; the scripted camera and its animation chasers stay untouched. */
public final class PonderFreePreview {
    private final PonderUI ui;
    private PonderSpeedButton button;
    private PonderScene scene;
    private float partialTicks;
    private float originalZoom;
    private float pitch;
    private float yaw;
    private double panX;
    private double panY;
    private int dragButton = -1;

    public PonderFreePreview(PonderUI ui) {
        this.ui = ui;
    }

    public PonderSpeedButton createButton(PonderZoomSlider slider) {
        Component label = Component.translatable("ponderer.ui.preview.pause");
        int width = Minecraft.getInstance().font.width(label) + 20;
        button = new PonderSpeedButton(slider.getBarRight() - width,
            slider.getPreviewButtonY(), width, label,
            Component.translatable("ponderer.ui.preview.shortcut"), this::toggle);
        button.active = canToggle();
        button.visible = button.active;
        updateButton();
        return button;
    }

    public boolean isActive() {
        return scene != null;
    }

    public boolean appliesTo(PonderScene target) {
        return scene == target;
    }

    private boolean canToggle() {
        return !PickState.isActive() && !NbtExpandedPickState.isActive() && !InterfaceSlotEditState.isActive();
    }

    public void toggle() {
        if (isActive()) {
            stop();
        } else if (canToggle()) {
            partialTicks = PonderUI.getPartialTicks();
            originalZoom = ((PonderUiZoomAccess) ui).ponderer$getUserZoom();
            scene = ui.getActiveScene();
            PonderUI.ponderPartialTicksPaused = partialTicks;
            ((PonderUIAccessor) ui).ponderer$setIdentifyMode(true);
            ui.setFocused(null);
            ui.setDragging(false);
            updateButton();
        }
    }

    public void stop() {
        if (!isActive()) {
            return;
        }
        scene.deselect();
        scene = null;
        pitch = yaw = 0;
        panX = panY = 0;
        dragButton = -1;
        ((PonderUiZoomAccess) ui).ponderer$setUserZoom(originalZoom);
        ((PonderUIAccessor) ui).ponderer$setIdentifyMode(false);
        updateButton();
    }

    public void tick() {
        if (button != null) {
            button.active = canToggle();
            button.visible = button.active;
        }
        if (!isActive()) {
            return;
        }
        if (scene != ui.getActiveScene() || !canToggle()) {
            stop();
            return;
        }
        ((PonderUIAccessor) ui).ponderer$setIdentifyMode(true);
    }

    private void updateButton() {
        if (button != null) {
            button.setLabel(Component.translatable(isActive()
                ? "ponderer.ui.preview.resume" : "ponderer.ui.preview.pause"));
            button.withCustomTheme(isActive() ? PonderButton.COLOR_CLICK : PonderButton.COLOR_IDLE,
                isActive() ? PonderButton.COLOR_CLICK : PonderButton.COLOR_HOVER, null, null);
        }
    }

    public float partialTicks() {
        return partialTicks;
    }

    public float pitch() {
        return pitch;
    }

    public float yaw() {
        return yaw;
    }

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    private boolean isOverWidget(double x, double y) {
        return ui.children().stream().anyMatch(child -> child.isMouseOver(x, y));
    }

    public boolean mouseClicked(double x, double y, int mouseButton) {
        if (!isActive() || y >= ui.height - 80 || isOverWidget(x, y)
            || (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseButton != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            return false;
        }
        dragButton = mouseButton;
        scene.deselect();
        ui.setFocused(null);
        return true;
    }

    public boolean mouseDragged(int mouseButton, double deltaX, double deltaY) {
        if (!isActive() || dragButton != mouseButton) {
            return false;
        }
        if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float basePitch = scene.getTransform().xRotation.getValue(partialTicks);
            pitch = Mth.clamp(basePitch + pitch - (float) deltaY, -89, 89) - basePitch;
            yaw = Mth.wrapDegrees(yaw + (float) deltaX);
        } else {
            panX += deltaX;
            panY += deltaY;
        }
        return true;
    }

    public boolean mouseReleased(int mouseButton) {
        if (dragButton != mouseButton) {
            return false;
        }
        dragButton = -1;
        return true;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (!isActive()) {
            return false;
        }
        if (delta != 0 && (ui.children().stream().anyMatch(child -> child instanceof PonderZoomSlider
            && child.isMouseOver(x, y)) || (y < ui.height - 80 && !isOverWidget(x, y)))) {
            PonderUiZoomAccess zoom = (PonderUiZoomAccess) ui;
            zoom.ponderer$setUserZoom((float) (zoom.ponderer$getUserZoom() * Math.pow(1.1, delta)));
        }
        // The wheel must never turn a page while previewing.
        return true;
    }

    public void renderButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (button == null || !button.visible || !button.isVisible() || !button.isMouseOver(mouseX, mouseY)) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 500);
        graphics.renderComponentTooltip(ui.getFontRenderer(),
            List.of(Component.translatable("ponderer.ui.preview.shortcut").withStyle(ChatFormatting.WHITE),
                Component.translatable("ponderer.ui.preview.rotate").withStyle(ChatFormatting.GRAY),
                Component.translatable("ponderer.ui.preview.pan").withStyle(ChatFormatting.GRAY),
                Component.translatable("ponderer.ui.preview.zoom").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        graphics.pose().popPose();
    }
}
