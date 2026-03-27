package com.nododiiiii.ponderer.compat.jei;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Temporarily forces the JEI ingredient overlay visible for flows that need it,
 * then restores the previous toggle state when finished.
 */
public final class JeiOverlayController {
    private static boolean pushed = false;
    @Nullable
    private static Boolean previousOverlayEnabled = null;

    private JeiOverlayController() {
    }

    public static void pushEnabled() {
        if (pushed || !JeiCompat.isAvailable()) {
            return;
        }

        Object toggleState = getClientToggleState();
        if (toggleState == null) {
            return;
        }

        try {
            Boolean overlayEnabled = invokeBooleanNoArg(toggleState, "isOverlayEnabled");
            if (overlayEnabled == null) {
                return;
            }

            previousOverlayEnabled = overlayEnabled;
            if (!overlayEnabled) {
                invokeNoArg(toggleState, "toggleOverlayEnabled");
            }
            pushed = true;
        } catch (Throwable ignored) {
            pushed = false;
            previousOverlayEnabled = null;
        }
    }

    public static void popEnabled() {
        if (!pushed || !JeiCompat.isAvailable()) {
            return;
        }

        try {
            Object toggleState = getClientToggleState();
            if (toggleState != null && previousOverlayEnabled != null) {
                Boolean currentOverlayEnabled = invokeBooleanNoArg(toggleState, "isOverlayEnabled");
                if (currentOverlayEnabled != null && currentOverlayEnabled.booleanValue() != previousOverlayEnabled.booleanValue()) {
                    invokeNoArg(toggleState, "toggleOverlayEnabled");
                }
            }
        } catch (Throwable ignored) {
        } finally {
            pushed = false;
            previousOverlayEnabled = null;
        }
    }

    @Nullable
    private static Object getClientToggleState() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Method getClientToggleState = internalClass.getMethod("getClientToggleState");
            return getClientToggleState.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method m = target.getClass().getMethod(methodName);
        m.setAccessible(true);
        return m.invoke(target);
    }

    @Nullable
    private static Boolean invokeBooleanNoArg(Object target, String methodName) throws Exception {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean b ? b : null;
    }
}
