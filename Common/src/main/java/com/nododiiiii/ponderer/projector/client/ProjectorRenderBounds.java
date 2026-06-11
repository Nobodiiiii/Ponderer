package com.nododiiiii.ponderer.projector.client;

import com.nododiiiii.ponderer.projector.ProjectorBlock;
import com.nododiiiii.ponderer.projector.ProjectorBlockEntity;
import com.nododiiiii.ponderer.projector.ProjectorKind;
import com.nododiiiii.ponderer.projector.ProjectorSceneBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ProjectorRenderBounds {

    private static final float MINIATURE_FILL = 0.85F;
    private static final float MINIATURE_Y_OFFSET = 1.06F;
    private static final double CULL_PADDING = 1.0D;
    private static final Map<BlockPos, CachedBounds> CACHE = new HashMap<>();
    @Nullable
    private static Level cachedLevel;

    private ProjectorRenderBounds() {
    }

    public static AABB estimate(ProjectorBlockEntity blockEntity) {
        Level level = Minecraft.getInstance().level;
        if (level != cachedLevel) {
            CACHE.clear();
            cachedLevel = level;
        }

        AABB fallback = new AABB(blockEntity.getBlockPos()).inflate(CULL_PADDING);
        if (!blockEntity.hasRenderableScene()) {
            return fallback;
        }

        List<String> sceneKeys = blockEntity.getSceneKeys();
        if (sceneKeys.isEmpty()) {
            sceneKeys = ProjectorClientSceneResolver.sceneKeysFor(blockEntity.getSourceItem());
        }
        if (sceneKeys.isEmpty()) {
            return fallback;
        }

        BlockPos blockPos = blockEntity.getBlockPos().immutable();
        String sceneKey = String.join("\n", sceneKeys);
        Direction facing = blockEntity.getBlockState().getValue(ProjectorBlock.FACING);
        ProjectorKind kind = blockEntity.getProjectorKind();
        BlockPos anchor = blockEntity.getAnchorPos();

        CachedBounds cached = CACHE.get(blockPos);
        if (cached != null
            && cached.matches(sceneKey, kind, facing, anchor)) {
            return cached.bounds();
        }

        ProjectorSceneBundle bundle = ProjectorSceneBundle.compile(sceneKeys);
        if (bundle != null) {
            AABB estimated = toWorldBounds(blockEntity, bundle.combinedBounds()).inflate(CULL_PADDING);
            CACHE.put(blockPos, new CachedBounds(sceneKey, kind, facing, anchor, estimated));
            return estimated;
        }

        BoundingBox fallbackBounds = ProjectorSceneBounds.estimate(sceneKeys);
        if (fallbackBounds == null) {
            CACHE.put(blockPos, new CachedBounds(sceneKey, kind, facing, anchor, fallback));
            return fallback;
        }

        AABB estimated = toWorldBounds(blockEntity, fallbackBounds).inflate(CULL_PADDING);
        CACHE.put(blockPos, new CachedBounds(sceneKey, kind, facing, anchor, estimated));
        return estimated;
    }

    private static AABB toWorldBounds(ProjectorBlockEntity blockEntity, BoundingBox bounds) {
        Direction facing = blockEntity.getBlockState().getValue(ProjectorBlock.FACING);
        float rotationDegrees = switch (facing) {
            case SOUTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        Vec3 worldOrigin;
        Vec3 sceneTranslate;
        float scale;

        if (blockEntity.getProjectorKind() == ProjectorKind.MINIATURE) {
            int spanX = Math.max(1, bounds.getXSpan());
            int spanY = Math.max(1, bounds.getYSpan());
            int spanZ = Math.max(1, bounds.getZSpan());
            scale = MINIATURE_FILL / Math.max(spanX, Math.max(spanY, spanZ));

            double centerX = (bounds.minX() + bounds.maxX() + 1) * 0.5D;
            double centerY = bounds.minY();
            double centerZ = (bounds.minZ() + bounds.maxZ() + 1) * 0.5D;

            BlockPos blockPos = blockEntity.getBlockPos();
            worldOrigin = new Vec3(
                blockPos.getX() + 0.5D,
                blockPos.getY() + MINIATURE_Y_OFFSET,
                blockPos.getZ() + 0.5D);
            sceneTranslate = new Vec3(-centerX, -centerY, -centerZ);
        } else {
            BlockPos projectorPos = blockEntity.getBlockPos();
            BlockPos anchor = blockEntity.getAnchorPos() == null ? projectorPos : blockEntity.getAnchorPos();
            worldOrigin = new Vec3(anchor.getX(), anchor.getY(), anchor.getZ());
            sceneTranslate = Vec3.ZERO;
            scale = 1.0F;
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        double[] xs = {bounds.minX(), bounds.maxX() + 1.0D};
        double[] ys = {bounds.minY(), bounds.maxY() + 1.0D};
        double[] zs = {bounds.minZ(), bounds.maxZ() + 1.0D};

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 translated = new Vec3(x, y, z).add(sceneTranslate);
                    Vec3 scaled = new Vec3(translated.x * scale, translated.y * scale, translated.z * scale);
                    Vec3 rotated = rotateY(scaled, rotationDegrees);
                    Vec3 world = worldOrigin.add(rotated);

                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 rotateY(Vec3 vec, float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double x = vec.x * cos + vec.z * sin;
        double z = vec.z * cos - vec.x * sin;
        return new Vec3(x, vec.y, z);
    }

    private record CachedBounds(String sceneKey, ProjectorKind kind, Direction facing,
                                @Nullable BlockPos anchor, AABB bounds) {
        boolean matches(String otherSceneKey, ProjectorKind otherKind, Direction otherFacing,
                        @Nullable BlockPos otherAnchor) {
            return sceneKey.equals(otherSceneKey)
                && kind == otherKind
                && facing == otherFacing
                && Objects.equals(anchor, otherAnchor);
        }
    }
}
