package com.nododiiiii.ponderer.runtime.core;

import com.nododiiiii.ponderer.runtime.api.PlaybackSession;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import java.util.ArrayList;
import java.util.List;

public final class TimelinePlayback implements PlaybackSession {
        final List<List<PonderInstruction>> steps = new ArrayList<>();
        private int previewStep = -1;

        public void previewStep(int index) {
            previewStep = index;
        }

        public boolean isPreviewingStep() {
            return previewStep >= 0;
        }

        public boolean isStepComplete() {
            return isPreviewingStep() && steps.get(previewStep).stream().allMatch(PonderInstruction::isComplete);
        }

        public void resumeTimeline() {
            previewStep = -1;
        }

        boolean pausesStep(int index) {
            return isPreviewingStep() && index > previewStep;
        }
    }
