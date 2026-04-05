package com.nododiiiii.ponderer.ui;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface HeldItemButtonHost {

    void useHeldItemFromButton(Consumer<ItemStack> onItemPicked);
}
