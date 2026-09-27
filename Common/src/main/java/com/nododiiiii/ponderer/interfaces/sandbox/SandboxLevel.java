package com.nododiiiii.ponderer.interfaces.sandbox;

import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.math.BBHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side level that only holds the blocks of one interface session.
 *
 * <p>Inherits Catnip's {@link SchematicLevel}: {@code isClientSide} mirrors the real client
 * level (true), entity and player queries are empty and block entities are never ticked.
 * On top of that the sandbox follows the block entity lifecycle rule (I10): block entities
 * are constructed, bound to this level and loaded from NBT, but never {@code onLoad},
 * {@code setRemoved}, ticked or notified of neighbour changes.
 */
public class SandboxLevel extends SchematicLevel {

    public SandboxLevel(Level realLevel) {
        super(realLevel);
        // Without renderMode SchematicLevel reports dirt below the lowest placed block.
        this.renderMode = true;
    }

    /** Places a block state without creating, loading or notifying anything. */
    public void placeState(BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        bounds = BBHelper.encapsulate(bounds, key);
        blocks.put(key, state);
        BlockEntity previous = blockEntities.remove(key);
        if (previous != null) {
            renderedBlockEntities.remove(previous);
        }
    }

    /** Binds an already-loaded block entity to this level. */
    public void putBlockEntity(BlockPos pos, BlockEntity blockEntity) {
        BlockPos key = pos.immutable();
        blockEntity.setLevel(this);
        BlockEntity previous = blockEntities.put(key, blockEntity);
        if (previous != null) {
            renderedBlockEntities.remove(previous);
        }
        renderedBlockEntities.add(blockEntity);
    }

    @Override
    public void blockEntityChanged(BlockPos pos) {
    }

    @Override
    public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {
    }

    @Override
    public void updateNeighborsAt(BlockPos pos, Block block) {
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block blockType, Direction skipSide) {
    }

    @Override
    public void neighborChanged(BlockPos pos, Block block, BlockPos fromPos) {
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
    }
}
