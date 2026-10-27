package com.nododiiiii.ponderer.interfaces.sandbox;

import com.mojang.blaze3d.platform.InputConstants;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import com.nododiiiii.ponderer.scene.interfaces.InterfaceStepSpecs;
import com.nododiiiii.ponderer.ui.UiAnchorCoords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Script-driven and author-driven mutations of a live session (design doc 12, 13): slot and
 * data writes, context patches, synthesized actions, and the built-in vanilla crafting
 * adapter that stands in for the missing server. Everything runs through
 * {@link SandboxSession#dispatch}, so it inherits the scope, the firewall and the degrade path.
 */
public final class SessionOps {

    private SessionOps() {
    }

    // ---- State writes -------------------------------------------------------------------

    /** Writes a real item (or clears the slot); {@code expect} mismatches skip the write (layout check). */
    public static void writeSlot(SandboxSession session, InterfaceStepSpecs.SlotWrite write) {
        AbstractContainerMenu menu = session.menu();
        if (menu == null) {
            return;
        }
        session.dispatch("set_interface_slots", () -> {
            if (write.index() >= menu.slots.size()) {
                session.report("set_interface_slots: slot " + write.index() + " out of range (" + menu.slots.size() + ")");
                return null;
            }
            Slot slot = menu.slots.get(write.index());
            if ((write.expectX() != null && write.expectX() != slot.x) || (write.expectY() != null && write.expectY() != slot.y)) {
                session.report("set_interface_slots: slot " + write.index() + " is at " + slot.x + "," + slot.y
                    + ", expected " + write.expectX() + "," + write.expectY() + "; skipped");
                return null;
            }
            ItemStack stack = write.item() == null ? ItemStack.EMPTY : ContextApplier.decodeItem(session, write.item());
            menu.setItem(write.index(), menu.getStateId(), stack);
            updateCrafting(session);
            return null;
        }, null);
    }

    public static void writeData(SandboxSession session, int id, int value) {
        AbstractContainerMenu menu = session.menu();
        if (menu == null) {
            return;
        }
        session.dispatch("set_interface_data", () -> {
            try {
                menu.setData(id, value);
            } catch (IndexOutOfBoundsException e) {
                session.report("set_interface_data: data slot " + id + " does not exist");
            }
            return null;
        }, null);
    }

    public static void patchContext(SandboxSession session, InterfaceStepSpecs.ContextPatch patch) {
        session.dispatch("set_interface_context", () -> {
            SandboxPlayer player = session.player();
            if (patch.gameMode() != null) {
                GameType type = GameType.byName(patch.gameMode().toLowerCase(Locale.ROOT), GameType.SURVIVAL);
                session.gameMode().setLocalMode(type, null);
                type.updatePlayerAbilities(player.getAbilities());
            }
            if (patch.xpLevel() != null) {
                player.experienceLevel = Math.max(0, patch.xpLevel());
            }
            if (patch.xpProgress() != null) {
                player.experienceProgress = patch.xpProgress();
            }
            Inventory inventory = player.getInventory();
            if (patch.selectedSlot() != null) {
                inventory.selected = Math.max(0, Math.min(8, patch.selectedSlot()));
            }
            if (patch.inventory() != null) {
                inventory.clearContent();
                for (InterfaceDescriptor.InventoryItem entry : patch.inventory()) {
                    ItemStack stack = ContextApplier.decodeItem(session, entry.item());
                    if (!stack.isEmpty()) {
                        ContextApplier.putInventory(session, inventory, entry.slot(), stack);
                    }
                }
            }
            // Menus such as enchanting and anvils re-evaluate costs against the player on change.
            AbstractContainerMenu menu = session.menu();
            if (menu != null) {
                menu.broadcastChanges();
            }
            return null;
        }, null);
    }

    /** Puts {@code stack} into menu slot {@code index} as a player would (JEI drop in try mode). */
    public static boolean placeStack(SandboxSession session, int index, ItemStack stack) {
        AbstractContainerMenu menu = session.menu();
        if (menu == null || index < 0 || index >= menu.slots.size() || !session.canInteract()) {
            return false;
        }
        return session.dispatch("place", () -> {
            Slot slot = menu.slots.get(index);
            if (!slot.mayPlace(stack)) {
                return false;
            }
            slot.setByPlayer(stack.copyWithCount(Math.min(stack.getCount(), slot.getMaxStackSize(stack))));
            updateCrafting(session);
            return true;
        }, false);
    }

    public static ItemStack slotItem(SandboxSession session, int index) {
        AbstractContainerMenu menu = session.menu();
        if (menu == null || index < 0 || index >= menu.slots.size()) {
            return ItemStack.EMPTY;
        }
        return menu.slots.get(index).getItem();
    }

    /** Registry id and count of slot {@code index}, for {@code await_interface} goals. */
    public static boolean goalMet(SandboxSession session, InterfaceStepSpecs.Goal goal) {
        ItemStack stack = slotItem(session, goal.slot());
        String id = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return InterfaceStepSpecs.goalMet(goal, id, stack.getCount());
    }

    // ---- Actions ------------------------------------------------------------------------

    /** Screen-space point of {@code target} on the current interface, or null when it cannot be resolved. */
    @Nullable
    public static double[] point(SandboxSession session, @Nullable InterfaceStepSpecs.Target target) {
        if (target == null) {
            return null;
        }
        int[] rect = session.guiRect();
        if (rect == null) {
            return null;
        }
        if (target.slot() != null) {
            AbstractContainerMenu menu = session.menu();
            if (menu == null || target.slot() < 0 || target.slot() >= menu.slots.size()) {
                return null;
            }
            Slot slot = menu.slots.get(target.slot());
            return new double[]{rect[0] + slot.x + 8, rect[1] + slot.y + 8};
        }
        if (target.gui() != null) {
            return new double[]{
                rect[0] + UiAnchorCoords.decodeToPixelX(target.gui()[0], Math.max(1, rect[2])),
                rect[1] + UiAnchorCoords.decodeToPixelYTopLeft(target.gui()[1], Math.max(1, rect[3]))};
        }
        AbstractWidget widget = findWidget(session.currentScreen(), target.widget());
        return widget == null ? null
            : new double[]{widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0};
    }

    /** The first half of a drag: pick the stack up from the source slot. */
    public static void beginDrag(SandboxSession session, InterfaceStepSpecs.Action action) {
        Integer from = action.target() == null ? null : action.target().slot();
        if (from != null) {
            slotClick(session, from, action.button(), ClickType.PICKUP);
        }
    }

    /**
     * Performs {@code action} (the second half, for drags). Slot actions go through the
     * sandbox game mode exactly like a screen click, so the mod's own {@code clicked} and
     * {@code quickMoveStack} decide the outcome; everything else is synthesized screen input.
     */
    public static void perform(SandboxSession session, InterfaceStepSpecs.Action action) {
        Integer slot = action.target() == null ? null : action.target().slot();
        switch (action.kind()) {
            case CLICK, RIGHT_CLICK, PRESS_BUTTON -> {
                if (slot != null) {
                    slotClick(session, slot, action.button(), ClickType.PICKUP);
                } else {
                    click(session, point(session, action.target()), action.button());
                }
            }
            case QUICK_MOVE -> {
                if (slot != null) {
                    slotClick(session, slot, 0, ClickType.QUICK_MOVE);
                }
            }
            case DRAG -> {
                Integer to = action.to() == null ? null : action.to().slot();
                if (to != null) {
                    slotClick(session, to, action.button(), ClickType.PICKUP);
                    AbstractContainerMenu menu = session.menu();
                    if (menu != null && !menu.getCarried().isEmpty() && slot != null) {
                        // Whatever did not fit goes back to the source, like a player finishing a move.
                        slotClick(session, slot, 0, ClickType.PICKUP);
                    }
                } else {
                    click(session, point(session, action.to()), action.button());
                }
            }
            case SWAP_HOTBAR -> {
                if (slot != null) {
                    slotClick(session, slot, action.hotbar(), ClickType.SWAP);
                }
            }
            case THROW -> {
                if (slot != null) {
                    slotClick(session, slot, action.button() == 1 ? 1 : 0, ClickType.THROW);
                }
            }
            case HOVER -> {
            }
            case TYPE_TEXT -> {
                String text = action.text();
                Screen screen = session.currentScreen();
                if (text != null && screen != null) {
                    session.dispatch("type_text", () -> {
                        text.codePoints().forEach(cp -> {
                            for (char c : Character.toChars(cp)) {
                                screen.charTyped(c, 0);
                            }
                        });
                        return null;
                    }, null);
                }
            }
            case SCROLL -> {
                double[] p = point(session, action.target());
                Screen screen = session.currentScreen();
                if (p != null && screen != null) {
                    session.dispatch("scroll", () -> screen.mouseScrolled(p[0], p[1], 0, action.scroll()), false);
                }
            }
            case KEY -> {
                int key = keyCode(action.key());
                Screen screen = session.currentScreen();
                if (key != InputConstants.UNKNOWN.getValue() && screen != null) {
                    session.dispatch("key", () -> {
                        screen.keyPressed(key, 0, 0);
                        screen.keyReleased(key, 0, 0);
                        return null;
                    }, null);
                }
            }
        }
        updateCrafting(session);
    }

    private static void slotClick(SandboxSession session, int index, int button, ClickType type) {
        AbstractContainerMenu menu = session.menu();
        if (menu == null || index < 0 || index >= menu.slots.size()) {
            session.report("interface_action: slot " + index + " out of range");
            return;
        }
        session.dispatch("interface_action", () -> {
            session.gameMode().handleInventoryMouseClick(menu.containerId, index, button, type, session.player());
            return null;
        }, null);
    }

    private static void click(SandboxSession session, @Nullable double[] p, int button) {
        Screen screen = session.currentScreen();
        if (p == null || screen == null) {
            session.report("interface_action: target could not be resolved");
            return;
        }
        session.click(p[0], p[1], button);
    }

    @Nullable
    private static AbstractWidget findWidget(@Nullable Screen screen, @Nullable String label) {
        if (screen == null || label == null) {
            return null;
        }
        String needle = label.toLowerCase(Locale.ROOT);
        List<GuiEventListener> children = new ArrayList<>(screen.children());
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget && widget.visible
                && widget.getMessage().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                return widget;
            }
        }
        return null;
    }

    static int keyCode(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return InputConstants.UNKNOWN.getValue();
        }
        String name = key.trim().toLowerCase(Locale.ROOT);
        try {
            return InputConstants.getKey(name.startsWith("key.") ? name : "key.keyboard." + name).getValue();
        } catch (IllegalArgumentException e) {
            return InputConstants.UNKNOWN.getValue();
        }
    }

    // ---- Built-in vanilla adapter: crafting result -------------------------------------

    /**
     * {@code CraftingMenu#slotChangedCraftingGrid} only computes results on the server (it casts
     * to {@code ServerPlayer}), so the sandbox computes the result slot from the client recipe
     * manager instead (design doc 12.2).
     */
    public static void updateCrafting(SandboxSession session) {
        AbstractContainerMenu menu = session.menu();
        int size;
        if (menu instanceof CraftingMenu) {
            size = 3;
        } else if (menu instanceof InventoryMenu) {
            size = 2;
        } else {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || menu.slots.size() < 1 + size * size) {
            return;
        }
        session.dispatch("crafting", () -> {
            List<ItemStack> items = new ArrayList<>(size * size);
            for (int i = 1; i <= size * size; i++) {
                items.add(menu.slots.get(i).getItem());
            }
            CraftingInput input = CraftingInput.of(size, size, items);
            ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
            Slot resultSlot = menu.slots.get(0);
            if (!ItemStack.matches(resultSlot.getItem(), result)) {
                resultSlot.set(result);
            }
            return null;
        }, null);
    }
}
