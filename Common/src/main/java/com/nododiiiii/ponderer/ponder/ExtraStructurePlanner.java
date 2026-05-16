package com.nododiiiii.ponderer.ponder;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.zip.GZIPInputStream;

/**
 * Loads a vanilla structure NBT file, rotates each block (state + position) around the
 * structure origin, drops air-family blocks, and normalises the result so its bounding-box
 * minimum corner lands at the user-supplied {@code base} position.
 *
 * Used by both the runtime {@code show_extra_structure} step and the Java-module export
 * emitter, which inlines an equivalent helper into the generated mod.
 */
public final class ExtraStructurePlanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> SKIPPED_BLOCK_IDS = Set.of(
        "minecraft:air",
        "minecraft:cave_air",
        "minecraft:void_air",
        "minecraft:structure_void"
    );

    private static final Set<String> ABSOLUTE_POS_NBT_KEYS = Set.of("x", "y", "z");

    public static final class PlacedBlock {
        public final BlockPos pos;
        public final BlockState state;
        @Nullable
        public final CompoundTag nbt;

        public PlacedBlock(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
            this.pos = pos;
            this.state = state;
            this.nbt = nbt;
        }
    }

    private ExtraStructurePlanner() {
    }

    public static Rotation toVanillaRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static List<PlacedBlock> plan(Path nbtFile, BlockPos base, int rotationDegrees) throws IOException {
        return plan(nbtFile, base, rotationDegrees, true);
    }

    public static List<PlacedBlock> plan(Path nbtFile, BlockPos base, int rotationDegrees, boolean skipAir) throws IOException {
        CompoundTag root;
        try (InputStream is = Files.newInputStream(nbtFile)) {
            root = NbtIo.read(
                new DataInputStream(new BufferedInputStream(new GZIPInputStream(is))),
                new NbtAccounter(0x20000000L)
            );
        }
        return plan(root, base, rotationDegrees, skipAir);
    }

    public static List<PlacedBlock> plan(CompoundTag root, BlockPos base, int rotationDegrees) {
        return plan(root, base, rotationDegrees, true);
    }

    public static List<PlacedBlock> plan(CompoundTag root, BlockPos base, int rotationDegrees, boolean skipAir) {
        Rotation rotation = toVanillaRotation(rotationDegrees);
        BlockState[] palette = parsePalette(root.getList("palette", Tag.TAG_COMPOUND));
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);

        List<BlockPos> rotatedPositions = new ArrayList<>();
        List<BlockState> rotatedStates = new ArrayList<>();
        List<CompoundTag> blockNbts = new ArrayList<>();

        if (skipAir) {
            // Default: only place explicit non-air, non-structure_void entries.
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag entry = blocks.getCompound(i);
                BlockPos src = readEntryPos(entry);
                if (src == null) continue;
                BlockState state = resolveEntryState(entry, palette);
                if (state == null || isSkippedBlock(state)) continue;

                rotatedPositions.add(src.rotate(rotation));
                rotatedStates.add(state.rotate(rotation));
                blockNbts.add(readBlockEntityPatch(entry));
            }
        } else {
            // Replace mode: clear the entire size bounding box. Every cell that isn't an
            // explicit non-air block becomes minecraft:air (air-family / structure_void /
            // cells missing from the blocks list).
            ListTag sizeTag = root.getList("size", Tag.TAG_INT);
            if (sizeTag.size() < 3) {
                return List.of();
            }
            int sizeX = sizeTag.getInt(0);
            int sizeY = sizeTag.getInt(1);
            int sizeZ = sizeTag.getInt(2);

            Map<Long, CompoundTag> entryByPos = new HashMap<>(blocks.size());
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag entry = blocks.getCompound(i);
                BlockPos p = readEntryPos(entry);
                if (p == null) continue;
                entryByPos.put(p.asLong(), entry);
            }

            BlockState airState = Blocks.AIR.defaultBlockState();
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockPos src = new BlockPos(x, y, z);
                        CompoundTag entry = entryByPos.get(src.asLong());
                        BlockState state;
                        CompoundTag patch = null;
                        if (entry == null) {
                            state = airState;
                        } else {
                            BlockState resolved = resolveEntryState(entry, palette);
                            if (resolved == null || isSkippedBlock(resolved)) {
                                state = airState;
                            } else {
                                state = resolved;
                                patch = readBlockEntityPatch(entry);
                            }
                        }
                        rotatedPositions.add(src.rotate(rotation));
                        rotatedStates.add(state.rotate(rotation));
                        blockNbts.add(patch);
                    }
                }
            }
        }

        if (rotatedPositions.isEmpty()) {
            return List.of();
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos p : rotatedPositions) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
        }
        int offsetX = base.getX() - minX;
        int offsetY = base.getY() - minY;
        int offsetZ = base.getZ() - minZ;

        List<PlacedBlock> result = new ArrayList<>(rotatedPositions.size());
        for (int i = 0; i < rotatedPositions.size(); i++) {
            BlockPos rp = rotatedPositions.get(i);
            BlockPos world = new BlockPos(
                rp.getX() + offsetX,
                rp.getY() + offsetY,
                rp.getZ() + offsetZ
            );
            result.add(new PlacedBlock(world, rotatedStates.get(i), blockNbts.get(i)));
        }
        return result;
    }

    @Nullable
    private static BlockPos readEntryPos(CompoundTag entry) {
        ListTag pos = entry.getList("pos", Tag.TAG_INT);
        if (pos.size() < 3) return null;
        return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }

    @Nullable
    private static BlockState resolveEntryState(CompoundTag entry, BlockState[] palette) {
        int stateIdx = entry.getInt("state");
        if (stateIdx < 0 || stateIdx >= palette.length) return null;
        return palette[stateIdx];
    }

    private static boolean isSkippedBlock(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && SKIPPED_BLOCK_IDS.contains(key.toString());
    }

    @Nullable
    private static CompoundTag readBlockEntityPatch(CompoundTag entry) {
        if (!entry.contains("nbt", Tag.TAG_COMPOUND)) return null;
        CompoundTag raw = entry.getCompound("nbt").copy();
        for (String absoluteKey : ABSOLUTE_POS_NBT_KEYS) {
            raw.remove(absoluteKey);
        }
        return raw.isEmpty() ? null : raw;
    }

    private static BlockState[] parsePalette(ListTag paletteTag) {
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = parsePaletteEntry(paletteTag.getCompound(i));
        }
        return palette;
    }

    @Nullable
    private static BlockState parsePaletteEntry(CompoundTag entry) {
        String name = entry.getString("Name");
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            LOGGER.warn("show_extra_structure: unparseable palette entry '{}'", name);
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            LOGGER.warn("show_extra_structure: unknown block in palette '{}'", name);
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag props = entry.getCompound("Properties");
            var def = block.getStateDefinition();
            for (String key : props.getAllKeys()) {
                Property<?> prop = def.getProperty(key);
                if (prop != null) {
                    state = applyProperty(state, prop, props.getString(key));
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    /**
     * Group rotated/filtered blocks for animated reveal.
     *
     * The strategy follows the spec: peel the structure layer-by-layer along the entrance
     * direction's perpendicular axis, then split each layer's row into contiguous strips
     * along a single axis. Each returned group is therefore safely representable as a
     * {@code fromTo} {@link net.createmod.ponder.api.scene.Selection} that never bridges
     * an air gap.
     */
    public static List<List<BlockPos>> segmentForAnimation(List<PlacedBlock> blocks, String entranceAnimation) {
        if (blocks.isEmpty()) {
            return List.of();
        }
        String anim = entranceAnimation == null ? "" : entranceAnimation;

        ToIntFunction<BlockPos> layerKey;
        ToIntFunction<BlockPos> rowKey;
        ToIntFunction<BlockPos> stripKey;
        boolean reverseLayer;
        switch (anim) {
            case "down" -> { layerKey = BlockPos::getY; rowKey = BlockPos::getZ; stripKey = BlockPos::getX; reverseLayer = true; }
            case "up" -> { layerKey = BlockPos::getY; rowKey = BlockPos::getZ; stripKey = BlockPos::getX; reverseLayer = false; }
            case "south" -> { layerKey = BlockPos::getZ; rowKey = BlockPos::getY; stripKey = BlockPos::getX; reverseLayer = false; }
            case "north" -> { layerKey = BlockPos::getZ; rowKey = BlockPos::getY; stripKey = BlockPos::getX; reverseLayer = true; }
            case "east" -> { layerKey = BlockPos::getX; rowKey = BlockPos::getY; stripKey = BlockPos::getZ; reverseLayer = false; }
            case "west" -> { layerKey = BlockPos::getX; rowKey = BlockPos::getY; stripKey = BlockPos::getZ; reverseLayer = true; }
            case "simultaneous" -> { layerKey = p -> 0; rowKey = BlockPos::getY; stripKey = BlockPos::getX; reverseLayer = false; }
            default -> { layerKey = BlockPos::getY; rowKey = BlockPos::getZ; stripKey = BlockPos::getX; reverseLayer = false; }
        }

        List<BlockPos> positions = new ArrayList<>(blocks.size());
        for (PlacedBlock b : blocks) {
            positions.add(b.pos);
        }

        Comparator<BlockPos> baseCmp = Comparator
            .comparingInt(layerKey)
            .thenComparingInt(rowKey)
            .thenComparingInt(stripKey);
        if (reverseLayer) {
            baseCmp = Comparator.comparingInt(layerKey).reversed()
                .thenComparingInt(rowKey)
                .thenComparingInt(stripKey);
        }
        positions.sort(baseCmp);

        List<List<BlockPos>> groups = new ArrayList<>();
        List<BlockPos> currentStrip = null;
        int curLayer = Integer.MIN_VALUE;
        int curRow = Integer.MIN_VALUE;
        int curStrip = Integer.MIN_VALUE;

        for (BlockPos p : positions) {
            int lk = layerKey.applyAsInt(p);
            int rk = rowKey.applyAsInt(p);
            int sk = stripKey.applyAsInt(p);
            boolean breakStrip = currentStrip == null
                || lk != curLayer
                || rk != curRow
                || sk != curStrip + 1;
            if (breakStrip) {
                currentStrip = new ArrayList<>();
                groups.add(currentStrip);
                curLayer = lk;
                curRow = rk;
            }
            currentStrip.add(p);
            curStrip = sk;
        }
        return groups;
    }
}
