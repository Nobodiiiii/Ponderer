package com.nododiiiii.ponderer.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncResponsePayloadTest {
    @TempDir
    Path tempDir;

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

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.encode(buffer);

        SyncResponsePayload decoded = SyncResponsePayload.decode(buffer);
        assertEquals(payload.scripts().size(), decoded.scripts().size());
        assertEquals(payload.scripts().get(0).id(), decoded.scripts().get(0).id());
        assertEquals(payload.scripts().get(0).pack(), decoded.scripts().get(0).pack());
        assertArrayEquals(payload.scripts().get(0).bytes(), decoded.scripts().get(0).bytes());
        assertEquals(payload.structures().size(), decoded.structures().size());
        assertEquals(payload.deletedScripts(), decoded.deletedScripts());
        assertEquals(payload.deletedStructures(), decoded.deletedStructures());
        assertEquals(payload.finalChunk(), decoded.finalChunk());
        assertEquals(payload.serverSkippedCount(), decoded.serverSkippedCount());
        assertEquals(payload.mode(), decoded.mode());
        assertEquals(payload.reason(), decoded.reason());
    }

    @Test
    void detectsIdenticalLocalContent() throws Exception {
        Path file = tempDir.resolve("scene.json");
        byte[] bytes = new byte[] {1, 2, 3, 4};
        Files.write(file, bytes);

        assertTrue(SyncResponsePayload.hasSameLocalContent(file, bytes));
    }

    @Test
    void rejectsDifferentOrMissingLocalContent() throws Exception {
        Path file = tempDir.resolve("scene.json");
        Files.write(file, new byte[] {1, 2, 3, 4});

        assertFalse(SyncResponsePayload.hasSameLocalContent(file, new byte[] {4, 3, 2, 1}));
        assertFalse(SyncResponsePayload.hasSameLocalContent(tempDir.resolve("missing.json"), new byte[] {1}));
    }
}
