package com.nododiiiii.ponderer.compat.jei;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Temporarily hides JEI overlays during embedded show_interface mirror rendering.
 * Uses reflection to avoid hard JEI runtime coupling.
 */
public final class JeiOverlaySuppressor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean pushed = false;
    private static Boolean previousOverlayEnabled = null;
    private static Boolean previousBookmarkEnabledRaw = null;

    private JeiOverlaySuppressor() {
    }

    public static void push() {
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
            previousBookmarkEnabledRaw = readBookmarkEnabledRaw(toggleState);

            if (previousBookmarkEnabledRaw != null && previousBookmarkEnabledRaw) {
                invokeBooleanArg(toggleState, "setBookmarkEnabled", false);
            }

            if (overlayEnabled) {
                invokeNoArg(toggleState, "toggleOverlayEnabled");
            }

            pushed = true;
        } catch (Throwable t) {
            previousOverlayEnabled = null;
            previousBookmarkEnabledRaw = null;
            pushed = false;
            LOGGER.debug("[jei] overlay suppression push failed: {}", t.toString());
        }
    }

    public static void pop() {
        if (!pushed || !JeiCompat.isAvailable()) {
            return;
        }

        try {
            Object toggleState = getClientToggleState();
            if (toggleState != null) {
                if (previousBookmarkEnabledRaw != null) {
                    invokeBooleanArg(toggleState, "setBookmarkEnabled", previousBookmarkEnabledRaw);
                }

                if (previousOverlayEnabled != null) {
                    Boolean currentOverlayEnabled = invokeBooleanNoArg(toggleState, "isOverlayEnabled");
                    if (currentOverlayEnabled != null && currentOverlayEnabled.booleanValue() != previousOverlayEnabled.booleanValue()) {
                        invokeNoArg(toggleState, "toggleOverlayEnabled");
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[jei] overlay suppression pop failed: {}", t.toString());
        } finally {
            previousOverlayEnabled = null;
            previousBookmarkEnabledRaw = null;
            pushed = false;
        }
    }

    private static Object getClientToggleState() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Method getClientToggleState = internalClass.getMethod("getClientToggleState");
            return getClientToggleState.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean readBookmarkEnabledRaw(Object toggleState) {
        try {
            Field field = toggleState.getClass().getDeclaredField("bookmarkOverlayEnabled");
            field.setAccessible(true);
            Object value = field.get(toggleState);
            return value instanceof Boolean b ? b : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method m = target.getClass().getMethod(methodName);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Boolean invokeBooleanNoArg(Object target, String methodName) throws Exception {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean b ? b : null;
    }

    private static void invokeBooleanArg(Object target, String methodName, boolean value) throws Exception {
        Method m = target.getClass().getMethod(methodName, boolean.class);
        m.setAccessible(true);
        m.invoke(target, value);
    }
}
