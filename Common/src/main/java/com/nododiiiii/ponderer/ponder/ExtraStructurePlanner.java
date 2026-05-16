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
import java.util.List;
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
        if (blocks.isEmpty()) {
            return List.of();
        }

        List<BlockPos> rotatedPositions = new ArrayList<>(blocks.size());
        List<BlockState> rotatedStates = new ArrayList<>(blocks.size());
        List<CompoundTag> blockNbts = new ArrayList<>(blocks.size());

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            if (pos.size() < 3) {
                continue;
            }
            int stateIdx = entry.getInt("state");
            if (stateIdx < 0 || stateIdx >= palette.length) {
                continue;
            }
            BlockState state = palette[stateIdx];
            if (state == null) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (skipAir && key != null && SKIPPED_BLOCK_IDS.contains(key.toString())) {
                continue;
            }

            BlockPos rotatedPos = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)).rotate(rotation);
            BlockState rotatedState = state.rotate(rotation);

            CompoundTag patch = null;
            if (entry.contains("nbt", Tag.TAG_COMPOUND)) {
                CompoundTag raw = entry.getCompound("nbt").copy();
                for (String absoluteKey : ABSOLUTE_POS_NBT_KEYS) {
                    raw.remove(absoluteKey);
                }
                if (!raw.isEmpty()) {
                    patch = raw;
                }
            }

            rotatedPositions.add(rotatedPos);
            rotatedStates.add(rotatedState);
            blockNbts.add(patch);

            if (rotatedPos.getX() < minX) minX = rotatedPos.getX();
            if (rotatedPos.getY() < minY) minY = rotatedPos.getY();
            if (rotatedPos.getZ() < minZ) minZ = rotatedPos.getZ();
        }

        if (rotatedPositions.isEmpty()) {
            return List.of();
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
