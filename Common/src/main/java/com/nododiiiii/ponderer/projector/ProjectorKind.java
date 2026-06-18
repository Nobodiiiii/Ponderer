package com.nododiiiii.ponderer.projector;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

public enum ProjectorKind {
    MINIATURE,
    LIFE_SIZE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "block.ponderer." + serializedName() + "_projector";
    }

    public boolean requiresAnchor() {
        return this == LIFE_SIZE;
    }

    public static ProjectorKind fromState(BlockState state) {
        if (state.getBlock() instanceof ProjectorBlock projectorBlock) {
            return projectorBlock.kind();
        }
        return MINIATURE;
    }
}
