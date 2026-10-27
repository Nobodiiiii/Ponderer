package com.nododiiiii.ponderer.editor.screen;

import com.nododiiiii.ponderer.editor.screen.widgets.PlainTextListEntry;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface HeldItemButtonHost {

    void openInventoryPickerFromButton(PlainTextListEntry entry, Consumer<ItemStack> onItemPicked);
}
