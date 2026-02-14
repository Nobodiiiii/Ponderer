package com.nododiiiii.ponderer.blueprint;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Ported from Create's SchematicExport.
 * Saves a structure selection to a .nbt file.
 */
public class BlueprintExport {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    public static ExportResult saveBlueprint(Path dir, String fileName, boolean overwrite,
                                             Level level, BlockPos first, BlockPos second) {
        BoundingBox bb = BoundingBox.fromCorners(first, second);
        BlockPos origin = new BlockPos(bb.minX(), bb.minY(), bb.minZ());
        BlockPos bounds = new BlockPos(bb.getXSpan(), bb.getYSpan(), bb.getZSpan());

        StructureTemplate structure = new StructureTemplate();
        structure.fillFromWorld(level, origin, bounds, true, Blocks.AIR);
        CompoundTag data = structure.save(new CompoundTag());
        BlueprintItem.replaceStructureVoidWithAir(data);

        if (fileName.isEmpty())
            fileName = "blueprint";
        if (!overwrite)
            fileName = findFirstValidFilename(fileName, dir, "nbt");
        if (!fileName.endsWith(".nbt"))
            fileName += ".nbt";
        Path file = dir.resolve(fileName).toAbsolutePath();

        try {
            Files.createDirectories(dir);
            boolean overwritten = Files.deleteIfExists(file);
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE)) {
                NbtIo.writeCompressed(data, out);
            }
            return new ExportResult(file, dir, fileName, overwritten, origin, bounds);
        } catch (IOException e) {
            LOGGER.error("An error occurred while saving blueprint [{}]", fileName, e);
            return null;
        }
    }

    /** Find a filename that doesn't conflict, by appending _2, _3, ... */
    public static String findFirstValidFilename(String name, Path folder, String extension) {
        int index = 0;
        String filename;
        Path filepath;
        do {
            filename = index == 0 ? name + "." + extension : name + "_" + index + "." + extension;
            filepath = folder.resolve(filename);
            index++;
        } while (Files.exists(filepath));
        return filename.substring(0, filename.length() - extension.length() - 1);
    }

    public record ExportResult(Path file, Path dir, String fileName, boolean overwritten,
                                BlockPos origin, BlockPos bounds) {
    }
}
