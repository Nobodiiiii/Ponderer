package com.nododiiiii.ponderer.runtime.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nododiiiii.ponderer.runtime.mixin.TextureAtlasAccessor;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Keeps cached Ponder geometry visible to texture animation culling. Render thread only. */
public final class PonderAnimatedTextures {
    private static final int GRID_SIZE = 16;
    private static final Map<SuperByteBuffer, Set<ResourceLocation>> BUFFERS = new WeakHashMap<>();
    private static Map<ResourceLocation, TextureAtlasSprite> atlasSprites;
    private static final List<List<TextureAtlasSprite>> ANIMATED = new ArrayList<>();

    private PonderAnimatedTextures() {}

    public static VertexConsumer track(SuperByteBuffer buffer, VertexConsumer target) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        Map<ResourceLocation, TextureAtlasSprite> sprites = ((TextureAtlasAccessor) atlas).ponderer$getSprites();
        if (sprites != atlasSprites) {
            // Restitching changes both UVs and sprite instances, even on the same atlas object.
            atlasSprites = sprites;
            BUFFERS.clear();
            ANIMATED.clear();
            for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) ANIMATED.add(new ArrayList<>());
            for (TextureAtlasSprite sprite : sprites.values()) {
                if (sprite.contents().getUniqueFrames().limit(2).count() < 2) continue;
                for (int y = cell(sprite.getV0()); y <= cell(sprite.getV1()); y++) {
                    for (int x = cell(sprite.getU0()); x <= cell(sprite.getU1()); x++) {
                        ANIMATED.get(y * GRID_SIZE + x).add(sprite);
                    }
                }
            }
        }

        Set<ResourceLocation> used = BUFFERS.get(buffer);
        if (used != null) {
            used.forEach(atlas::getSprite);
            return target;
        }
        used = new HashSet<>();
        BUFFERS.put(buffer, used);
        return new TrackingConsumer(target, atlas, used);
    }

    private static int cell(float uv) {
        return Math.clamp((int) (uv * GRID_SIZE), 0, GRID_SIZE - 1);
    }

    private static final class TrackingConsumer implements VertexConsumer {
        private final VertexConsumer target;
        private final TextureAtlas atlas;
        private final Set<ResourceLocation> used;
        private int vertices;
        private float sumU;
        private float sumV;

        private TrackingConsumer(VertexConsumer target, TextureAtlas atlas, Set<ResourceLocation> used) {
            this.target = target;
            this.atlas = atlas;
            this.used = used;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            target.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            target.setUv(u, v);
            sumU += u;
            sumV += v;
            if (++vertices == 4) {
                // A quad's UV centroid is inside its sprite; edge UVs can hit a neighbour.
                float centerU = sumU / 4;
                float centerV = sumV / 4;
                for (TextureAtlasSprite sprite : ANIMATED.get(cell(centerV) * GRID_SIZE + cell(centerU))) {
                    if (centerU > sprite.getU0() && centerU < sprite.getU1()
                        && centerV > sprite.getV0() && centerV < sprite.getV1()) {
                        // Sodium marks getSprite results active. Use that existing hook without
                        // linking optional Sodium classes or changing its global animation setting.
                        if (used.add(sprite.contents().name())) atlas.getSprite(sprite.contents().name());
                        break;
                    }
                }
                vertices = 0;
                sumU = 0;
                sumV = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            target.setNormal(x, y, z);
            return this;
        }
    }
}
