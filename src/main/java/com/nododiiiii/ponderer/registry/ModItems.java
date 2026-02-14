package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.BlueprintItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Ponderer.MODID);

    public static final DeferredHolder<Item, Item> BLUEPRINT = ITEMS.register("blueprint",
        () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
