package com.nododiiiii.ponderer.application.ui;

import com.nododiiiii.ponderer.application.ui.widgets.PlainTextListEntry;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface HeldItemButtonHost {

    void openInventoryPickerFromButton(PlainTextListEntry entry, Consumer<ItemStack> onItemPicked);
}
