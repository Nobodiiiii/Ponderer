package com.nododiiiii.ponderer.registry;

import com.nododiiiii.ponderer.platform.PondererServices;
import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockItem;
import com.nododiiiii.ponderer.projector.ProjectorKind;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public final class ModBlocks {

    public static final Supplier<ProjectorBlock> MINIATURE_PROJECTOR = PondererServices.REGISTRATION.registerBlock(
        "miniature_projector",
        () -> new ProjectorBlock(ProjectorKind.MINIATURE, projectorProperties()));
    public static final Supplier<ProjectorBlock> LIFE_SIZE_PROJECTOR = PondererServices.REGISTRATION.registerBlock(
        "life_size_projector",
        () -> new ProjectorBlock(ProjectorKind.LIFE_SIZE, projectorProperties()));

    public static final Supplier<Item> MINIATURE_PROJECTOR_ITEM = PondererServices.REGISTRATION.registerItem(
        "miniature_projector",
        () -> new ProjectorBlockItem(MINIATURE_PROJECTOR.get(), new Item.Properties()));
    public static final Supplier<Item> LIFE_SIZE_PROJECTOR_ITEM = PondererServices.REGISTRATION.registerItem(
        "life_size_projector",
        () -> new ProjectorBlockItem(LIFE_SIZE_PROJECTOR.get(), new Item.Properties()));

    private ModBlocks() {
    }

    public static void init() {
    }

    private static BlockBehaviour.Properties projectorProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.5F, 6.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> state.getValue(ProjectorBlock.LIT) ? 10 : 0)
            .noOcclusion();
    }
}
