package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.compat.jei.JeiCompat;
import com.nododiiiii.ponderer.ponder.DslScene;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

    public static boolean hasRuntimeBindingForSlot(int slotIndex) {
        return RUNTIME_BINDINGS.containsKey(slotIndex);
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

        // Keep overlay ingredients above native slot item rendering (including NBT-driven stacks).
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

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

        graphics.pose().popPose();
    }

    public static void renderTooltip(GuiGraphics graphics, Screen screen, int mouseX, int mouseY) {
        DslScene.InterfaceSlotBinding hoveredBinding = findBindingAt(screen, mouseX, mouseY);
        if (hoveredBinding == null || hoveredBinding.ingredientId == null || hoveredBinding.ingredientId.isBlank()) {
            return;
        }

        ItemStack stack = resolveTooltipStack(hoveredBinding);
        if (!stack.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
            return;
        }

        List<Component> lines = new ArrayList<>();
        if (hoveredBinding.ingredientKind != null && !hoveredBinding.ingredientKind.isBlank()) {
            lines.add(Component.literal(hoveredBinding.ingredientKind + ": " + hoveredBinding.ingredientId));
        } else {
            lines.add(Component.literal(hoveredBinding.ingredientId));
        }
        graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    private static Slot findMatchingSlot(List<Slot> slots, DslScene.InterfaceSlotBinding binding) {
        if (binding == null || binding.slotIndex == null || binding.slotIndex < 0 || binding.slotIndex >= slots.size()) {
            return null;
        }
        if (binding.slotX == null || binding.slotY == null) {
            return null;
        }
        Slot slot = slots.get(binding.slotIndex);
        if (!slot.isActive() || slot.x != binding.slotX || slot.y != binding.slotY) {
            return null;
        }
        return slot;
    }

    @Nullable
    private static DslScene.InterfaceSlotBinding findBindingAt(Screen screen, double mouseX, double mouseY) {
        Slot slot = findSlotAt(screen, mouseX, mouseY);
        if (slot == null) {
            return null;
        }

        Map<Integer, DslScene.InterfaceSlotBinding> bindings = InterfaceSlotEditState.isActive()
            ? InterfaceSlotEditState.getBindingsForRender()
            : RUNTIME_BINDINGS;

        DslScene.InterfaceSlotBinding binding = bindings.get(slot.index);
        if (binding == null) {
            return null;
        }
        if (binding.slotX != null && binding.slotY != null
            && (binding.slotX != slot.x || binding.slotY != slot.y)) {
            return null;
        }
        return binding;
    }

    private static ItemStack resolveTooltipStack(DslScene.InterfaceSlotBinding binding) {
        if (binding == null || binding.ingredientId == null || binding.ingredientId.isBlank()) {
            return ItemStack.EMPTY;
        }
        if (binding.ingredientKind != null
            && !binding.ingredientKind.isBlank()
            && !"item".equalsIgnoreCase(binding.ingredientKind)) {
            return ItemStack.EMPTY;
        }

        ResourceLocation id = ResourceLocation.tryParse(binding.ingredientId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
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
            if (!slot.isActive()) {
                continue;
            }
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
