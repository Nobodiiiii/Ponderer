package com.nododiiiii.ponderer.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** A read-only inventory popup anchored below the field being filled. */
final class PlayerInventoryPicker {

    static final int WIDTH = 176;
    static final int HEIGHT = 108;
    static final int GAP = 2;
    static final int SCREEN_MARGIN = 4;
    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("ponderer", "textures/gui/player_inventory.png");
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_X = 8;
    private static final int MAIN_SLOTS_Y = 18;
    private static final int HOTBAR_Y = 76;

    private final Inventory inventory;
    private final EditBox anchor;
    private final Consumer<ItemStack> onItemPicked;
    private int x;
    private int y;

    PlayerInventoryPicker(Inventory inventory, EditBox anchor,
                          Consumer<ItemStack> onItemPicked) {
        this.inventory = inventory;
        this.anchor = anchor;
        this.onItemPicked = onItemPicked;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth) {
        x = Math.max(SCREEN_MARGIN, Math.min(anchor.getX(), screenWidth - WIDTH - SCREEN_MARGIN));
        y = anchor.getY() + anchor.getHeight() + GAP;
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.blit(BACKGROUND, x, y, 0, 0, WIDTH, HEIGHT, 256, 256);
        graphics.drawString(font, Component.translatable("container.inventory"), x + 8, y + 6, 0x000000, false);
        int hoveredSlot = slotAt(mouseX, mouseY);
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            int itemX = x + SLOTS_X + slot % 9 * SLOT_SIZE;
            int itemY = y + (slot < 27 ? MAIN_SLOTS_Y + slot / 9 * SLOT_SIZE : HOTBAR_Y);
            ItemStack stack = stackAt(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, itemX, itemY);
                graphics.renderItemDecorations(font, stack, itemX, itemY);
            }
            if (slot == hoveredSlot) {
                AbstractContainerScreen.renderSlotHighlight(graphics, itemX, itemY, 0);
            }
        }
        ItemStack hovered = stackAt(hoveredSlot);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT;
    }

    void pick(double mouseX, double mouseY) {
        ItemStack stack = stackAt(slotAt(mouseX, mouseY));
        if (!stack.isEmpty()) {
            onItemPicked.accept(stack.copy());
        }
    }

    private int slotAt(double mouseX, double mouseY) {
        double localX = mouseX - x - SLOTS_X + 1;
        if (localX < 0 || localX >= 9 * SLOT_SIZE) {
            return -1;
        }
        int column = (int) localX / SLOT_SIZE;
        double mainY = mouseY - y - MAIN_SLOTS_Y + 1;
        if (mainY >= 0 && mainY < 3 * SLOT_SIZE) {
            return (int) mainY / SLOT_SIZE * 9 + column;
        }
        double hotbarY = mouseY - y - HOTBAR_Y + 1;
        return hotbarY >= 0 && hotbarY < SLOT_SIZE ? 27 + column : -1;
    }

    private ItemStack stackAt(int slot) {
        if (slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(slot < 27 ? slot + 9 : slot - 27);
    }
}
