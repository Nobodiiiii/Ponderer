package com.nododiiiii.ponderer.support.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Converts a structure NBT file into a text description for LLM consumption.
 * Parses the raw CompoundTag directly (no virtual world needed).
 */
public class StructureDescriber {

    /** Result of describing a structure. */
    public record StructureInfo(int sizeX, int sizeY, int sizeZ, String textDescription, List<String> blockTypes) {}

    /** One non-air block retained for structured agent queries. */
    public record BlockInfo(int x, int y, int z, String id, Map<String, String> properties,
                            @Nullable String nbt) {
    }

    /** Parsed data shared by the legacy description and the agent tools. */
    public record StructureSnapshot(StructureInfo info, List<BlockInfo> blocks) {
    }

    private record PaletteEntry(String id, Map<String, String> properties, String display) {
    }

    /**
     * Read and describe a structure NBT file.
     */
    public static StructureInfo describe(Path nbtFile) throws IOException {
        return inspect(nbtFile).info();
    }

    /**
     * Read and describe a structure from an InputStream.
     */
    public static StructureInfo describe(InputStream inputStream) throws IOException {
        return inspect(inputStream).info();
    }

    public static StructureSnapshot inspect(Path nbtFile) throws IOException {
        try (InputStream is = Files.newInputStream(nbtFile)) {
            return inspect(is);
        }
    }

    public static StructureSnapshot inspect(InputStream inputStream) throws IOException {
        CompoundTag nbt = NbtIo.read(
            new DataInputStream(new BufferedInputStream(new GZIPInputStream(inputStream))),
            new NbtAccounter(0x20000000L, 512)
        );
        return inspectFromNbt(nbt);
    }

    private static StructureSnapshot inspectFromNbt(CompoundTag nbt) {
        // Read size
        ListTag sizeTag = nbt.getList("size", Tag.TAG_INT);
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);

        // Read palette
        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        List<PaletteEntry> paletteEntries = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String id = entry.getString("Name");
            Map<String, String> properties = new LinkedHashMap<>();
            if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
                CompoundTag props = entry.getCompound("Properties");
                for (String key : props.getAllKeys()) {
                    properties.put(key, props.getString(key));
                }
            }
            Map<String, String> immutableProperties = Map.copyOf(properties);
            paletteEntries.add(new PaletteEntry(
                id, immutableProperties, formatBlock(id, immutableProperties)));
        }

        // Read blocks, group by Y layer
        ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
        Map<Integer, List<String>> layerBlocks = new LinkedHashMap<>();
        Set<String> blockTypes = new LinkedHashSet<>();
        List<BlockInfo> blockInfos = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            int stateIdx = block.getInt("state");
            PaletteEntry paletteEntry = stateIdx >= 0 && stateIdx < paletteEntries.size()
                ? paletteEntries.get(stateIdx)
                : new PaletteEntry("unknown", Map.of(), "unknown");

            // Skip air blocks
            if (paletteEntry.id().equals("minecraft:air")) continue;

            layerBlocks.computeIfAbsent(y, k -> new ArrayList<>())
                .add("[" + x + "," + y + "," + z + "] " + paletteEntry.display());

            blockTypes.add(paletteEntry.id());
            String blockNbt = block.contains("nbt", Tag.TAG_COMPOUND)
                ? block.getCompound("nbt").toString()
                : null;
            blockInfos.add(new BlockInfo(
                x, y, z, paletteEntry.id(), paletteEntry.properties(), blockNbt));
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

        StructureInfo info = new StructureInfo(
            sizeX, sizeY, sizeZ, sb.toString(), List.copyOf(blockTypes));
        return new StructureSnapshot(info, List.copyOf(blockInfos));
    }

    private static String formatBlock(String id, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return id;
        }
        StringBuilder out = new StringBuilder(id).append("[");
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) {
                out.append(",");
            }
            out.append(property.getKey()).append("=").append(property.getValue());
            first = false;
        }
        return out.append("]").toString();
    }
}
