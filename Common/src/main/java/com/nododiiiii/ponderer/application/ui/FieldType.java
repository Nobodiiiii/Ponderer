package com.nododiiiii.ponderer.application.ui;

/** Shared field descriptions and example placeholders. */
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
    private final String hintKey;

    FieldType(String name) {
        tooltipKey = "ponderer.ui.field." + name + ".tooltip";
        hintKey = "ponderer.ui.field." + name + ".hint";
    }

    public String tooltipKey() {
        return tooltipKey;
    }

    public String hintKey() {
        return hintKey;
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
