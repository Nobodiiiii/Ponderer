package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.Ponderer;
import com.nododiiiii.ponderer.blueprint.BlueprintItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Ponderer.MODID);

    public static final RegistryObject<Item> BLUEPRINT = ITEMS.register("blueprint",
        () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
