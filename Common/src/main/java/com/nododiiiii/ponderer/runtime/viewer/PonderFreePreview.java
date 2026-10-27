package com.nododiiiii.ponderer.runtime.viewer;

import com.nododiiiii.ponderer.runtime.viewer.PonderPreviewTooltip;
import com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import javax.annotation.Nullable;

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
    private boolean textVisible = true;

    public PonderFreePreview(PonderUI ui) {
        this.ui = ui;
    }

    public PonderSpeedButton createButton(PonderZoomSlider slider) {
        Component label = Component.translatable("ponderer.ui.preview.pause");
        int width = Minecraft.getInstance().font.width(label) + 20;
        button = new PreviewButton(slider.getBarRight() - width,
            slider.getPreviewButtonY(), width, label);
        button.active = canToggle();
        button.visible = button.active;
        updateButton();
        return button;
    }

    @Nullable
    static PonderFreePreview find(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof PonderFreePreview.PreviewButton previewButton) {
                return previewButton.owner();
            }
        }
        return null;
    }

    private final class PreviewButton extends PonderSpeedButton {
        private PreviewButton(int x, int y, int width, Component label) {
            super(x, y, width, label, Component.translatable("ponderer.ui.preview.shortcut"),
                PonderFreePreview.this::toggle);
        }

        private PonderFreePreview owner() {
            return PonderFreePreview.this;
        }
    }

    public int getTextPanelTop() {
        // The hint row above the button also separates its bottom from the panel's outer border.
        return button.getY() + button.getHeight() + PonderSmallHint.getRowHeight(ui);
    }

    public boolean isActive() {
        return scene != null;
    }

    public boolean appliesTo(PonderScene target) {
        return scene == target;
    }

    public boolean isTextVisible() {
        return textVisible;
    }

    public void toggleTextVisibility() {
        if (isActive()) {
            textVisible = !textVisible;
        }
    }

    private boolean canToggle() {
        return !com.nododiiiii.ponderer.runtime.api.PlaybackInputLocks.isLocked();
    }

    public void toggle() {
        if (isActive()) {
            stop();
        } else if (canToggle()) {
            textVisible = true;
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

    public void refreshAfterSeek() {
        if (isActive()) {
            // Seeking lands on a whole tick; discard the previous frame's interpolation.
            partialTicks = 0;
            PonderUI.ponderPartialTicksPaused = 0;
        }
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
        return ui.children().stream().anyMatch(child -> !(child instanceof PonderPreviewTooltip)
            && child.isMouseOver(x, y));
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
        graphics.renderComponentTooltip(ui.getFontRenderer(), controlsTooltip(textVisible), mouseX, mouseY);
        graphics.pose().popPose();
    }

    static List<Component> controlsTooltip(boolean textVisible) {
        return controlsTooltip("ponderer.ui.preview.shortcut", textVisible);
    }

    static List<Component> controlsTooltip(String titleKey, boolean textVisible) {
        return List.of(Component.translatable(titleKey).withStyle(ChatFormatting.WHITE),
            Component.translatable("ponderer.ui.preview.rotate").withStyle(ChatFormatting.GRAY),
            Component.translatable("ponderer.ui.preview.pan").withStyle(ChatFormatting.GRAY),
            Component.translatable("ponderer.ui.preview.zoom").withStyle(ChatFormatting.GRAY),
            Component.translatable(textVisible ? "ponderer.ui.preview.hide_text"
                : "ponderer.ui.preview.show_text").withStyle(ChatFormatting.GRAY));
    }
}
