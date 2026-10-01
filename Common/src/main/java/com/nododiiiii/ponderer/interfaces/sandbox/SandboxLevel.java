package com.nododiiiii.ponderer.interfaces.sandbox;

import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.levelWrappers.SchematicChunkSource;
import net.createmod.catnip.math.BBHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import java.util.HashMap;
import java.util.Map;

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
    private BlockPos lookupOrigin;
    private BlockPos sandboxOrigin;
    private final Map<Long, LevelChunk> lookupChunks = new HashMap<>();

    public void setLookupOrigin(int[] source, BlockPos target) {
        lookupOrigin = source == null ? null : new BlockPos(source[0], source[1], source[2]);
        sandboxOrigin = target;
    }

    /** Preserve opaque packet bytes. Resolve their source coordinates at the world boundary. */
    public BlockPos resolvePos(BlockPos pos) {
        if (lookupOrigin != null && !near(pos, sandboxOrigin) && near(pos, lookupOrigin)) {
            return pos.subtract(lookupOrigin).offset(sandboxOrigin);
        }
        return pos;
    }

    private static boolean near(BlockPos pos, BlockPos origin) {
        return Math.abs((long) pos.getX() - origin.getX()) <= 128
            && Math.abs((long) pos.getY() - origin.getY()) <= 128
            && Math.abs((long) pos.getZ() - origin.getZ()) <= 128;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return super.getBlockState(resolvePos(pos));
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // No lazy construction or lifecycle hooks from SchematicLevel.
        return blockEntities.get(resolvePos(pos));
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        placeState(resolvePos(pos), state);
        return true;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
        return setBlock(pos, state, flags);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        BlockEntity previous = blockEntities.remove(resolvePos(pos));
        if (previous != null) renderedBlockEntities.remove(previous);
    }

    /** A chunk facade whose reads and writes never reach the real chunk cache. */
    public LevelChunk lookupChunk(int x, int z, boolean required) {
        boolean populated = blocks.keySet().stream().anyMatch(pos -> {
            if ((pos.getX() >> 4) == x && (pos.getZ() >> 4) == z) return true;
            BlockPos source = lookupOrigin == null ? pos : pos.subtract(sandboxOrigin).offset(lookupOrigin);
            return (source.getX() >> 4) == x && (source.getZ() >> 4) == z;
        });
        if (!populated && !required) return null;
        long key = ChunkPos.asLong(x, z);
        LevelChunk cached = lookupChunks.get(key);
        if (cached != null) return cached;
        LevelChunk chunk = new LevelChunk(this, new ChunkPos(x, z)) {
            @Override public BlockState getBlockState(BlockPos pos) { return SandboxLevel.this.getBlockState(pos); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos pos, EntityCreationType creation) {
                return SandboxLevel.this.getBlockEntity(pos);
            }
            @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean moving) {
                BlockState old = getBlockState(pos);
                SandboxLevel.this.setBlock(pos, state, 0);
                return old;
            }
            @Override public void setBlockEntity(BlockEntity be) { /* foreign instances are never attached */ }
            @Override public void removeBlockEntity(BlockPos pos) { SandboxLevel.this.removeBlockEntity(pos); }
        };
        if (lookupChunks.size() < 64) lookupChunks.put(key, chunk);
        return chunk;
    }

    public SandboxLevel(Level realLevel) {
        super(realLevel);
        setChunkSource(new SchematicChunkSource(this) {
            @Override
            public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
                return lookupChunk(x, z, required);
            }

            @Override
            public ChunkAccess getChunk(int x, int z) {
                return lookupChunk(x, z, true);
            }
        });
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
