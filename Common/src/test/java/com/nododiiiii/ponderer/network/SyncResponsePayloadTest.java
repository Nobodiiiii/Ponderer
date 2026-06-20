package com.nododiiiii.ponderer.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncResponsePayloadTest {

    @Test
    void roundTripsModeReasonAndDeletes() {
        SyncResponsePayload payload = new SyncResponsePayload(
            List.of(new SyncResponsePayload.FileEntry("ponderer:test", "pack_a", new byte[] {1, 2, 3})),
            List.of(new SyncResponsePayload.FileEntry("ponderer:structure", null, new byte[] {4, 5})),
            List.of(new SyncResponsePayload.DeleteEntry("ponderer:old_scene", "pack_a")),
            List.of(new SyncResponsePayload.DeleteEntry("ponderer:old_structure", null)),
            true,
            2,
            "check",
            "auto");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        SyncResponsePayload.CODEC.encode(buffer, payload);

        SyncResponsePayload decoded = SyncResponsePayload.CODEC.decode(buffer);
        assertEquals(payload.scripts().size(), decoded.scripts().size());
        assertEquals(payload.scripts().get(0).id(), decoded.scripts().get(0).id());
        assertEquals(payload.scripts().get(0).pack(), decoded.scripts().get(0).pack());
        assertArrayEquals(payload.scripts().get(0).bytes(), decoded.scripts().get(0).bytes());
        assertEquals(payload.structures().size(), decoded.structures().size());
        assertArrayEquals(payload.structures().get(0).bytes(), decoded.structures().get(0).bytes());
        assertEquals(payload.deletedScripts(), decoded.deletedScripts());
        assertEquals(payload.deletedStructures(), decoded.deletedStructures());
        assertEquals(payload.finalChunk(), decoded.finalChunk());
        assertEquals(payload.serverSkippedCount(), decoded.serverSkippedCount());
        assertEquals(payload.mode(), decoded.mode());
        assertEquals(payload.reason(), decoded.reason());
    }
}
