package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.mixin.InputWindowElementAccessor;
import com.nododiiiii.ponderer.mixin.TextWindowElementAccessor;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.AnimatedOverlayElement;
import net.createmod.ponder.api.element.PonderElement;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ProjectorOverlayExtractor {
    private static final Set<String> SKIPPED_CLASSES = ConcurrentHashMap.newKeySet();

    private ProjectorOverlayExtractor() {
    }

    public static List<ProjectorSceneBundle.OverlayCue> extract(PonderScene scene, int localTick) {
        if (scene == null || scene.getElements().isEmpty()) {
            return List.of();
        }

        List<ProjectorSceneBundle.OverlayCue> result = new ArrayList<>();
        int fallbackLane = 0;
        for (PonderElement element : scene.getElements()) {
            if (!(element instanceof PonderOverlayElement) || !element.isVisible()) {
                continue;
            }
            if (element instanceof AnimatedOverlayElement animated && animated.getFade(0) < 1.0F / 16.0F) {
                continue;
            }

            try {
                ProjectorSceneBundle.OverlayCue cue = extractElement(element, localTick, fallbackLane);
                if (cue == null) {
                    continue;
                }
                result.add(cue);
                if (cue.anchorMode() == ProjectorSceneBundle.OverlayCue.AnchorMode.FALLBACK) {
                    fallbackLane++;
                }
            } catch (Throwable t) {
                SKIPPED_CLASSES.add(element.getClass().getName());
            }
        }

        return result;
    }

    @Nullable
    private static ProjectorSceneBundle.OverlayCue extractElement(PonderElement element, int localTick, int fallbackLane) {
        if (element instanceof TextWindowElement) {
            return extractText((TextWindowElementAccessor) element, localTick, fallbackLane);
        }
        if (element instanceof InputWindowElement) {
            return extractInput((InputWindowElementAccessor) element, localTick);
        }
        return extractUnknown(element, localTick, fallbackLane);
    }

    @Nullable
    private static ProjectorSceneBundle.OverlayCue extractText(TextWindowElementAccessor accessor,
                                                               int localTick, int fallbackLane) {
        String text = accessor.ponderer$getBakedText();
        if (text == null || text.isBlank()) {
            Supplier<String> supplier = accessor.ponderer$getTextGetter();
            text = supplier == null ? "" : supplier.get();
        }
        if (text == null || text.isBlank()) {
            return null;
        }

        PonderPalette palette = accessor.ponderer$getPalette();
        int accent = palette == null ? 0xE6FCFF : palette.getColor();
        Vec3 point = accessor.ponderer$getVec();
        List<Component> lines = List.of(Component.literal(text));
        if (point != null) {
            return ProjectorSceneBundle.OverlayCue.runtimeWorld(localTick, point, lines, accent);
        }
        return ProjectorSceneBundle.OverlayCue.runtimeFallback(localTick, fallbackLaneForY(accessor.ponderer$getY(), fallbackLane),
            lines, accent);
    }

    private static ProjectorSceneBundle.OverlayCue extractInput(InputWindowElementAccessor accessor, int localTick) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(controlActionKey(accessor.ponderer$getIcon())));

        ResourceLocation key = accessor.ponderer$getKey();
        if (key != null) {
            String shared = PonderIndex.getLangAccess().getShared(key);
            if (shared != null && !shared.isBlank()) {
                lines.add(Component.literal(shared));
            }
        }

        ItemStack item = accessor.ponderer$getItem();
        if (item != null && !item.isEmpty()) {
            lines.add(Component.translatable("ponderer.ui.projector.overlay.control.with_item",
                item.getHoverName().getString()));
        }

        return ProjectorSceneBundle.OverlayCue.runtimeWorld(localTick, accessor.ponderer$getSceneSpace(),
            List.copyOf(lines), 0x9CEBFF);
    }

    @Nullable
    private static ProjectorSceneBundle.OverlayCue extractUnknown(PonderElement element, int localTick, int fallbackLane) {
        Vec3 point = firstFieldValue(element, Vec3.class);
        List<Component> lines = new ArrayList<>();

        Component component = firstFieldValue(element, Component.class);
        if (component != null && !component.getString().isBlank()) {
            lines.add(component);
        }

        String text = firstStringLikeField(element);
        if (text != null && !text.isBlank()) {
            lines.add(Component.literal(text));
        }

        ItemStack item = firstFieldValue(element, ItemStack.class);
        if (item != null && !item.isEmpty()) {
            lines.add(Component.translatable("ponderer.ui.projector.overlay.control.with_item",
                item.getHoverName().getString()));
        }

        if (lines.isEmpty()) {
            SKIPPED_CLASSES.add(element.getClass().getName());
            return null;
        }

        if (point != null) {
            return ProjectorSceneBundle.OverlayCue.runtimeWorld(localTick, point, distinct(lines), 0xD9F4FF);
        }
        return ProjectorSceneBundle.OverlayCue.runtimeFallback(localTick, fallbackLane, distinct(lines), 0xD9F4FF);
    }

    private static int fallbackLaneForY(int y, int fallbackLane) {
        if (y <= 0) {
            return fallbackLane;
        }
        return Math.max(fallbackLane, Math.min(6, y / 32));
    }

    private static String controlActionKey(@Nullable ScreenElement icon) {
        if (icon == PonderGuiTextures.ICON_LMB) {
            return "ponderer.ui.projector.overlay.control.left_click";
        }
        if (icon == PonderGuiTextures.ICON_RMB) {
            return "ponderer.ui.projector.overlay.control.right_click";
        }
        if (icon == PonderGuiTextures.ICON_SCROLL) {
            return "ponderer.ui.projector.overlay.control.scroll";
        }
        return "ponderer.ui.projector.overlay.control.action";
    }

    @Nullable
    private static String firstStringLikeField(Object target) {
        String direct = firstFieldValue(target, String.class);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }

        Supplier<?> supplier = firstFieldValue(target, Supplier.class);
        if (supplier != null) {
            try {
                Object supplied = supplier.get();
                if (supplied instanceof String text) {
                    return text;
                }
                if (supplied instanceof Component component) {
                    return component.getString();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static <T> T firstFieldValue(Object target, Class<T> type) {
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (type.isInstance(value)) {
                        return type.cast(value);
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static List<Component> distinct(List<Component> lines) {
        if (lines.size() <= 1) {
            return List.copyOf(lines);
        }

        List<Component> result = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Component line : lines) {
            String text = line.getString();
            if (text.isBlank() || seen.contains(text)) {
                continue;
            }
            seen.add(text);
            result.add(line);
        }
        return result.isEmpty() ? Collections.emptyList() : List.copyOf(result);
    }
}
