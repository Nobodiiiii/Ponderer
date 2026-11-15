package com.nododiiiii.ponderer.runtime.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.catnip.levelWrappers.SchematicChunkSource;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

/** Exposes map-backed scene blocks to consumers that read chunk sections directly. */
public final class SceneChunkSource extends SchematicChunkSource {
    private static final int MAX_CACHED_CHUNKS = 64;
    private final PonderLevel world;
    private final Map<Long, SceneChunk> chunks = new LinkedHashMap<>(16, 0.75f, true);

    private SceneChunkSource(PonderLevel world) {
        super(world);
        this.world = world;
    }

    public static void install(PonderLevel world) {
        if (!(world.getChunkSource() instanceof SceneChunkSource)) {
            world.setChunkSource(new SceneChunkSource(world));
        }
    }

    @Override
    public LevelChunk getChunk(int x, int z, ChunkStatus status, boolean required) {
        return getChunk(x, z);
    }

    @Override
    public LevelChunk getChunk(int x, int z) {
        long key = ChunkPos.asLong(x, z);
        SceneChunk chunk = chunks.get(key);
        if (chunk == null) {
            if (chunks.size() >= MAX_CACHED_CHUNKS) {
                chunks.remove(chunks.keySet().iterator().next());
            }
            chunk = new SceneChunk(new ChunkPos(x, z));
            chunks.put(key, chunk);
        }
        chunk.synchronizeBlocks();
        return chunk;
    }

    @Override
    public int getLoadedChunksCount() {
        return chunks.size();
    }

    private final class SceneChunk extends LevelChunk {
        private Long2ObjectOpenHashMap<BlockState> previous = new Long2ObjectOpenHashMap<>();
        private Long2ObjectOpenHashMap<BlockState> current = new Long2ObjectOpenHashMap<>();

        private SceneChunk(ChunkPos pos) {
            super(world, pos);
        }

        private void synchronizeBlocks() {
            current.clear();
            ChunkPos chunkPos = getPos();
            for (BlockPos localPos : world.getBlockMap().keySet()) {
                BlockPos pos = localPos.offset(world.anchor);
                if ((pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z) {
                    capture(pos);
                }
            }
            // SchematicLevel can expose an implicit floor outside its block map.
            if (!world.renderMode) {
                int y = world.getBounds().minY() - 1 + world.anchor.getY();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    capture(pos.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z));
                }
            }
            // Read through PonderLevel on every chunk query: restore/restoreBlocks
            // mutate its map directly, and masks/entity ticks change visible states.
            for (var entry : previous.long2ObjectEntrySet()) {
                if (!current.containsKey(entry.getLongKey())) {
                    writeSection(entry.getLongKey(), Blocks.AIR.defaultBlockState());
                }
            }
            for (var entry : current.long2ObjectEntrySet()) {
                if (previous.get(entry.getLongKey()) != entry.getValue()) {
                    writeSection(entry.getLongKey(), entry.getValue());
                }
            }
            var swap = previous;
            previous = current;
            current = swap;
        }

        private void capture(BlockPos pos) {
            if (world.isOutsideBuildHeight(pos)) return;
            BlockState state = world.getBlockState(pos);
            if (state != Blocks.AIR.defaultBlockState()) current.put(pos.asLong(), state);
        }

        private void writeSection(long pos, BlockState state) {
            int y = BlockPos.getY(pos);
            // Use the normal section setter so block/fluid counts and optimizer
            // metadata (including oversized collision shapes) stay consistent.
            getSection(getSectionIndex(y)).setBlockState(BlockPos.getX(pos) & 15, y & 15,
                BlockPos.getZ(pos) & 15, state);
            heightmaps.clear();
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return world.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public FluidState getFluidState(int x, int y, int z) {
            return getFluidState(new BlockPos(x, y, z));
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos, EntityCreationType creation) {
            return world.getBlockEntity(pos);
        }

        @Override
        public BlockState setBlockState(BlockPos pos, BlockState state, boolean moving) {
            BlockState old = world.getBlockState(pos);
            world.setBlock(pos, state, 0);
            synchronizeBlocks();
            return old;
        }
    }
}
