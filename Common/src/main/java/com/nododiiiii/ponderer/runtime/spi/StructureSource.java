package com.nododiiiii.ponderer.runtime.spi;

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Host-owned resources. Opened streams belong to the caller; null means missing. */
public interface StructureSource {
    StructureTemplate loadSchematic(ResourceLocation id);
    @Nullable InputStream open(String path, @Nullable String pack) throws IOException;
}
