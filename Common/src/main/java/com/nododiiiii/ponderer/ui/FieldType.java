package com.nododiiiii.ponderer.ui;

/** Concise input hints shared across forms. */
public enum FieldType {
    ITEM("item"),
    BLOCK("block"),
    ENTITY("entity"),
    INGREDIENT("ingredient"),
    ITEM_TAG("item_tag"),
    NBT("nbt"),
    XYZ("xyz"),
    BLOCK_POSITION("block_position"),
    POSITION("position"),
    WORLD_POSITION("world_position"),
    UI_POSITION("ui_position"),
    VECTOR("vector"),
    ROTATION("rotation"),
    ANGLE("angle"),
    SCALE("scale"),
    SIZE("size"),
    TICKS("ticks"),
    ENTITY_LINK("entity_link"),
    SECTION_LINK("section_link"),
    STRUCTURE("structure"),
    LOCALIZED_TEXT("localized_text");

    private final String tooltipKey;

    FieldType(String name) {
        tooltipKey = "ponderer.ui.field." + name + ".tooltip";
    }

    public String tooltipKey() {
        return tooltipKey;
    }

    public static FieldType forId(IdFieldMode mode) {
        return switch (mode) {
            case ITEM -> ITEM;
            case BLOCK -> BLOCK;
            case ENTITY -> ENTITY;
            case INGREDIENT -> INGREDIENT;
        };
    }
}
