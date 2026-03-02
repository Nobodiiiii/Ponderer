package com.nododiiiii.ponderer.ui;

/**
 * Defines the type of ID that a text field expects,
 * used by JEI integration to filter and resolve ingredients.
 */
public enum IdFieldMode {
    /** Accepts only items with a block form (BlockItem). Resolves to Block registry name. */
    BLOCK,
    /** Accepts all items. Resolves to Item registry name. */
    ITEM,
    /** Accepts only spawn eggs. Resolves to EntityType registry name. */
    ENTITY,
    /** Accepts any JEI-registered ingredient (items, fluids, chemicals, etc.). Resolves to registry ID. */
    INGREDIENT
}
