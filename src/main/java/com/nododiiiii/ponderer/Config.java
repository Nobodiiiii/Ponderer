package com.nododiiiii.ponderer;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> BLUEPRINT_CARRIER_ITEM = BUILDER
        .comment("Which item activates the Blueprint selection tool.",
                 "Set to a different item (e.g. 'create:schematic_and_quill') to piggyback on it.",
                 "When set to anything other than 'ponderer:blueprint', or when Create is loaded,",
                 "the built-in Blueprint item will not appear in the creative tab.")
        .define("blueprintCarrierItem", "minecraft:paper");

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
