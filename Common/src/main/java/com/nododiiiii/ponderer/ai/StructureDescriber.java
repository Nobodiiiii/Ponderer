package com.nododiiiii.ponderer.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Converts a structure NBT file into a text description for LLM consumption.
 * Parses the raw CompoundTag directly (no virtual world needed).
 */
public class StructureDescriber {

    /** Result of describing a structure. */
    public record StructureInfo(int sizeX, int sizeY, int sizeZ, String textDescription, List<String> blockTypes) {}

    /**
     * Read and describe a structure NBT file.
     */
    public static StructureInfo describe(Path nbtFile) throws IOException {
        try (InputStream is = Files.newInputStream(nbtFile)) {
            return describe(is);
        }
    }

    /**
     * Read and describe a structure from an InputStream.
     */
    public static StructureInfo describe(InputStream inputStream) throws IOException {
        CompoundTag nbt = NbtIo.read(
            new DataInputStream(new BufferedInputStream(new GZIPInputStream(inputStream))),
            new NbtAccounter(0x20000000L)
        );
        return describeFromNbt(nbt);
    }

    private static StructureInfo describeFromNbt(CompoundTag nbt) {
        // Read size
        ListTag sizeTag = nbt.getList("size", Tag.TAG_INT);
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);

        // Read palette
        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        List<String> paletteNames = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String name = entry.getString("Name");
            if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag props = entry.getCompound("Properties");
                if (!props.isEmpty()) {
                    StringBuilder sb = new StringBuilder(name).append("[");
                    boolean first = true;
                    for (String key : props.getAllKeys()) {
                        if (!first) sb.append(",");
                        sb.append(key).append("=").append(props.getString(key));
                        first = false;
                    }
                    sb.append("]");
                    name = sb.toString();
                }
            }
            paletteNames.add(name);
        }

        // Read blocks, group by Y layer
        ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
        Map<Integer, List<String>> layerBlocks = new LinkedHashMap<>();
        List<String> blockTypes = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            int stateIdx = block.getInt("state");
            String blockName = stateIdx < paletteNames.size() ? paletteNames.get(stateIdx) : "unknown";

            // Skip air blocks
            if (blockName.equals("minecraft:air")) continue;

            layerBlocks.computeIfAbsent(y, k -> new ArrayList<>())
                .add("[" + x + "," + y + "," + z + "] " + blockName);

            // Collect unique block type names (without properties)
            String baseName = blockName.contains("[") ? blockName.substring(0, blockName.indexOf('[')) : blockName;
            if (!blockTypes.contains(baseName)) {
                blockTypes.add(baseName);
            }
        }

        // Build text description
        StringBuilder sb = new StringBuilder();
        sb.append("Structure size: ").append(sizeX).append("x").append(sizeY).append("x").append(sizeZ).append("\n");
        sb.append("Block types: ").append(String.join(", ", blockTypes)).append("\n");

        for (int y = 0; y < sizeY; y++) {
            List<String> layer = layerBlocks.get(y);
            if (layer == null || layer.isEmpty()) {
                sb.append("--- Y=").append(y).append(" (empty) ---\n");
                continue;
            }
            sb.append("--- Y=").append(y).append(" ---\n");
            for (String entry : layer) {
                sb.append("  ").append(entry).append("\n");
            }
        }

        return new StructureInfo(sizeX, sizeY, sizeZ, sb.toString(), blockTypes);
    }
}
