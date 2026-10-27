package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.spi.StructureSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Internal Ponder resource handles. Scene documents and their serialized structure IDs stay unchanged. */
public final class RuntimeStructureSources {
    private record Resource(StructureSource source, ResourceLocation original) {}
    private static final AtomicLong NEXT_OWNER = new AtomicLong();
    private static final Map<ResourceLocation, Resource> RESOURCES = new ConcurrentHashMap<>();
    private RuntimeStructureSources() {}

    @Nullable
    public static StructureTemplate load(ResourceLocation handle) {
        Resource resource = RESOURCES.get(handle);
        return resource == null ? null : resource.source().loadSchematic(resource.original());
    }

    static final class Registration implements AutoCloseable {
        private final long owner = NEXT_OWNER.incrementAndGet();
        private final StructureSource source;
        private final Map<ResourceLocation, ResourceLocation> handles = new HashMap<>();
        Registration(StructureSource source) { this.source = source; }
        ResourceLocation route(ResourceLocation original) {
            return handles.computeIfAbsent(original, id -> {
                ResourceLocation handle = ResourceLocation.fromNamespaceAndPath("ponderer_runtime",
                    owner + "/" + id.getNamespace() + "/" + id.getPath());
                RESOURCES.put(handle, new Resource(source, original));
                return handle;
            });
        }
        @Override public void close() {
            handles.values().forEach(RESOURCES::remove);
            handles.clear();
        }
    }
}
