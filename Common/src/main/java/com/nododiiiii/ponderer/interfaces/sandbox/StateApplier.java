package com.nododiiiii.ponderer.interfaces.sandbox;

import com.nododiiiii.ponderer.scene.interfaces.InterfaceDescriptor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Writes recorded menu state into a freshly opened sandbox menu (design doc 9, steps 5–6):
 * slot contents as if a {@code SetSlot} packet arrived, then vanilla data slots. Each write is
 * guarded individually, because menus such as Mekanism's keep no vanilla data slots and
 * CoFH's {@code setData} is a no-op.
 */
public final class StateApplier {

    private StateApplier() {
    }

    public static void apply(SandboxSession session, InterfaceDescriptor descriptor, AbstractContainerMenu menu) {
        for (InterfaceDescriptor.SlotState slot : descriptor.slots) {
            if (slot.index() < 0 || slot.index() >= menu.slots.size()) {
                session.report("slot " + slot.index() + " out of range (" + menu.slots.size() + " slots)");
                continue;
            }
            ItemStack stack = ContextApplier.decodeItem(session, slot.item());
            try {
                menu.setItem(slot.index(), menu.getStateId(), stack);
            } catch (Throwable t) {
                session.report("slot " + slot.index() + " write failed: " + t);
            }
        }
        for (InterfaceDescriptor.DataState data : descriptor.data) {
            try {
                menu.setData(data.id(), data.value());
            } catch (Throwable t) {
                session.report("data " + data.id() + " write failed: " + t);
            }
        }
    }
}
