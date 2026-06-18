package com.nododiiiii.ponderer.projector;

import com.nododiiiii.ponderer.ai.StructureDescriber;
import com.nododiiiii.ponderer.ponder.DslScene;
import com.nododiiiii.ponderer.ponder.ExtraStructurePlanner;
import com.nododiiiii.ponderer.ponder.SceneRuntime;
import com.nododiiiii.ponderer.ponder.SceneStore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ProjectorSceneBounds {

    private ProjectorSceneBounds() {
    }

    @Nullable
    public static BoundingBox estimate(List<String> sceneKeys) {
        BoundingBox combined = null;
        for (String sceneKey : sceneKeys) {
            BoundingBox sceneBounds = estimate(sceneKey);
            if (sceneBounds != null) {
                combined = union(combined, sceneBounds);
            }
        }
        return combined;
    }

    @Nullable
    public static BoundingBox estimate(@Nullable String sceneKey) {
        if (sceneKey == null || sceneKey.isBlank() || ProjectorSceneKey.parseNative(sceneKey) != null) {
            return null;
        }

        DslScene scene = SceneRuntime.findByKey(sceneKey);
        if (scene == null) {
            return null;
        }

        List<DslScene.SceneSegment> segments = scene.scenes == null ? List.of() : scene.scenes;
        BoundingBox currentStructure = estimateStructureBounds(resolveDefaultSchematic(scene), scene.pack);
        BoundingBox combined = currentStructure;

        if (segments.isEmpty()) {
            return combined;
        }

        for (DslScene.SceneSegment segment : segments) {
            String explicitStructure = extractExplicitStructureRef(segment);
            if (explicitStructure != null) {
                BoundingBox nextStructure = estimateStructureBounds(resolveStructureReference(scene, explicitStructure), scene.pack);
                if (nextStructure != null) {
                    currentStructure = nextStructure;
                }
            }

            BoundingBox segmentBounds = currentStructure;
            BoundingBox scanned = scanSegmentBounds(scene, segment);
            if (scanned != null) {
                segmentBounds = union(segmentBounds, scanned);
            }
            combined = union(combined, segmentBounds);
        }

        return combined;
    }

    @Nullable
    private static BoundingBox scanSegmentBounds(DslScene scene, @Nullable DslScene.SceneSegment segment) {
        if (segment == null || segment.steps == null) {
            return null;
        }

        BoundingBox bounds = null;
        for (DslScene.DslStep step : segment.steps) {
            if (step == null) {
                continue;
            }

            bounds = union(bounds, boundsFromBlockPos(step.blockPos));
            bounds = union(bounds, boundsFromBlockPos(step.blockPos2));

            if ("encapsulate_bounds".equalsIgnoreCase(step.type)
                && step.bounds != null
                && step.bounds.size() >= 3) {
                bounds = union(bounds, new BoundingBox(
                    0,
                    0,
                    0,
                    step.bounds.get(0),
                    step.bounds.get(1),
                    step.bounds.get(2)));
            }

            if ("show_extra_structure".equalsIgnoreCase(step.type)) {
                bounds = union(bounds, estimateExtraStructureBounds(scene, step));
            }
        }

        return bounds;
    }

    @Nullable
    private static BoundingBox estimateExtraStructureBounds(DslScene scene, DslScene.DslStep step) {
        if (step.structure == null || step.structure.isBlank() || step.blockPos == null || step.blockPos.size() < 3) {
            return null;
        }

        Path file = resolveExtraStructurePath(scene, step.structure);
        if (file == null || !Files.exists(file)) {
            return null;
        }

        BlockPos base = new BlockPos(step.blockPos.get(0), step.blockPos.get(1), step.blockPos.get(2));
        int rotationDegrees = step.rotation == null ? 0 : Math.round(step.rotation);
        boolean skipAir = !Boolean.TRUE.equals(step.replaceAir);

        try {
            List<ExtraStructurePlanner.PlacedBlock> placed = ExtraStructurePlanner.plan(file, base, rotationDegrees, skipAir);
            if (placed.isEmpty()) {
                return null;
            }

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (ExtraStructurePlanner.PlacedBlock block : placed) {
                BlockPos pos = block.pos;
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static BoundingBox boundsFromBlockPos(@Nullable List<Integer> pos) {
        if (pos == null || pos.size() < 3) {
            return null;
        }
        return new BoundingBox(pos.get(0), pos.get(1), pos.get(2), pos.get(0), pos.get(1), pos.get(2));
    }

    @Nullable
    private static BoundingBox estimateStructureBounds(@Nullable ResourceLocation structureId, @Nullable String pack) {
        if (structureId == null) {
            return null;
        }

        Path structurePath = pack == null || pack.isBlank()
            ? SceneStore.getStructurePath(structureId)
            : SceneStore.resolveStructurePath(toRelativeStructurePath(structureId), pack);
        if (structurePath == null || !Files.exists(structurePath)) {
            return null;
        }

        try {
            StructureDescriber.StructureInfo info = StructureDescriber.describe(structurePath);
            int maxX = Math.max(0, info.sizeX() - 1);
            int maxY = Math.max(0, info.sizeY() - 1);
            int maxZ = Math.max(0, info.sizeZ() - 1);
            return new BoundingBox(0, 0, 0, maxX, maxY, maxZ);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Path resolveExtraStructurePath(DslScene scene, String reference) {
        String trimmed = reference.trim();
        ResourceLocation location = trimmed.contains(":")
            ? ResourceLocation.tryParse(trimmed)
            : ResourceLocation.fromNamespaceAndPath("ponderer", trimmed);
        if (location == null) {
            return null;
        }
        return SceneStore.resolveStructurePath(toRelativeStructurePath(location), scene.pack);
    }

    private static String toRelativeStructurePath(ResourceLocation location) {
        return "ponderer".equals(location.getNamespace())
            ? location.getPath()
            : location.getNamespace() + "/" + location.getPath();
    }

    private static ResourceLocation resolveDefaultSchematic(DslScene scene) {
        List<String> pool = getStructurePool(scene);
        if (!pool.isEmpty()) {
            ResourceLocation fromPool = resolveSchematic(pool.get(0));
            if (fromPool != null) {
                return fromPool;
            }
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1");
    }

    private static List<String> getStructurePool(DslScene scene) {
        if (scene.structures != null && !scene.structures.isEmpty()) {
            return scene.structures;
        }
        if (scene.structure != null && !scene.structure.isBlank()) {
            return List.of(scene.structure);
        }
        return List.of();
    }

    @Nullable
    private static String extractExplicitStructureRef(@Nullable DslScene.SceneSegment segment) {
        if (segment == null || segment.steps == null) {
            return null;
        }
        for (DslScene.DslStep step : segment.steps) {
            if (step == null || step.type == null) {
                continue;
            }
            if (!"show_structure".equalsIgnoreCase(step.type)) {
                continue;
            }
            if (step.structure != null && !step.structure.isBlank()) {
                return step.structure.trim();
            }
            return null;
        }
        return null;
    }

    @Nullable
    private static ResourceLocation resolveStructureReference(DslScene scene, String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }

        List<String> pool = getStructurePool(scene);
        try {
            int parsed = Integer.parseInt(ref);
            int index = -1;
            if (parsed >= 1 && parsed <= pool.size()) {
                index = parsed - 1;
            } else if (parsed >= 0 && parsed < pool.size()) {
                index = parsed;
            }
            if (index >= 0) {
                return resolveSchematic(pool.get(index));
            }
        } catch (NumberFormatException ignored) {
        }

        return resolveSchematic(ref);
    }

    @Nullable
    private static ResourceLocation resolveSchematic(@Nullable String structure) {
        if (structure == null || structure.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1");
        }
        if (structure.contains(":")) {
            ResourceLocation location = ResourceLocation.tryParse(structure);
            return location == null ? ResourceLocation.fromNamespaceAndPath("ponder", "debug/scene_1") : location;
        }
        return ResourceLocation.fromNamespaceAndPath("ponder", structure);
    }

    @Nullable
    private static BoundingBox union(@Nullable BoundingBox first, @Nullable BoundingBox second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new BoundingBox(
            Math.min(first.minX(), second.minX()),
            Math.min(first.minY(), second.minY()),
            Math.min(first.minZ(), second.minZ()),
            Math.max(first.maxX(), second.maxX()),
            Math.max(first.maxY(), second.maxY()),
            Math.max(first.maxZ(), second.maxZ()));
    }
}
