package com.nododiiiii.ponderer.projector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectorBlockEntityPlaybackStateTest {

    @Test
    void manualLoopAutoplayIgnoresRedstoneState() {
        assertTrue(ProjectorBlockEntity.shouldClientAutoplay(ProjectorTriggerMode.MANUAL_LOOP, false));
        assertTrue(ProjectorBlockEntity.shouldClientAutoplay(ProjectorTriggerMode.MANUAL_LOOP, true));
    }

    @Test
    void poweredLoopAutoplayStillDependsOnRedstone() {
        assertFalse(ProjectorBlockEntity.shouldClientAutoplay(ProjectorTriggerMode.REDSTONE_POWERED_LOOP, false));
        assertTrue(ProjectorBlockEntity.shouldClientAutoplay(ProjectorTriggerMode.REDSTONE_POWERED_LOOP, true));
    }

    @Test
    void redstoneOnlySyncDoesNotInvalidatePlaybackStateToken() {
        assertFalse(ProjectorBlockEntity.shouldBumpClientPlaybackStateToken(false, false, true, false));
    }

    @Test
    void playbackDefinitionChangesStillInvalidatePlaybackStateToken() {
        assertTrue(ProjectorBlockEntity.shouldBumpClientPlaybackStateToken(true, false, false, false));
        assertTrue(ProjectorBlockEntity.shouldBumpClientPlaybackStateToken(false, true, false, false));
        assertTrue(ProjectorBlockEntity.shouldBumpClientPlaybackStateToken(false, false, false, true));
    }
}
