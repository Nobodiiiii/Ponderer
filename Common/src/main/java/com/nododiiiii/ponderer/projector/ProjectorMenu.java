package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class ProjectorMenu extends AbstractContainerMenu {

    public static final int SOURCE_SLOT = 0;
    public static final int SCREEN_WIDTH = 320;
    public static final int INVENTORY_PANEL_X = 72;
    public static final int INVENTORY_PANEL_Y = 184;
    public static final int INVENTORY_PANEL_WIDTH = 176;
    public static final int INVENTORY_PANEL_HEIGHT = 96;
    public static final int SOURCE_SLOT_X = 75;
    public static final int SOURCE_SLOT_Y = 32;
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_SIZE = 36;
    private static final int PLAYER_INV_X = INVENTORY_PANEL_X + 8;
    private static final int PLAYER_INV_Y = INVENTORY_PANEL_Y + 14;
    private static final int HOTBAR_Y = INVENTORY_PANEL_Y + 72;

    private final BlockPos projectorPos;
    private final ProjectorKind projectorKind;
    private final Container sourceContainer;
    @Nullable
    private final ProjectorBlockEntity projector;

    public ProjectorMenu(int id, Inventory inventory, FriendlyByteBuf extraData) {
        this(id, inventory, findProjector(inventory.player.level(), extraData.readBlockPos()));
    }

    public ProjectorMenu(int id, Inventory inventory, ProjectorBlockEntity projector) {
        this(ModMenuTypes.PROJECTOR.get(), id, inventory, projector);
    }

    private ProjectorMenu(MenuType<?> type, int id, Inventory inventory, @Nullable ProjectorBlockEntity projector) {
        super(type, id);
        this.projector = projector;
        this.projectorPos = projector == null ? BlockPos.ZERO : projector.getBlockPos();
        this.projectorKind = projector == null ? ProjectorKind.MINIATURE : projector.getProjectorKind();
        this.sourceContainer = projector == null ? new SimpleContainer(1) : projector;

        addSlot(new Slot(sourceContainer, SOURCE_SLOT, SOURCE_SLOT_X, SOURCE_SLOT_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerSlots(inventory);
    }

    public static ProjectorMenu create(int id, Inventory inventory, ProjectorBlockEntity projector) {
        return new ProjectorMenu(id, inventory, projector);
    }

    public BlockPos projectorPos() {
        return projectorPos;
    }

    public ProjectorKind projectorKind() {
        return projectorKind;
    }

    @Nullable
    public ProjectorBlockEntity projector() {
        return projector;
    }

    public ItemStack sourceItem() {
        return sourceContainer.getItem(SOURCE_SLOT).copy();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index == SOURCE_SLOT) {
            if (!moveItemStackTo(original, PLAYER_INV_START, PLAYER_INV_START + PLAYER_INV_SIZE, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(original, SOURCE_SLOT, SOURCE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return sourceContainer.stillValid(player);
    }

    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new Slot(inventory, hotbar, PLAYER_INV_X + hotbar * 18, HOTBAR_Y));
        }
    }

    @Nullable
    private static ProjectorBlockEntity findProjector(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ProjectorBlockEntity projector) {
            return projector;
        }
        return null;
    }
}
