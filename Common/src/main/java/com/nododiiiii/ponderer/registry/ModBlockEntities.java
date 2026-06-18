package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public final class ModBlockEntities {

    public static final Supplier<BlockEntityType<ProjectorBlockEntity>> PROJECTOR =
        PondererServices.REGISTRATION.registerBlockEntityType(
            "projector",
            () -> BlockEntityTypeFactory.create(
                ProjectorBlockEntity::new,
                ModBlocks.MINIATURE_PROJECTOR.get(),
                ModBlocks.LIFE_SIZE_PROJECTOR.get()));

    private ModBlockEntities() {
    }

    public static void init() {
    }
}
