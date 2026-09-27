package com.nododiiiii.ponderer.interfaces.sandbox;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Picks the "sandbox origin": a position the real client cannot have loaded. Any mod code
 * that still looks the target up through {@code Minecraft.level} then finds an unloaded
 * chunk and fails instead of binding to a real block entity.
 */
public final class SandboxCoordinates {

    private static final int FAR = 29_000_000;
    private static final int STEP = 4_096;
    private static final int CANDIDATES = 16;

    private SandboxCoordinates() {
    }

    /** Returns the first far candidate whose chunk is not loaded, or null if all are loaded. */
    @Nullable
    public static BlockPos pickOrigin(ClientLevel realLevel) {
        int y = Mth.clamp(64, realLevel.getMinBuildHeight() + 8, realLevel.getMaxBuildHeight() - 16);
        for (int i = 0; i < CANDIDATES; i++) {
            int sign = (i & 1) == 0 ? 1 : -1;
            int x = sign * (FAR - (i / 2) * STEP);
            int z = (i & 2) == 0 ? FAR - (i / 4) * STEP : -(FAR - (i / 4) * STEP);
            if (!isLoaded(realLevel, x, z) && !isLoaded(realLevel, x + 16, z + 16)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    /** True when the real client has the chunk at the given block column loaded. */
    public static boolean isLoaded(ClientLevel realLevel, int blockX, int blockZ) {
        return realLevel.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockX),
            SectionPos.blockToSectionCoord(blockZ));
    }
}
