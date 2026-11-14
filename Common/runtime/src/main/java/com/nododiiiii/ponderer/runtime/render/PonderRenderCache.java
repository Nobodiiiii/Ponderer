package com.nododiiiii.ponderer.runtime.render;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.render.SuperByteBufferCache;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Reclaims Ponder's scene geometry through its public cache invalidation API.
 * Accesses are recorded on the render thread, including projector and navigation
 * transition renders. Weak owners allow collection of scenes without abandoning
 * their cache keys; idle eviction also covers sections removed from a live scene.
 * Temporary navigation and replay retain entries until idle eviction or Ponder's
 * own redraw invalidation. Immediate release is reserved for discarded scenes.
 */
public final class PonderRenderCache {

    private static final long MAX_IDLE_MILLIS = 10 * 60_000;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000;
    private static final Map<Pair<Integer, Integer>, CachedSection> SECTIONS = new HashMap<>();
    private static long nextCleanup;
    @Nullable
    private static ClientLevel clientLevel;

    private PonderRenderCache() {
    }

    public static void record(PonderLevel world, WorldSectionElementImpl section, RenderType layer) {
        // Matches WorldSectionElementImpl.renderLayer in Ponder 1.0.69 and 1.0.82.
        Pair<Integer, Integer> key = Pair.of(section.hashCode() ^ world.hashCode(),
            RenderType.chunkBufferLayers().indexOf(layer));
        CachedSection cached = SECTIONS.get(key);
        if (cached == null || cached.world.get() != world) {
            cached = new CachedSection(world);
            SECTIONS.put(key, cached);
        }
        cached.lastUsed = Util.getMillis();
    }

    public static void releaseDiscardedScene(PonderScene scene) {
        PonderLevel world = scene.getWorld();
        if (world == null) {
            return;
        }
        Iterator<Map.Entry<Pair<Integer, Integer>, CachedSection>> iterator = SECTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Pair<Integer, Integer>, CachedSection> entry = iterator.next();
            if (entry.getValue().world.get() == world) {
                invalidate(entry.getKey());
                iterator.remove();
            }
        }
    }

    public static void tick() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel != clientLevel) {
            releaseAll();
            clientLevel = currentLevel;
        }
        if (currentLevel == null) {
            releaseAll();
            return;
        }

        long now = Util.getMillis();
        if (now < nextCleanup) {
            return;
        }
        nextCleanup = now + CLEANUP_INTERVAL_MILLIS;
        Iterator<Map.Entry<Pair<Integer, Integer>, CachedSection>> iterator = SECTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Pair<Integer, Integer>, CachedSection> entry = iterator.next();
            CachedSection cached = entry.getValue();
            if (cached.world.get() == null || now - cached.lastUsed >= MAX_IDLE_MILLIS) {
                invalidate(entry.getKey());
                iterator.remove();
            }
        }
    }

    private static void releaseAll() {
        SECTIONS.keySet().forEach(PonderRenderCache::invalidate);
        SECTIONS.clear();
        nextCleanup = 0;
    }

    private static void invalidate(Pair<Integer, Integer> key) {
        SuperByteBufferCache.getInstance().invalidate(WorldSectionElementImpl.PONDER_WORLD_SECTION, key);
    }

    private static final class CachedSection {
        private final WeakReference<PonderLevel> world;
        private long lastUsed;

        private CachedSection(PonderLevel world) {
            this.world = new WeakReference<>(world);
        }
    }
}
