package com.nododiiiii.ponderer.exporter;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

@FunctionalInterface
public interface StructureResourceSource {
    @Nullable Path find(ResourceLocation id, @Nullable String pack);
}
