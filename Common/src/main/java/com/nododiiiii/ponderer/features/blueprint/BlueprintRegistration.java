package com.nododiiiii.ponderer.features.blueprint;

import com.nododiiiii.ponderer.platform.PondererServices;
import net.minecraft.world.item.Item;
import java.util.function.Supplier;

/** Blueprint registration belongs to the feature; product initialization chooses its order. */
public final class BlueprintRegistration {
    public static final Supplier<Item> BLUEPRINT = PondererServices.REGISTRATION.registerItem("blueprint",
        () -> new BlueprintItem(new Item.Properties().stacksTo(1)));
    private BlueprintRegistration() {}
    public static void init() {}
}
