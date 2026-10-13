package com.nododiiiii.ponderer.ui;

import com.nododiiiii.ponderer.ui.catnip.PlainTextListEntry;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface HeldItemButtonHost {

    void openInventoryPickerFromButton(PlainTextListEntry entry, Consumer<ItemStack> onItemPicked);
}
