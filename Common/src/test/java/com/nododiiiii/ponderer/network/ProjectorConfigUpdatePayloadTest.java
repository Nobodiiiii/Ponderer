package com.nododiiiii.ponderer.network;

import com.nododiiiii.ponderer.projector.ProjectorTriggerMode;
import com.nododiiiii.ponderer.projector.ProjectorProjectionMode;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectorConfigUpdatePayloadTest {

    @Test
    void roundTripsOverlayAntiOcclusionFlag() {
        ProjectorConfigUpdatePayload payload = new ProjectorConfigUpdatePayload(
            new BlockPos(3, 64, -8),
            List.of("ponderer:test_scene"),
            ProjectorTriggerMode.REDSTONE_POWERED_LOOP,
            new BlockPos(1, 2, 3),
            240,
            30,
            false,
            false,
            false,
            ProjectorProjectionMode.TEXT_ONLY,
            0.75F,
            1.5F);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.encode(buffer);

        ProjectorConfigUpdatePayload decoded = ProjectorConfigUpdatePayload.decode(buffer);
        assertEquals(payload, decoded);
    }
}
