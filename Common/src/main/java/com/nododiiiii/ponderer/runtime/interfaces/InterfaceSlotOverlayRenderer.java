package com.nododiiiii.ponderer.runtime.interfaces;

import com.nododiiiii.ponderer.runtime.viewer.PonderRuntimeZLayers;
import com.nododiiiii.ponderer.runtime.interfaces.InterfaceExtensions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nododiiiii.ponderer.runtime.interfaces.platform.InterfacePlatform;
import com.nododiiiii.ponderer.scene.DslScene;
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

    /** Adds a display-only ingredient over slot {@code slotIndex} ({@code set_interface_slots.ghosts}). */
    public static void putGhost(int slotIndex, String ingredientId, @Nullable String ingredientKind) {
        RUNTIME_BINDINGS.put(slotIndex, new DslScene.InterfaceSlotBinding(slotIndex, ingredientId, ingredientKind));
    }

    public static void render(GuiGraphics graphics, Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }

        Map<Integer, DslScene.InterfaceSlotBinding> bindings = InterfaceExtensions.editing().isActive()
            ? InterfaceExtensions.editing().ghostBindings()
            : RUNTIME_BINDINGS;
        if (bindings.isEmpty()) {
            return;
        }

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.INTERFACE_OVERLAY_LAYER);
        RenderSystem.disableDepthTest();

        ContainerBounds bounds = readContainerBounds(container);
        List<Slot> slots = container.getMenu().slots;
        for (DslScene.InterfaceSlotBinding binding : bindings.values()) {
            Slot slot = findMatchingSlot(slots, binding);
            if (slot == null) {
                continue;
            }
            ScreenElement element = InterfaceExtensions.ingredients().resolve(binding.ingredientId, binding.ingredientKind);
            if (element == null) {
                continue;
            }
            element.render(graphics, bounds.left() + slot.x, bounds.top() + slot.y);
        }

        graphics.pose().popPose();
        graphics.flush();
    }

    public static void renderJeiVisibilityTooltip(GuiGraphics graphics, Screen screen, int mouseX, int mouseY) {
        InterfaceExtensions.editing().renderHint(graphics, screen, mouseX, mouseY);
    }

    public static void renderTooltip(GuiGraphics graphics, Screen screen, int mouseX, int mouseY) {
        DslScene.InterfaceSlotBinding hoveredBinding = findBindingAt(screen, mouseX, mouseY);
        if (hoveredBinding == null || hoveredBinding.ingredientId == null || hoveredBinding.ingredientId.isBlank()) {
            return;
        }

        ItemStack stack = resolveTooltipStack(hoveredBinding);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PonderRuntimeZLayers.TOOLTIP_LAYER);
        RenderSystem.disableDepthTest();

        if (!stack.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
            graphics.pose().popPose();
            graphics.flush();
            return;
        }

        List<Component> lines = new ArrayList<>();
        if (hoveredBinding.ingredientKind != null && !hoveredBinding.ingredientKind.isBlank()) {
            lines.add(Component.literal(hoveredBinding.ingredientKind + ": " + hoveredBinding.ingredientId));
        } else {
            lines.add(Component.literal(hoveredBinding.ingredientId));
        }
        graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
        graphics.pose().popPose();
        graphics.flush();
    }

    private static Slot findMatchingSlot(List<Slot> slots, DslScene.InterfaceSlotBinding binding) {
        if (binding == null || binding.slotIndex == null || binding.slotIndex < 0 || binding.slotIndex >= slots.size()) {
            return null;
        }
        Slot slot = slots.get(binding.slotIndex);
        // Positions are an optional layout check; ghosts from set_interface_slots carry none.
        boolean positioned = binding.slotX != null && binding.slotY != null;
        if (!slot.isActive() || (positioned && (slot.x != binding.slotX || slot.y != binding.slotY))) {
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

        Map<Integer, DslScene.InterfaceSlotBinding> bindings = InterfaceExtensions.editing().isActive()
            ? InterfaceExtensions.editing().ghostBindings()
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
        int[] rect = InterfacePlatform.get().containerRect(container);
        if (rect != null && rect[2] > 1 && rect[3] > 1) {
            return new ContainerBounds(rect[0], rect[1], rect[2], rect[3]);
        }
        int imageWidth = 176;
        int imageHeight = 166;
        return new ContainerBounds(Math.max(0, (container.width - imageWidth) / 2),
            Math.max(0, (container.height - imageHeight) / 2), imageWidth, imageHeight);
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

    public record ContainerBounds(int left, int top, int width, int height) {
    }
}
