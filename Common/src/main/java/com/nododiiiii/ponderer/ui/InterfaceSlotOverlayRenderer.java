package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders persisted JEI ingredients over mirrored container slots and keeps the
 * current runtime slot snapshot for show_interface scenes.
 */
public final class InterfaceSlotOverlayRenderer {
    private static final LinkedHashMap<Integer, DslScene.InterfaceSlotBinding> RUNTIME_BINDINGS = new LinkedHashMap<>();

    private InterfaceSlotOverlayRenderer() {
    }

    public static void clearRuntimeBindings() {
        RUNTIME_BINDINGS.clear();
    }

    public static void applyStep(DslScene.DslStep step) {
        RUNTIME_BINDINGS.clear();
        if (step == null || step.interfaceSlots == null) {
            return;
        }
        for (DslScene.InterfaceSlotBinding binding : step.interfaceSlots) {
            if (binding == null || binding.slotIndex == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
                continue;
            }
            RUNTIME_BINDINGS.put(binding.slotIndex,
                new DslScene.InterfaceSlotBinding(
                    binding.slotIndex,
                    binding.slotX,
                    binding.slotY,
                    binding.ingredientId,
                    binding.ingredientKind));
        }
    }

    public static void render(GuiGraphics graphics, Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }

        Map<Integer, DslScene.InterfaceSlotBinding> bindings = InterfaceSlotEditState.isActive()
            ? InterfaceSlotEditState.getBindingsForRender()
            : RUNTIME_BINDINGS;
        if (bindings.isEmpty()) {
            return;
        }

        ContainerBounds bounds = readContainerBounds(container);
        List<Slot> slots = container.getMenu().slots;
        for (DslScene.InterfaceSlotBinding binding : bindings.values()) {
            Slot slot = findMatchingSlot(slots, binding);
            if (slot == null) {
                continue;
            }
            ScreenElement element = JeiCompat.resolveIngredientById(binding.ingredientId, binding.ingredientKind);
            if (element == null) {
                continue;
            }
            element.render(graphics, bounds.left() + slot.x, bounds.top() + slot.y);
        }
    }

    private static Slot findMatchingSlot(List<Slot> slots, DslScene.InterfaceSlotBinding binding) {
        if (binding == null || binding.slotIndex == null || binding.slotIndex < 0 || binding.slotIndex >= slots.size()) {
            return null;
        }
        if (binding.slotX == null || binding.slotY == null) {
            return null;
        }
        Slot slot = slots.get(binding.slotIndex);
        if (slot.x != binding.slotX || slot.y != binding.slotY) {
            return null;
        }
        return slot;
    }

    public static ContainerBounds readContainerBounds(AbstractContainerScreen<?> container) {
        Integer left = readIntField(AbstractContainerScreen.class, container, "leftPos");
        Integer top = readIntField(AbstractContainerScreen.class, container, "topPos");
        Integer width = readIntField(AbstractContainerScreen.class, container, "imageWidth");
        Integer height = readIntField(AbstractContainerScreen.class, container, "imageHeight");

        int imageWidth = width != null && width > 0 ? width : 176;
        int imageHeight = height != null && height > 0 ? height : 166;
        int resolvedLeft = left != null ? left : Math.max(0, (container.width - imageWidth) / 2);
        int resolvedTop = top != null ? top : Math.max(0, (container.height - imageHeight) / 2);

        if (resolvedLeft == 0 && resolvedTop == 0 && (container.width > imageWidth || container.height > imageHeight)) {
            resolvedLeft = Math.max(0, (container.width - imageWidth) / 2);
            resolvedTop = Math.max(0, (container.height - imageHeight) / 2);
        }
        return new ContainerBounds(resolvedLeft, resolvedTop, imageWidth, imageHeight);
    }

    @Nullable
    public static Slot findSlotAt(Screen screen, double mouseX, double mouseY) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return null;
        }

        ContainerBounds bounds = readContainerBounds(container);
        for (Slot slot : container.getMenu().slots) {
            int left = bounds.left() + slot.x;
            int top = bounds.top() + slot.y;
            if (mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16) {
                return slot;
            }
        }
        return null;
    }

    private static Integer readIntField(Class<?> owner, Object target, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public record ContainerBounds(int left, int top, int width, int height) {
    }
}
