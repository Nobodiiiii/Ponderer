package com.nododiiiii.ponderer.runtime.spi;

import java.io.IOException;
import java.io.InputStream;
import net.createmod.ponder.foundation.registration.PonderSceneRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Resource-pack-aware default for content mods. It never needs Ponderer's workspace. */
public final class AssetStructureSource implements StructureSource {
    private final String namespace;
    private final java.util.function.Supplier<net.minecraft.server.packs.resources.ResourceManager> resources;
    public AssetStructureSource(String namespace) { this(namespace, () -> Minecraft.getInstance().getResourceManager()); }
    public AssetStructureSource(String namespace, java.util.function.Supplier<net.minecraft.server.packs.resources.ResourceManager> resources) {
        this.namespace = namespace;
        this.resources = java.util.Objects.requireNonNull(resources);
    }
    @Override public StructureTemplate loadSchematic(ResourceLocation id) {
        ResourceLocation asset = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "ponder/" + id.getPath() + ".nbt");
        try (InputStream input = resources.get().getResourceOrThrow(asset).open()) {
            return PonderSceneRegistry.loadSchematic(input);
        } catch (IOException error) { throw new IllegalArgumentException("Cannot load structure " + id + " (" + asset + ")", error); }
    }
    @Override public InputStream open(String path, String pack) throws IOException {
        int slash = path.indexOf('/');
        String owner = slash < 0 ? namespace : path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        if (!name.endsWith(".nbt")) name += ".nbt";
        if (!name.startsWith("ponder/")) name = "ponder/" + name;
        return resources.get().getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(owner, name)).open();
    }
}
