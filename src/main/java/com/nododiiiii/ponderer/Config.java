package com.nododiiiii.ponderer;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> BLUEPRINT_CARRIER_ITEM = BUILDER
        .comment("Which item activates the Blueprint selection tool.",
                 "Set to a different item (e.g. 'create:schematic_and_quill') to piggyback on it.",
                 "When set to anything other than 'ponderer:blueprint', or when Create is loaded,",
                 "the built-in Blueprint item will not appear in the creative tab.")
        .define("blueprintCarrierItem", "minecraft:paper");

    public static final ModConfigSpec SPEC = BUILDER.build();
}
