package com.nododiiiii.ponderer.runtime.interfaces.sandbox;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Visual extents may exceed the menu rectangle used for native slot coordinates. */
final class PanelRenderBounds {
    private static final String CREATE_CONTAINER = "com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen";
    private static final String PACKAGE_PORT = "com.simibubi.create.content.logistics.packagePort.PackagePortScreen";
    private static final ClassValue<CreateLayout> CREATE_LAYOUTS = new ClassValue<>() {
        @Override protected CreateLayout computeValue(Class<?> type) {
            Method extraAreas = null;
            Field frogMode = null;
            int postboxHeader = 24, frogportHeader = 17;
            boolean packagePort = false;
            for (Class<?> base = type; base != null; base = base.getSuperclass()) {
                if (base.getName().equals(CREATE_CONTAINER)) {
                    try { extraAreas = base.getMethod("getExtraAreas"); }
                    catch (ReflectiveOperationException | SecurityException ignored) {}
                }
                if (!base.getName().equals(PACKAGE_PORT)) continue;
                packagePort = true;
                try {
                    frogMode = base.getDeclaredField("frogMode");
                    if (!frogMode.trySetAccessible()) frogMode = null;
                } catch (ReflectiveOperationException | SecurityException ignored) {}
                try {
                    Class<?> textures = Class.forName("com.simibubi.create.foundation.gui.AllGuiTextures", false, base.getClassLoader());
                    Method height = textures.getMethod("getHeight");
                    postboxHeader = ((Number) height.invoke(textures.getField("POSTBOX_HEADER").get(null))).intValue();
                    frogportHeader = ((Number) height.invoke(textures.getField("FROGPORT_HEADER").get(null))).intValue();
                } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {}
            }
            return new CreateLayout(extraAreas, packagePort, frogMode, postboxHeader, frogportHeader);
        }
    };

    private PanelRenderBounds() {}

    static int[] resolve(Screen screen, int[] nativeRect) {
        Rect2i bounds = new Rect2i(nativeRect[0], nativeRect[1], nativeRect[2], nativeRect[3]);
        CreateLayout create = CREATE_LAYOUTS.get(screen.getClass());
        if (create.packagePort) {
            int header = create.postboxHeader;
            try {
                if (create.frogMode != null && create.frogMode.getBoolean(screen)) header = create.frogportHeader;
            } catch (ReflectiveOperationException ignored) {}
            // Create 1.21.1 PackagePortScreen.renderBg draws the header above topPos and
            // PLAYER_INVENTORY at topPos + imageHeight - inventoryHeight + 8.
            bounds = union(bounds, new Rect2i(nativeRect[0], nativeRect[1] - header,
                nativeRect[2], nativeRect[3] + header + 8));
        }
        if (create.extraAreas != null) {
            try {
                if (create.extraAreas.invoke(screen) instanceof Iterable<?> areas)
                    for (Object area : areas) if (area instanceof Rect2i rect) bounds = union(bounds, rect);
            } catch (ReflectiveOperationException ignored) {}
        }
        // Include permanent controls outside the declared GUI, without adding hover tooltips.
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible)
                bounds = union(bounds, new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        }
        return new int[]{bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()};
    }

    private static Rect2i union(Rect2i a, Rect2i b) {
        if (b.getWidth() <= 0 || b.getHeight() <= 0) return a;
        int left = Math.min(a.getX(), b.getX()), top = Math.min(a.getY(), b.getY());
        return new Rect2i(left, top, Math.max(a.getX() + a.getWidth(), b.getX() + b.getWidth()) - left,
            Math.max(a.getY() + a.getHeight(), b.getY() + b.getHeight()) - top);
    }

    private record CreateLayout(Method extraAreas, boolean packagePort, Field frogMode, int postboxHeader, int frogportHeader) {}
}
