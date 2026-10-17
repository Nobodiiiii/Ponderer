package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.mixin.PonderUIAccessor;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    private WorldSectionElement hoveredSection;
    private BlockPos hoveredPos;
    private Component hoveredName;
    private double nearestHitDistance;

    public PonderFreePreview(PonderUI ui) {
        this.ui = ui;
    }

    public PonderSpeedButton createButton(PonderZoomSlider slider) {
        Component label = Component.translatable("ponderer.ui.preview.pause");
        int width = Minecraft.getInstance().font.width(label) + 12;
        button = new PonderSpeedButton(slider.getX() + slider.getWidth() - width,
            slider.getY() + slider.getHeight() + 6, width, label,
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
        hoveredSection = null;
        hoveredPos = null;
        hoveredName = null;
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
        hoveredName = null;
        scene.deselect();
        ui.setFocused(null);
        return true;
    }

    /** Use the same section ray traces as PonderScene, including the base plate and excluding misses. */
    public void updateHoveredBlock(double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        hoveredSection = null;
        hoveredPos = null;
        hoveredName = null;
        scene.deselect();
        if (dragButton != -1 || mouseY >= ui.height - 80 || isOverWidget(mouseX, mouseY)) {
            return;
        }
        PonderScene.SceneTransform transform = scene.getTransform();
        Vec3 from = transform.screenToScene(mouseX, mouseY, 1000, partialTicks);
        Vec3 to = transform.screenToScene(mouseX, mouseY, -100, partialTicks);
        nearestHitDistance = Double.POSITIVE_INFINITY;
        scene.forEachVisible(WorldSectionElement.class, section -> {
            var hit = section.rayTrace(scene.getWorld(), from, to);
            if (hit == null || hit.getSecond().getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockPos pos = hit.getSecond().getBlockPos();
            if (!scene.getWorld().getBounds().isInside(pos) || scene.getWorld().getBlockState(pos).isAir()) {
                return;
            }
            double distance = hit.getFirst().distanceToSqr(from);
            if (distance < nearestHitDistance) {
                nearestHitDistance = distance;
                hoveredSection = section;
                hoveredPos = pos;
            }
        });
        if (hoveredSection != null) {
            hoveredSection.selectBlock(hoveredPos);
            hoveredName = scene.getWorld().getBlockState(hoveredPos).getBlock().getName();
        }
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

    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.PONDER_BUTTON_LAYER + 500);
        if (button != null && button.visible && button.isVisible() && button.isMouseOver(mouseX, mouseY)) {
            graphics.renderComponentTooltip(ui.getFontRenderer(),
                List.of(Component.translatable("ponderer.ui.preview.shortcut").withStyle(ChatFormatting.WHITE),
                    Component.translatable("ponderer.ui.preview.rotate").withStyle(ChatFormatting.GRAY),
                    Component.translatable("ponderer.ui.preview.pan").withStyle(ChatFormatting.GRAY),
                    Component.translatable("ponderer.ui.preview.zoom").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (isActive() && dragButton == -1 && mouseY < ui.height - 80
            && !isOverWidget(mouseX, mouseY) && hoveredName != null) {
            graphics.renderTooltip(ui.getFontRenderer(), hoveredName, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }
}
