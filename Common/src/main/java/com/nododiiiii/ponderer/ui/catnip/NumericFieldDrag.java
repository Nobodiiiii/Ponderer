package com.nododiiiii.ponderer.ui.catnip;

import net.createmod.catnip.config.ui.ConfigScreenList;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/** Long-press scrubbing for numeric fields, including fields nested in Catnip list entries. */
final class NumericFieldDrag {

    private static final long HOLD_MILLIS = 300;
    private static final double PIXELS_PER_STEP = 6;
    private static final double TEXT_SELECTION_THRESHOLD = 4;

    @Nullable
    private ClippedConfigTextField pressedField;
    @Nullable
    private ClippedConfigTextField hoveredField;
    private long pressedAt;
    private double pressedX;
    private double pressedY;
    private double initialValue;
    private long appliedSteps;
    private boolean adjusting;
    private boolean selectingText;
    private long resizeCursor;
    private boolean cursorApplied;

    @Nullable
    static ClippedConfigTextField fieldAt(@Nullable ConfigScreenList.Entry entry, double mouseX, double mouseY) {
        if (entry == null) return null;
        for (var listener : entry.getGuiListeners()) {
            if (listener instanceof ClippedConfigTextField field && field.supportsNumericDrag()
                    && field.isMouseOver(mouseX, mouseY)) {
                return field;
            }
        }
        return null;
    }

    void press(@Nullable ClippedConfigTextField field, double mouseX, double mouseY) {
        cancel();
        if (field == null) return;
        double value = field.numericDragValue();
        if (!Double.isFinite(value)) return;
        pressedField = field;
        initialValue = value;
        pressedAt = Util.getMillis();
        pressedX = mouseX;
        pressedY = mouseY;
    }

    boolean drag(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (pressedField == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        // A quick horizontal drag retains the text box's selection gesture.
        if (!adjusting && Util.getMillis() - pressedAt < HOLD_MILLIS
                && Math.abs(mouseX - pressedX) > TEXT_SELECTION_THRESHOLD
                && Math.abs(mouseX - pressedX) > Math.abs(mouseY - pressedY)) {
            selectingText = true;
        }
        update(mouseY);
        if (!adjusting) {
            pressedField.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return true;
    }

    boolean release(double mouseX, double mouseY, int button) {
        if (pressedField == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        // Account for the last movement even if no frame was rendered before release.
        update(mouseY);
        pressedField.mouseReleased(mouseX, mouseY, button);
        cancel();
        return true;
    }

    void beginFrame() {
        hoveredField = null;
    }

    void hover(@Nullable ClippedConfigTextField field) {
        if (field != null) hoveredField = field;
    }

    void finishFrame(int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive() || (pressedField != null && (!pressedField.supportsNumericDrag()
                || !pressedField.isFocused() || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS))) {
            cancel();
        } else {
            update(mouseY);
        }
        setResizeCursor(mc.isWindowActive() && (adjusting || hoveredField != null));
    }

    private void update(double mouseY) {
        if (pressedField == null || selectingText || Util.getMillis() - pressedAt < HOLD_MILLIS) return;
        adjusting = true;
        long steps = (long) ((pressedY - mouseY) / PIXELS_PER_STEP);
        if (steps != appliedSteps) {
            pressedField.adjustDraggedValue(initialValue, steps);
            appliedSteps = steps;
        }
    }

    void cancel() {
        pressedField = null;
        adjusting = false;
        selectingText = false;
        appliedSteps = 0;
        setResizeCursor(false);
    }

    private void setResizeCursor(boolean resize) {
        if (resize == cursorApplied) return;
        if (resize && resizeCursor == 0) {
            resizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
            if (resizeCursor == 0) return;
        }
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), resize ? resizeCursor : 0);
        cursorApplied = resize;
    }

    void close() {
        cancel();
        if (resizeCursor != 0) {
            GLFW.glfwDestroyCursor(resizeCursor);
            resizeCursor = 0;
        }
    }
}
